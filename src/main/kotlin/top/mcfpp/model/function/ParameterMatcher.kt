package top.mcfpp.model.function

import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCObject
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Generic
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor

/** Single source-to-parameter query shared by ordinary, generic, and native functions. */
object ParameterMatcher {
    fun sameSignature(a: Function, b: Function): Boolean {
        fun readonly(f: Function) = (f as? Generic<*>)?.readOnlyParams ?: (f as? NativeFunction)?.readOnlyParams ?: emptyList()
        return a.identifier == b.identifier && a.normalParams.map { it.type.typeId } == b.normalParams.map { it.type.typeId }
            && readonly(a).map { it.type.typeId } == readonly(b).map { it.type.typeId }
    }

    fun argumentType(value: Var<*>): MCFPPType = when {
        value is MCObject -> value.type
        value is MCAny -> value.inferredType ?: MCFPPBaseType.Any
        else -> value.type
    }

    fun accepts(value: Var<*>, target: MCFPPType): Boolean =
        (target !is MCFPPDeclaredConcreteType || top.mcfpp.analysis.SpecializationKeys.isConstant(value)) && conversion(argumentType(value), target) != null

    private fun conversion(source: MCFPPType, target: MCFPPType): TypeRelations.Conversion? {
        // Bare native container signatures denote a constructor pattern, not a wildcard value.
        if (target is MCFPPNotCompiledGenericType && target.type.isInstance(source)) return TypeRelations.Conversion.EXACT
        return TypeRelations.resolveImplicitConversion(source, target)
    }

    fun accepts(function: Function, key: String, readonly: List<Var<*>>, normal: List<Var<*>>, defaults: Boolean): Boolean =
        match(function, key, readonly, normal, defaults) != null

    private data class Match(val function: Function, val ranks: List<Int>, val targets: List<MCFPPType>, val defaults: Int)

    private fun match(function: Function, key: String, readonly: List<Var<*>>, normal: List<Var<*>>, defaults: Boolean): Match? {
        if (key != function.identifier) return null
        val rp = (function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams ?: emptyList()
        if (readonly.size > rp.size || normal.size > function.normalParams.size) return null
        if (!defaults && (readonly.size != rp.size || normal.size != function.normalParams.size)) return null
        if (rp.drop(readonly.size).any { !it.hasDefault } || function.normalParams.drop(normal.size).any { !it.hasDefault }) return null
        val bindings = rp.zip(readonly).mapNotNull { (p, arg) -> (arg as? MCFPPTypeVar)?.let { p.identifier to it.value } }.toMap()
        fun target(param: FunctionParam): MCFPPType {
            return SpecializationPolicy.bind(param.type, bindings)
        }
        val targets = rp.take(readonly.size).map(::target) + function.normalParams.take(normal.size).map(::target)
        if (readonly.any { !top.mcfpp.analysis.SpecializationKeys.isConstant(it) }) return null
        val ranks = (readonly + normal).zip(targets).map { (arg, type) ->
            if (!accepts(arg, type)) return null
            conversion(argumentType(arg), type)!!.rank
        }
        return Match(function, ranks, targets, rp.size + function.normalParams.size - readonly.size - normal.size)
    }

    fun select(functions: List<Function>, key: String, readonly: List<Var<*>>, normal: List<Var<*>>): Function? {
        val matches = functions.mapNotNull { match(it, key, readonly, normal, true) }
        if (matches.isEmpty()) return null
        fun better(a: Match, b: Match): Boolean {
            if (a.ranks.zip(b.ranks).all { (x, y) -> x <= y } && a.ranks.zip(b.ranks).any { (x, y) -> x < y }) return true
            if (a.ranks != b.ranks) return false
            if (a.targets.zip(b.targets).all { (x, y) -> TypeRelations.isSubtype(x, y) } &&
                a.targets.zip(b.targets).any { (x, y) -> x != y }) return true
            return a.targets == b.targets && a.defaults < b.defaults
        }
        val best = matches.filter { candidate -> matches.none { other -> other !== candidate && better(other, candidate) } }
        if (best.size == 1) return best.single().function
        LogProcessor.error("Ambiguous overload '$key': ${best.joinToString { it.function.toString() }}")
        return UnknownFunction(key)
    }
}
