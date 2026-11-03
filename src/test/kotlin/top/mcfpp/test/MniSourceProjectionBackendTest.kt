package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniSourceProjectionBackendTest {
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

    private class Fixture(configure: Fixture.() -> Unit = {}) : AutoCloseable {
        val namespace = Namespace("backend")
        val file = MCFPPFile(namespace)
        val caller = Function("caller", "backend", null)
        val counter = MCInt("counter")
        val total = MCInt("total")
        val counterPlace = Place(top.mcfpp.analysis.SymbolId.fresh())
        val counterData = StoredData(
            counterPlace,
            NBTPath(StorageSource("backend:input")).memberIndex("counter"),
            layout = StorageLayout.Scoreboard(counter.name, counter.sbObject.name)
        )
        val items = NBTList("items", MCFPPListType(MCFPPBaseType.Int)).apply {
            nbtPath = NBTPath(StorageSource("backend:input")).memberIndex("items")
        }
        val session: ExpansionSession
        val bindings: CompilerSourceBindings
        val environment: SourceProjectionEnvironment
        val arena: CodeArena
        val int: TypeRef<*>
        val bool: TypeRef<*>
        val scope: ScopeId
        init {
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            namespace.scope.journalFileFields.add(file.field)
            caller.declarationFile = file
            namespace.scope.functions[caller.identifier] = arrayListOf(caller)
            val sink = Function("sink", "backend", parser("{}").curlBlock()).apply {
                declarationFile = file
                returnType = MCFPPPrivateType.Void
                normalParams.add(FunctionParam(MCFPPBaseType.Int, "value", this))
            }
            namespace.scope.functions["sink"] = arrayListOf(sink)
            counter.symbol = Symbol(counterPlace.root, "counter", MCFPPBaseType.Int.typeId, true, forceRuntime = true)
            counter.storageBinding = StorageBinding(counterData, counterPlace, counterData.path)
            counterData.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            counterData.declarations[counterPlace] = counter.symbol!!
            counterData.facts.write(counterPlace, ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown))
            val alias = MCInt("alias").apply {
                name = total.name
                sbObject = total.sbObject
            }
            caller.scope.vars["counter"] = counter
            caller.scope.vars["total"] = total
            caller.scope.vars["alias"] = alias
            caller.scope.vars["items"] = items
            Function.currFunction = caller
            configure()
            val syntax = parser("sink(counter)").functionCall()
            val ready = assertIs<CompilerCallPreparationResult.Ready>(CompilerBindingEnvironment.prepareCall(syntax, caller))
            session = ExpansionSession(
                ExpansionSite(ready.call.source, "backend-test"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = "backend"),
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
            int = bindings.binding.types.ref(MCFPPBaseType.Int)
            bool = bindings.binding.types.ref(MCFPPBaseType.Bool)
        }
        fun meta(scope: ScopeId = this.scope) = arena.metadata(scope, session.origin, bindings.loweringTarget.executionContext)
        fun root(name: String) = bindings.roots.getValue(bindings.binding.symbol(name)!!.symbol)
        fun integer(value: Long, scope: ScopeId = this.scope) = arena.expr(meta(scope), int, ExprNode.Literal(ConstantValue.Integral(value)))
        fun expr(type: TypeRef<*>, node: ExprNode, scope: ScopeId = this.scope) = arena.expr(meta(scope), type, node)
        fun stmt(node: StmtNode, scope: ScopeId = this.scope) = arena.stmt(meta(scope), node)
        fun block(statements: List<Stmt>, scope: ScopeId = this.scope) = arena.block(meta(scope), statements)
        fun binary(operator: BinaryOperator, left: Expr<*>, right: Expr<*>, scope: ScopeId = this.scope) = expr(if (operator in setOf(BinaryOperator.EQ, BinaryOperator.LT)) bool else int, ExprNode.Binary(operator, left, right), scope)
        fun location(ref: Ref<*>) = (ref as ImmutableRef<*>).location
        fun alias(root: Ref<*>, address: AddressId, type: TypeRef<*>, scope: ScopeId) = arena.ref(
            meta(scope),
            type,
            NodeLocation(location(root).root, executionContextId = location(root).executionContextId, capturedBase = address)
        )
        fun lower(code: Code) = environment.lower(code)
        fun compile(code: Code): Pair<CompilerBridge, PreparedCompilerCommit> {
            val plan = lower(code)
            val bridge = CompilerBridge(bindings)
            val before = caller.commands.toList()
            val commit = bridge.prepare(plan).prepare()
            assertEquals(before, caller.commands.toList(), "Preparation must not publish commands")
            commit.apply()
            return bridge to commit
        }
        fun execute(initial: List<String> = emptyList()): ScoreCommandExecutor {
            val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.functions.values.flatten() }.associate { function ->
                    function.namespaceID.toString() to function.commands.map { it.analyze() }
                } + Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { it.value.lines() }
            val commands = listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "scoreboard players set ${counter.name} ${counter.sbObject.name} 0",
                "scoreboard players set ${total.name} ${total.sbObject.name} 0"
            ) + initial +
                caller.commands.map { it.analyze() } + "data remove storage mcfpp:system stack_frame[0]"
            return ScoreCommandExecutor(commands, functions)
        }
        override fun close() = session.close()
        companion object {
            fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "backend-source.mcfpp"))))
        }
    }

    @Test fun branchLoopAndOriginalAliasesExecuteRealCommandsAndPlanExitStaysLocal() = isolated {
        Fixture { caller.commands.add("say before-plan") }.use { f ->
            val counter = f.root("counter")
            val total = f.root("total")
            val alias = f.root("alias")
            assertEquals(f.location(total), f.location(alias))
            val bodyScope = f.arena.childScope(f.scope)
            val thenScope = f.arena.childScope(bodyScope)
            val elseScope = f.arena.childScope(bodyScope)
            val remainder = f.binary(BinaryOperator.MODULO, counter.load(), f.integer(2), bodyScope)
            val condition = f.binary(BinaryOperator.EQ, remainder, f.integer(0), bodyScope)
            val yes = f.block(
                listOf(
                    f.stmt(
                        StmtNode.Assign(
                            total,
                            f.binary(BinaryOperator.ADD, alias.load(), f.integer(10), thenScope)
                        ),
                        thenScope
                    )
                ),
                thenScope
            )
            val no = f.block(
                listOf(
                    f.stmt(
                        StmtNode.Assign(
                            alias,
                            f.binary(BinaryOperator.ADD, total.load(), f.integer(1), elseScope)
                        ),
                        elseScope
                    )
                ),
                elseScope
            )
            val body = f.block(
                listOf(
                    f.stmt(StmtNode.If(condition, yes, no), bodyScope),
                    f.stmt(StmtNode.Assign(counter, f.binary(BinaryOperator.ADD, counter.load(), f.integer(1), bodyScope)), bodyScope)
                ),
                bodyScope
            )
            val loop = f.stmt(StmtNode.While(f.binary(BinaryOperator.LT, counter.load(), f.integer(4)), body, f.arena.freshRegion()))
            f.compile(f.block(listOf(loop)))
            f.caller.commands.add("say after-plan")
            val machine = f.execute()
            assertEquals(4, machine.values["${f.counter.name} ${f.counter.sbObject.name}"])
            assertEquals(22, machine.values["${f.total.name} ${f.total.sbObject.name}"])
            assertEquals(listOf("before-plan", "after-plan"), machine.messages)
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }

    @Test fun dynamicCapturedBaseAndTwoAliasesKeepTheOriginalElementAfterIndexChanges() = isolated {
        Fixture().use { f ->
            val root = f.root("items")
            val counter = f.root("counter")
            val bodyScope = f.arena.childScope(f.scope)
            val base = f.arena.freshAddress()
            val selected = f.arena.freshAddress()
            val listType = f.bindings.binding.types.ref(MCFPPListType(MCFPPBaseType.Int))
            val first = f.stmt(
                StmtNode.CaptureAddress(
                    base,
                    NodeLocation(
                        f.location(root).root,
                        listOf(LocationStep.CapturedIndex(f.location(counter).root)),
                        executionContextId = f.location(root).executionContextId
                    ),
                    listType
                ),
                bodyScope
            )
            val second = f.stmt(
                StmtNode.CaptureAddress(
                    selected,
                    NodeLocation(
                        f.location(root).root,
                        listOf(LocationStep.CapturedIndex(f.location(counter).root)),
                        executionContextId = f.location(root).executionContextId,
                        capturedBase = base
                    ),
                    f.int
                ),
                bodyScope
            )
            val a = f.alias(root, selected, f.int, bodyScope)
            val b = f.alias(root, selected, f.int, bodyScope)
            val region = f.arena.freshRegion()
            val body = f.block(
                listOf(
                    first,
                    f.stmt(StmtNode.Assign(counter, f.integer(1)), bodyScope),
                    second,
                    f.stmt(StmtNode.Assign(counter, f.integer(0)), bodyScope),
                    f.stmt(StmtNode.Assign(a, f.integer(42)), bodyScope),
                    f.stmt(StmtNode.Assign(b, f.binary(BinaryOperator.ADD, a.load(), f.integer(1), bodyScope)), bodyScope),
                    f.stmt(StmtNode.Yield(region, b.load()), bodyScope)
                ),
                bodyScope
            )
            val (bridge, _) = f.compile(f.expr(f.int, ExprNode.ExprBlock(body, f.int, region)))
            val machine = f.execute(listOf("data modify storage backend:input items set value [[3,7],[8,9]]"))
            assertEquals(43, machine.read(assertIs<MCInt>(bridge.result())))
            assertEquals(43, machine.readNbt("backend:input", "items[0][1]").value)
            assertEquals(3, machine.readNbt("backend:input", "items[0][0]").value)
            assertEquals(9, machine.readNbt("backend:input", "items[1][1]").value)
        }
    }

    @Test fun valueIndexUsesTheReceiverSnapshotBeforeItsIndexMutatesOriginalStorage() = isolated {
        Fixture().use { f ->
            val root = f.root("items")
            val listType = f.bindings.binding.types.ref(MCFPPListType(MCFPPBaseType.Int))
            val receiver = f.expr(listType, ExprNode.Index(root.load(), f.integer(0)))
            val indexScope = f.arena.childScope(f.scope)
            val region = f.arena.freshRegion()
            val replacement = f.expr(
                root.type(),
                ExprNode.Literal(
                    ConstantValue.Sequence(
                        listOf(
                            ConstantValue.Sequence(listOf(ConstantValue.Integral(90), ConstantValue.Integral(91))),
                            ConstantValue.Sequence(listOf(ConstantValue.Integral(80), ConstantValue.Integral(81)))
                        )
                    )
                )
            )
            val indexBody = f.block(
                listOf(
                    f.stmt(StmtNode.Assign(root, replacement), indexScope),
                    f.stmt(StmtNode.Yield(region, f.integer(1)), indexScope)
                ),
                indexScope
            )
            val index = f.expr(f.int, ExprNode.ExprBlock(indexBody, f.int, region))
            val (bridge, _) = f.compile(f.expr(f.int, ExprNode.Index(receiver, index)))
            val machine = f.execute(listOf("data modify storage backend:input items set value [[3,7],[8,9]]"))
            assertEquals(7, machine.read(assertIs<MCInt>(bridge.result())))
            assertEquals(91, machine.readNbt("backend:input", "items[0][1]").value)
        }
    }

    @Test fun stagingFailureAfterARealWriteRestoresCommandsFunctionsStorageFlowAndResources() = isolated {
        Fixture { caller.commands.add("say sentinel") }.use { f ->
            val sink = f.bindings.binding.functions.single { it.name == "sink" }.function
            val call = f.expr(sink.returnType(), ExprNode.Call(sink, listOf(CallArgument.value(f.root("counter").load()))))
            val code = f.block(listOf(f.stmt(StmtNode.Assign(f.root("counter"), f.integer(9))), f.stmt(StmtNode.Eval(call))))
            val original = f.lower(code)
            // A canonical ordinary target remains real; only the copied plan kind is forged,
            // so the plan is genuinely unsupported rather than relying on ordinary calls
            // being unimplemented.
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
                original.target, original.entry, original.resultType, blocks, original.symbols,
                original.scopes, original.domain, original.executionFrames, original.contextRegions
            )
            val bridge = CompilerBridge(f.bindings)
            val participant = bridge.prepare(plan)
            val commands = f.caller.commands.toList()
            val children = f.caller.child.toList()
            val functions = f.namespace.scope.functions.mapValues { it.value.toList() }
            val resources = Project.macroFunction.toMap()
            val facts = f.counterData.facts.fork()
            val version = f.counterData.versions.version(f.counterPlace)
            val diagnostics = Project.errorCount to Project.warningCount
            val failure = assertFailsWith<CodeModelException> { participant.prepare() }
            assertEquals("backend-source.mcfpp", failure.source.file)
            assertTrue(failure.message!!.contains("call lowering"))
            assertEquals(commands, f.caller.commands.toList())
            assertEquals(children, f.caller.child.toList())
            assertEquals(functions, f.namespace.scope.functions.mapValues { it.value.toList() })
            assertEquals(resources, Project.macroFunction.toMap())
            assertEquals(facts, f.counterData.facts)
            assertEquals(version, f.counterData.versions.version(f.counterPlace))
            assertEquals(diagnostics, Project.errorCount to Project.warningCount)
            assertSame(f.caller, Function.currFunction)
        }
    }

    @Test fun completeDomainAndCanonicalTypeAreRequiredBeforeAnyEmission() = isolated {
        Fixture().use { f ->
            val original = f.lower(f.integer(4))
            val stale = LoweringPlan(
                original.target,
                original.entry,
                original.resultType,
                original.blocks,
                original.symbols,
                original.scopes,
                PlanDomainStamp(original.domain.typeDomains, "stale", original.domain.targetVersion)
            )
            assertFailsWith<IllegalArgumentException> { CompilerBridge(f.bindings).prepare(stale) }
            assertTrue(f.caller.commands.isEmpty())
            val badType = original.resultType!!.copy(stableSourceId = "forged:type")
            val block = original.blocks.single()
            val literal = assertIs<PlanOperation.Literal>(block.operations.single())
            val forged = LoweringPlan(
                original.target,
                original.entry,
                badType,
                listOf(
                    PlanBasicBlock(
                        block.id,
                        block.scope,
                        listOf(literal.copy(result = literal.result.copy(type = badType))),
                        block.terminator
                    )
                ),
                original.symbols,
                original.scopes,
                original.domain
            )
            assertFailsWith<IllegalArgumentException> { CompilerBridge(f.bindings).prepare(forged).prepare() }
            assertTrue(f.caller.commands.isEmpty())
        }
    }

    @Test fun compilerOnlyLocalsAndEqualBranchResultsKeepCanonicalTypeValues() = isolated {
        fun fixture() = Fixture { caller.scope.vars["descriptor"] = MCFPPTypeVar(MCFPPBaseType.Int, "descriptor") }
        fixture().use { f ->
            val descriptor = f.root("descriptor")
            val local = f.arena.local(f.meta(), descriptor.type(), "typeLocal")
            val region = f.arena.freshRegion()
            val yesScope = f.arena.childScope(f.scope)
            val noScope = f.arena.childScope(f.scope)
            val yes = f.block(listOf(f.stmt(StmtNode.Yield(region, local.load()), yesScope)), yesScope)
            val no = f.block(listOf(f.stmt(StmtNode.Yield(region, descriptor.load()), noScope)), noScope)
            val body = f.block(
                listOf(
                    f.stmt(StmtNode.LocalDecl(local, descriptor.load())),
                    f.stmt(StmtNode.If(f.binary(BinaryOperator.EQ, f.root("counter").load(), f.integer(0)), yes, no))
                )
            )
            val (bridge, _) = f.compile(f.expr(descriptor.type(), ExprNode.ExprBlock(body, descriptor.type(), region)))
            assertSame(MCFPPBaseType.Int, StorageAccess.resolveTypeValue(assertNotNull(bridge.result())))
            assertTrue(f.execute().failedScoreOperations.isEmpty())
            assertTrue(f.execute(listOf("scoreboard players set ${f.counter.name} ${f.counter.sbObject.name} 1")).failedScoreOperations.isEmpty())
        }
        fixture().use { f ->
            val type = f.root("descriptor").type()
            val region = f.arena.freshRegion()
            val yesScope = f.arena.childScope(f.scope)
            val noScope = f.arena.childScope(f.scope)
            val yes = f.block(
                listOf(
                    f.stmt(
                        StmtNode.Yield(
                            region,
                            f.expr(type, ExprNode.Literal(ConstantValue.TypeValue(f.int)), yesScope)
                        ),
                        yesScope
                    )
                ),
                yesScope
            )
            val no = f.block(
                listOf(
                    f.stmt(
                        StmtNode.Yield(
                            region,
                            f.expr(type, ExprNode.Literal(ConstantValue.TypeValue(f.bool)), noScope)
                        ),
                        noScope
                    )
                ),
                noScope
            )
            val body = f.block(
                listOf(
                    f.stmt(
                        StmtNode.If(
                            f.binary(BinaryOperator.EQ, f.root("counter").load(), f.integer(0)),
                            yes,
                            no
                        )
                    )
                )
            )
            assertFailsWith<CodeModelException> { f.compile(f.expr(type, ExprNode.ExprBlock(body, type, region))) }
            assertTrue(f.caller.commands.isEmpty())
        }
    }

    @Test fun oneShotSourceInitializationDominatesBothRuntimeBranches() = isolated {
        var initializations = 0
        Fixture {
            counterData.refreshWriter {
                initializations++
                Function.addCommand("scoreboard players set ${counter.name} ${counter.sbObject.name} 11")
            }
        }.use { f ->
            val yesScope = f.arena.childScope(f.scope)
            val noScope = f.arena.childScope(f.scope)
            fun arm(scope: ScopeId) = f.block(listOf(f.stmt(StmtNode.Assign(f.root("total"), f.root("counter").load()), scope)), scope)
            f.compile(
                f.block(
                    listOf(
                        f.stmt(
                            StmtNode.If(
                                f.binary(BinaryOperator.EQ, f.root("total").load(), f.integer(0)),
                                arm(yesScope),
                                arm(noScope)
                            )
                        )
                    )
                )
            )
            assertEquals(1, initializations)
            assertEquals(11, f.execute().values["${f.total.name} ${f.total.sbObject.name}"])
            assertEquals(
                11,
                f.execute(listOf("scoreboard players set ${f.total.name} ${f.total.sbObject.name} 1"))
                    .values["${f.total.name} ${f.total.sbObject.name}"]
            )
        }
    }

    @Test fun derivedFieldUsesItsFrozenScoreLayoutInsteadOfTheParentNbtLayout() = isolated {
        Fixture {
            val model = DataTemplate("Box", namespace.identifier).apply { declarationFile = file }
            namespace.scope.template[model.identifier] = model
            val member = MCInt("value").apply { declaredParentTemplate = model }
            model.scope.putVar(member.identifier, member, true)
            model.scope.putProperty(member.identifier, Property.buildSimpleProperty(member).apply { declaredParentTemplate = model })
            val box = DataTemplateObject(model, "box")
            val place = Place(top.mcfpp.analysis.SymbolId.fresh())
            val data = StoredData(
                place,
                NBTPath(StorageSource("backend:input")).memberIndex("box"),
                layout = StorageLayout.Nbt("storage backend:input", "box")
            )
            box.symbol = Symbol(place.root, "box", box.type.typeId, true, forceRuntime = true)
            box.storageBinding = StorageBinding(data, place, data.path)
            data.types[box.type.typeId] = box.type
            data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
            data.declarations[place] = box.symbol!!
            data.registerLayout(place.field("value"), StorageLayout.Scoreboard("#child", "mcfpp_default"))
            caller.scope.vars[box.identifier] = box
        }.use { f ->
            val box = f.root("box")
            val field = box.type().fields().single { it.name() == "value" }
            val child = f.arena.ref(
                f.meta(),
                f.int,
                NodeLocation(
                    f.location(box).root,
                    listOf(LocationStep.Field(field)),
                    executionContextId = f.location(box).executionContextId
                )
            )
            f.compile(
                f.block(
                    listOf(
                        f.stmt(StmtNode.Assign(child, f.integer(23))),
                        f.stmt(StmtNode.Assign(f.root("total"), child.load()))
                    )
                )
            )
            val machine = f.execute(
                listOf(
                    "scoreboard players set #child mcfpp_default 7",
                    "data modify storage backend:input box set value {value:99}"
                )
            )
            assertEquals(23, machine.values["#child mcfpp_default"])
            assertEquals(23, machine.values["${f.total.name} ${f.total.sbObject.name}"])
            assertEquals(99, machine.readNbt("backend:input", "box.value").value)
        }
    }

    @Test fun frozenRepresentationViewsPreserveStringPayloadAndRuntimeConcatStoresExactResult() = isolated {
        fun fixture() = Fixture {
            caller.scope.vars["text"] = MCString("text").apply {
                nbtPath = NBTPath(StorageSource("backend:input")).memberIndex("text")
            }
            caller.scope.vars["erased"] = MCAny("erased").apply {
                val place = Place(top.mcfpp.analysis.SymbolId.fresh())
                val data = StoredData(place, NBTPath(StorageSource("backend:input")).memberIndex("erased"))
                symbol = Symbol(place.root, identifier, type.typeId, true, forceRuntime = true)
                storageBinding = StorageBinding(data, place, data.path)
                data.types[type.typeId] = type
                data.declarations[place] = symbol!!
                data.facts.write(place, ValueFacts(TypeKnowledge.Exact(type.typeId), ValueKnowledge.Unknown))
            }
            caller.scope.vars["nullType"] = MCFPPTypeVar(MCFPPPrivateType.Null, "nullType")
        }
        val payload = "a\"b\\c"
        fixture().use { f ->
            val text = f.root("text")
            val any = f.root("erased").type()
            val erased = f.expr(any, ExprNode.Convert(text.load(), any, ConversionKind.IMPLICIT))
            val restored = f.expr(text.type(), ExprNode.Convert(erased, text.type(), ConversionKind.EXPLICIT))
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(text, restored)))))
            val machine = f.execute(listOf("data modify storage backend:input text set value ${Tag.toSNBT(StringTag(payload))}"))
            assertEquals(payload, machine.readNbt("backend:input", "text").value)
        }
        fixture().use { f ->
            val erased = f.root("erased")
            val nullType = f.bindings.binding.types.ref(MCFPPPrivateType.Null)
            val missing = f.expr(nullType, ExprNode.Literal(ConstantValue.NullValue))
            val empty = f.expr(erased.type(), ExprNode.Convert(missing, erased.type(), ConversionKind.IMPLICIT))
            val previous = f.expr(f.root("text").type(), ExprNode.Literal(ConstantValue.Text(payload)))
            val initial = f.expr(erased.type(), ExprNode.Convert(previous, erased.type(), ConversionKind.IMPLICIT))
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(erased, initial)), f.stmt(StmtNode.Assign(erased, empty)))))
            val machine = f.execute()
            assertFails { machine.readNbt("backend:input", "erased") }
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
        fixture().use { f ->
            val text = f.root("text")
            val suffix = f.expr(text.type(), ExprNode.Literal(ConstantValue.Text("\"\\")))
            val concat = f.expr(text.type(), ExprNode.Binary(BinaryOperator.ADD, text.load(), suffix))
            f.compile(f.block(listOf(f.stmt(StmtNode.Assign(text, concat)))))
            val machine = f.execute(
                listOf(
                    "data modify storage backend:input text set value ${Tag.toSNBT(StringTag(payload))}"
                )
            )
            assertEquals(payload + "\"\\", machine.readNbt("backend:input", "text").value)
            assertTrue(machine.failedScoreOperations.isEmpty())
        }
    }
}
