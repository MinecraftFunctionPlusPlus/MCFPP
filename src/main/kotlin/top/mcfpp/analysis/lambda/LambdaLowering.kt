package top.mcfpp.analysis.lambda

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPReadonlyExprVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.SpecializationPolicy
import top.mcfpp.model.Generic
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.TypeRelations
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Source sugar lowers to an ordinary isolated function and its owned value payload. */
internal object LambdaLowering {
    fun literal(syntax: P.InlineLambdaContext, expected: MCFPPFunctionType?): Var<*> {
        val caller = Function.currFunction
        val environment = DeclarationBinding(caller, emptyMap()).lambdaEnvironment()
        val bound = when (val binding = LambdaBinding.bind(syntax, expected, environment)) {
            is LambdaBinding.Result.Bound -> binding.value
            is LambdaBinding.Result.Rejected -> return fail(binding.diagnostic.render())
        }
        val structured = StructuredLambdaBodies.handle(syntax, caller, bound.resultType != MCFPPPrivateType.Void)
        fun source(node: ParserRuleContext) = node.start.inputStream.getText(Interval(node.start.startIndex, node.stop.stopIndex))
        val body = if (structured != null) "" else buildString {
            append("{\n")
            syntax.lambdaBody().statement().forEach { append(source(it)); append("\n;\n") }
            bound.tailExpression?.let {
                if (bound.resultType != MCFPPPrivateType.Void) append("return ")
                append(source(it)); append(";\n")
            }
            append("}")
        }
        val context = if (structured != null) null else {
            val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(body, "lambda body"))))
            parser.curlBlock().also { if (parser.numberOfSyntaxErrors != 0) return fail("Cannot lower lambda body") }
        }
        val target = Function(TempPool.getFunctionIdentify("lambda"), caller.namespace, context)
        target.isDerivedImplementation = true
        target.bodyHandle = structured
        target.declarationFile = caller.declarationFile
        target.declarationEnvironment = caller.declarationEnvironment
        target.returnType = bound.resultType
        val captureScope = LambdaCaptureScope(target, caller.scope)
        target.scope = captureScope
        bound.parameters.forEach { target.appendNormalParam(it.type, it.name) }
        target.buildParamVar()
        GlobalScope.getOrCreateNamespace(target.namespace).scope.addFunction(target, false)
        val errors = top.mcfpp.Project.errorCount
        target.compileBody()
        if (top.mcfpp.Project.errorCount != errors) return UnknownVar("lambda")
        val captures = captureScope.captures().map { result ->
            when (result) {
                is top.mcfpp.core.lang.LambdaCaptureResult.Captured -> result.capture
                is top.mcfpp.core.lang.LambdaCaptureResult.Rejected -> return fail(result.diagnostic.message)
            }
        }
        return FunctionValue(TempPool.getVarIdentify(), bound.signature, target).also { it.initializeEnvironment(captures) }
    }

    fun empty(signature: MCFPPFunctionType): Var<*> {
        if (signature.executionCategory != MCFPPFunctionType.ExecutionCategory.NORMAL)
            return fail("Ordinary lambdas require the normal execution category")
        if (signature.parameters.isNotEmpty() || signature.result != MCFPPPrivateType.Void)
            return fail("An empty lambda requires target () -> void")
        val creator = Function.currFunction
        val structured = StructuredLambdaBodies.empty(creator)
        val context = if (structured != null) null
            else P(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}", "empty lambda body")))).curlBlock()
        val target = Function(TempPool.getFunctionIdentify("lambda"), creator.namespace, context).apply {
            isDerivedImplementation = true
            bodyHandle = structured
            declarationFile = creator.declarationFile
            declarationEnvironment = creator.declarationEnvironment
        }
        GlobalScope.getOrCreateNamespace(target.namespace).scope.addFunction(target, false)
        target.compileBody()
        return FunctionValue(TempPool.getVarIdentify(), signature, target)
    }

    fun hasCallbackTarget(name: String): Boolean = LambdaBinding.Environment(caller = Function.currFunction)
        .functions(name).any { function -> function.normalParams.any { it.type is MCFPPFunctionType ||
            (it.type as? top.mcfpp.type.UnresolvedType)?.originalTypeString?.let { source ->
                source.trimStart().startsWith("(") && source.contains("->") } == true } }

    private fun arguments(syntax: P.ArgumentsContext?): List<ParserRuleContext> {
        val normal = syntax?.normalArgs() ?: return emptyList()
        normal.callArgumentList()?.let { list -> return list.callArgument().map { argument ->
            argument.inlineLambda() ?: argument.expression()
                ?: throw DeclarationBinding.Failure("REF arguments require a reference-capable call target")
        } }
        return normal.expressionList()?.expression().orEmpty()
    }

    /** Resolve contextual arguments before lowering each once, in their source order. */
    fun call(name: String, syntax: P.ArgumentsContext?, trailing: P.InlineLambdaContext?, cache: ArrayList<Var<*>>,
             boundSite: ParserRuleContext? = null): Var<*> {
        val spills = StorageAccess.spill(cache)
        val previousSize = cache.size
        try {
            val binding = boundSite?.let { top.mcfpp.analysis.StructuredBindings.name(it, Function.currFunction) }
            val boundCandidates = boundSite?.let { top.mcfpp.analysis.StructuredBindings.functions(it, Function.currFunction) }
            val local = if (binding != null && binding !is top.mcfpp.analysis.StructuredNameBinding.Functions)
                top.mcfpp.analysis.StructuredBindings.variable(requireNotNull(boundSite), Function.currFunction) as? FunctionValue
                else if (binding == null && !name.contains(':')) Function.currFunction.scope.getVar(name) as? FunctionValue else null
            val callable = local?.let { StorageAccess.capture(it) as? FunctionValue ?: return fail("Cannot read callable '$name'") }
            val readonly = syntax?.readOnlyArgs()?.expressionList()?.expression().orEmpty().map {
                MCFPPReadonlyExprVisitor().visit(it) ?: return fail("Readonly argument is not concrete")
            }
            if (callable != null && readonly.isNotEmpty()) return fail("Function values do not accept readonly arguments")
            val nodes = arguments(syntax) + listOfNotNull(trailing)
            val supplied = readonly.map { DeclarationBinding.Bound(it.type, StorageAccess.snapshot(it), descriptors = StorageAccess.boundTypes(it)) }
            val declarations = if (callable == null) boundCandidates
                ?: LambdaBinding.Environment(caller = Function.currFunction).functions(name) else emptyList()
            val signatures = declarations.mapNotNull { function ->
                val parameters = (function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()
                val bound = SpecializationPolicy.resolveDeclaredSignature(function, parameters, supplied) ?: return@mapNotNull null
                if (nodes.size > bound.normalTypes.size || function.normalParams.drop(nodes.size).any { !it.hasDefault }) null
                else function to bound.normalTypes
            }
            val args = arrayListOf<Var<*>>()
            val visitor = MCFPPExprVisitor(processVarCache = cache)
            for ((index, node) in nodes.withIndex()) {
                val targets = if (callable != null) listOfNotNull((callable.type as MCFPPFunctionType).parameters.getOrNull(index))
                    else signatures.filter { (_, types) -> args.indices.all {
                        TypeRelations.resolveImplicitConversion(args[it].type, types[it]) != null
                    } }.mapNotNull { it.second.getOrNull(index) }.distinctBy { it.typeId }
                val expected = targets.singleOrNull()
                val value = when (node) {
                    is P.InlineLambdaContext -> {
                        if (targets.size > 1) return fail("Lambda target is ambiguous: ${targets.joinToString { it.typeName }}")
                        literal(node, expected as? MCFPPFunctionType)
                    }
                    is P.ExpressionContext -> visitor.visitExpression(node, expected)
                    else -> return fail("Unsupported function-value argument")
                }
                if (value.isError) return value
                val captured = StorageAccess.capture(value)
                if (captured.isError) return captured
                args += captured
                cache += captured
            }
            if (callable != null) return invoke(callable, args)
            val selected = ParameterMatcher.selectDeclaredTypes(declarations,
                if (boundCandidates != null) declarations.firstOrNull()?.identifier ?: name else name, supplied, args.map { it.type })
            val target = when (selected) {
                is ParameterMatcher.TypeSelection.Selected -> selected.function
                is ParameterMatcher.TypeSelection.Ambiguous -> return fail(ParameterMatcher.ambiguity(name, selected.functions))
                else -> return fail("Cannot resolve function '$name' for supplied argument types")
            }
            Function.currFunction.child.add(target)
            target.parent.add(Function.currFunction)
            return when (target) {
                is Generic<*> -> target.invoke(readonly, args, null)
                is NativeFunction -> target.invoke(readonly, args, null)
                else -> target.invoke(args, null)
            }
        } catch (failure: DeclarationBinding.Failure) {
            return fail(failure.message ?: "Cannot bind callback arguments")
        } finally {
            while (cache.size > previousSize) cache.removeAt(cache.lastIndex)
            StorageAccess.restore(spills)
        }
    }

    fun reference(name: String, target: MCFPPFunctionType, site: ParserRuleContext): Var<*> {
        val environment = LambdaBinding.Environment(caller = Function.currFunction)
        val candidates = top.mcfpp.analysis.StructuredBindings.functions(site, Function.currFunction) ?: environment.functions(name)
        return when (val binding = LambdaBinding.resolveReference(candidates.firstOrNull()?.identifier ?: name, target, candidates, site)) {
            is LambdaBinding.Result.Bound -> FunctionValue(TempPool.getVarIdentify(), target, binding.value.function)
            is LambdaBinding.Result.Rejected -> fail(binding.diagnostic.render())
        }
    }

    fun invoke(value: FunctionValue, arguments: List<Var<*>>): Var<*> {
        val signature = value.type as MCFPPFunctionType
        if (arguments.size != signature.parameters.size || arguments.indices.any {
                TypeRelations.resolveImplicitConversion(arguments[it].type, signature.parameters[it]) == null })
            return fail("Arguments do not match function value '${signature.typeName}'")
        val targets = value.knownTargets()
        if (targets.isEmpty()) return fail("Function value '${value.identifier}' requires finite compiler-known callable targets; " +
            "arbitrary string addresses and loading persistent callable data are not supported")
        if (targets.size > 1) return LambdaDispatch.invoke(value, arguments, targets)
        val target = targets.single()
        if (StorageAccess.snapshot(value) == null) {
            StorageAccess.materialize(value)
            val path = StorageAccess.ensure(value).path.memberIndex("target")
            val probe = top.mcfpp.lib.NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
            val different = top.mcfpp.core.lang.MCInt()
            Function.addCommand("execute unless data ${path.toCommandPart()} run return fail")
            Function.addCommand(top.mcfpp.command.Commands.dataSetFrom(probe, path))
            Function.addCommand(top.mcfpp.command.Command("execute store success score ${different.name} ${different.sbObject} run")
                .build(top.mcfpp.command.Commands.dataSetValue(probe,
                    top.mcfpp.nbt.tags.primitive.StringTag(target.namespaceID.toString()))))
            Function.addCommand("execute if score ${different.name} ${different.sbObject} matches 1 run return fail")
        }
        return invokeKnown(value, arguments, target)
    }

    internal fun invokeKnown(value: FunctionValue, arguments: List<Var<*>>, target: Function): Var<*> {
        val signature = value.type as MCFPPFunctionType
        Function.currFunction.child.add(target)
        target.parent.add(Function.currFunction)
        val actualArguments = if (target.normalParams.size == signature.parameters.size + 1)
            arguments + value.environment() else arguments
        val returned = target.invoke(actualArguments, null)
        return if (signature.result == MCFPPPrivateType.Void || returned.isError) returned else returned.implicitCast(signature.result)
    }

    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("lambda")
    }
}
