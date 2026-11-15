package top.mcfpp.analysis.lambda

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Shared signature binding and ordinary function lowering for standard value operations. */
internal object LambdaCollections {
    data class Call(val name: String, val arguments: List<ParserRuleContext>, val readonly: Boolean)
    data class Parameter(val name: String, val type: MCFPPType)
    data class Plan(val result: MCFPPType, val parameters: List<Parameter>, val body: String,
                    val helperResult: MCFPPType = result, val writeBack: Boolean = false)
    private val lists = setOf("map", "filter", "fold", "foreach", "forEach", "forEachIndexed", "mapIndexed", "flatMap",
        "filterNot", "onEach", "any", "all", "none", "count", "indexOfFirst", "indexOfLast", "firstOrElse", "lastOrElse",
        "getOrElse", "tryFirst", "sum", "sumOf", "takeWhile", "dropWhile", "take", "drop", "slice", "reversed", "distinct", "toList", "toImmutableList")
    private val scopes = setOf("let", "also")
    private val bounds = setOf("coerceIn", "coerceAtLeast", "coerceAtMost")
    private val maps = setOf("getOrElse", "getOrPut", "values", "entries")
    private val names = lists + scopes + bounds + maps + setOf("toIntOrElse", "get", "set")
    private fun ruleValue(type: MCFPPType): MCFPPType? {
        val template = (type as? MCFPPDataTemplateType)?.template as? top.mcfpp.model.compound.CompiledGenericDataTemplate ?: return null
        if (template.originTemplate.identifier != "GameRule" || template.originTemplate.namespace != "mcfpp.minecraft") return null
        return (template.args.singleOrNull() as? top.mcfpp.core.lang.MCFPPTypeVar)?.value
    }
    fun supports(type: MCFPPType, name: String): Boolean = when {
        name in setOf("get", "set") -> ruleValue(type) != null
        name in scopes -> type != MCFPPPrivateType.Void
        name in bounds -> type == MCFPPBaseType.Int || type == MCFPPBaseType.Float
        name == "toIntOrElse" -> type == MCFPPBaseType.String
        type is MCFPPListType || type is MCFPPImmutableListType -> name in lists
        type is MCFPPMapType -> name in maps
        else -> false
    }
    private fun arguments(name: String, syntax: P.ArgumentsContext?, trailing: P.InlineLambdaContext?): Call {
        val normal = syntax?.normalArgs()
        val args = normal?.callArgumentList()?.callArgument()?.map {
            if (it.REF() != null || it.ASSIGNMENT() != null) throw DeclarationBinding.Failure("Standard '$name' expects positional value arguments")
            it.inlineLambda() ?: it.expression()
        } ?: normal?.expressionList()?.expression().orEmpty()
        return Call(name, args.filterNotNull() + listOfNotNull(trailing), syntax?.readOnlyArgs() != null)
    }
    fun global(call: P.FunctionCallContext?, trailing: P.InlineLambdaContext? = null, bare: P.VarWithSuffixContext? = null): Call? {
        val name = call?.namespaceID()?.text ?: bare?.Identifier()?.text ?: return null
        return if (name in setOf("run", "repeat")) arguments(name, call?.arguments(), trailing) else null
    }
    fun selector(selector: P.SelectorContext, trailing: P.InlineLambdaContext? = null): Call? {
        val call = selector.`var`()?.functionCall()
        val name = call?.namespaceID()?.text ?: selector.memberKeyword()?.text ?: selector.`var`()?.varWithSuffix()
            ?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
        return if (name in names) arguments(name!!, call?.arguments() ?: selector.arguments(), trailing ?: selector.inlineLambda()) else null
    }

    fun plan(type: MCFPPType, call: Call, environment: LambdaBinding.Environment,
             bind: (ParserRuleContext, MCFPPType?) -> MCFPPType): Plan {
        val name = call.name
        if (call.readonly) throw DeclarationBinding.Failure("Standard '$name' does not accept readonly arguments")
        val params = arrayListOf<Parameter>()
        val int = MCFPPBaseType.Int; val bool = MCFPPBaseType.Bool; val void = MCFPPPrivateType.Void
        fun arity(vararg sizes: Int) {
            if (call.arguments.size !in sizes) throw DeclarationBinding.Failure("Standard '$name' expects ${sizes.joinToString(" or ")} argument(s)")
        }
        fun value(index: Int, key: String, expected: MCFPPType) {
            if (TypeRelations.resolveImplicitConversion(bind(call.arguments[index], expected), expected) == null)
                throw DeclarationBinding.Failure("Standard '$name' argument '$key' must be '${expected.typeName}'")
            params += Parameter(key, expected)
        }
        fun callback(index: Int, key: String, inputs: List<MCFPPType>, output: MCFPPType? = null): MCFPPType {
            val node = call.arguments[index]
            val lambda = node as? P.InlineLambdaContext ?: (node as? P.ExpressionContext)?.primary()?.inlineLambda()
            var target = output?.let { MCFPPFunctionType(inputs, it) }
            var referenceNode: org.antlr.v4.runtime.tree.ParseTree = node
            while (referenceNode !is P.PrimaryContext && referenceNode.childCount == 1)
                referenceNode = referenceNode.getChild(0)
            val reference = (referenceNode as? P.PrimaryContext)?.`var`()?.varWithSuffix()
                ?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
            if (target != null && reference != null) {
                val signatures = (environment.symbolTypes[reference] as? MCFPPFunctionType)?.let(::listOf)
                    ?: environment.functions(reference).mapNotNull { LambdaBinding.functionType(it) }
                signatures.filter { candidate -> candidate.parameters.size == inputs.size && inputs.indices.all {
                    TypeRelations.resolveImplicitConversion(inputs[it], candidate.parameters[it]) != null
                } && TypeRelations.resolveImplicitConversion(candidate.result, output!!) != null }.singleOrNull()?.let {
                    target = MCFPPFunctionType(inputs, output!!, contexts = it.contexts)
                }
            }
            val signature = if (target == null && lambda != null) {
                when (val bound = LambdaBinding.bindWithParameterTypes(lambda, inputs, environment)) {
                    is LambdaBinding.Result.Bound -> bound.value.signature
                    is LambdaBinding.Result.Rejected -> throw DeclarationBinding.Failure(bound.diagnostic.render())
                }
            } else {
                if (target == null) {
                    val ref = reference
                    if (ref != null && ref !in environment.symbolTypes) {
                        val candidates = environment.functions(ref).mapNotNull { LambdaBinding.functionType(it) }.filter { c ->
                            c.parameters.size == inputs.size && inputs.indices.all { TypeRelations.resolveImplicitConversion(inputs[it], c.parameters[it]) != null }
                        }.distinctBy { it.typeId }
                        if (candidates.size != 1) throw DeclarationBinding.Failure("Cannot infer $name callback '$ref': a unique result signature is required")
                        target = candidates.single()
                    }
                }
                bind(node, target) as? MCFPPFunctionType ?: throw DeclarationBinding.Failure("Standard '$name' requires a function value")
            }
            val required = MCFPPFunctionType(inputs, output ?: signature.result, signature.executionCategory, signature.contexts)
            if (signature.executionCategory != MCFPPFunctionType.ExecutionCategory.NORMAL)
                throw DeclarationBinding.Failure("Standard '$name' requires a synchronous function")
            if (TypeRelations.resolveImplicitConversion(signature, required) == null)
                throw DeclarationBinding.Failure("Collection '$name' callback '${signature.typeName}' does not match '${required.typeName}'")
            params += Parameter(key, required)
            return signature.result
        }
        fun result(result: MCFPPType, body: String) = Plan(result, params.toList(), body)
        if (name in setOf("get", "set")) ruleValue(type)?.let { valueType ->
            arity(if (name == "get") 0 else 1)
            if (name == "set") value(0, "newValue", valueType)
            return result(if (name == "get") valueType else GlobalScope.getCanonicalTemplate("mcfpp.minecraft.std", "CommandResult")
                ?.getType() ?: throw DeclarationBinding.Failure("CommandResult standard declaration is missing; rebuild the standard library"), "")
        }
        if (name in scopes || name in setOf("run", "repeat")) {
            arity(if (name == "repeat") 2 else 1)
            if (name == "repeat") value(0, "times", int)
            val out = callback(call.arguments.lastIndex, "callback", when (name) { "run" -> emptyList(); "repeat" -> listOf(int); else -> listOf(type) },
                if (name in setOf("also", "repeat")) void else null)
            return when (name) {
                "let" -> result(out, if (out == void) "callback(source);" else "return callback(source);")
                "also" -> result(type, "callback(source); return source;")
                "run" -> result(out, if (out == void) "callback();" else "return callback();")
                else -> result(void, "var index = 0; while (index < times) { callback(index); index += 1; }")
            }
        }
        if (name == "toIntOrElse") { arity(1); callback(0, "fallback", emptyList(), int); return result(int, "return fallback();") }
        if (name in bounds) {
            arity(if (name == "coerceIn") 2 else 1); value(0, "bound", type)
            if (name == "coerceIn") value(1, "upper", type)
            return result(type, when (name) { "coerceIn" -> "return mcfpp.math:clamp(source, bound, upper);"; "coerceAtLeast" -> "return mcfpp.math:max(source, bound);"; else -> "return mcfpp.math:min(source, bound);" })
        }
        val element = (type as MCFPPTypeWithGeneric).generic.single()
        val list = MCFPPListType(element)
        if (type is MCFPPMapType) {
            if (name in setOf("values", "entries")) {
                arity(0)
                val entry = if (name == "entries") {
                    val template = GlobalScope.getTemplate("mcfpp.lang", "MapEntry") as? top.mcfpp.model.compound.GenericDataTemplate
                        ?: throw DeclarationBinding.Failure("MapEntry standard declaration is missing; rebuild the standard library")
                    template.compile(listOf(top.mcfpp.core.lang.MCFPPTypeVar(element)))?.getType()
                        ?: throw DeclarationBinding.Failure("Cannot specialize MapEntry")
                } else element
                val item = if (name == "entries") "mcfpp.lang:MapEntry<CollectionValue>(key, source[key])" else "source[key]"
                return result(MCFPPListType(entry), "var output: CollectionResult = []; for (key : source.keys()) { output.add($item); } return output;")
            }
            arity(2); value(0, "key", MCFPPBaseType.String); callback(1, "fallback", emptyList(), element)
            return if (name == "getOrPut") Plan(element, params, "if (!source.containsKey(key)) { source[key] = fallback(); } return source;", type, true)
            else result(element, "if (source.containsKey(key)) { return source[key]; } return fallback();")
        }
        if (name in setOf("take", "drop", "slice", "reversed", "distinct", "toList", "toImmutableList", "sum")) {
            arity(when (name) { "slice" -> 2; "take", "drop" -> 1; else -> 0 })
            if (name in setOf("take", "drop", "slice")) value(0, "bound", int)
            if (name == "slice") value(1, "upper", int)
            if (name == "sum" && element !in setOf(int, MCFPPBaseType.Float)) throw DeclarationBinding.Failure("sum requires a list of int or float")
            val loop = when (name) {
                "take" -> "var index = 0; for (entry : source) { if (index < bound) { output.add(entry); } index += 1; }"
                "drop" -> "var index = 0; for (entry : source) { if (index >= bound) { output.add(entry); } index += 1; }"
                "slice" -> "var index = 0; for (entry : source) { if (index >= bound && index < upper) { output.add(entry); } index += 1; }"
                "reversed" -> "for (entry : source) { output.prepend(entry); }"
                "distinct" -> "for (entry : source) { if (!output.contains(entry)) { output.add(entry); } }"
                "sum" -> "for (entry : source) { output += entry; }"
                else -> "for (entry : source) { output.add(entry); }"
            }
            val out = if (name == "sum") element else list
            val initial = if (name == "sum") (if (element == int) "0" else "0.0") else "[]"
            return Plan(if (name == "toImmutableList") MCFPPImmutableListType(element) else out, params,
                "var output: CollectionOutput = $initial; $loop return output;", out)
        }
        if (name in setOf("getOrElse", "firstOrElse", "lastOrElse")) {
            arity(if (name == "getOrElse") 2 else 1, if (name == "firstOrElse") 2 else if (name == "getOrElse") 2 else 1)
            if (name == "getOrElse") value(0, "selected", int)
            if (name == "firstOrElse" && call.arguments.size == 2) callback(0, "predicate", listOf(element), bool)
            callback(call.arguments.lastIndex, "fallback", emptyList(), element)
            return result(element, when {
                name == "firstOrElse" && call.arguments.size == 2 -> "for (entry : source) { if (predicate(entry)) { return entry; } } return fallback();"
                name == "getOrElse" -> "if (selected >= 0 && selected < source.size()) { return source[selected]; } return fallback();"
                name == "lastOrElse" -> "if (source.size() > 0) { return source[source.size() - 1]; } return fallback();"
                else -> "for (entry : source) { return entry; } return fallback();"
            })
        }
        arity(if (name in setOf("fold", "tryFirst")) 2 else if (name in setOf("count", "any", "none")) 0 else 1,
            if (name in setOf("fold", "tryFirst")) 2 else 1)
        val initial = if (name == "fold") bind(call.arguments.first(), null).also { params += Parameter("initial", it) } else null
        val indexed = name in setOf("mapIndexed", "forEachIndexed")
        val out = if (call.arguments.isEmpty()) bool else callback(if (initial == null) 0 else 1, "callback",
            if (initial != null) listOf(initial, element) else if (indexed) listOf(int, element) else listOf(element), when (name) {
                "filter", "filterNot", "any", "all", "none", "count", "indexOfFirst", "indexOfLast", "tryFirst", "takeWhile", "dropWhile" -> bool
                "foreach", "forEach", "forEachIndexed", "onEach" -> void
                "fold" -> initial
                else -> null
            })
        if (name in setOf("map", "mapIndexed", "flatMap", "sumOf") && out == void) throw DeclarationBinding.Failure("Collection $name callback must produce a value")
        if (name == "flatMap" && out !is MCFPPListType && out !is MCFPPImmutableListType) throw DeclarationBinding.Failure("flatMap callback must return a list")
        if (name == "sumOf" && out !in setOf(int, MCFPPBaseType.Float)) throw DeclarationBinding.Failure("sumOf callback must return int or float")
        if (name == "tryFirst") callback(1, "action", listOf(element), void)
        val predicate = if (call.arguments.isEmpty()) "true" else "callback(entry)"
        val mapped = if (indexed) "callback(index, entry)" else "callback(entry)"
        return when (name) {
            "map", "mapIndexed", "flatMap" -> result(MCFPPListType(if (name == "flatMap") (out as MCFPPTypeWithGeneric).generic.single() else out),
                "var output: CollectionResult = []; var index = 0; for (entry : source) { output.${if (name == "flatMap") "addAll" else "add"}($mapped); index += 1; } return output;")
            "filter", "filterNot" -> result(list, "var output: CollectionResult = []; for (entry : source) { if (${if (name == "filterNot") "!" else ""}callback(entry)) { output.add(entry); } } return output;")
            "fold" -> result(initial!!, "var accumulator: CollectionResult = initial; for (entry : source) { accumulator = callback(accumulator, entry); } return accumulator;")
            "any", "none", "all" -> result(bool, "for (entry : source) { if (${if (name == "all") "!callback(entry)" else predicate}) { return ${name == "any"}; } } return ${name != "any"};")
            "count" -> result(int, "var count = 0; for (entry : source) { if ($predicate) { count += 1; } } return count;")
            "indexOfFirst", "indexOfLast" -> result(int, "var index = 0; var found = -1; for (entry : source) { if (callback(entry)) { ${if (name == "indexOfFirst") "return index;" else "found = index;"} } index += 1; } return found;")
            "tryFirst" -> result(bool, "for (entry : source) { if (callback(entry)) { action(entry); return true; } } return false;")
            "sumOf" -> result(out, "var output: CollectionResult = ${if (out == int) "0" else "0.0"}; for (entry : source) { output += callback(entry); } return output;")
            "takeWhile" -> result(list, "var output: CollectionResult = []; for (entry : source) { if (!callback(entry)) { return output; } output.add(entry); } return output;")
            "dropWhile" -> result(list, "var output: CollectionResult = []; var dropping = true; for (entry : source) { if (dropping) { dropping = callback(entry); } if (!dropping) { output.add(entry); } } return output;")
            else -> result(if (name == "onEach") type else void, "var index = 0; for (entry : source) { $mapped; index += 1; } ${if (name == "onEach") "return source;" else ""}")
        }
    }

    fun invoke(receiver: Var<*>?, call: Call, cache: ArrayList<Var<*>>): Var<*> {
        val spills = StorageAccess.spill(cache); val size = cache.size
        try {
            val type = receiver?.type ?: MCFPPPrivateType.Void
            val plan = DeclarationBinding(Function.currFunction, emptyMap()).collectionPlan(type, call)
            val args = arrayListOf<Var<*>>()
            receiver?.let { args += StorageAccess.capture(it); cache += args.last() }
            val visitor = MCFPPExprVisitor(processVarCache = cache)
            call.arguments.forEachIndexed { index, node ->
                val expected = plan.parameters[index].type
                val value = if (node is P.InlineLambdaContext) LambdaLowering.literal(node, expected as MCFPPFunctionType)
                    else visitor.visitExpression(node as P.ExpressionContext, expected)
                if (value.isError) return value
                args += StorageAccess.capture(value); cache += args.last()
            }
            if (args.any { it.isError }) return UnknownVar().apply { isError = true }
            if (call.name in setOf("get", "set") && ruleValue(type) != null) {
                val native = top.mcfpp.mni.NativeCallContext(Function.currFunction, args[0], args.drop(1), plan.result)
                if (call.name == "get") top.mcfpp.mni.minecraft.GameRuleData.get(native) else top.mcfpp.mni.minecraft.GameRuleData.set(native)
                return native.resultAdapter() ?: UnknownVar("game_rule").apply { isError = true }
            }
            var body = plan.body
            if (call.name == "toIntOrElse") {
                fun payload(v: top.mcfpp.analysis.CompilerValue?): top.mcfpp.analysis.CompilerValue? = if (v is top.mcfpp.analysis.CompilerValue.Typed) payload(v.payload) else v
                val text = (payload(StorageAccess.snapshot(args[0])) as? top.mcfpp.analysis.CompilerValue.Text)?.value
                    ?: throw DeclarationBinding.Failure("string.toIntOrElse requires a compile-time string value")
                text.toIntOrNull()?.let { body = "return $it;" }
            }
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString("{ ${body.replace("}", "}\n")} }", "standard ${call.name}"))))
            val syntax = parser.curlBlock()
            if (parser.numberOfSyntaxErrors != 0) throw DeclarationBinding.Failure("Cannot lower standard '${call.name}'")
            val target = Function(TempPool.getFunctionIdentify("list_${call.name}"), Function.currFunction.namespace, syntax)
            target.isDerivedImplementation = true; target.returnType = plan.helperResult
            target.scope.putType("CollectionResult", plan.result, true)
            ruleValue(type)?.let { target.scope.putType("CollectionValue", it, true) }
            (type as? MCFPPTypeWithGeneric)?.generic?.singleOrNull()?.let { target.scope.putType("CollectionValue", it, true) }
            target.scope.putType("CollectionOutput", if (plan.result is MCFPPImmutableListType) MCFPPListType(plan.result.generic.single()) else plan.result, true)
            receiver?.let { target.appendNormalParam(type, "source") }
            plan.parameters.forEach { target.appendNormalParam(it.type, it.name) }
            target.contextParametersPrepared = true
            plan.parameters.mapNotNull { it.type as? MCFPPFunctionType }.flatMap { it.contexts }.distinctBy { it.typeId }
                .forEachIndexed { index, context -> target.declaredContextParameters += top.mcfpp.model.function.FunctionParam(context, "context$index", target) }
            target.buildParamVar()
            GlobalScope.getOrCreateNamespace(target.namespace).scope.addFunction(target, false)
            Function.currFunction.recordCall(target)
            val result = target.invoke(args, null)
            if (call.name == "toImmutableList" && !result.isError) return StorageAccess.view(result, plan.result, diagnose = false)
            if (plan.writeBack && receiver != null && !result.isError) {
                StorageAccess.writeReceiver(receiver, result)
                return top.mcfpp.backend.MapOperations.element(receiver as top.mcfpp.core.lang.nbt.NBTMap, args[1] as top.mcfpp.core.lang.nbt.MCString)
            }
            return result
        } catch (failure: DeclarationBinding.Failure) {
            LogProcessor.error(failure.message ?: "Cannot bind standard operation")
            return UnknownVar("collection_${call.name}").apply { isError = true }
        } finally {
            while (cache.size > size) cache.removeAt(cache.lastIndex)
            StorageAccess.restore(spills)
        }
    }
}
