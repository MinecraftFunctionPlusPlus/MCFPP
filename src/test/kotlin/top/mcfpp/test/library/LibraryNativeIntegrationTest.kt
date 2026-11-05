package top.mcfpp.test.library

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.model.DeclarationAccess
import top.mcfpp.library.syntax.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.api.*
import top.mcfpp.mni.api.TypeRef as MniTypeRef
import top.mcfpp.mni.expand.*
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.test.MniCodeLoweringPlanTest
import top.mcfpp.test.SourceProjectionFixture
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import java.util.function.Consumer
import kotlin.test.*

class LibraryNativeIntegrationTest {
    class Mark private constructor(restoring: Boolean) : Annotation("Mark", "fixture.catalog") {
        constructor() : this(false)
        init { if (!restoring) constructed++ }
        var applications = 0
        var labels = arrayListOf<String>()
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) { applications++; annotationActions++ }
        companion object { fun restoreShell() = Mark(true) }
    }
    class ParentMark : Annotation("ParentMark", "fixture.catalog") {
        var applications = 0
        var labels = arrayListOf<String>()
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) { applications++; annotationActions++ }
    }
    private object MarkCodec : PortableAnnotationCodec<Mark> {
        override val annotationClass = Mark::class.java
        override val schema = "fixture.catalog.mark"
        override val version = 1L
        override fun capture(value: Mark, writer: PortableAnnotationWriter) = writer.fields("applications" to value.applications, "labels" to value.labels)
        override fun allocateShell(reader: PortableAnnotationReader) = Mark.restoreShell()
        @Suppress("UNCHECKED_CAST") override fun restore(value: Mark, reader: PortableAnnotationReader) {
            value.applications = reader.integer("applications"); value.labels = reader.list("labels") as ArrayList<String>
        }
    }
    private object ParentCodec : PortableAnnotationCodec<ParentMark> {
        override val annotationClass = ParentMark::class.java
        override val schema = "fixture.catalog.parent"
        override val version = 1L
        override fun capture(value: ParentMark, writer: PortableAnnotationWriter) = writer.fields("applications" to value.applications, "labels" to value.labels)
        override fun allocateShell(reader: PortableAnnotationReader) = ParentMark()
        @Suppress("UNCHECKED_CAST") override fun restore(value: ParentMark, reader: PortableAnnotationReader) {
            value.applications = reader.integer("applications"); value.labels = reader.list("labels") as ArrayList<String>
        }
    }
    companion object {
        private val providerPackage = PackageKey("fixture:native-catalog", "1")
        private var macroCalls = 0
        private var nativeCalls = 0
        private var annotationActions = 0
        private var constructed = 0
        private val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = LibraryNativeIntegrationTest::class.java.classLoader
            override fun provider(owner: Class<*>) = if (owner.name.startsWith(LibraryNativeIntegrationTest::class.java.name)) providerPackage else null
        }
        private fun registry() = PortableAnnotationCodecs.builtins().register(MarkCodec, providerPackage, managed).register(ParentCodec, providerPackage, managed)
        @JvmStatic @NoExternalWrites
        fun nativeTwice(context: NativeCallContext) {
            nativeCalls++
            context.withArguments { arguments -> context.publishResult(MCInt(requireNotNull((arguments.last() as MCInt).value) * 2)) }
        }
        @JvmStatic
        @MNIMacro(identifier = "adapt", normalParams = [
            "int", "int",
            "int",
            "(int)->int"
        ], returnType = "int")
        fun adapt(context: MacroContext, input: Value<IntT>, destination: Ref<IntT>, seed: Const<IntT>, body: InlineFn<IntT>): Expr<IntT> {
            macroCalls++
            return context.quote().expression(context.types().intType(), Consumer { builder ->
                val applied = builder.inline<IntT>(body, listOf(InlineArgument.value(input.expr())))
                builder.assign(destination, builder.add(builder.add(destination.load(), applied), seed.expr()))
                builder.yield(destination.load())
            })
        }
        private fun common(name: String, tag: Int, owner: DeclarationId? = DeclarationId(1), signature: DeclarationSignature? = null) =
            DeclCommon(DeclarationKey("fixture.catalog", emptyList(), tag, name, signature), null, name, owner,
                EnvironmentId(1), DeclarationAccess.PUBLIC, 0)
        private fun parameter(name: String, role: ValueRole, type: top.mcfpp.library.syntax.TypeRef) = ValueDeclaration(
            DeclCommon(null, null, name, DeclarationId(2), EnvironmentId(1), DeclarationAccess.PRIVATE, 0), role,
            ValueStorage.PARAMETER, type, null, null, null, parameterIndex = 0)
    }

    private class Capture : NativeCaptureBindings, AnnotationCaptureBindings {
        val generic = MCFPPGenericParamType("T", arrayListOf())
        val providers = linkedMapOf<AnnotationProviderBinding, DeclarationId>()
        override val types = CompilerTypeTableCapture(object : CompilerTypeBindings {
            override fun parameter(id: top.mcfpp.type.TypeId.Opaque) = if (id == generic.typeId)
                LibraryType.Parameter(DeclarationReference.Local(DeclarationId(2)), ParameterRole.READONLY, 0) else null
        })
        override val identities = object : CanonicalIdentityBindings {
            override fun localDeclaration(reference: DeclarationId): ByteArray {
                require(reference == DeclarationId(2))
                return "fixture.catalog:nativeTwice".toByteArray()
            }
        }
        override fun sourceProvider(ownerClass: String) = providerPackage
        override fun loader(provider: PackageKey?) = managed.loader(provider)
        override fun provider(owner: Class<*>) = managed.provider(owner)
        override fun sourceType(namespace: String, expression: String): MCFPPType = when (expression) {
            "int" -> MCFPPBaseType.Int; "void" -> MCFPPPrivateType.Void; else -> error("No prepared source type $expression")
        }
        override fun provider(binding: AnnotationProviderBinding) = DeclarationReference.Local(providers.getOrPut(binding) { DeclarationId(providers.size + 7L) })
        override fun constant(value: CompilerValue) = types.constant(value)
        override fun type(value: MCFPPType) = types.type(value)
        override fun declaration(value: Any): DeclarationReference = error("No compiler declaration state in these providers")
        lateinit var pending: Mark
        override fun phase(value: Annotation) = if (value === pending) AnnotationPhase.PENDING else AnnotationPhase.COMPLETED
    }
    private data class Fixture(val model: LibraryModel, val roots: List<AnnotationId>, val originalGeneric: top.mcfpp.type.TypeId)
    private fun capture(codecs: PortableAnnotationCodecs): Fixture {
        val capture = Capture()
        val method = LibraryNativeIntegrationTest::class.java.getDeclaredMethod("adapt", MacroContext::class.java, Value::class.java,
            Ref::class.java, Const::class.java, InlineFn::class.java)
        val macro = NativeDeclarations.captureMacro("fixture.catalog", common("adapt", 11), assertNotNull(MacroFunctionInfo.from(method)), capture)
        val helper = NativeDeclarations.captureReference(common("saved_access", 12), ReferenceHelperRecipe("fixture.catalog", "saved_access",
            ReferenceKind.SCORE_HOLDER, null, true, mapOf(capture.generic.typeId to capture.generic)), capture)
        val native = NativeBinding(providerPackage, LibraryNativeIntegrationTest::class.java.name, "nativeTwice", listOf(NativeCallContext::class.java.name), "void")
        val int = capture.type(MCFPPBaseType.Int); val type = capture.type(MCFPPConcreteType.Type)
        capture.pending = Mark().apply { labels.add("saved"); parents.add(ParentMark().also { it.labels = labels }) }
        val completed = Mark().also { it.on(DataTemplate("Writer", "fixture.catalog")) }
        val annotations = codecs.capture(listOf(capture.pending, completed), capture)
        val functions = listOf<LibraryDeclaration>(
            NamespaceDeclaration(common("fixture.catalog", 1, null), listOf(DeclarationId(2), DeclarationId(5), DeclarationId(6)) + capture.providers.values),
            FunctionDeclaration(common("nativeTwice", 2, signature = DeclarationSignature(listOf(KeyType.Builtin("Type")), listOf(KeyType.Builtin("int")))),
                FunctionExecutionKind.NATIVE, null, listOf(DeclarationId(3)), listOf(DeclarationId(4)), int, null, native),
            parameter("T", ValueRole.READONLY_PARAMETER, type), parameter("value", ValueRole.NORMAL_PARAMETER, int), macro, helper
        ) + capture.providers.map { (binding, _) -> AnnotationProviderDeclaration(common(binding.ownerClass.substringAfterLast('.').replace('$', '_'), 13), binding) }
        val model = LibraryModel(LibraryManifest(PackageKey("fixture:catalog-library", "1"), "main", LibraryTarget("26.3"),
            entryEnvironments = listOf(EnvironmentId(1)), rootDeclarations = listOf(DeclarationId(1))),
            LibraryDeclarations(functions, listOf(EnvironmentRecord("fixture.catalog", emptyList(), DeclarationId(1)))), capture.types.snapshot(),
            LibrarySyntax(emptyList(), emptyList(), emptyList()), annotations.annotations, annotations.stateCells)
        val binary = LibraryModelCodec(listOf(NativeCodec))
        return Fixture(binary.decode(binary.encode(model)), annotations.roots, capture.generic.typeId)
    }

    private class Restore(val fixture: Fixture, val file: top.mcfpp.io.MCFPPFile) : FunctionRestoreBindings {
        lateinit var freshGeneric: MCFPPGenericParamType
        val types = CompilerTypeTableRestore(fixture.model.tables, object : CompilerTypeBindings {
            override fun restoreParameter(value: LibraryType.Parameter) = freshGeneric
        })
        override fun type(reference: top.mcfpp.library.syntax.TypeRef) = types.type(reference)
        // Function defaults and annotation constants use different DTO return boundaries.
        override fun constant(reference: ConstantRef) = RestoredDefault(types.constant(reference))
        override fun environment(reference: EnvironmentId) = file
        override fun owner(reference: DeclarationId): CompoundData? = null
        override fun nativeMethod(binding: NativeBinding) = NativeDeclarations.resolveMethod(binding, managed)
        override fun readonlyType(owner: DeclarationId, index: Int, parameter: top.mcfpp.model.function.FunctionParam, type: MCFPPGenericParamType) { freshGeneric = type }
        fun provider(reference: DeclarationReference) = (fixture.model.declarations.declaration((reference as DeclarationReference.Local).target) as AnnotationProviderDeclaration).binding
        fun declaration(reference: DeclarationReference): Any = error("No declaration-valued annotation state")
        fun native(): NativeFunction = FunctionDeclarations.restoreHead(DeclarationId(2), fixture.model.declarations.declaration(DeclarationId(2)) as FunctionDeclaration,
            { fixture.model.declarations.declaration(it) as ValueDeclaration }, this).function as NativeFunction
        val annotationBindings = object : AnnotationRestoreBindings {
            override fun provider(reference: DeclarationReference) = this@Restore.provider(reference)
            override fun constant(reference: ConstantRef) = types.constant(reference)
            override fun type(reference: top.mcfpp.library.syntax.TypeRef) = types.type(reference)
            override fun declaration(reference: DeclarationReference): Any = this@Restore.declaration(reference)
        }
    }

    @Test
    fun dtoBytesBindFreshDirectoriesThenInvokeNativeMacroRefCallbackAndGeneratedHelpers() {
        macroCalls = 0; nativeCalls = 0
        registry().use { codecs ->
            lateinit var fixture: Fixture
            SourceProjectionFixture.isolated { fixture = capture(codecs) }
            repeat(2) {
                SourceProjectionFixture.isolated {
                    val file = file("fixture.catalog")
                    val restore = Restore(fixture, file)
                    val native = restore.native()
                    file.namespace.scope.functions["nativeTwice"] = arrayListOf(native)
                    val caller = caller(file)
                    PreparedNativeDirectory.prepare(fixture.model.declarations, restore.types, managed, codecs).activate().use { directory ->
                        assertEquals(native.javaMethod, directory.nativeMethod((fixture.model.declarations.declaration(DeclarationId(2)) as FunctionDeclaration).native!!))
                        val result = native.invoke(listOf(MCFPPTypeVar(MCFPPBaseType.Int)), listOf(MCInt(7)), null)
                        assertEquals(14, (result as MCInt).value)
                        directory.installReferenceHelpers(); directory.installReferenceHelpers()
                        val helper = ReferenceAccessHelpers.recipes("fixture.catalog").single()
                        assertTrue(restore.freshGeneric.typeId in helper.operandTypes); assertFalse(fixture.originalGeneric in helper.operandTypes)
                        caller.scope.vars["state"] = MCInt("state")
                        val call = internalReady("adapt(4,state,5,{ v -> return v*2; })", caller, setOf(1))
                        assertTrue(MacroCallPipeline.isPotentialMacro(parser("adapt(4,state,5,{ v -> return v*2; })").functionCall(), caller))
                        lateinit var root: SymbolId
                        val plan = session(call).use { session ->
                            val projection = SourceProjectionEnvironment.capture(call.environment, session)
                            root = location(projection.roots.getValue(projection.binding.symbol("state")!!.symbol)).root
                            val expanded = MacroExpander(projection).expandSource(call.call).validate()
                            session.finalizeHelpers()
                            projection.lower(expanded.code)
                        }
                        val machine = MniCodeLoweringPlanTest.Machine(plan)
                        machine.storage[MniCodeLoweringPlanTest.Address(root)] = 10L
                        assertEquals(23L, machine.run().second)
                        assertEquals(23L, machine.storage[MniCodeLoweringPlanTest.Address(root)])
                        val functions = file.namespace.scope.functions.values.flatten().associate { function ->
                            function.namespaceID.toString() to function.commands.map { it.analyze() }
                        }.toMutableMap()
                        Project.macroFunction.forEach { (name, body) -> functions["mcfpp:dynamic/$name"] = body.lines() }
                        val commands = ScoreCommandExecutor(listOf(
                            "data modify storage mcfpp:system reference_access set value {reference:{holder:\"#library\",objective:\"saved\"},input:12}",
                            "function ${helper.writeId}", "function ${helper.readId}"), functions)
                        assertEquals(12, commands.values["#library saved"])
                        assertEquals(12, (commands.readNbt("mcfpp:system", "reference_access.value") as IntTag).value)
                        assertEquals(1, (commands.readNbt("mcfpp:system", "reference_access.success") as ByteTag).value.toInt())
                        call.environment.requireCurrent()
                    }
                }
            }
        }
        assertEquals(2, macroCalls); assertEquals(2, nativeCalls)
    }

    @Test
    fun managedParentProvidersSupportPreparationWithoutNamespaceRegistrationOrCompletedActionReplay() = SourceProjectionFixture.isolated {
        registry().use { codecs ->
            val fixture = capture(codecs)
            val file = file("fixture.catalog")
            val restore = Restore(fixture, file)
            restore.native()
            val beforeConstructed = constructed; val beforeActions = annotationActions
            val directory = PreparedNativeDirectory.prepare(fixture.model.declarations, restore.types, managed, codecs)
            assertTrue(file.namespace.scope.annotations.isEmpty())
            directory.activate().use {
                val arena = directory.restoreAnnotations(fixture.model.annotations, fixture.model.stateCells, restore.annotationBindings)
                val pending = arena.annotation(fixture.roots[0]) as Mark
                val completed = arena.annotation(fixture.roots[1]) as Mark
                val parent = pending.parents.single() as ParentMark
                assertEquals(beforeConstructed, constructed); assertEquals(beforeActions, annotationActions)
                assertSame(pending.labels, parent.labels)
                assertTrue(parent.hasDeclarationStateSupport()); assertTrue(file.namespace.scope.annotations.isEmpty())
                val target = DataTemplate("Imported", file.namespace.identifier).apply {
                    declarationFile = file; annotations.addAll(listOf(pending, completed)); pendingDeclarationAnnotations.add(pending)
                }
                file.namespace.scope.template[target.identifier] = target
                DeclarationStateJournal.checkpoint(listOf(target))
                repeat(2) { Project.registerTemplateContract(target); Project.completeTemplateDeclarations() }
                assertEquals(1, pending.applications); assertEquals(1, parent.applications); assertEquals(1, completed.applications)
                assertEquals(beforeActions + 2, annotationActions); assertTrue(target.hasCompletedDeclaration)
                assertTrue(target.pendingDeclarationAnnotations.isEmpty())
                assertFailsWith<IllegalArgumentException> {
                    ReferenceAccessHelpers.restoreRecipes(listOf(ReferenceHelperRecipe("fixture.catalog", "valid_first", ReferenceKind.SCORE_HOLDER, null, true, emptyMap()),
                        ReferenceHelperRecipe("fixture.catalog", "invalid_later", ReferenceKind.SCORE_HOLDER,
                            top.mcfpp.core.lang.ref.ReferenceTargetShape.BoundExecutor, true, emptyMap())))
                }
                assertFalse(file.namespace.scope.functions.containsKey("valid_first_write"))
                assertFailsWith<IllegalArgumentException> {
                    PreparedNativeDirectory.prepare(fixture.model.declarations, restore.types, object : ManagedNativeBindings by managed {
                        override fun provider(owner: Class<*>) = PackageKey("wrong:provider", "1")
                    }, codecs)
                }
                assertEquals(beforeActions + 2, annotationActions)
            }
            assertFailsWith<IllegalStateException> { directory.installReferenceHelpers() }
            assertFalse(Mark.restoreShell().hasDeclarationStateSupport()); assertFalse(ParentMark().hasDeclarationStateSupport())
        }
    }
}
