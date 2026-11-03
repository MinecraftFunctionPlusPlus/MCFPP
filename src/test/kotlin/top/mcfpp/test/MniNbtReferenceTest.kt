package top.mcfpp.test

import com.ibm.icu.impl.data.ResourceReader
import org.antlr.v4.runtime.CharStreams
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.Place
import top.mcfpp.analysis.StorageBinding
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.StoredData
import top.mcfpp.analysis.Symbol
import top.mcfpp.analysis.SymbolId
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MacroParam
import top.mcfpp.antlr.MCFPPAnnotationVisitor
import top.mcfpp.antlr.MCFPPErrorListener
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.MCFPPTypeVisitor
import top.mcfpp.antlr.VersionPreprocessor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Expr
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.OwnedCode
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Ref
import top.mcfpp.mni.api.StmtNode
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.kotlin
import top.mcfpp.mni.expand.CompilerBridge
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.mni.expand.ExpansionFailure
import top.mcfpp.mni.expand.MacroArgumentBinder
import top.mcfpp.mni.expand.SourceProjectionEnvironment
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Compiler-internal deterministic NBT location capabilities through the production backend.
 *
 * The original Ref<IntT> damage builder is retained. Real source declarations allocate the
 * data/list storage; explicit Kotlin position indices supply internal capabilities instead of
 * the removed public `ref expression` syntax.
 */
class MniNbtReferenceTest {

    class DamageEntries {
        companion object {
            var hostCalls = 0

            @JvmStatic
            @MNIMacro(
                identifier = "damage",
                params = [
                    MacroParam(name = "health", type = "int", mode = ParamMode.REF),
                    MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
                ],
                returnType = "void"
            )
            fun damage(context: MacroContext, health: Ref<IntT>, amount: Value<IntT>): Block {
                hostCalls++
                return context.quote().kotlin().block {
                    ifElse(
                        lt(health.load(), amount.expr()),
                        { assign(health, intLiteral(0)) },
                        { assign(health, sub(health.load(), amount.expr())) }
                    )
                }
            }
        }
    }

    class IndexEntries {
        companion object {
            lateinit var cursor: MCInt
            lateinit var calls: MCInt
            @JvmStatic
            @MNIFunction(identifier = "nbtNextIndex", returnType = "int")
            fun nextIndex(context: top.mcfpp.mni.NativeCallContext) {
                val previous = top.mcfpp.analysis.StorageAccess.capture(cursor)
                top.mcfpp.analysis.StorageAccess.write(cursor, top.mcfpp.analysis.StorageAccess.binary(cursor, MCInt(1), "+"))
                top.mcfpp.analysis.StorageAccess.write(calls, top.mcfpp.analysis.StorageAccess.binary(calls, MCInt(1), "+"))
                context.publishResult(previous)
            }

            @JvmStatic
            @MNIFunction(identifier = "nbtAdvance", returnType = "int")
            fun advance(context: top.mcfpp.mni.NativeCallContext) {
                top.mcfpp.analysis.StorageAccess.write(cursor, MCInt(5))
                context.publishResult(MCInt(5))
            }
        }
    }

    private class Compiled(
        private val journal: CompilerStateJournal,
        val main: Function,
        val errors: Int
    ) : AutoCloseable {
        fun execute(): ScoreCommandExecutor {
            val functions = linkedMapOf<String, List<String>>()
            val visited = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
            fun collect(function: Function) {
                if (!visited.add(function)) return
                functions[function.namespaceID.toString()] =
                    function.commands.filterNot { it is Comment }.map { it.analyze() }
                function.compiledFunctions.values.forEach(::collect)
            }
            GlobalScope.localNamespaces.values.forEach { namespace ->
                namespace.scope.functions.values.flatten().forEach(::collect)
                namespace.scope.template.values.forEach { template ->
                    template.constructors.forEach(::collect)
                    template.scope.forEachFunction(::collect)
                }
                namespace.scope.objects.forEach { template ->
                    if (template is DataTemplate) template.constructors.forEach(::collect)
                    template.scope.forEachFunction(::collect)
                }
            }
            Project.macroFunction.forEach { (key, body) -> functions["mcfpp:dynamic/$key"] = body.lines() }
            return ScoreCommandExecutor(listOf("function ${main.namespaceID}"), functions)
        }

        override fun close() = journal.restore()
    }

    /**
     * The production compilation unit stages, with the real provider injected after the type
     * index has created the source namespace. This is the same sequence used by the string
     * compiler helper, only split so namespace macro declarations can be registered first.
     */
    private fun compile(source: String): Compiled {
        val journal = CompilerStateJournal.checkpoint()
        try {
            val configuration = ConfigurationSource(
                ResourceReader::class.java.classLoader.getResourceAsStream("log4j2.xml")
            )
            Configurator.initialize(null, configuration)
            Project.config = ProjectConfig(version = "26.3", rootNamespace = "default")
            Project.config.root = Path.of("./")
            Project.config.name = "nbt-reference"
            Project.config.description = "deterministic NBT reference fixture"
            Project.config.targetPath = null
            CompileSettings.isDebug = true
            Project.init()
            Project.readProject()
            MCFPPFile.currFile = MCFPPFile()
            val text = VersionPreprocessor.process(source, Project.config.version)
            val parser = mcfppParser(
                org.antlr.v4.runtime.CommonTokenStream(
                    mcfppLexer(CharStreams.fromString(text, "nbt-reference.mcfpp"))
                )
            )
            parser.removeErrorListeners()
            parser.addErrorListener(MCFPPErrorListener())
            val context = parser.compilationUnit()
            check(parser.numberOfSyntaxErrors == 0) { "NBT reference source has syntax errors" }
            Project.compileStage = Project.CompileStage.INDEX_TYPE
            Project.resolveImportedTemplateParents()
            Project.currNamespace = MCFPPFile.currFile!!.namespace.identifier
            MCFPPTypeVisitor().visitCompilationUnit(context)
            MCFPPFile.currFile!!.field.namespaceField =
                GlobalScope.localNamespaces[Project.currNamespace]!!.scope
            GlobalScope.localNamespaces.getValue(Project.currNamespace).apply {
                injectedBy(DamageEntries::class.java)
                injectedBy(IndexEntries::class.java)
            }
            Project.pairTemplateCompanions()
            Project.stageProcessor[Project.compileStage.ordinal].forEach { it() }
            MCFPPFile.currFile!!.resolveImports()
            MCFPPFile.currFile!!.field.checkIndex()
            MCFPPFieldVisitor().visit(context)
            GlobalScope.importedLibNamespaces.clear()
            GlobalScope.localNamespaces.values.flatMap { it.scope.template.values }.forEach {
                if (it.parent.isEmpty()) it.extends(DataTemplate.baseDataTemplate)
            }
            MCFPPAnnotationVisitor().visit(context)
            Project.compileStage = Project.CompileStage.COMPILE
            Project.completeTemplateDeclarations()
            Project.prepareObjectInitializers()
            object : MCFPPImVisitor() {
                override fun visitCurlBlock(ctx: mcfppParser.CurlBlockContext): Any? {
                    if (ctx.parent is mcfppParser.FunctionDeclarationContext) { compileFunctionBody(ctx); return null }
                    return super.visitCurlBlock(ctx)
                }
                override fun visitStatementExpression(ctx: mcfppParser.StatementExpressionContext): Any? {
                    fun find(node: org.antlr.v4.runtime.tree.ParseTree): mcfppParser.FunctionCallContext? {
                        if (node is mcfppParser.FunctionCallContext && node.namespaceID().text == "damage") return node
                        return (0 until node.childCount).firstNotNullOfOrNull { find(node.getChild(it)) }
                    }
                    val call = find(ctx) ?: return super.visitStatementExpression(ctx)
                    val current = Function.currFunction
                    (current.scope.getVar("cursor") as? MCInt)?.let { IndexEntries.cursor = it }
                    (current.scope.getVar("calls") as? MCInt)?.let { IndexEntries.calls = it }
                    try { InternalReferenceCalls.expand(call, current, setOf(0)) }
                    catch (failure: IllegalArgumentException) { top.mcfpp.util.LogProcessor.error(failure.message.orEmpty()) }
                    return null
                }
            }.visit(context)
            Project.optimization()
            Project.ctx.clear()
            GlobalScope.printAll()
            val namespace = GlobalScope.localNamespaces.getValue(Project.currNamespace)
            return Compiled(journal, namespace.scope.functions.getValue("main").single(), Project.errorCount)
        } catch (failure: Throwable) {
            journal.restore()
            throw failure
        }
    }

    private fun compiled(source: String, action: (Compiled) -> Unit) {
        compile(source).use(action)
    }

    private fun read(machine: ScoreCommandExecutor, function: Function, name: String): Int = machine.read(assertIs<MCInt>(function.scope.getVar(name)))

    @Test
    fun realSourceNbtFieldReferenceWritesTheOriginalPositionAndAliasReadsIt() {
        DamageEntries.hostCalls = 0
        compiled(
            """
            namespace fixture.nbtref.field
            data Box { var hp: int = 100 }
            func main() {
                var box = Box()
                damage(box.hp, 30)
                external normal = box.hp

                var alias = Box()
                damage(alias.hp, 40)
                damage(alias.hp, alias.hp - 10)
                external aliased = alias.hp
            }
            """.trimIndent()
        ) { fixture ->
            assertEquals(0, fixture.errors)
            assertEquals(3, DamageEntries.hostCalls)
            val machine = fixture.execute()
            assertEquals(
                70,
                read(machine, fixture.main, "normal"),
                "The REF write must land in the compiler-created box.hp NBT position"
            )
            assertEquals(
                10,
                read(machine, fixture.main, "aliased"),
                "The alias argument must read the original NBT position before the REF write"
            )
            assertEquals(0, machine.stackDepth)
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
            assertTrue(fixture.main.child.none { it.identifier.contains("damage") })
            assertTrue(Project.macroFunction.keys.none { it.contains("damage") })
        }
    }

    @Test
    fun realSourceNbtListIndexIsFrozenOnceBeforeTheAmountMutatesItsSource() {
        DamageEntries.hostCalls = 0
        compiled(
            """
            namespace fixture.nbtref.index
            func main() {
                external values = [10, 20]
                external cursor = 0
                external calls = 0
                damage(values[nbtNextIndex()], nbtAdvance())
                external selected = values[0]
                external untouched = values[1]
                external indexCalls = calls
            }
            """.trimIndent()
        ) { fixture ->
            assertEquals(0, fixture.errors)
            assertEquals(1, DamageEntries.hostCalls)
            val machine = fixture.execute()
            assertEquals(
                5,
                read(machine, fixture.main, "selected"),
                "The frozen index must select values[0] before the amount changes its sources"
            )
            assertEquals(20, read(machine, fixture.main, "untouched"))
            assertEquals(
                1,
                read(machine, fixture.main, "indexCalls"),
                "nbtNextIndex must execute exactly once, before nbtAdvance mutates the index source"
            )
            assertEquals(0, machine.stackDepth)
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
        }
    }

    @Test
    fun getterPropertyAndReadonlyReferenceAreRejectedBeforeTheMacroHostRuns() {
        DamageEntries.hostCalls = 0
        compiled(
            """
            namespace fixture.nbtref.property
            data Box {
                var hp: int = 100
                var computed: int { get { return 123; } }
            }
            func main() {
                var box = Box()
                damage(box.computed, 5)
            }
            """.trimIndent()
        ) { fixture ->
            assertTrue(fixture.errors > 0, "A getter-only property has no source location capability")
            assertEquals(0, DamageEntries.hostCalls)
            assertTrue(fixture.main.commands.none { it.analyze().contains(":damage") })
        }

        DamageEntries.hostCalls = 0
        compiled(
            """
            namespace fixture.nbtref.readonly
            func main() {
                const value = 1
                damage(value, 5)
            }
            """.trimIndent()
        ) { fixture ->
            assertTrue(fixture.errors > 0, "A readonly source symbol cannot satisfy REF")
            assertEquals(0, DamageEntries.hostCalls)
            assertTrue(fixture.main.commands.none { it.analyze().contains(":damage") })
        }
    }

    @Test
    fun unstableExecutorReferenceIsRejectedBeforeCommandPublication() = SourceProjectionFixture.isolated {
        DamageEntries.hostCalls = 0
        val file = file("fixture.nbtref.context")
        file.namespace.injectedBy(DamageEntries::class.java)
        val caller = caller(file)
        val place = Place(SymbolId.fresh())
        // The physical score keeps the original int location and objective, but its holder is
        // the unsupported selector @e rather than the declared symbol name.
        val health = MCInt("health").apply { name = "@e" }
        val data = StoredData(
            place,
            NBTPath(StorageSource("fixture:nbtref.context")).memberIndex("health"),
            layout = StorageLayout.Scoreboard(health.name, health.sbObject.name)
        )
        health.symbol = Symbol(place.root, health.identifier, health.type.typeId, mutable = true)
        health.storageBinding = StorageBinding(data, place, data.path)
        data.types[health.type.typeId] = health.type
        data.declarations[place] = health.symbol!!
        assertEquals(StorageLayout.Scoreboard("@e", "mcfpp_default"), data.layoutAt(place))
        caller.scope.vars["health"] = health
        val prepared = internalReady("damage(health, 1)", caller, setOf(0))
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        session(prepared).use { session ->
            val environment = SourceProjectionEnvironment.capture(prepared.environment, session)
            val failure = assertFailsWith<ExpansionFailure> {
                // Bind through the production macro argument binder and lower a final body that
                // uses the resulting original REF location. The ordered prelude alone is not the
                // REF contract, and the binder must not invoke the macro host.
                val bound = MacroArgumentBinder(environment).bind(prepared.call)
                val reference = bound.arguments.filterIsInstance<Ref<*>>().single()
                val prelude = bound.orderedPrelude
                val metadata = (prelude as OwnedCode).metadata
                val body = session.arena.block(
                    metadata,
                    prelude.statements() +
                        session.arena.stmt(metadata, StmtNode.Eval(reference.load()))
                )
                // The pure plan boundary has no physical selector knowledge. Emission through
                // the production bridge participant reaches the actual location guard; a
                // successful prepare is intentionally not applied, so nothing is published.
                environment.checked(prepared.call.source) {
                    val plan = environment.lower(body)
                    CompilerBridge(environment.bindings).prepare(plan).prepare()
                }
            }
            assertTrue(failure.message.orEmpty().contains("context", ignoreCase = true), failure.message)
            assertEquals(0, DamageEntries.hostCalls, "A rejected unstable context must not invoke the macro host")
            sentinel.unchanged()
        }
    }
}
