package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.annotation.Name
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniAnnotationDeclarationStateTest {
    private fun isolated(action: (Namespace) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val function = Function.currFunction
        val forced = Function.forcedField
        val file = MCFPPFile.currFile
        val template = DataTemplate.currTemplate
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString("func main(){}", version = "26.3")
            val namespace = GlobalScope.getOrCreateNamespace("annotation.state")
            namespace.scope.annotations["Generate"] = Generate::class.java
            Generate.observed = 0
            Generate.constructed = 0
            Generate.callbacks = 0
            action(namespace)
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = function
            Function.forcedField = forced
            MCFPPFile.currFile = file
            DataTemplate.currTemplate = template
            CompileSettings.ignoreStdLib = ignore
        }
    }

    private fun annotation() = Annotation.build(Generate::class.java, arrayListOf()) as Generate
    internal fun portableDeclarationState(action: (Namespace) -> Unit) = isolated(action)
    private fun target(namespace: Namespace, annotation: Generate): DataTemplate = DataTemplate("Target", namespace.identifier).apply {
        declarationFile = null
        namespace.scope.addTemplate(identifier, this, false)
        pendingDeclarationAnnotations.add(annotation)
    }

    @Test fun registeredExtensionCaptureDoesNotRunConstructorsActionsGettersOrHashes() = isolated { _ ->
        val annotation = annotation()
        assertEquals(1, Generate.constructed)
        val facts = annotation.preparationFacts()
        val before = DeclarationStateJournal.checkpoint(listOf(annotation))
        assertEquals(facts, annotation.preparationFacts())
        facts.hashCode()
        before.captureCurrent().restore()
        assertEquals(1, Generate.constructed)
        assertEquals(0, Generate.observed)
        assertEquals(0, Generate.callbacks)
    }

    @Test fun realDeclarationBarrierExecutesRegisteredExtensionOnceAndGeneratesAField() = isolated { namespace ->
        val annotation = annotation()
        val target = target(namespace, annotation)
        DeclarationStateJournal.checkpoint(listOf(target))
        assertEquals(0, Generate.observed)
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        val field = assertNotNull(target.scope.getVar("made"))
        assertSame(target, field.declaredParentTemplate)
        assertNotNull(target.scope.getProperty("made"))
        repeat(2) { Project.registerTemplateContract(target) }
        assertEquals(1, Generate.observed)
        assertEquals(1, annotation.applications)
    }

    @Test fun successfulPrepareAppliesActualAfterStateWithoutExecutingAnnotationAgain() = isolated { namespace ->
        val annotation = annotation()
        val target = target(namespace, annotation)
        val before = CompilerStateJournal.checkpoint()
        val prepared = before.prepare { Project.registerTemplateContract(target) }
        assertEquals(1, Generate.observed)
        assertEquals(0, annotation.applications)
        assertNull(target.scope.getVar("made"))
        assertFalse(target.hasCompletedDeclaration)
        repeat(2) {
            prepared.apply()
            assertEquals(1, annotation.applications)
            assertEquals(1, Generate.observed)
            assertNotNull(target.scope.getVar("made"))
            assertTrue(target.hasCompletedDeclaration)
            prepared.rollback()
            assertEquals(0, annotation.applications)
            assertNull(target.scope.getVar("made"))
        }
    }

    @Test fun failedRealBarrierRestoresPayloadMembersAndAllowsACleanRetry() = isolated { namespace ->
        val annotation = annotation()
        val target = target(namespace, annotation)
        val before = CompilerStateJournal.checkpoint()
        assertFailsWith<IllegalStateException> {
            before.prepare {
                annotation.fail = true
                Project.registerTemplateContract(target)
            }
        }
        assertEquals(1, Generate.observed, "External observations are intentionally not rolled back")
        assertEquals(0, annotation.applications)
        assertFalse(annotation.fail)
        assertNull(target.scope.getVar("made"))
        assertNull(target.scope.getProperty("made"))
        assertSame(annotation, target.pendingDeclarationAnnotations.single())
        Project.registerTemplateContract(target)
        assertTrue(target.hasCompletedDeclaration)
        assertNotNull(target.scope.getVar("made"))
        assertEquals(2, Generate.observed)
        assertEquals(1, annotation.applications)
    }

    @Test fun sharedPayloadContainersAndReplacedFieldsRestoreTheirOriginalIdentities() = isolated { _ ->
        val annotation = annotation()
        val original = annotation.labels
        annotation.payload = linkedMapOf("labels" to original)
        val map = annotation.payload
        val before = annotation.checkpointDeclarationState()
        annotation.labels = arrayListOf("new")
        annotation.second = annotation.labels
        original.add("after")
        annotation.payload = null
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(original, annotation.labels)
            assertSame(original, annotation.second)
            assertSame(map, annotation.payload)
            assertEquals(listOf("before"), original)
            after.restore()
            assertEquals(listOf("before", "after"), original)
            assertSame(annotation.labels, annotation.second)
            assertNull(annotation.payload)
        }
    }

    @Test fun deepPayloadReferencesRemainCapturedAfterTheirContainerIsDetached() = isolated { _ ->
        val annotation = annotation()
        val declaration = DataTemplate("Owned", "annotation.state").apply { declarationFile = null }
        val payload = arrayListOf<Any>(declaration)
        annotation.payload = payload
        val before = DeclarationStateJournal.checkpoint(listOf(annotation))
        annotation.payload = null
        declaration.identifier = "after"
        payload.clear()
        val after = before.captureCurrent()
        declaration.identifier = "third"
        val third = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertEquals("Owned", declaration.identifier)
            assertSame(declaration, payload.single())
            after.restore()
            assertEquals("after", declaration.identifier)
            assertTrue(payload.isEmpty())
            third.restore()
            assertEquals("third", declaration.identifier)
        }
    }

    @Test fun cyclicClosedContainersHaveStableFactsAndCanBeRestored() = isolated { _ ->
        val annotation = annotation()
        val cycle = arrayListOf<Any>()
        cycle.add(cycle)
        annotation.payload = cycle
        val facts = annotation.preparationFacts()
        val before = annotation.checkpointDeclarationState()
        assertEquals(facts, annotation.preparationFacts())
        facts.hashCode()
        cycle.clear()
        before.restore()
        assertSame(cycle, cycle.single())
    }

    @Test fun oldFactsAreImmutableAcrossScalarAndNestedContainerMutations() = isolated { _ ->
        val annotation = annotation()
        annotation.payload = linkedMapOf("fact" to CompilerValue.Text("old"), "list" to annotation.labels)
        val facts = annotation.preparationFacts()
        val hash = facts.hashCode()
        annotation.fieldName = "other"
        annotation.labels.add("new")
        assertNotEquals(facts, annotation.preparationFacts())
        assertEquals(hash, facts.hashCode())
    }

    @Test fun revokedRegistrationRejectsNewCaptureButRetainedCheckpointStillRestores() = isolated { namespace ->
        val annotation = annotation()
        val before = DeclarationStateJournal.checkpoint(listOf(annotation))
        namespace.scope.annotations.remove("Generate")
        annotation.fieldName = "after"
        assertFalse(annotation.hasDeclarationStateSupport())
        assertFailsWith<IllegalStateException> { annotation.preparationFacts() }
        val after = before.captureCurrent()
        before.restore()
        assertEquals("made", annotation.fieldName)
        after.restore()
        assertEquals("after", annotation.fieldName)
    }

    @Test fun unregisteredSubclassesAreRejectedWithoutCallingTheirOverrides() = isolated { _ ->
        val unknown = object : DataTemplateAnnotation("Unknown", "annotation.state") {
            override fun forDataTemplate(data: DataTemplate) {
                error("must not execute")
            }
            override fun hashCode(): Int {
                Generate.callbacks++
                return 0
            }
        }
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(unknown)) }
        assertEquals(0, Generate.callbacks)
    }

    @Test fun unknownPayloadsAndClosuresAreRejectedBeforeHostCallbacks() = isolated { _ ->
        val annotation = annotation()
        val host = object {
            override fun hashCode(): Int {
                Generate.callbacks++
                return 0
            }
            override fun equals(other: Any?): Boolean {
                Generate.callbacks++
                return false
            }
            override fun toString(): String {
                Generate.callbacks++
                return "host"
            }
        }
        for (payload in listOf(host, { Generate.callbacks++ })) {
            annotation.payload = payload
            assertFailsWith<IllegalStateException> { annotation.preparationFacts() }
            assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(annotation)) }
        }
        assertEquals(0, Generate.callbacks)
    }

    @Test fun unknownContainersAreRejectedBeforeTheirCollectionMethodsRun() = isolated { _ ->
        val annotation = annotation()
        annotation.payload = object : ArrayList<String>() {
            override val size: Int get() {
                Generate.callbacks++
                return super.size
            }
            override fun iterator(): MutableIterator<String> {
                Generate.callbacks++
                return super.iterator()
            }
        }
        assertFailsWith<IllegalStateException> { annotation.checkpointDeclarationState() }
        annotation.payload = Collections.unmodifiableList(arrayListOf("borrowed"))
        assertFailsWith<IllegalStateException> { annotation.preparationFacts() }
        assertEquals(0, Generate.callbacks)
    }

    @Test fun detachedPayloadIsPreflightedBeforeLowerJournalsReadAnUnknownType() = isolated { _ ->
        val annotation = annotation()
        val payload = arrayListOf<Any>()
        annotation.payload = payload
        val before = DeclarationStateJournal.checkpoint(listOf(annotation))
        annotation.payload = null
        payload.add(object : MCFPPType(arrayListOf()) {
            override val typeId: TypeId get() {
                Generate.callbacks++
                return TypeId.Builtin("unknown")
            }
        })
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, Generate.callbacks)
    }

    @Test fun malformedBaseRolesAreCheckedBeforeErasedMembersAreConsumed() = isolated { _ ->
        val annotation = annotation()
        @Suppress("UNCHECKED_CAST")
        (annotation.parents as MutableList<Any>).add("wrong")
        val failure = assertFailsWith<IllegalStateException> { annotation.checkpointDeclarationState() }
        assertTrue(failure.message.orEmpty().contains("parent"))
        assertEquals(0, Generate.callbacks)
    }

    @Test fun builtinPayloadFieldsAndParentAnnotationsRemainCompatible() = isolated { _ ->
        val annotation = Name("original")
        annotation.parents.add(Name("parent"))
        annotation.params.add("int")
        val before = DeclarationStateJournal.checkpoint(listOf(annotation))
        val field = Name::class.java.getDeclaredField("name").apply { isAccessible = true }
        field.set(annotation, "changed")
        annotation.parents.clear()
        annotation.params.clear()
        before.restore()
        assertEquals("original", field.get(annotation))
        assertEquals(1, annotation.parents.size)
        assertEquals("int", annotation.params.single())
    }

    class Generate private constructor(restoring: Boolean) : DataTemplateAnnotation("Generate", "annotation.state") {
        constructor() : this(false)
        var fieldName = "made"
        var applications = 0
        var fail = false
        var labels = arrayListOf("before")
        var second: List<String> = labels
        var payload: Any? = null
        val computed: String get() {
            callbacks++
            return "must-not-read"
        }
        init {
            if (!restoring) constructed++
        }
        override fun hashCode(): Int {
            callbacks++
            return 0
        }
        override fun forDataTemplate(data: DataTemplate) {
            observed++
            applications++
            val field = MCInt(fieldName).apply { declaredParentTemplate = data }
            data.scope.putVar(fieldName, field)
            data.scope.putProperty(fieldName, Property.buildSimpleProperty(field))
            if (fail) error("external annotation failed after adding its field")
        }
        companion object {
            internal fun restoreShell() = Generate(true)
            var observed = 0
            var constructed = 0
            var callbacks = 0
        }
    }
}
