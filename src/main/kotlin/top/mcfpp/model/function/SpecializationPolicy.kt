package top.mcfpp.model.function

import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.type.MCFPPDeclaredConcreteType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPTypeWithGeneric
import top.mcfpp.util.LogProcessor
import top.mcfpp.antlr.MCFPPImVisitor

/** Ordinary runtime constants never cause a new function body. */
object SpecializationPolicy {
    fun bind(type: MCFPPType, bindings: Map<String, MCFPPType>): MCFPPType = when (type) {
        is MCFPPDeclaredConcreteType -> MCFPPDeclaredConcreteType(bind(type.type, bindings))
        is MCFPPGenericParamType -> bindings[type.identifier] ?: type
        is MCFPPTypeWithGeneric -> if (bindings.isEmpty()) type else type.replaceGenericParam(bindings)
        else -> type
    }

    fun compileGeneric(function: Function, readonly: List<FunctionParam>, args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>> {
        val readonlyArgs = readonly.map { args.getValue(it.identifier) }
        val normalArgs = function.normalParams.map { args.getValue(it.identifier) }
        val bindings = readonly.zip(readonlyArgs).mapNotNull { (param, value) ->
            (value as? MCFPPTypeVar)?.let { param.identifier to it.value }
        }.toMap()
        val types = function.normalParams.map { bind(it.type, bindings) }
        val normalSpecialized = types.zip(normalArgs).map { (type, value) -> requiresParameter(type, value) }
        val allArgs = readonlyArgs + normalArgs
        val specialized = List(readonlyArgs.size) { true } + normalSpecialized
        if (allArgs.indices.any { specialized[it] && !SpecializationKeys.isConstant(allArgs[it]) }) {
            LogProcessor.error("Generic arguments require complete immutable compile-time values")
            return UnknownFunction(function.identifier) to args
        }
        val runtimeArgs = LinkedHashMap<String, Var<*>>()
        for (i in normalArgs.indices) if (!normalSpecialized[i]) runtimeArgs[function.normalParams[i].identifier] = normalArgs[i]
        val cacheKey = key(function, allArgs, specialized)
        function.compiledFunctions[cacheKey]?.let { return it to runtimeArgs }
        val compiled = Function(function)
        for ((name, type) in bindings) compiled.scope.putType(name, type, true)
        for ((param, value) in readonly.zip(readonlyArgs))
            compiled.scope.putVar(param.identifier, compiled.scope.getVar(param.identifier)!!.assignedBy(value), true)
        val runtimeParams = ArrayList<FunctionParam>()
        for (i in function.normalParams.indices) {
            val original = function.normalParams[i]
            val param = FunctionParam(types[i], original.identifier, compiled, original.isStatic, original.hasDefault)
            param.defaultVar = original.defaultVar
            val storage = param.buildVar()
            compiled.scope.putVar(param.identifier, if (normalSpecialized[i]) storage.assignedBy(normalArgs[i]) else storage, true)
            if (!normalSpecialized[i]) runtimeParams.add(param)
        }
        compiled.normalParams = runtimeParams
        compiled.returnType = bind(function.returnType, bindings)
        compiled.returnVar = compiled.buildReturnVar(compiled.returnType)
        compiled.commands.clear()
        compiled.identifier = function.identifier + "_" + function.compiledFunctions.size
        compiled.ast = null
        function.compiledFunctions[cacheKey] = compiled
        compiled.runInFunction { MCFPPImVisitor().visitCurlBlock(function.ast!!) }
        return compiled to runtimeArgs
    }

    fun requiresParameter(type: MCFPPType, value: Var<*>): Boolean =
        type is MCFPPDeclaredConcreteType || !type.hasRuntimeRepresentation ||
            !(if (value is MCAny) value.inferredType ?: value.type else value.type).hasRuntimeRepresentation

    fun key(function: Function, arguments: List<Var<*>>, specialized: List<Boolean>): SpecializationKey {
        require(arguments.size == specialized.size)
        return SpecializationKey(function.declarationId, arguments.indices.map { index ->
            if (specialized[index]) SpecializationKeys.argument(arguments[index]) else SpecializationArgument.Unknown
        }, GenerationOptions(top.mcfpp.Project.config.version, top.mcfpp.CompileSettings.maxWhileInline,
            top.mcfpp.CompileSettings.foldIRConstants))
    }
}
