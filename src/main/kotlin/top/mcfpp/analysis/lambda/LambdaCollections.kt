package top.mcfpp.analysis.lambda

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Synchronous collection sugar uses ordinary function frames and the existing for-in traversal. */
internal object LambdaCollections {
    data class Call(val name: String, val arguments: List<ParserRuleContext>, val readonly: Boolean)
    data class Plan(val callback: MCFPPFunctionType, val result: MCFPPType, val initial: MCFPPType?)
    private val names = setOf("map", "filter", "fold", "foreach")
    fun supports(type: MCFPPType, name: String) = name in names &&
        (type is MCFPPListType || type is MCFPPImmutableListType)

    fun selector(selector: P.SelectorContext, trailing: P.InlineLambdaContext? = null): Call? {
        val call = selector.`var`()?.functionCall()
        val name = call?.namespaceID()?.text ?: selector.memberKeyword()?.text ?:
            selector.`var`()?.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
        if (name !in names) return null
        val syntax = call?.arguments() ?: selector.arguments()
        val normal = syntax?.normalArgs()
        val arguments = normal?.callArgumentList()?.callArgument()?.map { it.inlineLambda() ?: it.expression() }
            ?: normal?.expressionList()?.expression().orEmpty()
        if (arguments.any { it == null }) throw DeclarationBinding.Failure("Collection callbacks do not accept REF arguments")
        return Call(name!!, arguments.filterNotNull() + listOfNotNull(trailing), syntax?.readOnlyArgs() != null)
    }

    fun plan(type: MCFPPType, call: Call, environment: LambdaBinding.Environment,
             bind: (ParserRuleContext, MCFPPType?) -> MCFPPType): Plan {
        if (call.readonly) throw DeclarationBinding.Failure("Collection '${call.name}' does not accept readonly arguments")
        val fold = call.name == "fold"
        if (call.arguments.size != if (fold) 2 else 1)
            throw DeclarationBinding.Failure("Collection '${call.name}' expects ${if (fold) "an initial value and a callback" else "one callback"}")
        val element = (type as MCFPPTypeWithGeneric).generic.single()
        val initial = if (fold) bind(call.arguments.first(), null) else null
        val parameters = if (initial != null) listOf(initial, element) else listOf(element)
        val expectedResult = when (call.name) {
            "filter" -> MCFPPBaseType.Bool
            "foreach" -> MCFPPPrivateType.Void
            "fold" -> initial
            else -> null
        }
        val node = call.arguments.last()
        val lambda = node as? P.InlineLambdaContext ?: (node as? P.ExpressionContext)?.primary()?.inlineLambda()
        val signature = if (expectedResult == null && lambda != null) {
            when (val result = LambdaBinding.bindWithParameterTypes(lambda, parameters, environment)) {
                is LambdaBinding.Result.Bound -> result.value.signature
                is LambdaBinding.Result.Rejected -> throw DeclarationBinding.Failure(result.diagnostic.render())
            }
        } else {
            var target = expectedResult?.let { MCFPPFunctionType(parameters, it) }
            // A map reference can infer a result from one unique ordinary declared signature.
            if (target == null) {
                val name = (node as? P.ExpressionContext)?.primary()?.`var`()?.varWithSuffix()
                    ?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
                if (name != null && name !in environment.symbolTypes) {
                    val candidates = environment.functions(name).mapNotNull { LambdaBinding.functionType(it) }.filter { candidate ->
                        candidate.parameters.size == parameters.size && parameters.indices.all {
                            TypeRelations.resolveImplicitConversion(parameters[it], candidate.parameters[it]) != null
                        }
                    }.distinctBy { it.typeId }
                    if (candidates.size != 1) throw DeclarationBinding.Failure("Cannot infer map callback '$name': a unique result signature is required")
                    target = candidates.single()
                }
            }
            bind(node, target) as? MCFPPFunctionType
                ?: throw DeclarationBinding.Failure("Collection '${call.name}' requires a function value")
        }
        val required = MCFPPFunctionType(parameters, expectedResult ?: signature.result)
        if (TypeRelations.resolveImplicitConversion(signature, required) == null)
            throw DeclarationBinding.Failure("Collection '${call.name}' callback '${signature.typeName}' does not match '${required.typeName}'")
        if (call.name == "map" && signature.result == MCFPPPrivateType.Void)
            throw DeclarationBinding.Failure("Collection map callback must produce a value")
        val result = when (call.name) {
            "map" -> MCFPPListType(signature.result)
            "filter" -> MCFPPListType(element)
            "fold" -> initial!!
            else -> MCFPPPrivateType.Void
        }
        return Plan(required, result, initial)
    }

    fun invoke(receiver: Var<*>, call: Call, cache: ArrayList<Var<*>>): Var<*> {
        val spills = StorageAccess.spill(cache)
        val size = cache.size
        try {
            val plan = DeclarationBinding(Function.currFunction, emptyMap()).collectionPlan(receiver.type, call)
            val source = StorageAccess.capture(receiver)
            if (source.isError) return source
            cache += source
            val visitor = MCFPPExprVisitor(processVarCache = cache)
            val arguments = arrayListOf(source)
            call.arguments.forEachIndexed { index, node ->
                val expected = if (index == call.arguments.lastIndex) plan.callback else plan.initial
                val value = if (node is P.InlineLambdaContext) LambdaLowering.literal(node, expected as MCFPPFunctionType)
                    else visitor.visitExpression(node as P.ExpressionContext, expected)
                if (value.isError) return value
                val captured = StorageAccess.capture(value)
                if (captured.isError) return captured
                arguments += captured
                cache += captured
            }
            val body = when (call.name) {
                "map" -> "var output: CollectionResult = [];\nfor (entry : source) { output.add(callback(entry)); }\nreturn output;"
                "filter" -> "var output: CollectionResult = [];\nfor (entry : source) { if (callback(entry)) { output.add(entry); }\n}\nreturn output;"
                "fold" -> "var accumulator: CollectionResult = initial;\nfor (entry : source) { accumulator = callback(accumulator, entry); }\nreturn accumulator;"
                else -> "for (entry : source) { callback(entry); }\n"
            }
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString("{ $body }", "collection ${call.name}"))))
            val syntax = parser.curlBlock()
            if (parser.numberOfSyntaxErrors != 0) throw DeclarationBinding.Failure("Cannot lower collection '${call.name}'")
            val target = Function(TempPool.getFunctionIdentify("list_${call.name}"), Function.currFunction.namespace, syntax)
            target.isDerivedImplementation = true
            target.returnType = plan.result
            // Bind the actual descriptor rather than round-tripping diagnostic typeName
            // (for example list.typeName deliberately omits its element type).
            target.scope.putType("CollectionResult", plan.result, true)
            target.appendNormalParam(receiver.type, "source")
            plan.initial?.let { target.appendNormalParam(it, "initial") }
            target.appendNormalParam(plan.callback, "callback")
            target.buildParamVar()
            GlobalScope.getOrCreateNamespace(target.namespace).scope.addFunction(target, false)
            Function.currFunction.child.add(target)
            target.parent.add(Function.currFunction)
            return target.invoke(arguments, null)
        } catch (failure: DeclarationBinding.Failure) {
            LogProcessor.error(failure.message ?: "Cannot bind collection callback")
            return UnknownVar("collection_${call.name}").apply { isError = true }
        } finally {
            while (cache.size > size) cache.removeAt(cache.lastIndex)
            StorageAccess.restore(spills)
        }
    }
}
