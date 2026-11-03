package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeId
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniTemplateProcessorJournalTest {
    companion object {
        private var constructions = 0
        private var expansions = 0
        private var annotations = 0
        private var getters = 0
        private var unknownCalls = 0
        @JvmStatic fun getter(context: NativeCallContext) {
            getters++
            error("Journal executed a getter")
        }
    }
    class Processor : TemplateProcessor {
        init {
            constructions++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Journal executed a processor")
        }
    }
    class OtherProcessor : TemplateProcessor {
        init {
            constructions++
        }
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits {
            expansions++
            error("Journal executed another processor")
        }
    }
    private class Marker : DataTemplateAnnotation("Marker", "journal.processor") {
        override fun forDataTemplate(data: DataTemplate) {
            annotations++
        }
    }
    private class Unknown {
        override fun hashCode(): Int {
            unknownCalls++
            error("Unknown hash")
        }
        override fun equals(other: Any?): Boolean {
            unknownCalls++
            error("Unknown equality")
        }
        override fun toString(): String {
            unknownCalls++
            error("Unknown display")
        }
    }

    private inline fun isolated(action: (Namespace) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        try {
            Project.init()
            constructions = 0
            expansions = 0
            annotations = 0
            getters = 0
            unknownCalls = 0
            Function.currFunction = Function("sentinel", "journal.processor", null)
            val namespace = Namespace("journal.processor").also { GlobalScope.localNamespaces[it.identifier] = it }
            action(namespace)
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
            assertEquals(listOf(0, 0, 0, 0, 0), listOf(constructions, expansions, annotations, getters, unknownCalls))
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
        }
    }
    private fun nodes(tree: ParseTree): List<ParseTree> = listOf(tree) +
        (0 until tree.childCount).flatMap { nodes(tree.getChild(it)) }
    private fun record(namespace: Namespace, name: String, fileOrder: Int = 0, repeated: Boolean = false): List<TemplateProcessorRequest> {
        assertTrue(namespace.registerTemplateProcessor("journal.processor:Mark", Processor::class.java))
        val parser = P(
            CommonTokenStream(
                mcfppLexer(
                    CharStreams.fromString(
                        "namespace journal.processor\n@Mark\n" + (if (repeated) "@Mark\n" else "") + "data $name{}"
                    )
                )
            )
        )
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val declaration = nodes(tree).filterIsInstance<P.TemplateDeclarationContext>().single()
        val inputs = nodes(tree).filterIsInstance<P.AnnotationContext>()
        val file = MCFPPFile(namespace)
        val target = DataTemplate(name, namespace.identifier).apply { declarationFile = file }
        val registration = namespace.templateProcessors.find("journal.processor:Mark")!!
        return inputs.mapIndexed { ordinal, annotation ->
            assertNotNull(
                namespace.templateProcessors.record(
                    registration,
                    target,
                    file,
                    declaration,
                    annotation,
                    listOf(CompilerValue.Typed(TypeId.Builtin("int"), CompilerValue.Integral(ordinal.toLong()))),
                    TemplateProcessorSourceOrder(fileOrder, declaration.start.tokenIndex, ordinal)
                )
            )
        }
    }
    private fun field(target: DataTemplate, name: String = "value"): Pair<MCInt, Property> {
        val value = MCInt(name).also { it.declaredParentTemplate = target }
        val native = NativeFunction("get_$name", target.namespace).apply {
            returnType = MCFPPBaseType.Int
            owner = target
            javaMethod = MniTemplateProcessorJournalTest::class.java.getMethod("getter", NativeCallContext::class.java)
            javaMethodName = "getter"
        }
        val property = Property(name, NativeAccessor(native), null).also { it.declaredParentTemplate = target }
        target.scope.putVar(name, value)
        target.scope.putProperty(name, property)
        return value to property
    }

    /** Compose the actual journals, restoring deep state before registration relationships. */
    private class Combined(val namespace: NamespaceStateJournal, val deep: DeclarationStateJournal) {
        fun restore() {
            deep.restore()
            namespace.restore()
        }
        fun captureCurrent(): Combined {
            val current = namespace.captureCurrent()
            return Combined(
                current,
                DeclarationStateJournal.checkpoint(
                    ArrayList<Any>().apply {
                        addAll(current.elements)
                        addAll(deep.elements)
                    }
                )
            )
        }
        companion object {
            fun checkpoint(): Combined {
                val namespace = NamespaceStateJournal.checkpoint()
                return Combined(namespace, DeclarationStateJournal.checkpoint(namespace.elements))
            }
        }
    }

    @Test fun actualRegistrationsAndOrderedRequestsRestoreBothDirectionsWithoutExecution() = isolated { namespace ->
        val before = NamespaceStateJournal.checkpoint()
        val late = record(namespace, "Late", fileOrder = 1).single()
        val early = record(namespace, "Early", repeated = true)
        early.first().target.annotations.add(Marker())
        assertTrue(namespace.registerTemplateProcessor("journal.processor:Other", OtherProcessor::class.java))
        val after = before.captureCurrent()
        val expected = early + late
        repeat(3) {
            before.restore()
            assertTrue(namespace.templateProcessors.registrations().isEmpty())
            assertTrue(namespace.templateProcessors.requests().isEmpty())
            after.restore()
            assertEquals(expected, namespace.templateProcessors.requests())
            assertSame(OtherProcessor::class.java, namespace.templateProcessors.find("journal.processor:Other")!!.processorClass)
            assertSame(
                early.first(),
                namespace.templateProcessors.record(
                    early.first().registration,
                    early.first().target,
                    early.first().sourceFile,
                    early.first().declarationSyntax,
                    early.first().annotationSyntax,
                    early.first().arguments,
                    early.first().order
                )
            )
        }
        assertEquals(2, after.sourceFiles.size, "File roots use identity even when their physical paths compare equal")
        expected.forEach { request ->
            assertTrue(after.sourceFiles.any { it === request.sourceFile })
            assertTrue(after.elements.any { it === request.target })
            assertTrue(after.elements.any { it === request.target.scope })
            assertTrue(after.elements.any { it === request.arguments.single() })
        }
        assertFalse(
            after.elements.any {
                it is MCFPPFile ||
                    it is ParseTree ||
                    it is TemplateProcessorRegistry ||
                    it is TemplateProcessorRequest ||
                    it is TemplateProcessorRegistration ||
                    it === Processor::class.java
            }
        )
        assertFailsWith<UnsupportedOperationException> { (after.sourceFiles as MutableList<*>).clear() }
    }

    @Test fun lostRequestsKeepAllInputRootsAcrossNamespaceRemovalResetAndMoreThanTwoGenerations() = isolated { namespace ->
        val first = record(namespace, "First").single()
        val before = NamespaceStateJournal.checkpoint()
        val second = record(namespace, "Second", 1).single()
        val after = before.captureCurrent()
        before.restore()
        GlobalScope.localNamespaces.remove(namespace.identifier)
        val removed = after.captureCurrent()
        Project.init()
        val reset = removed.captureCurrent()
        repeat(2) {
            after.restore()
            assertEquals(listOf(first, second), namespace.templateProcessors.requests())
            reset.restore()
            assertFalse(GlobalScope.localNamespaces.containsKey(namespace.identifier))
            assertEquals(listOf(first), namespace.templateProcessors.requests())
            before.restore()
            assertSame(namespace, GlobalScope.localNamespaces[namespace.identifier])
        }
        for (request in listOf(first, second)) {
            assertTrue(reset.sourceFiles.any { it === request.sourceFile })
            assertTrue(reset.elements.any { it === request.target })
            assertTrue(reset.elements.any { it === request.sourceFile.field })
            assertTrue(reset.elements.any { it === request.sourceFile.topFunction })
        }
        // The detached request retains the exact parser inputs and registration, never rebuilt facts.
        after.restore()
        assertSame(second, namespace.templateProcessors.requests()[1])
        assertSame(second.annotationSyntax, namespace.templateProcessors.requests()[1].annotationSyntax)
        assertSame(second.declarationSyntax, namespace.templateProcessors.requests()[1].declarationSyntax)
    }

    @Test fun declarationCompositionRestoresOldAndNewTargetFieldsPropertiesAndLostRoots() = isolated { namespace ->
        val first = record(namespace, "First").single()
        val (oldValue, oldProperty) = field(first.target)
        val topName = first.sourceFile.topFunction.identifier
        val imported = NamespaceScope("processor.imported")
        val before = Combined.checkpoint()
        oldValue.identifier = "changed"
        oldProperty.accessModifier = Member.AccessModifier.PRIVATE
        first.sourceFile.topFunction.identifier = "changed_top"
        first.sourceFile.field.importedNamespaceField["added"] = imported
        val second = record(namespace, "Second", 1).single()
        val (newValue, newProperty) = field(second.target)
        val after = before.captureCurrent()
        before.restore()
        assertEquals("value", oldValue.identifier)
        assertEquals(Member.AccessModifier.PUBLIC, oldProperty.accessModifier)
        assertEquals(topName, first.sourceFile.topFunction.identifier)
        assertTrue(first.sourceFile.field.importedNamespaceField.isEmpty())
        assertEquals(listOf(first), namespace.templateProcessors.requests())
        // Mutate the target which is no longer reachable from live requests, then recapture it.
        newValue.identifier = "detached"
        newProperty.accessModifier = Member.AccessModifier.PROTECTED
        second.target.scope.putVar("extra", MCInt("extra"))
        val lost = after.captureCurrent()
        repeat(3) {
            after.restore()
            assertEquals(listOf(first, second), namespace.templateProcessors.requests())
            assertEquals("changed", oldValue.identifier)
            assertEquals(Member.AccessModifier.PRIVATE, oldProperty.accessModifier)
            assertEquals("changed_top", first.sourceFile.topFunction.identifier)
            assertSame(imported, first.sourceFile.field.importedNamespaceField["added"])
            assertEquals("value", newValue.identifier)
            assertEquals(Member.AccessModifier.PUBLIC, newProperty.accessModifier)
            assertNull(second.target.scope.getVar("extra"))
            assertSame(newValue, second.target.scope.getVar("value"))
            assertSame(newProperty, second.target.scope.getProperty("value"))
            lost.restore()
            assertEquals(listOf(first), namespace.templateProcessors.requests())
            assertEquals(topName, first.sourceFile.topFunction.identifier)
            assertTrue(first.sourceFile.field.importedNamespaceField.isEmpty())
            assertEquals("detached", newValue.identifier)
            assertEquals(Member.AccessModifier.PROTECTED, newProperty.accessModifier)
            assertNotNull(second.target.scope.getVar("extra"))
        }
        assertTrue(lost.namespace.elements.any { it === second.target })
    }

    @Test fun preparedCommitRevokesLiveRequestsUntilApplyAndSupportsRepeatedRollback() = isolated { namespace ->
        val first = record(namespace, "First").single()
        val (value, property) = field(first.target)
        val before = Combined.checkpoint()
        lateinit var second: TemplateProcessorRequest
        lateinit var afterRegistrations: NamespaceStateJournal
        val commit = try {
            before.deep.prepare {
                value.identifier = "prepared"
                property.accessModifier = Member.AccessModifier.PRIVATE
                second = record(namespace, "Second", 1).single()
                field(second.target)
                assertTrue(namespace.registerTemplateProcessor("journal.processor:Other", OtherProcessor::class.java))
                afterRegistrations = before.namespace.captureCurrent()
            }
        } finally {
            before.namespace.restore()
        }
        assertEquals(listOf(first), namespace.templateProcessors.requests())
        assertEquals("value", value.identifier)
        assertNull(namespace.templateProcessors.find("journal.processor:Other"))
        repeat(2) {
            commit.apply()
            afterRegistrations.restore()
            assertEquals(listOf(first, second), namespace.templateProcessors.requests())
            assertEquals("prepared", value.identifier)
            assertEquals(Member.AccessModifier.PRIVATE, property.accessModifier)
            assertNotNull(second.target.scope.getVar("value"))
            commit.rollback()
            before.namespace.restore()
            assertEquals(listOf(first), namespace.templateProcessors.requests())
            assertEquals("value", value.identifier)
            assertEquals(Member.AccessModifier.PUBLIC, property.accessModifier)
            assertNull(namespace.templateProcessors.find("journal.processor:Other"))
        }
    }

    @Test fun failedPreparationRestoresRelationsAndDeepTargetsBeforeAnyCommitEscapes() = isolated { namespace ->
        val first = record(namespace, "First").single()
        val (value, property) = field(first.target)
        val before = Combined.checkpoint()
        assertFailsWith<IllegalArgumentException> {
            try {
                before.deep.prepare {
                    value.identifier = "failed"
                    property.accessModifier = Member.AccessModifier.PRIVATE
                    record(namespace, "Failed", 1)
                    assertTrue(namespace.registerTemplateProcessor("journal.processor:Other", OtherProcessor::class.java))
                    throw IllegalArgumentException("failed detached declaration")
                }
            } finally {
                before.namespace.restore()
            }
        }
        assertEquals(listOf(first), namespace.templateProcessors.requests())
        assertEquals(1, namespace.templateProcessors.registrations().size)
        assertEquals("value", value.identifier)
        assertEquals(Member.AccessModifier.PUBLIC, property.accessModifier)
    }

    @Suppress("UNCHECKED_CAST")
    private fun inputs(namespace: Namespace): MutableList<Any?> = TemplateProcessorRegistry::class.java
        .getDeclaredField("inputs").apply { isAccessible = true }.get(namespace.templateProcessors) as MutableList<Any?>

    @Test fun erasedArgumentPayloadIsRejectedBeforeUnknownHashEqualityOrDisplay() = isolated { namespace ->
        val request = record(namespace, "First").single()
        val before = NamespaceStateJournal.checkpoint()
        @Suppress("UNCHECKED_CAST")
        val payload = CompilerValue.Sequence(listOf(Unknown()) as List<CompilerValue>)
        val invalid = TemplateProcessorRequest(
            request.registration,
            request.target,
            request.sourceFile,
            request.declarationSyntax,
            request.annotationSyntax,
            listOf(CompilerValue.Typed(TypeId.Builtin("list"), payload)),
            request.order
        )
        inputs(namespace)[0] = invalid
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertSame(invalid, inputs(namespace).single())
        before.restore()
        assertSame(request, namespace.templateProcessors.requests().single())
    }

    @Test fun erasedRawRequestFailsClosedWithoutCallingAnUnknownHostObject() = isolated { namespace ->
        val request = record(namespace, "First").single()
        val before = NamespaceStateJournal.checkpoint()
        val unknown = Unknown()
        inputs(namespace)[0] = unknown
        assertFails { before.captureCurrent() }
        assertSame(unknown, inputs(namespace).single())
        before.restore()
        assertSame(request, namespace.templateProcessors.requests().single())
    }
}
