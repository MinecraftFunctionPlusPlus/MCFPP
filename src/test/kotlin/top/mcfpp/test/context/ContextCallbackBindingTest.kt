package top.mcfpp.test.context

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.CompileSettings
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.context.ContextCallbacks
import top.mcfpp.core.lang.context.ContextRelation
import top.mcfpp.core.lang.context.ContextTypes
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import kotlin.test.*

/** Real grammar + declaration binder; no command region or runtime callback is synthesized. */
class ContextCallbackBindingTest {
    private lateinit var caller: Function
    private var previousIgnoreStdLib = false

    @BeforeTest fun prepareDeclarations() {
        previousIgnoreStdLib = CompileSettings.ignoreStdLib
        MCFPPStringTest.readFromString("func main(){}", args = arrayOf("-ignoreStdLib"), version = "26.3")
        assertEquals(0, Project.errorCount)
        val file = assertNotNull(MCFPPFile.currFile)
        caller = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val tp = Function("tp", caller.namespace, null)
        tp.appendNormalParam(MCFPPEntityType.NormalSelector, "target")
        tp.appendNormalParam(ContextTypes.Position, "position")
        tp.returnType = MCFPPPrivateType.Void
        file.field.namespaceField.addFunction(tp, false)
    }

    @AfterTest fun restoreSettings() {
        CompileSettings.ignoreStdLib = previousIgnoreStdLib
    }

    private fun expression(source: String): P.ExpressionContext {
        val errors = arrayListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                errors += "$line:$charPositionInLine $msg"
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(source, "context-binding.mcfpp")).apply {
            removeErrorListeners()
            addErrorListener(listener)
        }
        val tokens = CommonTokenStream(lexer)
        val parser = P(tokens).apply { removeErrorListeners(); addErrorListener(listener) }
        val result = parser.expression()
        assertTrue(errors.isEmpty(), "Unexpected grammar errors: $errors\n$source")
        assertEquals(Token.EOF, tokens.LA(1), "Source was not fully parsed: $source")
        return result
    }

    private fun binder(extra: Map<String, MCFPPType> = emptyMap()) = DeclarationBinding(caller, emptyMap(),
        values = (mapOf("qwq" to MCFPPEntityType.NormalSelector) + extra)
            .mapValues { DeclarationBinding.Bound(it.value) })

    private fun plan(source: String, receiver: MCFPPType = MCFPPEntityType.NormalSelector): ContextCallbacks.Plan {
        val syntax = assertNotNull(expression(source).lambdaCallExpression())
        val selector = syntax.selector().last { it.start.tokenIndex < syntax.inlineLambda().start.tokenIndex }
        val call = assertNotNull(ContextCallbacks.selector(selector, syntax.inlineLambda()))
        return binder(mapOf("qwq" to receiver)).contextPlan(receiver, call)
    }

    private fun rejects(source: String, message: String, extra: Map<String, MCFPPType> = emptyMap()) {
        val failure = assertFailsWith<DeclarationBinding.Failure> { binder(extra).expression(expression(source)) }
        assertContains(failure.message.orEmpty(), message)
    }

    @Test fun userExampleBindsBothCallbacksAndOuterPositionThroughNormalExpressionEntry() {
        val source = """
            qwq.withPosCtx { posCtx ->
                posCtx.onPassengers { psgCtx ->
                    tp(psgCtx.executor, posCtx.pos)
                }
            }
        """.trimIndent()
        val commands = caller.commands.toList()
        assertEquals(MCFPPPrivateType.Void, binder().expression(expression(source)).type)
        val outer = plan(source)
        assertEquals(ContextCallbacks.Operation.POSITION, outer.operation)
        assertEquals(listOf(ContextTypes.PosContext), outer.callback.parameters)
        assertEquals(listOf("posCtx"), outer.binding.parameters.map { it.name })
        val inner = outer.nested.single()
        assertEquals(ContextCallbacks.Operation.RELATION, inner.operation)
        assertEquals(ContextRelation.PASSENGERS, inner.relation)
        assertEquals(listOf(ContextTypes.RelationContext), inner.callback.parameters)
        assertEquals(listOf("psgCtx"), inner.binding.parameters.map { it.name })
        assertEquals(commands, caller.commands, "Type binding must not emit runtime commands")
    }

    @Test fun entrySignaturesAndPrototypeMemberTypesDoNotNeedALiveContextInstance() {
        val position = plan("qwq.withPosCtx { ctx -> ctx.pos }")
        val entity = plan("qwq.withEntityCtx { ctx -> ctx.executor }")
        assertEquals(ContextTypes.PosContext, position.binding.parameters.single().type)
        assertEquals(ContextTypes.EntityContext, entity.binding.parameters.single().type)
        assertEquals(ContextCallbacks.Operation.ENTITY, entity.operation)
        assertEquals(MCFPPEntityType.NormalSelector, ContextTypes.memberType(ContextTypes.ExecutionContext, "executor"))
        assertEquals(ContextTypes.Position, ContextTypes.memberType(ContextTypes.RelationContext, "pos"))
        assertNull(ContextTypes.memberType(ContextTypes.PosContext, "missing"))
        assertNull(ContextTypes.memberType(MCFPPBaseType.Int, "executor"))
    }

    @Test fun nestedAliasesAndBlockShadowingFollowLexicalSourceOrder() {
        val source = """
            qwq.withPosCtx { ctx ->
                var alias = ctx
                if (true) { var ctx = 1; }
                alias.onPassengers { passenger ->
                    var target = passenger.executor
                    var destination = ctx.pos
                    tp(target, destination)
                }
            }
        """.trimIndent()
        assertEquals(MCFPPPrivateType.Void, binder().expression(expression(source)).type)
        rejects("qwq.withPosCtx { ctx -> var ctx = 1; ctx.onPassengers { p -> p.executor } }",
            "requires an ExecutionContext receiver")
        rejects("qwq.withPosCtx { ctx -> var alias = missing; alias.onPassengers { p -> p.executor } }",
            "Cannot bind declaration name 'missing'")
    }

    @Test fun relationshipFamilySharesSinglePresentContextCallbackSignature() {
        for (relation in ContextRelation.values()) {
            val name = "on" + relation.commandName.replaceFirstChar { it.uppercaseChar() }
            val bound = plan("qwq.$name { related -> related.executor }", ContextTypes.PosContext)
            assertEquals(ContextCallbacks.Operation.RELATION, bound.operation)
            assertEquals(relation, bound.relation)
            assertEquals(listOf(ContextTypes.RelationContext), bound.callback.parameters)
            assertEquals(MCFPPPrivateType.Void, bound.callback.result)
        }
    }

    @Test fun callbacksRequireStaticInlineBodyAndExactlyOneContextParameter() {
        val callback = MCFPPFunctionType(listOf(ContextTypes.PosContext), MCFPPPrivateType.Void)
        rejects("qwq.withPosCtx(callback)", "statically bound inline lambda", mapOf("callback" to callback))
        rejects("qwq.withPosCtx { first, second -> first }", "expects one parameter")
        rejects("qwq.withPosCtx(1) { ctx -> ctx.pos }", "expects one inline synchronous callback")
        rejects("qwq.withPosCtx<int>() { ctx -> ctx.pos }", "does not accept readonly arguments")
        rejects("qwq.withPosCtx { ctx: int -> ctx }", "incompatible with callback parameter")
    }

    @Test fun inlineArgumentFormUsesTheSameControlledSignature() {
        assertEquals(MCFPPPrivateType.Void,
            binder().expression(expression("qwq.withEntityCtx({ ctx -> ctx.executor })")).type)
        assertEquals(MCFPPPrivateType.Void,
            binder().expression(expression("qwq.withPosCtx { ctx: ExecutionContext -> ctx.onVehicle { vehicle -> vehicle.executor } }")).type)
    }

    @Test fun receiverMemberAndVoidResultFailuresAreLocatedAtBindingBoundary() {
        rejects("qwq.withPosCtx { ctx -> ctx.missing }", "Member 'missing' is not defined on execution context")
        rejects("number.withPosCtx { ctx -> ctx.pos }", "requires an entity receiver", mapOf("number" to MCFPPBaseType.Int))
        rejects("qwq.onPassengers { ctx -> ctx.executor }", "requires an ExecutionContext receiver")
        rejects("qwq.withPosCtx { ctx -> return 1; }", "Void lambda cannot return a value")
        assertEquals(MCFPPPrivateType.Void, binder().expression(expression("qwq.withPosCtx { ctx -> return; }")).type)
        assertEquals(MCFPPPrivateType.Void, binder().expression(expression("qwq.withPosCtx { ctx -> 42 }")).type)
    }

    @Test fun ordinaryLambdasKeepOrdinarySignaturesInsideContextCallbacks() {
        val ordinary = MCFPPFunctionType(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)
        assertEquals(ordinary, binder().expression(expression("{ value -> value + 1 }"), ordinary).type)
        val bound = plan("""
            qwq.withPosCtx { ctx ->
                var callback: (int) -> int = { value -> value + 1 }
                ctx.pos
            }
        """.trimIndent())
        assertTrue(bound.nested.isEmpty(), "An ordinary lambda is not a Minecraft context operation")
        assertEquals(listOf(ContextTypes.PosContext), bound.callback.parameters)
    }
}
