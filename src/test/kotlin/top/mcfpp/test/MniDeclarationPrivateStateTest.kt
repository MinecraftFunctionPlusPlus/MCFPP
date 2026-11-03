package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.GenerationOptions
import top.mcfpp.analysis.SpecializationArgument
import top.mcfpp.analysis.SpecializationKey
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.info.DeclarationEnvironmentInfo
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.TemplatePrivateCapture
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.FunctionMutator
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.NativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniDeclarationPrivateStateTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val current = Function.currFunction
        try {
            Project.init()
            action()
        } finally {
            project.restore()
            Function.currFunction = current
        }
    }

    private fun parser(text: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun model(name: String, parent: DataTemplate? = null) = DataTemplate(name, "journal.declaration").also {
        if (parent != null) it.extends(parent)
    }
    private fun field(owner: DataTemplate): MCInt = MCInt("value").also {
        it.declaredParentTemplate = owner
        owner.scope.putVar("value", it)
        owner.scope.putProperty("value", Property.buildSimpleProperty(it))
    }
    private fun generic() = GenericDataTemplate(parser("{}").templateBody(), "Generic", "journal.declaration")
    private fun key(generic: GenericDataTemplate, value: Long) = SpecializationKey(
        generic.declarationId,
        listOf(SpecializationArgument.Constant(CompilerValue.Integral(value))),
        GenerationOptions("26.3", 0)
    )
    private fun bound(generic: GenericDataTemplate, name: String, value: Long) = CompiledGenericDataTemplate(name, "journal.declaration", generic, emptyList(), listOf(CompilerValue.Integral(value)))

    private class Marker : Annotation("Marker", "journal.declaration") {
        var invocations = 0
        override fun forFunction(function: Function) {
            invocations++
        }
        override fun forDataTemplate(data: DataTemplate) {
            invocations++
        }
        override fun forField(field: top.mcfpp.core.lang.Var<*>) {
            invocations++
        }
    }

    @Test fun anUncompletedLayoutCanBeRestoredAndCompletedAgainWithoutAStaleFlattenedParent() = isolated {
        val parent = model("Parent").also(::field)
        val child = model("Child", parent)
        val before = child.checkpointDeclarationState()
        child.flatExtends()
        val inherited = child.scope.getVar("value")!!
        val origins = child.scope.fieldOrigins.getValue("value")
        val property = child.scope.getProperty("value")!!
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertNull(child.scope.getVar("value"))
            assertTrue(child.scope.fieldOrigins.isEmpty())
            child.flatExtends()
            assertNotSame(inherited, child.scope.getVar("value"))
            after.restore()
            assertSame(inherited, child.scope.getVar("value"))
            assertSame(origins, child.scope.fieldOrigins.getValue("value"))
            assertSame(property, child.scope.getProperty("value"))
            child.flatExtends()
            assertSame(inherited, child.scope.getVar("value"))
            assertSame(property, child.scope.getProperty("value"))
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun replacingTheScopeAndConstructorsKeepsEveryOriginalContainerAndDetachedRoot() = isolated {
        val template = model("Slots")
        val original = template.scope
        val value = field(template)
        val constructors = template.constructors
        val constructor = DataTemplateConstructor(template, null)
        constructors.add(constructor)
        val before = template.checkpointDeclarationState()
        val replacement = CompoundDataScope(arrayListOf())
        template.scope = replacement
        template.constructors = arrayListOf()
        constructors.clear()
        template.restoredFlattenedFields = true
        val after = before.captureCurrent()
        assertTrue(after.elements.any { it === value })
        assertTrue(after.elements.any { it === constructor })
        repeat(2) {
            before.restore()
            assertSame(original, template.scope)
            assertSame(constructors, template.constructors)
            assertSame(constructor, constructors.single())
            assertFalse(template.restoredFlattenedFields)
            after.restore()
            assertSame(replacement, template.scope)
            assertTrue(constructors.isEmpty())
            assertTrue(template.restoredFlattenedFields)
        }
    }

    @Test fun restoredInheritedEffectsAndTheirAppliedMarkerTravelWithTheLayoutAndOrigins() = isolated {
        val parent = model("Parent").also(::field)
        val source = model("Source", parent).also { it.flatExtends() }
        (source.scope.getVar("value") as MCInt).isDataOnly = true
        val effects = source.completedInheritedFields()
        val target = model("Target", parent)
        target.restoreInheritedFields(effects)
        val before = target.checkpointDeclarationState()
        target.flatExtends()
        val inherited = target.scope.getVar("value") as MCInt
        val property = target.scope.getProperty("value")!!
        val origin = target.scope.fieldOrigins.getValue("value").single()
        assertTrue(inherited.isDataOnly)
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            target.flatExtends()
            assertTrue((target.scope.getVar("value") as MCInt).isDataOnly)
            after.restore()
            target.flatExtends()
            assertSame(inherited, target.scope.getVar("value"))
            assertSame(property, target.scope.getProperty("value"))
            assertSame(origin, target.scope.fieldOrigins.getValue("value").single())
            assertSame(parent, origin.owner)
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun completedSnapshotsExposeTheirAnnotationAndPropertyFunctionRootsWithoutInvokingThem() = isolated {
        val parent = model("Parent")
        field(parent).annotations.add(Marker())
        val getter = Function("getter", parent, null)
        val setter = Function("setter", parent, null)
        parent.scope.putProperty("value", Property("value", FunctionAccessor(getter), FunctionMutator(setter)), true)
        val source = model("Source", parent).also { it.flatExtends() }
        val target = model("Target", parent)
        target.restoreInheritedFields(source.completedInheritedFields())
        val before = target.checkpointDeclarationState()
        assertTrue(before.elements.any { it === getter })
        assertTrue(before.elements.any { it === setter })
        val marker = before.elements.filterIsInstance<Marker>().single()
        target.restoreInheritedFields(source.completedInheritedFields())
        val after = before.captureCurrent()
        assertTrue(after.elements.any { it === marker })
        before.restore()
        after.restore()
        before.restore()
        assertEquals(0, marker.invocations)
        assertTrue(getter.commands.isEmpty())
        assertTrue(setter.commands.isEmpty())
    }

    @Test fun nativeAccessorAndMutatorKeepTheirExactCanonicalFunctionWithoutHostExecution() = isolated {
        val template = model("NativeRoots")
        field(template)
        val native = NativeFunction(
            "native",
            template.namespace,
            MniDeclarationPrivateStateTest::class.java.getMethod("nativeSentinel", NativeCallContext::class.java)
        )
        val property = Property("value", NativeAccessor(native), NativeMutator(native))
        template.scope.putProperty("value", property, true)
        nativeCalls = 0
        val before = template.checkpointDeclarationState()
        template.scope.removeProperty("value")
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(property, template.scope.getProperty("value"))
            assertSame(native, (property.accessor as NativeAccessor).function)
            after.restore()
            assertNull(template.scope.getProperty("value"))
        }
        assertTrue(after.elements.any { it === native })
        assertEquals(0, nativeCalls)
    }

    @Test fun inheritanceContractDiagnosticDeduplicationIsRestoredWithoutRevalidatingDuringRestore() = isolated {
        val template = model("Abstract").apply { isAbstract = true }
        val before = template.checkpointDeclarationState()
        assertFalse(template.canInstantiate())
        assertEquals(1, Project.errorCount)
        val after = before.captureCurrent()
        repeat(2) {
            Project.errorCount = 0
            before.restore()
            assertEquals(0, Project.errorCount)
            assertFalse(template.canInstantiate())
            assertEquals(1, Project.errorCount)
            Project.errorCount = 0
            after.restore()
            assertEquals(0, Project.errorCount)
            assertFalse(template.canInstantiate())
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun fieldLayoutDiagnosticDeduplicationSurvivesAForcedHeaderRebuild() = isolated {
        val first = model("First").also(::field)
        val second = model("Second").also { field(it).isConst = true }
        val child = model("Conflict", first).also { it.extends(second) }
        val before = child.checkpointDeclarationState()
        child.flatExtends()
        assertEquals(1, Project.errorCount)
        val after = before.captureCurrent()
        // A new parent without fields rebuilds the same conflicting field requirements.
        child.extends(model("Empty"))
        repeat(2) {
            Project.errorCount = 0
            before.restore()
            child.flatExtends()
            assertEquals(1, Project.errorCount)
            Project.errorCount = 0
            after.restore()
            child.flatExtends()
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun declarationEnvironmentImportsAndPendingMetadataRestoreOriginalIdentities() = isolated {
        val template = model("Pending")
        val imports = linkedMapOf("one" to "ns.one")
        val environment = DeclarationEnvironmentInfo("journal.declaration", imports)
        val initialFile = template.declarationFile
        val expression = parser("1").expression()
        val marker = Marker()
        val annotations = arrayListOf<Annotation>(marker)
        val deferred = DataTemplate.DeferredFieldDeclaration(
            parser("var value:int").templateFieldDeclaration(),
            top.mcfpp.model.Member.AccessModifier.PUBLIC,
            annotations
        )
        template.declarationEnvironment = environment
        template.preInit["value"] = expression
        template.deferredFields["value"] = deferred
        template.pendingFieldAnnotations["value"] = annotations
        template.pendingDeclarationAnnotations.add(marker)
        val before = template.checkpointDeclarationState()
        imports.clear()
        annotations.clear()
        template.declarationFile = null
        template.declarationEnvironment = DeclarationEnvironmentInfo("other", emptyMap())
        template.preInit.clear()
        template.deferredFields.clear()
        template.pendingFieldAnnotations.clear()
        template.pendingDeclarationAnnotations.clear()
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(initialFile, template.declarationFile)
            assertSame(environment, template.declarationEnvironment)
            assertSame(imports, template.declarationEnvironment!!.imports)
            assertEquals("ns.one", imports["one"])
            assertSame(expression, template.preInit["value"])
            assertSame(deferred, template.deferredFields["value"])
            assertSame(annotations, template.pendingFieldAnnotations["value"])
            assertSame(marker, annotations.single())
            assertSame(marker, template.pendingDeclarationAnnotations.single())
            after.restore()
            assertTrue(imports.isEmpty())
            assertTrue(annotations.isEmpty())
            assertTrue(template.preInit.isEmpty())
            assertTrue(template.pendingFieldAnnotations.isEmpty())
            assertNull(template.declarationFile)
        }
        assertEquals(0, marker.invocations)
    }

    @Test fun removedAndReplacedPendingListsRemainRootsThroughThreeGenerations() = isolated {
        val template = model("PendingRoots")
        val first = Marker()
        val second = Marker()
        val third = Marker()
        val original = arrayListOf<Annotation>(first)
        template.pendingFieldAnnotations["value"] = original
        val before = template.checkpointDeclarationState()
        template.pendingFieldAnnotations["value"] = arrayListOf(second)
        original.clear()
        val after = before.captureCurrent()
        template.pendingFieldAnnotations.clear()
        original.add(third)
        val last = after.captureCurrent()
        assertTrue(last.elements.any { it === first })
        assertTrue(last.elements.any { it === second })
        assertTrue(last.elements.any { it === third })
        repeat(2) {
            before.restore()
            assertSame(first, original.single())
            after.restore()
            assertTrue(original.isEmpty())
            assertSame(second, template.pendingFieldAnnotations["value"]!!.single())
            last.restore()
            assertSame(third, original.single())
            assertTrue(template.pendingFieldAnnotations.isEmpty())
        }
    }

    @Test fun genericHeaderAndVariancePreparationFlagsCanReturnToTheUnpreparedState() = isolated {
        val template = generic()
        val parameter = DataTemplateParam("int", "n", MCFPPBaseType.Int)
        template.readOnlyParams.add(parameter)
        val before = template.checkpointDeclarationState()
        template.prepareHeader()
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            parameter.type = null
            template.prepareHeader()
            assertSame(MCFPPBaseType.Int, parameter.type)
            after.restore()
            parameter.type = null
            template.prepareHeader()
            assertNull(parameter.type)
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun readonlyParameterMetadataAndIndexRestoreDetachedParametersAndOriginalOrder() = isolated {
        val template = generic()
        val original = DataTemplateParam("int", "n", MCFPPBaseType.Int)
        val replacement = DataTemplateParam("bool", "flag", MCFPPBaseType.Bool)
        val params = template.readOnlyParams
        params.add(original)
        val before = template.checkpointDeclarationState()
        params.clear()
        params.add(replacement)
        original.identifier = "detached"
        original.typeIdentifier = "float"
        original.type = MCFPPBaseType.Float
        template.index = 7
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(params, template.readOnlyParams)
            assertSame(original, params.single())
            assertEquals("n", original.identifier)
            assertEquals("int", original.typeIdentifier)
            assertSame(MCFPPBaseType.Int, original.type)
            assertEquals(0, template.index)
            after.restore()
            assertSame(replacement, params.single())
            assertEquals("detached", original.identifier)
            assertSame(MCFPPBaseType.Float, original.type)
            assertEquals(7, template.index)
        }
        assertTrue(after.elements.any { it === MCFPPBaseType.Int })
        assertTrue(after.elements.any { it === MCFPPBaseType.Float })
    }

    @Test fun compiledBindingsRemainCanonicalAndDetachedAcrossMultipleCurrentCaptures() = isolated {
        val template = generic()
        val first = bound(template, "first", 1)
        val second = bound(template, "second", 2)
        val third = bound(template, "third", 3)
        val firstKey = key(template, 1)
        val secondKey = key(template, 2)
        val thirdKey = key(template, 3)
        val compiled = template.compiledTemplates
        compiled[firstKey] = first
        val before = template.checkpointDeclarationState()
        compiled.clear()
        compiled[secondKey] = second
        val after = before.captureCurrent()
        compiled.clear()
        compiled[thirdKey] = third
        val last = after.captureCurrent()
        assertTrue(last.elements.any { it === first })
        assertTrue(last.elements.any { it === second })
        assertTrue(last.elements.any { it === third })
        repeat(2) {
            before.restore()
            assertSame(compiled, template.compiledTemplates)
            assertSame(first, compiled[firstKey])
            assertEquals(1, compiled.size)
            after.restore()
            assertSame(second, compiled[secondKey])
            assertEquals(1, compiled.size)
            last.restore()
            assertSame(third, compiled[thirdKey])
            assertEquals(1, compiled.size)
        }
    }

    @Test fun deepEntityMutationIsExplicitlyLeftForTheComposingJournal() = isolated {
        val template = model("DeepBoundary")
        val variable = field(template)
        val property = template.scope.getProperty("value")!!
        val before = template.checkpointDeclarationState()
        variable.isConst = true
        property.isFinal = true
        before.restore()
        assertSame(variable, template.scope.getVar("value"))
        assertSame(property, template.scope.getProperty("value"))
        assertTrue(variable.isConst)
        assertTrue(property.isFinal)
        assertTrue(before.elements.any { it === variable })
        assertTrue(before.elements.any { it === property })
    }

    @Test fun unknownPendingAnnotationListIsRejectedBeforeCollectionCallbacks() = isolated {
        val template = model("UnknownList")
        var callbacks = 0
        template.pendingFieldAnnotations["value"] = object : ArrayList<Annotation>() {
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override fun iterator(): MutableIterator<Annotation> {
                callbacks++
                return super.iterator()
            }
            override fun toArray(): Array<Any?> {
                callbacks++
                return super.toArray()
            }
        }
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, callbacks)
    }

    @Test fun malformedAnnotationMembersAreRejectedBeforeElementCallbacks() = isolated {
        val template = model("MalformedAnnotation")
        var hashes = 0
        val hostile = object {
            override fun hashCode(): Int {
                hashes++
                error("Unexpected hash")
            }
        }
        @Suppress("UNCHECKED_CAST")
        (template.pendingDeclarationAnnotations as MutableList<Any?>).add(hostile)

        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, hashes)
    }

    @Test fun oneEmptyPhysicalListCanSatisfyMultipleSemanticRoles() = isolated {
        val shared = arrayListOf<Any>()
        val graph = TemplatePrivateCapture(emptyList())

        graph.list(shared, TemplatePrivateCapture.ListRole.ANNOTATIONS) { _, value ->
            check(value is Annotation)
        }
        graph.list(shared, TemplatePrivateCapture.ListRole.ORIGINS) { _, value ->
            check(value is CompoundDataScope.FieldOrigin)
        }

        assertTrue(graph.elements.any { it === shared })
    }

    @Test fun unknownDeclarationImportMapIsRejectedBeforeItsEntriesGetter() = isolated {
        val template = model("UnknownMap")
        var callbacks = 0
        val imports = object : HashMap<String, String>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, String>>
                get() {
                    callbacks++
                    return super.entries
                }
            override val size: Int get() {
                callbacks++
                return super.size
            }
        }
        template.declarationEnvironment = DeclarationEnvironmentInfo("unknown", imports)
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, callbacks)
    }

    @Test fun erasedSpecializationPayloadIsRejectedBeforeRestoreCouldHashAnUnknownHost() = isolated {
        val template = generic()
        var hashes = 0
        val host = object {
            override fun hashCode(): Int {
                hashes++
                return 1
            }
        }
        @Suppress("UNCHECKED_CAST")
        val sequence = CompilerValue.Sequence(listOf(host) as List<CompilerValue>)
        val key = SpecializationKey(template.declarationId, listOf(SpecializationArgument.Constant(sequence)), GenerationOptions("26.3", 0))
        template.compiledTemplates[key] = bound(template, "bad", 1)
        hashes = 0
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, hashes)
    }

    @Test fun constructorListsCannotReuseTheValidationOfTheirAnnotationAlias() = isolated {
        val template = model("ConstructorAlias")
        val marker = Marker()
        val shared = arrayListOf<Annotation>(marker)
        template.pendingFieldAnnotations["value"] = shared
        @Suppress("UNCHECKED_CAST")
        val constructors = shared as ArrayList<DataTemplateConstructor>
        template.constructors = constructors
        val failure = assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertTrue(failure.message!!.contains("invalid constructor"))
        assertSame<Any>(shared, template.constructors)
        assertSame(marker, shared.single())
        assertEquals(0, marker.invocations)
    }

    @Test fun currentCaptureStillValidatesADetachedContainersPreviouslyRegisteredRole() = isolated {
        val template = model("DetachedConstructorAlias")
        val shared = arrayListOf<Annotation>()
        template.pendingFieldAnnotations["value"] = shared
        @Suppress("UNCHECKED_CAST")
        val constructors = shared as ArrayList<DataTemplateConstructor>
        template.constructors = constructors
        val before = template.checkpointDeclarationState()
        val replacement = arrayListOf(DataTemplateConstructor(template, null))
        template.constructors = replacement
        val marker = Marker()
        shared.add(marker)
        val failure = assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertTrue(failure.message!!.contains("invalid constructor"))
        assertSame(replacement, template.constructors)
        assertEquals(0, marker.invocations)
        before.restore()
        assertSame(constructors, template.constructors)
        assertTrue(shared.isEmpty())
        val after = before.captureCurrent()
        repeat(2) {
            after.restore()
            before.restore()
            assertTrue(shared.isEmpty())
        }
    }

    @Test fun importMapsCannotReuseTheValidationOfAnInitializerMapAlias() = isolated {
        val template = model("ImportAlias")
        val initializer = parser("7").expression()
        template.preInit["value"] = initializer
        @Suppress("UNCHECKED_CAST")
        val imports = template.preInit as Map<String, String>
        template.declarationEnvironment = DeclarationEnvironmentInfo("journal.declaration", imports)
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertSame<Any>(template.preInit, template.declarationEnvironment!!.imports)
        assertSame(initializer, template.preInit["value"])
    }

    @Test fun sharedSetContentsMustSatisfyEveryConsumerRoleBeforeRestoreIsPublished() = isolated {
        val parent = model("SetAlias")
        val shared = hashSetOf<Any>(parent)
        val graph = TemplatePrivateCapture(emptyList())
        graph.set(shared, TemplatePrivateCapture.SetRole.PARENTS) { g, value ->
            check(value is DataTemplate)
            g.element(value)
        }
        assertFailsWith<IllegalStateException> {
            graph.set(shared, TemplatePrivateCapture.SetRole.STRINGS) { _, value -> check(value is String) }
        }
        assertSame(parent, shared.single())
    }

    companion object {
        private var nativeCalls = 0
        @JvmStatic fun nativeSentinel(context: NativeCallContext) {
            nativeCalls++
        }
    }
}
