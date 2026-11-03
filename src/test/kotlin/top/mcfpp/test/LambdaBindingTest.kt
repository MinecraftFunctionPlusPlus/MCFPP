package top.mcfpp.test

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.lambda.LambdaBinding
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.scope.SimpleScopeWithType
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Source binding for function types, lambda target signatures and ordinary function
 * references. These checks use real ANTLR trees and the existing type/function model;
 * runtime function-value lowering is deliberately a different compiler boundary.
 */
class LambdaBindingTest {

    private data class Parsed(val tree: mcfppParser.CompilationUnitContext, val errors: List<String>)

    private fun parse(source: String): Parsed {
        val errors = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                errors += "line $line:$charPositionInLine: $msg"
            }
        }
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source)).apply {
            removeErrorListeners()
            addErrorListener(listener)
        })).apply {
            removeErrorListeners()
            addErrorListener(listener)
        }
        return Parsed(parser.compilationUnit(), errors)
    }

    private fun tree(source: String): mcfppParser.CompilationUnitContext {
        val parsed = parse(source)
        assertTrue(parsed.errors.isEmpty(), "Unexpected syntax errors: ${parsed.errors}\n$source")
        return parsed.tree
    }

    private fun <T : ParseTree> collect(root: ParseTree, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun walk(node: ParseTree) {
            if (type.isInstance(node)) found += type.cast(node)
            for (index in 0 until node.childCount) walk(node.getChild(index))
        }
        walk(root)
        return found
    }

    private fun lambda(sourceBody: String): mcfppParser.InlineLambdaContext =
        collect(tree("func main(){ var f = $sourceBody; }"), mcfppParser.InlineLambdaContext::class.java).first()

    private fun function(
        parameters: List<MCFPPType>,
        result: MCFPPType
    ): MCFPPFunctionType = MCFPPFunctionType(parameters, result)

    private fun function(name: String, parameters: List<MCFPPType>, result: MCFPPType): Function =
        Function(name, "default", null).also { function ->
            parameters.forEachIndexed { index, type -> function.appendNormalParam(type, "p$index") }
            function.returnType = result
        }

    private fun bind(
        sourceBody: String,
        expected: MCFPPFunctionType?,
        symbols: Map<String, MCFPPType> = emptyMap(),
        inherited: LambdaBinding.Parameter? = null,
        callReturnType: (mcfppParser.FunctionCallContext) -> MCFPPType? = { null },
        lambdaTargets: (mcfppParser.InlineLambdaContext) -> List<MCFPPFunctionType> = { emptyList() }
    ): LambdaBinding.Result<LambdaBinding.BoundLambda> = LambdaBinding.bind(
        lambda(sourceBody), expected,
        LambdaBinding.Environment(
            symbolTypes = symbols,
            callReturnType = callReturnType,
            lambdaTargets = lambdaTargets
        ),
        inherited
    )

    private fun bound(sourceBody: String, expected: MCFPPFunctionType?,
                      symbols: Map<String, MCFPPType> = emptyMap(),
                      callReturnType: (mcfppParser.FunctionCallContext) -> MCFPPType? = { null },
                      lambdaTargets: (mcfppParser.InlineLambdaContext) -> List<MCFPPFunctionType> = { emptyList() }): LambdaBinding.BoundLambda {
        val result = bind(sourceBody, expected, symbols, null, callReturnType, lambdaTargets)
        return assertIs<LambdaBinding.Result.Bound<LambdaBinding.BoundLambda>>(result,
            (result as? LambdaBinding.Result.Rejected)?.diagnostic?.render() ?: sourceBody).value
    }

    private fun rejected(sourceBody: String, expected: MCFPPFunctionType?,
                         symbols: Map<String, MCFPPType> = emptyMap(),
                         callReturnType: (mcfppParser.FunctionCallContext) -> MCFPPType? = { null },
                         lambdaTargets: (mcfppParser.InlineLambdaContext) -> List<MCFPPFunctionType> = { emptyList() }): LambdaBinding.Diagnostic =
        assertIs<LambdaBinding.Result.Rejected>(
            bind(sourceBody, expected, symbols, null, callReturnType, lambdaTargets)
        ).diagnostic

    @Test
    fun functionTypesAreRecognisedInVariablesParametersAndReturns() {
        val parsed = tree(
            """
            func apply(callback: (int) -> int): (int) -> int {
                return callback;
            }
            func main(){
                var f: (int, string) -> int
                var g: () -> void
                var h: ((int) -> int) -> void
            }
            """.trimIndent()
        )
        val scope = SimpleScopeWithType.getTypeScope()
        val declarations = collect(parsed, mcfppParser.FunctionDeclarationContext::class.java)
            .associateBy { it.functionDeclarationPart().Identifier().text }

        val apply = declarations.getValue("apply").functionDeclarationPart()
        val parameter = MCFPPType.parseFromContextNotNull(
            assertNotNull(apply.functionParams().normalParams().parameterList().parameter().single().type()), scope)
        val result = MCFPPType.parseFromContextNotNull(assertNotNull(apply.functionReturnType()).type(), scope)
        assertIs<MCFPPFunctionType>(parameter).also {
            assertEquals(listOf(MCFPPBaseType.Int), it.parameters)
            assertEquals(MCFPPBaseType.Int, it.result)
        }
        assertIs<MCFPPFunctionType>(result).also {
            assertEquals(listOf(MCFPPBaseType.Int), it.parameters)
            assertEquals(MCFPPBaseType.Int, it.result)
        }

        val fields = collect(parsed, mcfppParser.FieldDeclarationContext::class.java)
            .associateBy { it.Identifier().text }
        val f = MCFPPType.parseFromContextNotNull(assertNotNull(fields.getValue("f").type()), scope)
        assertIs<MCFPPFunctionType>(f).also {
            assertEquals(listOf(MCFPPBaseType.Int, MCFPPBaseType.String), it.parameters)
            assertEquals(MCFPPBaseType.Int, it.result)
        }
        val g = MCFPPType.parseFromContextNotNull(assertNotNull(fields.getValue("g").type()), scope)
        assertIs<MCFPPFunctionType>(g).also {
            assertTrue(it.parameters.isEmpty())
            assertEquals(MCFPPPrivateType.Void, it.result)
        }
        val h = MCFPPType.parseFromContextNotNull(assertNotNull(fields.getValue("h").type()), scope)
        assertIs<MCFPPFunctionType>(h).also {
            val nested = assertIs<MCFPPFunctionType>(it.parameters.single())
            assertEquals(listOf(MCFPPBaseType.Int), nested.parameters)
            assertEquals(MCFPPBaseType.Int, nested.result)
            assertEquals(MCFPPPrivateType.Void, it.result)
        }
    }

    @Test
    fun explicitParametersAndContextualItBindToTheTargetSignature() {
        val typed = bound("{ a: int, b: int -> a }", function(listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int))
        assertEquals(listOf("a", "b"), typed.parameters.map { it.name })
        assertEquals(listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), typed.parameters.map { it.type })
        assertEquals(MCFPPBaseType.Int, typed.resultType)

        val contextual = bound("{ it + 1 }", function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int))
        assertEquals(listOf("it"), contextual.parameters.map { it.name })
        assertEquals(MCFPPBaseType.Int, contextual.parameters.single().type)
        assertTrue(contextual.parameters.single().implicitIt)

        val inferred = bound("{ a: int, b: int -> a + b }", null)
        assertEquals(function(listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int), inferred.signature)
    }

    @Test
    fun localDeclarationsContributeToTailInference() {
        val inferred = bound("{ var x = 1; x }", null)
        assertEquals(function(emptyList(), MCFPPBaseType.Int), inferred.signature)
        assertEquals(MCFPPBaseType.Int, inferred.resultType)

        val targeted = bound("{ var x = 1; x }", function(emptyList(), MCFPPBaseType.Int))
        assertEquals(MCFPPBaseType.Int, targeted.resultType)

        // A local declaration shadows a parameter with the same name from its declaration on.
        val shadowed = bound("{ x: int -> var x = 1; x }", function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int))
        assertEquals(MCFPPBaseType.Int, shadowed.resultType)
    }

    @Test
    fun unresolvedTailsAndNamedCallTailsFollowTheTargetRules() {
        val unresolved = rejected("{ unknown }", null)
        assertEquals(LambdaBinding.DiagnosticCode.UNRESOLVED_RETURN_TYPE, unresolved.code)
        assertEquals(1, unresolved.source.startLine)

        val call = bound("{ id(1) }", null,
            callReturnType = { syntax ->
                if (syntax.namespaceID().text == "id") MCFPPBaseType.Int else null
            })
        assertEquals(MCFPPBaseType.Int, call.resultType)
        assertEquals(function(emptyList(), MCFPPBaseType.Int), call.signature)
    }

    @Test
    fun nestedContextualImplicitItIsResolvedBeforeTheOuterScan() {
        val callback = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)
        // The outer lambda targets zero parameters, so its own scan has no `it`; the nested
        // trailing lambda still resolves its single-parameter target and binds `it` itself.
        val lexical = bound("{ apply { it } }", function(emptyList(), MCFPPPrivateType.Void),
            lambdaTargets = { listOf(callback) })
        assertEquals(MCFPPPrivateType.Void, lexical.resultType)

        val unbound = rejected("{ apply { it } }", function(emptyList(), MCFPPPrivateType.Void))
        assertEquals(LambdaBinding.DiagnosticCode.UNBOUND_IT, unbound.code)

        val ambiguous = rejected("{ apply { it } }", function(emptyList(), MCFPPPrivateType.Void),
            lambdaTargets = {
                listOf(callback, function(listOf(MCFPPBaseType.String), MCFPPBaseType.String))
            })
        assertEquals(LambdaBinding.DiagnosticCode.AMBIGUOUS_TARGET, ambiguous.code)

        // A nested lambda used as the tail value takes its target from the expected result.
        val returned = bound("{ { it } }", function(emptyList(), callback))
        assertEquals(callback, returned.resultType)
    }

    @Test
    fun missingAndAmbiguousTargetsHaveSourceDiagnostics() {
        val missingIt = rejected("{ it + 1 }", null)
        assertEquals(LambdaBinding.DiagnosticCode.UNBOUND_IT, missingIt.code)
        assertEquals(1, missingIt.source.startLine)
        assertTrue(missingIt.render().contains(":"), missingIt.render())

        val missingParameterType = rejected("{ a, b -> a }", null)
        assertEquals(LambdaBinding.DiagnosticCode.MISSING_PARAMETER_TYPE, missingParameterType.code)

        val count = rejected("{ a: int -> a }", function(listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int))
        assertEquals(LambdaBinding.DiagnosticCode.PARAMETER_COUNT_MISMATCH, count.code)

        val mismatch = rejected("{ a: string -> a }", function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int))
        assertEquals(LambdaBinding.DiagnosticCode.PARAMETER_TYPE_MISMATCH, mismatch.code)

        val ambiguous = assertIs<LambdaBinding.Result.Rejected>(LambdaBinding.bind(
            lambda("{ it }"),
            listOf(function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int),
                function(listOf(MCFPPBaseType.String), MCFPPBaseType.String))
        )).diagnostic
        assertEquals(LambdaBinding.DiagnosticCode.AMBIGUOUS_TARGET, ambiguous.code)

        val missingReturn = rejected("{ print(\"x\"); }", function(emptyList(), MCFPPBaseType.Int))
        assertEquals(LambdaBinding.DiagnosticCode.MISSING_RETURN, missingReturn.code)

        val badReturn = rejected("{ 1 }", function(emptyList(), MCFPPBaseType.String))
        assertEquals(LambdaBinding.DiagnosticCode.RETURN_TYPE_MISMATCH, badReturn.code)
    }

    @Test
    fun itIsLexicalAndInnerSingleParameterLambdasShadowTheOuterBinding() {
        val outer = bound("{ it + 1 }", function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int))
        val outerIt = assertNotNull(outer.implicitIt)

        // A zero-parameter nested lambda keeps reading the lexical outer `it`.
        val nestedZero = assertIs<LambdaBinding.Result.Bound<LambdaBinding.BoundLambda>>(LambdaBinding.bind(
            lambda("{ it }"), null, LambdaBinding.Environment(), outerIt
        )).value
        assertEquals(MCFPPBaseType.Int, nestedZero.resultType)

        // Explicit named inner parameter lists do not auto-declare `it`; the outer binding stays visible.
        val nestedExplicit = assertIs<LambdaBinding.Result.Bound<LambdaBinding.BoundLambda>>(LambdaBinding.bind(
            lambda("{ x: int -> it }"), function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int),
            LambdaBinding.Environment(), outerIt
        )).value
        assertEquals(listOf("x"), nestedExplicit.parameters.map { it.name })
        assertEquals(MCFPPBaseType.Int, nestedExplicit.resultType)

        // An inner contextual single-parameter lambda shadows the outer `it` with its own type.
        val shadow = assertIs<LambdaBinding.Result.Bound<LambdaBinding.BoundLambda>>(LambdaBinding.bind(
            lambda("{ it }"), function(listOf(MCFPPBaseType.String), MCFPPBaseType.String),
            LambdaBinding.Environment(), outerIt
        )).value
        assertEquals(MCFPPBaseType.String, assertNotNull(shadow.implicitIt).type)
    }

    @Test
    fun ordinaryFunctionReferencesResolveOnlyForTheTargetSignature() {
        val intAdd = function("add", listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int)
        val stringAdd = function("add", listOf(MCFPPBaseType.String, MCFPPBaseType.String), MCFPPBaseType.String)
        val site = lambda("{ 1 }")

        val target = function(listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int)
        val resolved = assertIs<LambdaBinding.Result.Bound<LambdaBinding.BoundReference>>(
            LambdaBinding.resolveReference("add", target, listOf(intAdd, stringAdd), site)
        ).value
        assertEquals(intAdd, resolved.function)
        assertEquals(target, resolved.signature)

        val noMatch = assertIs<LambdaBinding.Result.Rejected>(
            LambdaBinding.resolveReference("add", function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int), listOf(intAdd), site)
        ).diagnostic
        assertEquals(LambdaBinding.DiagnosticCode.NO_MATCHING_TARGET, noMatch.code)

        val duplicate = function("add", listOf(MCFPPBaseType.Int, MCFPPBaseType.Int), MCFPPBaseType.Int)
        val ambiguous = assertIs<LambdaBinding.Result.Rejected>(
            LambdaBinding.resolveReference("add", target, listOf(intAdd, duplicate), site)
        ).diagnostic
        assertEquals(LambdaBinding.DiagnosticCode.AMBIGUOUS_TARGET, ambiguous.code)
    }

    @Test
    fun genericHigherOrderCallbackReceivesSubstitutedParameterTypes() {
        val generic = MCFPPGenericParamType("T", arrayListOf())
        val apply = Function("apply", "default", null)
        apply.appendNormalParam(MCFPPFunctionType(listOf(generic), generic), "callback")
        apply.returnType = generic

        val target = assertIs<MCFPPFunctionType>(LambdaBinding.parameterType(apply, 0, mapOf("T" to MCFPPBaseType.Int)))
        assertEquals(listOf(MCFPPBaseType.Int), target.parameters)
        assertEquals(MCFPPBaseType.Int, target.result)

        val callback = assertIs<LambdaBinding.Result.Bound<LambdaBinding.BoundLambda>>(
            LambdaBinding.bindContextualArgument(apply, 0, lambda("{ value -> value }"), mapOf("T" to MCFPPBaseType.Int))
        ).value
        assertEquals(listOf(MCFPPBaseType.Int), callback.parameters.map { it.type })
        assertEquals(MCFPPBaseType.Int, callback.resultType)
    }

    @Test
    fun declarationBindingResolvesNestedTargetsAndOrdinaryReferences() {
        MCFPPStringTest.readFromString("func main(){}", version = "26.3")
        assertEquals(0, Project.errorCount)
        val file = assertNotNull(MCFPPFile.currFile)
        val caller = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val callback = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)
        file.field.namespaceField.addFunction(function("apply", listOf(callback), MCFPPPrivateType.Void), false)
        file.field.namespaceField.addFunction(function("id", listOf(MCFPPBaseType.Int), MCFPPBaseType.Int), false)
        file.field.namespaceField.addFunction(function("id", listOf(MCFPPBaseType.String), MCFPPBaseType.String), false)
        val binder = DeclarationBinding(caller, emptyMap())
        fun expression(source: String) = collect(tree("func check(){ var f = $source; }"),
            mcfppParser.FieldDeclarationContext::class.java).single().expression()

        assertEquals(function(emptyList(), MCFPPPrivateType.Void),
            binder.expression(expression("{ apply { it } }"), function(emptyList(), MCFPPPrivateType.Void)).type)
        assertEquals(MCFPPPrivateType.Void, binder.expression(expression("apply { it }")).type)
        assertEquals(MCFPPPrivateType.Void, binder.expression(expression("apply() { it }")).type)
        assertEquals(function(emptyList(), callback),
            binder.expression(expression("{ { it } }"), function(emptyList(), callback)).type)
        assertEquals(callback, binder.expression(expression("id"), callback).type)
    }

    @Test
    fun conditionsKeepTheirExistingControlFlowBinding() {
        MCFPPStringTest.readFromString(
            """
            func main(){
                var i = 0
                while(i < 3){
                    i = i + 1
                }
                if(i == 3){
                    i = 4
                } else if(i == 4){
                    i = 5
                }
            }
            """.trimIndent()
        )
        assertEquals(0, Project.errorCount)
    }
}
