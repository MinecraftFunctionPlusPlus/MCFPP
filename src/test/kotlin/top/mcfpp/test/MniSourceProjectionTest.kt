@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.api.*
import top.mcfpp.mni.api.SymbolId
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** All projector fixtures enter through the production compiler index and session bridge. */
internal class SourceProjectionFixture {
    fun parser(text: String, file: String = "projection-call.mcfpp") = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, file))))
    fun file(name: String): MCFPPFile = Namespace(name).let { namespace ->
        GlobalScope.localNamespaces[name] = namespace
        MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    fun function(
        file: MCFPPFile,
        name: String,
        parameters: List<MCFPPType> = emptyList(),
        result: MCFPPType = MCFPPBaseType.Int
    ): Function = Function(
        name,
        file.namespace.identifier,
        parser("{}", "$name-declaration.mcfpp").curlBlock()
    ).apply {
        declarationFile = file
        returnType = result
        parameters.forEachIndexed { index, type -> normalParams.add(FunctionParam(type, "p$index", this)) }
        file.namespace.scope.functions.getOrPut(name) { arrayListOf() }.add(this)
    }
    fun caller(file: MCFPPFile) = Function("caller", file.namespace.identifier, null).apply {
        declarationFile = file
        Function.currFunction = this
        commands.add("say projection-sentinel")
    }
    fun ready(text: String, caller: Function): CompilerCallPreparationResult.Ready {
        val parser = parser(text)
        val syntax = parser.functionCall()
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertEquals(Token.EOF, parser.currentToken.type)
        val result = CompilerBindingEnvironment.prepareCall(syntax, caller)
        return assertIs<CompilerCallPreparationResult.Ready>(
            result,
            (result as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
    }
    fun internalReady(text: String, caller: Function, positions: Set<Int>) =
        InternalReferenceCalls.prepare(text, caller, positions)
    fun session(prepared: CompilerCallPreparationResult.Ready) = ExpansionSession(
        ExpansionSite(prepared.call.source, "projection-test"),
        ExpansionBudget(),
        CompilerSourceBindings.expansionEnvironment(prepared.environment),
        CompilerTransaction(CompilerTransactionBackend { error("Pure projection cannot publish") })
    )
    fun location(reference: Ref<*>): NodeLocation = when (reference) {
        is ImmutableRef<*> -> reference.location
        is ImmutableLocal<*> -> reference.location
        is ImmutableMinecraftScoreRef -> error("Expected the compiler's enrolled source root")
    }
    fun operations(plan: LoweringPlan) = plan.blocks.flatMap { it.operations }

    class Sentinel(private val caller: Function) {
        private val commands = caller.commands.toList()
        private val scope = caller.scope.vars.toMap()
        private val stack = scope.mapValues { it.value.stackIndex }
        private val children = caller.child.toList()
        private val parents = caller.parent.toList()
        private val flow = scope.values.mapNotNull { it.storageBinding?.data }.distinct().associateWith { it.facts.fork() }
        private val resources = GlobalScope.scoreboards.toMap()
        private val diagnostics = Project.errorCount to Project.warningCount
        fun unchanged() {
            assertSame(caller, Function.currFunction)
            assertEquals(commands, caller.commands)
            assertEquals(scope.keys, caller.scope.vars.keys)
            scope.forEach { (name, value) ->
                assertSame(value, caller.scope.vars[name])
                assertEquals(stack[name], value.stackIndex)
            }
            assertEquals(children, caller.child)
            assertEquals(parents, caller.parent)
            flow.forEach { (data, facts) -> assertEquals(facts, data.facts) }
            assertEquals(resources, GlobalScope.scoreboards)
            assertEquals(diagnostics, Project.errorCount to Project.warningCount)
        }
    }
    companion object {
        fun isolated(action: SourceProjectionFixture.() -> Unit) {
            val project = ProjectStateJournal.checkpoint()
            val namespaces = NamespaceStateJournal.checkpoint()
            val current = Function.currFunction
            val nullFunction = Function.nullFunction
            val extra = Function.extraFunction
            val forced = Function.forcedField
            val template = DataTemplate.currTemplate
            try {
                Project.config = ProjectConfig(version = "26.3")
                Project.init()
                MCFPPFile.currFile = null
                Function.forcedField = null
                DataTemplate.currTemplate = null
                SourceProjectionFixture().action()
            } finally {
                namespaces.restore()
                project.restore()
                Function.currFunction = current
                Function.nullFunction = nullFunction
                Function.extraFunction = extra
                Function.forcedField = forced
                DataTemplate.currTemplate = template
            }
        }
    }
}

class MniSourceProjectionTest {
    companion object {
        private var hostExecutions = 0
    }
    class Entries {
        companion object {
            @JvmStatic
            @MNIMacro(
                identifier = "sequence",
                normalParams = [
                    "int",
                    "int",
                    "int",
                    "int"
                ],
                returnType = "void"
            )
            fun sequence(context: MacroContext, unused: Value<IntT>, first: Ref<IntT>, later: Value<IntT>, alias: Ref<IntT>): Block {
                hostExecutions++
                error("Macro host executed during projection")
            }
        }
    }

    @Test fun realSourceExpressionsKeepConversionsShortCircuitAndValueFieldEvaluationAtTheirOriginalPosition() = SourceProjectionFixture.isolated {
        val file = file("projection.expressions")
        val combine = function(file, "combine", listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Bool, MCFPPBaseType.Bool))
        val predicate = function(file, "predicate", result = MCFPPBaseType.Bool)
        val caller = caller(file)
        val x = MCInt("x").apply { stackIndex = 7 }
        caller.scope.vars["x"] = x
        val place = Place(top.mcfpp.analysis.SymbolId.fresh())
        val data = StoredData(place, NBTPath(StorageSource("projection:locals")), layout = StorageLayout.Scoreboard("x", "mcfpp"))
        x.symbol = Symbol(top.mcfpp.analysis.SymbolId.fresh(), "x", x.type.typeId, true)
        x.storageBinding = StorageBinding(data, place, data.path)
        data.types[x.type.typeId] = x.type
        data.declarations[place] = x.symbol!!
        val result = ready("combine(-x+4,(x as float)==3.0f,false&&predicate(),x==3.0f)", caller)
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        lateinit var plan: LoweringPlan
        lateinit var root: SymbolId
        session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            root = location(environment.roots.getValue(environment.binding.symbol("x")!!.symbol)).root
            val code = SourceCodeProjector(environment).call(result.call)
            assertIs<Expr<*>>(code)
            plan = environment.lower(code)
            assertTrue(plan.domain.complete)
            val conversions = operations(plan).filterIsInstance<PlanOperation.Convert>()
            assertEquals(setOf(ConversionKind.EXPLICIT, ConversionKind.IMPLICIT), conversions.map { it.kind }.toSet())
            assertTrue(plan.blocks.size > 1)
            assertTrue(plan.blocks.flatMap { it.operations }.all { it.site.callSite == result.call.source })
            assertTrue(session.transaction.snapshot().resources.isEmpty())
            assertTrue(session.transaction.snapshot().names.isEmpty())
            sentinel.unchanged()
        }
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[MniCodeLoweringPlanTest.Address(root)] = 3L
        machine.invoke = { target, arguments ->
            assertTrue(target.name.endsWith(":combine"))
            assertEquals(listOf(1L, true, false, true), arguments)
            11L
        }
        assertEquals(11L, machine.run().second)
        assertEquals(listOf("projection.expressions:combine"), machine.calls)
        assertFalse(combine.bodyCompiled)
        assertFalse(predicate.bodyCompiled)
        sentinel.unchanged()

        // A real completed generic field supplies the canonical FieldRef for a value receiver.
        val syntax = parser("data Box<T>{var visible:T;private var hidden:int;}").templateDeclaration()
        val prototype = GenericDataTemplate(syntax.templateBody(), "Box", file.namespace.identifier).apply {
            declarationFile = file
            syntax.compoundDeclaration().declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { index, parameter ->
                readOnlyParams.add(
                    DataTemplateParam(
                        ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                        ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                        MCFPPConcreteType.Type
                    )
                )
            }
        }
        file.namespace.scope.template["Box"] = prototype
        val produce = GenericFunction("produce", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
            returnType = UnresolvedType("Box<T>")
        }
        file.namespace.scope.functions["produce"] = arrayListOf(produce)
        val sink = function(file, "sink", listOf(MCFPPBaseType.Int))
        ready("produce<int>()", caller)
        val inside = function(file, "inside").apply { owner = prototype.compiledTemplates.values.single() }
        val fieldResult = ready("sink(produce<int>().visible)", caller)
        val afterDeclaration = SourceProjectionFixture.Sentinel(caller)
        val fieldPlan = session(fieldResult).use { session ->
            val environment = SourceProjectionEnvironment.capture(fieldResult.environment, session)
            val projector = SourceCodeProjector(environment)
            val fieldRead = assertIs<BoundExprNode.Field>(assertIs<BoundCallArgument.Value>(fieldResult.call.arguments.single()).expression.node)
            val receiver = projector.expression(fieldRead.receiver)
            val hidden = receiver.type().fields().single { it.name() == "hidden" }
            val hiddenKey = (hidden as ImmutableFieldRef<*>).key
            assertTrue(environment.declarations.values.any { it.field(receiver.type(), "hidden")?.canonical === hidden })
            assertTrue(hiddenKey !in environment.bindings.fieldCapabilities)
            assertEquals(environment.bindings.fieldCapabilities, session.validationEnvironment().fieldCapabilities)
            // Host-generated fields bypass the source projector. Validation must retain the
            // caller's whitelist even when another original declaration can see this field.
            val meta = session.arena.metadata(environment.rootScope, session.origin, environment.executionContext)
            val forbidden = session.arena.expr(meta, hidden.type(), ExprNode.Field(receiver, hidden))
            val failure = assertFailsWith<CodeModelException> {
                CodeValidator().validate(forbidden, session.validationEnvironment(), ValidationPhase.FINAL)
            }
            assertTrue(failure.message!!.contains("Field capability is not authorized"))
            environment.lower(projector.call(fieldResult.call)).also { afterDeclaration.unchanged() }
        }
        val field = operations(fieldPlan).filterIsInstance<PlanOperation.Field>().single().field
        val fields = MniCodeLoweringPlanTest.Machine(fieldPlan)
        fields.invoke = { target, arguments ->
            when {
                target.name.endsWith(":produce") -> mapOf(field.key to 23L)
                target.name.endsWith(":sink") -> arguments.single()
                else -> error("Unexpected target")
            }
        }
        assertEquals(23L, fields.run().second)
        assertEquals(listOf("projection.expressions:produce", "projection.expressions:sink"), fields.calls)
        assertFalse(produce.bodyCompiled)
        assertTrue(produce.compiledFunctions.isEmpty())
        assertFalse(sink.bodyCompiled)
        assertFalse(inside.bodyCompiled)
        assertTrue(inside.commands.isEmpty())
        afterDeclaration.unchanged()
    }

    @Test fun realMacroArgumentsFreezeEveryPrefixAndIndexOnceAndKeepPhysicalAliasesAfterLaterEffects() = SourceProjectionFixture.isolated {
        hostExecutions = 0
        val file = file("projection.references")
        file.namespace.declareMniFixtures(Entries::class.java)
        val effects = listOf("effectA", "first", "second", "effectB").map { function(file, it) }
        val caller = caller(file)
        val nested = MCFPPListType(MCFPPListType(MCFPPBaseType.Int))
        caller.scope.vars["rows"] = nested.buildUnConcrete("rows")
        caller.scope.vars["index"] = MCInt("index")
        val result = internalReady("sequence(effectA(),rows[first()][second()],effectB(),rows[0][1])", caller, setOf(1, 3))
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        lateinit var root: SymbolId
        lateinit var index: SymbolId
        val plan = session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            root = location(environment.roots.getValue(environment.binding.symbol("rows")!!.symbol)).root
            index = location(environment.roots.getValue(environment.binding.symbol("index")!!.symbol)).root
            val projected = SourceCodeProjector(environment).arguments(result.call)
            val left = assertIs<ArgumentPayload.Reference>(projected.arguments[1].payload).reference
            val right = assertIs<ArgumentPayload.Reference>(projected.arguments[3].payload).reference
            assertEquals(root, location(left).root)
            assertEquals(root, location(right).root)
            assertNotEquals(location(left).capturedBase, location(right).capturedBase)
            val scope = projected.scope
            val meta = session.arena.metadata(scope, Origin(result.call.source, result.call.source), environment.executionContext)
            val int = left.type()
            fun add(reference: Ref<*>, amount: Long): Stmt {
                val value = session.arena.expr(meta, int, ExprNode.Literal(ConstantValue.Integral(amount)))
                return session.arena.stmt(
                    meta,
                    StmtNode.Assign(
                        reference,
                        session.arena.expr(meta, int, ExprNode.Binary(BinaryOperator.ADD, reference.load(), value))
                    )
                )
            }
            val region = session.arena.freshRegion()
            val body = session.arena.block(
                meta,
                projected.orderedPrelude.statements() + listOf(
                    add(left, 1),
                    add(right, 2),
                    session.arena.stmt(meta, StmtNode.Yield(region, right.load()))
                )
            )
            val code = session.arena.expr(meta, int, ExprNode.ExprBlock(body, int, region))
            environment.lower(code).also {
                assertEquals(0, hostExecutions)
                sentinel.unchanged()
                assertTrue(session.transaction.snapshot().resources.isEmpty())
            }
        }
        val selected = MniCodeLoweringPlanTest.Address(root, listOf(0, 1))
        val other = MniCodeLoweringPlanTest.Address(root, listOf(1, 1))
        val indexAddress = MniCodeLoweringPlanTest.Address(index)
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[selected] = 10L
        machine.storage[other] = 40L
        machine.storage[indexAddress] = 0L
        machine.invoke = { target, _ ->
            when (target.name.substringAfter(':')) {
                "effectA" -> 6L
                "first" -> machine.storage[indexAddress]
                "second" -> {
                    machine.storage[indexAddress] = 1L
                    1L
                }
                "effectB" -> {
                    machine.storage[indexAddress] = 2L
                    9L
                }
                else -> error("Unexpected target")
            }
        }
        assertEquals(13L, machine.run().second)
        assertEquals(13L, machine.storage[selected])
        assertEquals(40L, machine.storage[other])
        assertEquals(listOf("effectA", "first", "second", "effectB"), machine.calls.map { it.substringAfter(':') })
        val captures = operations(plan).filterIsInstance<PlanOperation.CaptureLocation>().filter { it.location.root.binding == root }
        assertEquals(6, captures.size)
        assertEquals(captures[0].result, captures[1].location.capturedBase)
        assertEquals(captures[1].result, captures[2].location.capturedBase)
        assertTrue(effects.all { !it.bodyCompiled && it.commands.isEmpty() })
        assertEquals(0, hostExecutions)
        sentinel.unchanged()
    }
}
