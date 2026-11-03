package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniRuntimeStringTest {
    private fun isolated(action: () -> Unit) {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            action()
        } finally {
            before.restore()
        }
    }

    private fun inputCommands(left: String, right: String) = listOf(
        "data modify storage strings:input left set value ${Tag.toSNBT(StringTag(left))}",
        "data modify storage strings:input right set value ${Tag.toSNBT(StringTag(right))}"
    )

    /**
     * Minimal frozen-source fixture for a runtime string ADD: two input strings live in
     * `strings:input`, the assignment target is a third string at `strings:input.result`,
     * and `alias` is a second symbol bound to the same physical result path.
     */
    private class Fixture : AutoCloseable {
        val namespace = Namespace("strings")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", "strings", null)
        val left = string("left")
        val right = string("right")
        val result = string("result")
        val alias = string("alias", "result")
        val literal = string("literal")
        val folded = string("folded")
        val sink: Function
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        val arena: CodeArena
        val scope: ScopeId

        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            sink = Function("sink", "strings", parser("{}").curlBlock()).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
                normalParams.add(FunctionParam(MCFPPBaseType.String, "value", this))
            }
            namespace.scope.functions["sink"] = arrayListOf(sink)
            caller.scope.vars["left"] = left
            caller.scope.vars["right"] = right
            caller.scope.vars["result"] = result
            caller.scope.vars["alias"] = alias
            caller.scope.vars["literal"] = literal
            caller.scope.vars["folded"] = folded
            Function.currFunction = caller
            val syntax = parser("sink(left)").functionCall()
            val ready = assertIs<CompilerCallPreparationResult.Ready>(
                CompilerBindingEnvironment.prepareCall(syntax, caller)
            )
            session = ExpansionSession(
                ExpansionSite(ready.call.source, "strings-test"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = "strings"),
                CompilerTransaction(
                    CompilerTransactionBackend {
                        object : PreparedCompilerCommit {
                            override fun apply() = Unit
                            override fun rollback() = Unit
                        }
                    }
                )
            )
            bindings = CompilerSourceBindings.capture(ready.environment, session)
            environment = SourceProjectionEnvironment(bindings)
            arena = session.arena
            scope = arena.rootScope
        }

        private fun string(name: String, storageName: String = name): MCString {
            val place = Place(top.mcfpp.analysis.SymbolId.fresh())
            val data = StoredData(place, NBTPath(StorageSource("strings:input")).memberIndex(storageName))
            return MCString(name).apply {
                symbol = Symbol(place.root, name, MCFPPBaseType.String.typeId, true, forceRuntime = true)
                storageBinding = StorageBinding(data, place, data.path)
                data.types[MCFPPBaseType.String.typeId] = MCFPPBaseType.String
                data.declarations[place] = symbol!!
                data.facts.write(place, ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.String.typeId), ValueKnowledge.Unknown))
            }
        }

        fun meta(scope: ScopeId = this.scope) = arena.metadata(scope, session.origin, bindings.loweringTarget.executionContext)
        fun root(name: String) = bindings.roots.getValue(bindings.binding.symbol(name)!!.symbol)
        fun sinkPlan() = bindings.binding.functions.single { it.name == "sink" }.function
        fun expr(type: TypeRef<*>, node: ExprNode, scope: ScopeId = this.scope) = arena.expr(meta(scope), type, node)
        fun stmt(node: StmtNode, scope: ScopeId = this.scope) = arena.stmt(meta(scope), node)
        fun block(statements: List<Stmt>, scope: ScopeId = this.scope) = arena.block(meta(scope), statements)
        fun lower(code: Code) = environment.lower(code)

        fun compile(code: Code): CompilerBridge {
            val plan = lower(code)
            val bridge = CompilerBridge(bindings)
            val commit = bridge.prepare(plan).prepare()
            commit.apply()
            return bridge
        }

        fun functionBodies(localOnly: Boolean = false): Map<String, List<String>> {
            val namespaces = if (localOnly) GlobalScope.localNamespaces.values
            else GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
            return namespaces.flatMap { it.scope.functions.values.flatten() }.associate { function ->
                function.namespaceID.toString() to function.commands.map { it.analyze() }
            }
        }

        fun functionTable(): Map<String, Map<String, List<Function>>> = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
            .associate { it.identifier to it.scope.functions.mapValues { entry -> entry.value.toList() } }

        fun execute(initial: List<String> = emptyList()): ScoreCommandExecutor {
            val functions = functionBodies() + Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }
                .mapValues { it.value.lines() }
            val commands = listOf("data modify storage mcfpp:system stack_frame prepend value {}") + initial +
                caller.commands.map { it.analyze() } + "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }

        /**
         * Export the actual CompilerBridge-generated functions and Project.macroFunction bodies,
         * never a re-typed prototype, plus a 26.3 manifest for the native DSB check.
         */
        fun exportRuntimeStringArtifact(left: String, right: String, expected: Map<String, String>) {
            val root = Path.of(System.getProperty("user.dir"), "build", "inheritance-mni", "runtime-string-check")
            if (Files.exists(root)) Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
            val pack = root.resolve("pack")
            Files.createDirectories(pack)
            Files.writeString(
                pack.resolve("pack.mcmeta"),
                JSON.toJSONString(
                    mapOf(
                        "pack" to mapOf(
                            "description" to "actual compiler runtime STRING ADD artifact",
                            "min_format" to listOf(121, 0),
                            "max_format" to listOf(121, 0)
                        )
                    )
                ),
                Charsets.UTF_8
            )
            functionBodies(localOnly = true).forEach { (id, commands) ->
                val file = pack.resolve("data/${id.substringBefore(':')}/function/${id.substringAfter(':')}.mcfunction")
                Files.createDirectories(file.parent)
                Files.writeString(file, commands.joinToString("\n") + "\n", Charsets.UTF_8)
            }
            Project.macroFunction.forEach { (key, body) ->
                val file = pack.resolve("data/mcfpp/function/dynamic/$key.mcfunction")
                Files.createDirectories(file.parent)
                Files.writeString(file, body + if (body.endsWith('\n')) "" else "\n", Charsets.UTF_8)
            }
            val wrapper = pack.resolve("data/strings/function/runtime_export.mcfunction")
            Files.createDirectories(wrapper.parent)
            Files.writeString(
                wrapper,
                listOf(
                    "scoreboard objectives add mcfpp_default dummy",
                    "data modify storage mcfpp:system stack_frame prepend value {}",
                    "function strings:caller",
                    "data remove storage mcfpp:system stack_frame[0]"
                ).joinToString("\n") + "\n",
                Charsets.UTF_8
            )
            val manifest = linkedMapOf<String, Any>(
                "version" to "26.3",
                "unsupported" to "error",
                "failOnMissingResources" to true,
                "packs" to listOf("./pack"),
                "world" to mapOf(
                    "storage" to mapOf(
                        "strings:input" to mapOf("left" to left, "right" to right)
                    )
                ),
                "steps" to listOf(mapOf("commands" to listOf("function strings:runtime_export"))),
                "assertions" to expected.map { (path, value) ->
                    mapOf(
                        "storage" to mapOf<String, Any>(
                            "id" to "strings:input",
                            "path" to path,
                            "equals" to value
                        )
                    )
                } + mapOf(
                    "storage" to mapOf<String, Any>(
                        "id" to "mcfpp:system",
                        "path" to "stack_frame",
                        "equals" to emptyList<Any>()
                    )
                )
            )
            Files.writeString(
                root.resolve("runtime-string.dps.json"),
                JSON.toJSONString(manifest, JSONWriter.Feature.PrettyFormat),
                Charsets.UTF_8
            )
        }

        override fun close() = session.close()

        companion object {
            fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "strings-source.mcfpp"))))
        }
    }

    private fun assertConcat(left: String, right: String) = isolated {
        Fixture().use { f ->
            val sum = f.expr(
                f.root("left").type(),
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.root("right").load()
                )
            )
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(f.root("result"), sum)))))
            val machine = f.execute(inputCommands(left, right))
            assertEquals(left + right, (machine.readNbt("strings:input", "result") as StringTag).value)
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun runtimeStringAddKeepsExactQuotesBackslashesControlsAndUnicode() {
        assertConcat("", "")
        assertConcat("a", "b")
        assertConcat("he said \"hi\"", " and \"bye\"")
        assertConcat("a\\b\\\\c", "\\d\\")
        assertConcat("\\\"", "\"\\")
        assertConcat("a\nb\tc\rd\be\u000cf", "g\n")
        assertConcat("caf\u00e9\u4e2d\u6587", "\u00e9\u4e2d")
        assertConcat("$(left)", "$(right)\\")
    }

    @Test fun runtimeStringAddWithLiteralOperandPreservesMacroText() = isolated {
        Fixture().use { f ->
            val literal = "\$(name)\"\\"
            val sum = f.expr(
                f.root("left").type(),
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.expr(f.root("left").type(), ExprNode.Literal(ConstantValue.Text(literal)))
                )
            )
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(f.root("literal"), sum)))))
            assertTrue(
                Project.macroFunction.values.none { it.contains(literal) },
                "literal SNBT must not be registered as a macro"
            )
            val left = "run \$(left)\"\\"
            val machine = f.execute(inputCommands(left, ""))
            assertEquals(left + literal, (machine.readNbt("strings:input", "literal") as StringTag).value)
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun foldedStringAddPreservesLiteralMacroText() = isolated {
        Fixture().use { f ->
            val type = f.root("left").type()
            val left = "fold\$(left)\""
            val right = "\\tail\$(right)"
            val sum = f.expr(
                type,
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.expr(type, ExprNode.Literal(ConstantValue.Text(left))),
                    f.expr(type, ExprNode.Literal(ConstantValue.Text(right)))
                )
            )
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(f.root("folded"), sum)))))
            assertTrue(Project.macroFunction.isEmpty(), "literal SNBT must not be registered as a macro")
            val machine = f.execute()
            assertEquals(left + right, (machine.readNbt("strings:input", "folded") as StringTag).value)
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun runtimeStringAddCopiesEachOperandOnceInSourceOrder() = isolated {
        Fixture().use { f ->
            val sum = f.expr(
                f.root("left").type(),
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.root("right").load()
                )
            )
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(f.root("result"), sum)))))
            val owner = f.functionBodies().values.single { commands ->
                commands.count { it.contains("s_state.srcL set from") } == 1 &&
                    commands.count { it.contains("s_state.srcR set from") } == 1
            }
            val leftCopy = owner.indexOfFirst { it.contains("s_state.srcL set from") }
            val rightCopy = owner.indexOfFirst { it.contains("s_state.srcR set from") }
            assertTrue(leftCopy >= 0)
            assertTrue(rightCopy > leftCopy, "right operand must be copied after the left operand")
            assertEquals(1, owner.count { it.contains("s_state.srcL set from") })
            assertEquals(1, owner.count { it.contains("s_state.srcR set from") })
        }
    }

    @Test fun runtimeStringAddWritesTheActualAliasedDestination() = isolated {
        Fixture().use { f ->
            val sum = f.expr(
                f.root("left").type(),
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.root("right").load()
                )
            )
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(f.root("alias"), sum)))))
            val machine = f.execute(inputCommands("\"", "\\"))
            assertEquals("\"\\", (machine.readNbt("strings:input", "result") as StringTag).value)
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun generationFailureAfterStringHelpersRestoresCommandsFunctionsMacrosAndCurrentFunction() = isolated {
        Fixture().use { f ->
            val sum = f.expr(
                f.root("left").type(),
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.root("right").load()
                )
            )
            val sink = f.sinkPlan()
            val call = f.expr(
                sink.returnType(),
                ExprNode.Call(
                    sink,
                    listOf(CallArgument.value(f.root("left").load()))
                )
            )
            val original = f.lower(
                f.block(
                    listOf(
                        f.stmt(StmtNode.Assign(f.root("result"), sum)),
                        f.stmt(StmtNode.Eval(call))
                    )
                )
            )
            // Keep the canonical ordinary target but forge only the copied plan kind, so the
            // failure occurs after the string ADD and its helpers have been published.
            val blocks = original.blocks.map { block ->
                val operations = block.operations.map { operation ->
                    if (operation is PlanOperation.Call) PlanOperation.Call(
                        operation.result,
                        PlanFunction(
                            operation.function.key,
                            operation.function.name,
                            PlanCallKind.LEGACY_NATIVE,
                            operation.function.parameters,
                            operation.function.resultType,
                            operation.function.readonlyParameters
                        ),
                        operation.arguments,
                        operation.site,
                        operation.readonlyArguments
                    ) else operation
                }
                if (operations == block.operations) block else PlanBasicBlock(
                    block.id,
                    block.scope,
                    operations,
                    block.terminator,
                    block.executionFrame,
                    block.contextRegions
                )
            }
            val plan = LoweringPlan(
                original.target, original.entry, original.resultType, blocks,
                original.symbols, original.scopes, original.domain, original.executionFrames,
                original.contextRegions
            )
            val bridge = CompilerBridge(f.bindings)
            val participant = bridge.prepare(plan)
            val commands = f.caller.commands.toList()
            val children = f.caller.child.toList()
            val functions = f.functionTable()
            val macros = Project.macroFunction.toMap()
            val current = Function.currFunction
            assertFailsWith<CodeModelException> { participant.prepare() }
            assertEquals(commands, f.caller.commands.toList())
            assertEquals(children, f.caller.child.toList())
            assertEquals(functions, f.functionTable())
            assertEquals(macros, Project.macroFunction.toMap())
            assertSame(current, Function.currFunction)
        }
    }

    @Test fun runtimeStringExportWritesActualCompilerPackAndManifest() = isolated {
        Fixture().use { f ->
            val type = f.root("left").type()
            val dynamic = f.expr(
                type,
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.root("right").load()
                )
            )
            val runtimeLiteralText = "\$(left)\\\"\\"
            val runtimeLiteral = f.expr(
                type,
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.root("left").load(),
                    f.expr(type, ExprNode.Literal(ConstantValue.Text(runtimeLiteralText)))
                )
            )
            val foldLeft = "fold\$(left)\\\""
            val foldRight = "\\\"tail\$(right)"
            val folded = f.expr(
                type,
                ExprNode.Binary(
                    BinaryOperator.ADD,
                    f.expr(type, ExprNode.Literal(ConstantValue.Text(foldLeft))),
                    f.expr(type, ExprNode.Literal(ConstantValue.Text(foldRight)))
                )
            )
            f.compile(
                f.block(
                    listOf(
                        f.stmt(StmtNode.Assign(f.root("result"), dynamic)),
                        f.stmt(StmtNode.Assign(f.root("literal"), runtimeLiteral)),
                        f.stmt(StmtNode.Assign(f.root("folded"), folded))
                    )
                )
            )
            val left = "he said \"a\\b\"\n\r\b\u000c中"
            val right = "\uD83D\uDE00\\\"\t中"
            val expected = linkedMapOf(
                "result" to left + right,
                "literal" to left + runtimeLiteralText,
                "folded" to foldLeft + foldRight
            )
            f.exportRuntimeStringArtifact(left, right, expected)
            val root = Path.of(System.getProperty("user.dir"), "build", "inheritance-mni", "runtime-string-check")
            assertTrue(Files.exists(root.resolve("runtime-string.dps.json")))
            assertTrue(Files.exists(root.resolve("pack/data/strings/function/caller.mcfunction")))
            assertTrue(Files.exists(root.resolve("pack/data/strings/function/runtime_export.mcfunction")))
            assertTrue(Files.readString(root.resolve("pack/pack.mcmeta")).contains("121"))
            val manifest = JSON.parseObject(Files.readString(root.resolve("runtime-string.dps.json")))
            val assertions = manifest.getJSONArray("assertions")
            assertEquals(expected.size + 1, assertions.size)
            expected.entries.forEachIndexed { index, (path, value) ->
                val storage = assertions.getJSONObject(index).getJSONObject("storage")
                assertEquals("strings:input", storage.getString("id"))
                assertEquals(path, storage.getString("path"))
                assertEquals(value, storage.getString("equals"))
            }
            val frame = assertions.getJSONObject(expected.size).getJSONObject("storage")
            assertEquals("mcfpp:system", frame.getString("id"))
            assertEquals("stack_frame", frame.getString("path"))
            assertEquals(0, frame.getJSONArray("equals").size)
        }
    }
}
