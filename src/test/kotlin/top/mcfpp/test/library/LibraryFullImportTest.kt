package top.mcfpp.test.library

import top.mcfpp.*
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.library.api.*
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.model.DeclarationAccess
import top.mcfpp.library.syntax.*
import top.mcfpp.library.syntax.ConstantValue
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.MacroExpander
import top.mcfpp.mni.expand.SourceProjectionEnvironment
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.SourceProjectionFixture
import top.mcfpp.test.MniCodeLoweringPlanTest
import top.mcfpp.type.*
import java.nio.file.Files
import java.util.function.Consumer
import kotlin.test.*

class LibraryFullImportTest {
    private val binary = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
    private val packageA = LibraryIdentity(PackageKey("fixture:full-a", "1"), "main")
    private val packageB = LibraryIdentity(PackageKey("fixture:full-b", "1"), "main")
    class Mark private constructor(restoring: Boolean) : Annotation("Mark", "full.host") {
        constructor() : this(false)
        init { if (!restoring) constructed++ }
        var applications = 0
        var labels = arrayListOf<String>()
        override fun forFunction(function: Function) { applications++ }
        override fun forField(field: Var<*>) { applications++ }
        override fun forDataTemplate(data: DataTemplate) { applications++ }
        companion object { fun shell() = Mark(true) }
    }
    private object MarkCodec : PortableAnnotationCodec<Mark> {
        override val annotationClass = Mark::class.java
        override val schema = "fixture.full.mark"
        override val version = 1L
        override fun capture(value: Mark, writer: PortableAnnotationWriter) = writer.fields("applications" to value.applications, "labels" to value.labels)
        override fun allocateShell(reader: PortableAnnotationReader) = Mark.shell()
        @Suppress("UNCHECKED_CAST") override fun restore(value: Mark, reader: PortableAnnotationReader) {
            value.applications = reader.integer("applications"); value.labels = reader.list("labels") as ArrayList<String>
        }
    }
    companion object {
        private val providerPackage = PackageKey("fixture:full-provider", "1")
        private var constructed = 0
        private val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = LibraryFullImportTest::class.java.classLoader
            override fun provider(owner: Class<*>) = if (owner.name.startsWith(LibraryFullImportTest::class.java.name)) providerPackage else null
        }
        @JvmStatic @NoExternalWrites fun twice(context: NativeCallContext) {
            context.withArguments { context.publishResult(MCInt(requireNotNull((it.last() as MCInt).value) * 2)) }
        }
        @JvmStatic @MNIMacro(identifier = "sumInto", normalParams = ["int",
            "int"], returnType = "int")
        fun sumInto(context: MacroContext, input: Value<IntT>, destination: Ref<IntT>): Expr<IntT> =
            context.quote().expression(context.types().intType(), Consumer { builder ->
                builder.assign(destination, builder.add(destination.load(), input.expr()))
                builder.yield(destination.load())
            })
    }

    @Test fun sourceGenericDefaultsTemplatesAndNestedClosuresRunFromAV1File() = isolated {
        val directory = Files.createTempDirectory("mcfpp-full-generic-")
        val path = directory.resolve("bin.mclib")
        try {
            parse("""
                namespace full.generic
                data Parent<T> {var base:int=1;constructor(){}}
                data Box<T>:Parent<T> {var item:T;constructor(initial:T){this.item=initial};func echo(value:T):T{return value}}
                func add<T,N:int=2>(value:T=3):T {
                    /say once
                    return value+N
                }
                func apply(callback:(int)->int,value:int):int{return callback(value)}
                func closure(seed:int=4):int {
                    var callback:(int)->int={outer:int ->
                        var inner:(int)->int={value:int -> value+outer+seed}
                        inner(outer)
                    }
                    return apply(callback,seed)
                }
                func main(){}
            """.trimIndent())
            val file = sourceFile("full.generic")
            val roots = functions("full.generic").filter { it.identifier in setOf("add", "closure") }
            LibraryExport.write(OwnedLibrarySelection(packageA, LibraryTarget("26.3"), roots, listOf(file),
                templates = listOf(file.namespace.scope.template.getValue("Box"))), path)
            LibraryImportSession().use { session ->
                parse("""
                    namespace consumer
                    import full.generic:*
                    func main(){
                        external input:int=4
                        external known=add<int>(5)
                        external unknown=add<int>(input)
                        external defaults=add<int>()
                        external nested=closure(input)
                        var box=Box<int>(9)
                        external field=box.item
                        external parent=box.base
                        external method=box.echo(7)
                    }
                """.trimIndent()) { session.stage(path); session.bindAll(); session.commit() }
                val main = functions("consumer").single { it.identifier == "main" }
                val executor = execute(main, session)
                mapOf("known" to 7, "unknown" to 6, "defaults" to 5, "nested" to 12, "field" to 9, "parent" to 1, "method" to 7)
                    .forEach { (name, value) -> assertEquals(value, executor.read(main.scope.getVar(name) as MCInt), name) }
                assertEquals(listOf("once", "once", "once"), executor.messages)
                assertEquals(0, executor.stackDepth)
                val default = session.functions().single { it.identifier == "add" }.normalParams.single()
                assertNotSame(default.defaultSyntax(), default.defaultSyntax())
                session.close()
                assertFailsWith<IllegalStateException> { default.defaultSyntax() }
            }
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }

    @Test fun twoNativeUnitsShareOneAdmissionDirectoryAndRestoreAnnotationCellsWithoutReplay() = isolated {
        val registry = PortableAnnotationCodecs.builtins().register(MarkCodec, providerPackage, managed)
        val models = nativeModels(registry)
        val beforeConstructed = constructed
        val session = LibraryImportSession().also { it.configureNative(managed, registry) }
        lateinit var retained: NativeFunction
        lateinit var genericCopy: NativeFunction
        try {
            parse("""
                namespace consumer
                import full.host:*
                func main(){
                    external state:int=2
                    external first=twice(3)
                    /data modify storage mcfpp:system reference_access set value {reference:{holder:"#library",objective:"saved"},input:12}
                    /function full.host:saved_access_write
                    /function full.host:saved_access_read
                }
            """.trimIndent()) {
                session.stage(binary.encode(models.second), packageB)
                session.stage(binary.encode(models.first), packageA)
                session.bindAll()
                assertEquals(beforeConstructed, constructed)
                assertFalse(GlobalScope.libNamespaces.containsKey("full.host"))
                session.commit()
            }
            val imported = GlobalScope.libNamespaces.getValue("full.host").scope.functions.getValue("twice").single()
            retained = imported as NativeFunction
            genericCopy = retained.replaceGenericParams(emptyMap())
            val annotations = imported.annotations.filterIsInstance<Mark>()
            assertEquals(listOf(1, 7), annotations.map { it.applications })
            assertSame(annotations[0].labels, annotations[1].labels)
            assertEquals(listOf("saved"), annotations[0].labels)
            assertEquals(beforeConstructed, constructed)
            val main = functions("consumer").single { it.identifier == "main" }
            val machine = execute(main, session)
            assertEquals(6, machine.read(main.scope.getVar("first") as MCInt))
            val source = SourceProjectionFixture()
            val previousFunction = Function.currFunction
            lateinit var stateRoot: SymbolId
            val plan = try {
                Function.currFunction = main
                val call = source.internalReady("sumInto(4,state)", main, setOf(1))
                source.session(call).use { expansion ->
                    val projection = SourceProjectionEnvironment.capture(call.environment, expansion)
                    stateRoot = source.location(projection.roots.getValue(projection.binding.symbol("state")!!.symbol)).root
                    val expanded = MacroExpander(projection).expandSource(call.call).validate()
                    expansion.finalizeHelpers()
                    projection.lower(expanded.code)
                }
            } finally { Function.currFunction = previousFunction }
            val lowered = MniCodeLoweringPlanTest.Machine(plan)
            val stateAddress = MniCodeLoweringPlanTest.Address(stateRoot)
            lowered.storage[stateAddress] = machine.read(main.scope.getVar("state") as MCInt).toLong()
            assertEquals(6L, lowered.run().second)
            assertEquals(6L, lowered.storage[stateAddress])
            assertEquals(12, machine.values["#library saved"])
            assertTrue(session.functions().any { it.identifier == "saved_access_read" })
            assertEquals(listOf(packageA.library, packageB.library), session.exportDependencies().map { it.library })
            assertEquals(1L, session.exportOwnership().functionReference(imported)?.dependencyIndex)
        } finally { session.close() }
        assertFailsWith<IllegalStateException> { retained.invoke(emptyList(), listOf(MCInt(3)), null) }
        assertFailsWith<IllegalStateException> { genericCopy.invoke(emptyList(), listOf(MCInt(3)), null) }
        assertTrue(ReferenceAccessHelpers.recipes("full.host").isEmpty())
        assertFalse(Mark.shell().hasDeclarationStateSupport())
        assertFalse(GlobalScope.libNamespaces.containsKey("full.host"))
    }

    @Test fun libraryTopLevelInitializationRunsBeforeItsLoadCallback() = isolated {
        parse("""
            namespace full.startup
            var counter:int=7
            @Load
            func on_load(){counter+=1}
            func main(){}
        """.trimIndent())
        val author = functions("full.startup").single { it.identifier == "on_load" }
        // The string helper retains this parsed tree on the function, but does not index it in Project.
        Project.trees[requireNotNull(author.declarationFile)] = generateSequence<org.antlr.v4.runtime.tree.ParseTree>(requireNotNull(author.ast)) { it.parent }
            .filterIsInstance<top.mcfpp.antlr.mcfppParser.CompilationUnitContext>().single()
        val model = LibraryExporter.capture(LibraryExporter.snapshotCurrentProject(packageA, LibraryTarget("26.3")))
        LibraryImportSession().use { session ->
            parse("namespace consumer\nfunc main(){}") { session.stage(binary.encode(model)); session.bindAll(); session.commit() }
            val owned = session.functions().toSet()
            val unrelated = FunctionTag.LOAD.functions.filter { it !in owned }
            val targets = session.prepareInitializers()
            val ordered = FunctionTag.LOAD.functions.toList()
            val load = ordered.filter { it in owned }
            assertEquals(2, load.size)
            assertSame(targets.first(), load.first())
            assertEquals("on_load", load.last().identifier)
            assertEquals(unrelated, ordered.filter { it !in owned })
            session.prepareInitializers()
            assertEquals(ordered, FunctionTag.LOAD.functions)
            val commands = session.functions().associate { it.namespaceID.toString() to it.commands.analyzeAll() }
            val machine = ScoreCommandExecutor(load.map { "function ${it.namespaceID}" }, commands)
            val counter = requireNotNull(load.last().declarationFile).field.getVar("counter") as MCInt
            assertEquals(8, machine.read(counter))
            session.close()
            assertEquals(unrelated, FunctionTag.LOAD.functions)
        }
    }

    @Test fun resourcesChooseLinkedOrReanalyzedBodyAndCloseInvalidatesTheOwnershipView() = isolated {
        parse("namespace full.resources\nfunc answer():int{return 7}\nfunc main(){}")
        val file = sourceFile("full.resources")
        val original = LibraryExporter.capture(OwnedLibrarySelection(packageA, LibraryTarget("26.3"),
            functions("full.resources").filter { it.identifier == "answer" }, listOf(file)))
        // This fixture supplies a legacy linked body with empty generation metadata.
        parse("namespace full.resources\nfunc answer():int{return 8}\nfunc main(){external used=answer()}") {
            Project.config.optimization = top.mcfpp.optimization.OptimizationConfig(level = 0)
        }
        val compiled = functions("full.resources").single { it.identifier == "answer" }.commands.analyzeAll().joinToString("\n").toByteArray()
        val path = "datapack/fixture/data/full.resources/function/answer.mcfunction"
        val staticPath = "datapack/fixture/data/full.resources/item_modifier/value.json"
        val staticBytes = "{}".toByteArray()
        fun resource(name: String, bytes: ByteArray, origin: ResourceOrigin) = LibraryResource(name, bytes.size.toULong(),
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).toList(), null, "fixture", null, origin)
        val functionId = DeclarationId(original.declarations.declarations.indexOfFirst { it is FunctionDeclaration } + 1L)
        val constants = original.tables.constants + ConstantValue.Record(emptyList())
        val generation = ConstantRef(constants.size.toLong())
        for (policy in listOf(RuntimePolicy.REANALYZABLE_FALLBACK, RuntimePolicy.LINK_ONLY)) {
            val model = LibraryModel(original.manifest, original.declarations, LibraryTables(original.tables.types, constants), original.syntax,
                runtime = listOf(RuntimeRecord.FunctionBinding(DeclarationReference.Local(functionId), path, LibraryTarget("26.3"), generation, policy)),
                resources = listOf(resource(path, compiled, ResourceOrigin.PRECOMPILED), resource(staticPath, staticBytes, ResourceOrigin.STATIC)))
            val bytes = binary.encode(model)
            val session = LibraryImportSession()
            try {
                parse("namespace consumer\nimport full.resources:*\nfunc main(){external answer=answer()}") {
                    session.stage(bytes, packageA, mapOf(path to compiled, staticPath to staticBytes)); session.bindAll(); session.commit()
                }
                val main = functions("consumer").single { it.identifier == "main" }
                assertEquals(if (policy == RuntimePolicy.LINK_ONLY) 8 else 7, execute(main, session).read(main.scope.getVar("answer") as MCInt))
                assertEquals(policy == RuntimePolicy.REANALYZABLE_FALLBACK, path in session.suppressedResourcePaths())
                assertContentEquals(staticBytes, session.resources().getValue(staticPath))
                assertEquals(policy == RuntimePolicy.LINK_ONLY, path in session.resources())
                val ownership = session.exportOwnership()
                val imported = session.functions().single { it.identifier == "answer" }
                assertNotNull(ownership.functionReference(imported))
                session.close()
                assertFailsWith<IllegalStateException> { ownership.functionReference(imported) }
                assertFailsWith<IllegalStateException> { session.resources() }
            } finally { session.close() }
            Project.init()
            val before = GlobalScope.libNamespaces.toMap()
            LibraryImportSession().use { failed ->
                assertFailsWith<IllegalArgumentException> { failed.stage(bytes, packageA, mapOf(path to byteArrayOf(0), staticPath to staticBytes)) }
                assertEquals(before, GlobalScope.libNamespaces)
                assertEquals(LibraryImportState.FAILED, failed.state)
            }
        }
    }

    private fun nativeModels(registry: PortableAnnotationCodecs): Pair<LibraryModel, LibraryModel> {
        val namespace = "full.host"
        fun common(name: String, kind: Int, owner: DeclarationId? = DeclarationId(1), signature: DeclarationSignature? = null) =
            DeclCommon(DeclarationKey(namespace, emptyList(), kind, name, signature), null, name, owner, EnvironmentId(1), DeclarationAccess.PUBLIC, 0)
        val arena = CompilerTypeTableCapture(object : CompilerTypeBindings {})
        val int = arena.type(MCFPPBaseType.Int)
        val shared = arrayListOf("saved")
        val pending = Mark.shell().apply { labels = shared }
        val completed = Mark.shell().apply { applications = 7; labels = shared }
        val captured = registry.capture(listOf(pending, completed), object : AnnotationCaptureBindings {
            override fun provider(binding: AnnotationProviderBinding) = DeclarationReference.Local(DeclarationId(4))
            override fun constant(value: CompilerValue) = arena.constant(value)
            override fun type(value: MCFPPType) = arena.type(value)
            override fun declaration(value: Any): DeclarationReference = error("No declaration state in Mark")
            override fun phase(value: Annotation) = if (value === pending) AnnotationPhase.PENDING else AnnotationPhase.COMPLETED
        })
        val native = NativeBinding(providerPackage, LibraryFullImportTest::class.java.name, "twice", listOf(NativeCallContext::class.java.name), "void")
        val declarations = listOf<LibraryDeclaration>(
            NamespaceDeclaration(common(namespace, 1, null), listOf(DeclarationId(2), DeclarationId(4), DeclarationId(5))),
            FunctionDeclaration(common("twice", 2, signature = DeclarationSignature(readonly = emptyList(), normal = listOf(KeyType.Builtin("int")))).copy(annotations = captured.roots),
                FunctionExecutionKind.NATIVE, null, emptyList(), listOf(DeclarationId(3)), int, null, native),
            ValueDeclaration(DeclCommon(null, null, "value", DeclarationId(2), EnvironmentId(1), DeclarationAccess.PRIVATE, 0),
                ValueRole.NORMAL_PARAMETER, ValueStorage.PARAMETER, int, null, null, null, parameterIndex = 0),
            AnnotationProviderDeclaration(common("Mark", 13), registry.bindings().single { it.ownerClass == Mark::class.java.name }),
            ReferenceHelperDeclaration(common("saved_access", 12), ReferenceRecipe(namespace, "saved_access", LibraryReferenceKind.SCORE_HOLDER,
                null, true, listOf(int), emptyList())))
        fun manifest(identity: LibraryIdentity) = LibraryManifest(identity.library, identity.classifier, LibraryTarget("26.3"),
            entryEnvironments = listOf(EnvironmentId(1)), rootDeclarations = listOf(DeclarationId(1)))
        val environments = listOf(EnvironmentRecord(namespace, emptyList(), DeclarationId(1)))
        val first = LibraryModel(manifest(packageA), LibraryDeclarations(declarations, environments), arena.snapshot(),
            LibrarySyntax(emptyList(), emptyList(), emptyList()), captured.annotations, captured.stateCells)
        val macros = CompilerTypeTableCapture(object : CompilerTypeBindings {})
        val method = LibraryFullImportTest::class.java.getDeclaredMethod("sumInto", MacroContext::class.java, Value::class.java, Ref::class.java)
        val macro = NativeDeclarations.captureMacro(namespace, common("sumInto", 11), requireNotNull(MacroFunctionInfo.from(method)), object : NativeCaptureBindings {
            override val types = macros
            override val identities = object : CanonicalIdentityBindings { override fun localDeclaration(reference: DeclarationId): ByteArray = error("Builtin types only") }
            override fun sourceProvider(ownerClass: String) = providerPackage
            override fun loader(provider: PackageKey?) = managed.loader(provider)
            override fun provider(owner: Class<*>) = managed.provider(owner)
            override fun sourceType(namespace: String, expression: String): MCFPPType = if (expression == "int") MCFPPBaseType.Int else error("Unknown source type")
        })
        val second = LibraryModel(manifest(packageB), LibraryDeclarations(listOf(NamespaceDeclaration(common(namespace, 1, null), listOf(DeclarationId(2))), macro), environments),
            macros.snapshot(), LibrarySyntax(emptyList(), emptyList(), emptyList()))
        return first to second
    }

    private fun functions(namespace: String) = GlobalScope.localNamespaces.getValue(namespace).scope.functions.values.flatten().filter { !it.actualCallBody }
    private fun sourceFile(namespace: String): MCFPPFile = requireNotNull(functions(namespace).first().declarationFile)
    private fun parse(code: String, install: (() -> Unit)? = null) {
        Project.config = ProjectConfig()
        MCFPPStringTest.readFromString(code, arrayOf("-ignoreStdLib"), version = "26.3", afterFileCreated = { install?.invoke() })
        assertEquals(0, Project.errorCount)
    }
    private fun execute(main: Function, session: LibraryImportSession): ScoreCommandExecutor {
        val functions = (GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() } + session.functions())
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions)
    }
    private fun isolated(action: () -> Unit) {
        val config = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        try { action() } finally {
            MCFPPFile.currFile = null; Project.config = config
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
        }
    }
}
