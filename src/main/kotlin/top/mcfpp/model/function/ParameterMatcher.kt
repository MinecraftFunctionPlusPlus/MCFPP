package top.mcfpp.model.function

import org.antlr.v4.runtime.CharStreams
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCObject
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Generic
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor

/** Single source-to-parameter query shared by ordinary, generic, and native functions. */
object ParameterMatcher {
    private fun readonlyParameters(function: Function): List<FunctionParam> =
        (function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()

    fun sameSignature(a: Function, b: Function): Boolean {
        fun signature(function: Function): List<Any> {
            val readonly = readonlyParameters(function)
            val names = readonly.mapIndexed { index, param -> param.identifier to "#readonly$index" }.toMap()
            fun identity(type: MCFPPType): Any {
                if (type !is UnresolvedType) return type.typeId
                val lexer = mcfppLexer(CharStreams.fromString(type.originalTypeString))
                return lexer.allTokens.joinToString("") { token ->
                    if (token.type == mcfppLexer.Identifier) names[token.text] ?: token.text else token.text
                }
            }
            return (readonly + function.normalParams).map { identity(it.type) }
        }
        return a.identifier == b.identifier &&
            readonlyParameters(a).size == readonlyParameters(b).size && signature(a) == signature(b)
    }

    fun argumentType(value: Var<*>): MCFPPType = when {
        value is MCObject -> value.type
        value is MCAny -> value.inferredType ?: MCFPPBaseType.Any
        else -> value.type
    }

    fun accepts(value: Var<*>, target: MCFPPType): Boolean =
        conversion(argumentType(value), target) != null

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
        val readonlyParams = readonlyParameters(function)
        if (readonly.size > readonlyParams.size || normal.size > function.normalParams.size) return null
        if (!defaults && (readonly.size != readonlyParams.size || normal.size != function.normalParams.size)) return null
        if (readonlyParams.drop(readonly.size).any { !it.hasDefault } || function.normalParams.drop(normal.size).any { !it.hasDefault }) return null
        val bindings = readonlyParams.zip(readonly).mapNotNull { (param, argument) ->
            (argument as? MCFPPTypeVar)?.let { param.identifier to it.value }
        }.toMap()
        fun target(param: FunctionParam): MCFPPType {
            return SpecializationPolicy.bind(param.type, bindings)
        }
        if (readonly.any { !SpecializationKeys.isConstant(it) }) return null
        if (readonlyParams.zip(readonly).any { (param, value) ->
                val type = target(param)
                type !is UnresolvedType && !accepts(value, type)
            }) return null
        val signature = if (function is GenericFunction) {
            SpecializationPolicy.resolveBoundSignature(function, readonlyParams, readonly) ?: return null
        } else null
        val targets = if (signature != null)
            signature.readonlyTypes.take(readonly.size) + signature.normalTypes.take(normal.size)
        else readonlyParams.take(readonly.size).map(::target) + function.normalParams.take(normal.size).map(::target)
        val ranks = (readonly + normal).zip(targets).map { (arg, type) ->
            conversion(argumentType(arg), type)?.rank ?: return null
        }
        return Match(function, ranks, targets, readonlyParams.size + function.normalParams.size - readonly.size - normal.size)
    }

    fun select(functions: List<Function>, key: String, readonly: List<Var<*>>, normal: List<Var<*>>): Function? {
        val matches = functions.mapNotNull { match(it, key, readonly, normal, true) }
        if (matches.isEmpty()) return null
        val best = best(matches)
        if (best.size == 1) return best.single().function
        LogProcessor.error("Ambiguous overload '$key': ${best.joinToString { it.function.toString() }}")
        return UnknownFunction(key)
    }

    sealed interface TypeSelection {
        data class Selected(val function: Function) : TypeSelection
        data class Ambiguous(val functions: List<Function>) : TypeSelection
        object Missing : TypeSelection
    }

    fun selectConstructor(constructors: List<DataTemplateConstructor>, arguments: List<Var<*>>): TypeSelection =
        typeSelection(constructors.mapNotNull { match(it, it.identifier, emptyList(), arguments, true) })

    fun selectOperatorTypes(candidates: List<Pair<MCFPPType?, Function>>, actual: MCFPPType?): TypeSelection {
        val matches = candidates.mapNotNull { (formal, function) ->
            when {
                formal == actual -> Match(function, listOf(0), listOfNotNull(formal), 0)
                formal != null && actual != null && actual.isSubOf(formal) -> Match(function, listOf(1), listOf(formal), 0)
                else -> null
            }
        }
        return typeSelection(matches)
    }

    /** Preliminary type-only binding; value requirements remain the responsibility of the IR boundary. */
    fun selectTypes(functions: List<Function>, key: String, normal: List<MCFPPType>): TypeSelection =
        selectTypes(functions, key, emptyList(), normal)

    /** Declaration binding never manufactures ordinary argument values. */
    internal fun selectTypes(functions: List<Function>, key: String, suppliedReadonly: List<Var<*>>, normal: List<MCFPPType>): TypeSelection {
        val matches = functions.mapNotNull { function ->
            val readonly = readonlyParameters(function)
            if (!(function.identifier == key || key.isEmpty() && function is DataTemplateConstructor)) return@mapNotNull null
            if (suppliedReadonly.size > readonly.size || readonly.drop(suppliedReadonly.size).any { !it.hasDefault }) return@mapNotNull null
            if (normal.size > function.normalParams.size || function.normalParams.drop(normal.size).any { !it.hasDefault }) return@mapNotNull null
            val signature = if (function is GenericFunction) {
                SpecializationPolicy.resolveBoundSignature(function, readonly, suppliedReadonly) ?: return@mapNotNull null
            } else null
            if (signature == null && readonly.zip(suppliedReadonly).any { (parameter, value) ->
                    !SpecializationKeys.isConstant(value) || !accepts(value, parameter.type) }) return@mapNotNull null
            val bindings = readonly.zip(suppliedReadonly).mapNotNull { (parameter, value) ->
                (value as? MCFPPTypeVar)?.let { parameter.identifier to it.value }
            }.toMap()
            val targets = (signature?.normalTypes ?: function.normalParams.map { SpecializationPolicy.bind(it.type, bindings) }).take(normal.size)
            val ranks = normal.zip(targets).map { (source, target) -> conversion(source, target)?.rank ?: return@mapNotNull null }
            Match(function, ranks, targets, function.normalParams.size - normal.size)
        }
        return typeSelection(matches)
    }

    private fun best(matches: List<Match>): List<Match> {
        fun better(a: Match, b: Match): Boolean {
            val ranks = a.ranks.zip(b.ranks)
            if (ranks.all { (x, y) -> x <= y } && ranks.any { (x, y) -> x < y }) return true
            if (a.ranks != b.ranks) return false
            val targets = a.targets.zip(b.targets)
            if (targets.all { (x, y) -> TypeRelations.isSubtype(x, y) } && targets.any { (x, y) -> x != y }) return true
            return a.targets == b.targets && a.defaults < b.defaults
        }
        return matches.filter { candidate -> matches.none { other -> other !== candidate && better(other, candidate) } }
    }

    internal fun selectDeclaredTypes(functions: List<Function>, key: String,
                                     readonlyArguments: List<DeclarationBinding.Bound>, normal: List<MCFPPType>,
                                     receiverTypes: Map<String, MCFPPType> = emptyMap()): TypeSelection {
        val matches = functions.mapNotNull { function ->
            val readonly = readonlyParameters(function)
            if (!(function.identifier == key || key.isEmpty() && function is DataTemplateConstructor)) return@mapNotNull null
            if (normal.size > function.normalParams.size || function.normalParams.drop(normal.size).any { !it.hasDefault }) return@mapNotNull null
            val signature = SpecializationPolicy.resolveDeclaredSignature(function, readonly, readonlyArguments, receiverTypes) ?: return@mapNotNull null
            val targets = signature.normalTypes.take(normal.size)
            val ranks = normal.zip(targets).map { (source, target) -> conversion(source, target)?.rank ?: return@mapNotNull null }
            Match(function, ranks, targets, function.normalParams.size - normal.size)
        }
        return typeSelection(matches)
    }

    private fun typeSelection(matches: List<Match>): TypeSelection {
        val selected = best(matches)
        return when (selected.size) {
            0 -> TypeSelection.Missing
            1 -> TypeSelection.Selected(selected.single().function)
            else -> TypeSelection.Ambiguous(selected.map { it.function })
        }
    }
}
