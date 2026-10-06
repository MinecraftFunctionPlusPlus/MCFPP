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
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.type.UnresolvedType

/** Ordinary runtime constants never cause a new function body. */
object SpecializationPolicy {
    /** A compiler-only interpretation of an erased formal needs its static payload binding. */
    fun needsStaticErasedBindings(function: Function): Boolean {
        val body = function.ast ?: return false
        val erased = function.normalParams.filter { it.type == top.mcfpp.type.MCFPPBaseType.Any || it.type == top.mcfpp.type.MCFPPBaseType.Object }
            .map { it.identifier }.toSet()
        if (erased.isEmpty()) return false
        fun inspect(tree: org.antlr.v4.runtime.tree.ParseTree): Boolean {
            if (tree is top.mcfpp.antlr.mcfppParser.CastExpressionContext && tree.type() != null &&
                tree.unaryExpression().text.trim('(', ')') in erased) {
                val target = MCFPPType.parseFromString(tree.type().text, function.scope)
                if (target != null && !target.hasRuntimeRepresentation) return true
            }
            return (0 until tree.childCount).any { inspect(tree.getChild(it)) }
        }
        return inspect(body)
    }

    fun bind(type: MCFPPType, bindings: Map<String, MCFPPType>): MCFPPType = when (type) {
        is MCFPPDeclaredConcreteType -> MCFPPDeclaredConcreteType(bind(type.type, bindings))
        is MCFPPGenericParamType -> bindings[type.identifier] ?: type
        is MCFPPTypeWithGeneric -> if (bindings.isEmpty()) type else type.replaceGenericParam(bindings)
        else -> type
    }

    data class BoundSignature(val readonlyValues: List<Var<*>>, val readonlyTypes: List<MCFPPType>,
                              val normalTypes: List<MCFPPType>, val returnType: MCFPPType)

    fun resolveBoundSignature(function: Function, readonly: List<FunctionParam>, supplied: List<Var<*>>): BoundSignature? {
        if (supplied.size > readonly.size || readonly.drop(supplied.size).any { !it.hasDefault }) return null
        val resolve = resolve@ {
            val scope = FunctionScope(function.scope)
            val bindings = LinkedHashMap<String, MCFPPType>()
            fun resolveType(type: MCFPPType): MCFPPType? = if (type is UnresolvedType) {
                MCFPPType.parseFromString(type.originalTypeString, scope, function).also {
                    if (it == null) LogProcessor.error("Invalid bound type: ${type.originalTypeString}")
                }
            } else bind(type, bindings)
            val values = ArrayList<Var<*>>()
            val types = ArrayList<MCFPPType>()
            for ((index, param) in readonly.withIndex()) {
                val value = supplied.getOrNull(index) ?: param.defaultVar ?: return@resolve null
                val type = resolveType(param.type) ?: return@resolve null
                if (!ParameterMatcher.accepts(value, type) || !SpecializationKeys.isConstant(value)) return@resolve null
                val cast = value.implicitCast(type)
                if (cast.isError) return@resolve null
                val frozen = StorageAccess.freezeReadonly(cast, param.identifier) ?: run {
                    LogProcessor.error("Readonly argument layout is not supported for '${param.identifier}'")
                    return@resolve null
                }
                scope.putVar(param.identifier, frozen)
                if (frozen is MCFPPTypeVar) {
                    bindings[param.identifier] = frozen.value
                    scope.putType(param.identifier, frozen.value)
                }
                values.add(frozen)
                types.add(type)
            }
            val normal = function.normalParams.map { resolveType(it.type) ?: return@resolve null }
            val result = resolveType(function.returnType) ?: return@resolve null
            BoundSignature(values, types, normal, result)
        }
        val file = function.restoreDeclarationEnvironment()
        return if (file == null) resolve() else file.withDeclarationContext(resolve)
    }

    fun compileGeneric(function: Function, readonly: List<FunctionParam>, args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>> {
        val signature = resolveBoundSignature(function, readonly, readonly.map { args.getValue(it.identifier) })
            ?: return UnknownFunction(function.identifier) to args
        val readonlyArgs = signature.readonlyValues
        val types = signature.normalTypes
        val normalArgs = function.normalParams.mapIndexed { i, param ->
            val value = args.getValue(param.identifier)
            if (param.type is UnresolvedType && value === param.defaultVar) value.implicitCast(types[i]) else value
        }
        if (normalArgs.any { it.isError }) return UnknownFunction(function.identifier) to args
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
        for ((param, value) in readonly.zip(readonlyArgs)) {
            compiled.scope.removeVar(param.identifier)
            compiled.scope.putVar(param.identifier, value, true)
            if (value is MCFPPTypeVar) compiled.scope.putType(param.identifier, value.value, true)
        }
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
        compiled.returnType = signature.returnType
        compiled.returnVar = compiled.buildReturnVar(compiled.returnType)
        compiled.commands.clear()
        compiled.identifier = function.identifier + "-" + function.compiledFunctions.size
        compiled.ast = null
        function.compiledFunctions[cacheKey] = compiled
        function.compileBody(compiled, function.ast)
        return compiled to runtimeArgs
    }

    fun requiresParameter(type: MCFPPType, value: Var<*>): Boolean =
        type is MCFPPDeclaredConcreteType || !type.hasRuntimeRepresentation ||
            !StorageAccess.hasRuntimeRepresentation(value)

    fun key(function: Function, arguments: List<Var<*>>, specialized: List<Boolean>): SpecializationKey {
        require(arguments.size == specialized.size)
        return SpecializationKey(function.declarationId, arguments.indices.map { index ->
            if (specialized[index]) SpecializationKeys.argument(arguments[index]) else SpecializationArgument.Unknown
        }, GenerationOptions(top.mcfpp.Project.config.version, top.mcfpp.CompileSettings.maxWhileInline,
            top.mcfpp.CompileSettings.foldIRConstants))
    }
}
