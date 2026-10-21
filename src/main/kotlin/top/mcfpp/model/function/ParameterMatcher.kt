package top.mcfpp.model.function

import org.antlr.v4.runtime.CharStreams
import top.mcfpp.analysis.CallableDeclarationId
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.DeclarationPreparationContext
import top.mcfpp.analysis.PreparedCompilerSignature
import top.mcfpp.analysis.PreparedDeclarations
import top.mcfpp.analysis.PreparedExpressionFact
import top.mcfpp.analysis.SignaturePreparationRequest
import top.mcfpp.analysis.SpecializationKeys
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCObject
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Generic
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor

/** Single source-to-parameter query shared by ordinary, generic, and native functions. */
object ParameterMatcher {
    private fun readonlyParameters(function: Function): List<FunctionParam> = (function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()

    fun sameSignature(a: Function, b: Function): Boolean {
        fun signature(function: Function): List<Any> {
            val readonly = readonlyParameters(function)
            val names = readonly.mapIndexed { index, param -> param.identifier to "#readonly$index" }.toMap()
            fun identity(type: MCFPPType): Any {
                if (type is MCFPPGenericParamType && type.identifier in names) return names.getValue(type.identifier)
                if (type is MCFPPTypeWithGeneric && type.generic.isNotEmpty()) {
                    val id = type.typeId
                    if (id is TypeId.Applied) return id.constructor to type.generic.map(::identity)
                }
                if (type !is UnresolvedType) return type.typeId
                val lexer = mcfppLexer(CharStreams.fromString(type.originalTypeString))
                return lexer.allTokens.joinToString("") { token ->
                    if (token.type == mcfppLexer.Identifier) names[token.text] ?: token.text else token.text
                }
            }
            return (readonly + function.normalParams).map { identity(it.type) }
        }
        return a.identifier == b.identifier &&
            readonlyParameters(a).size == readonlyParameters(b).size &&
            signature(a) == signature(b)
    }

    fun argumentType(value: Var<*>): MCFPPType = when {
        value is MCObject -> value.type
        value is MCAny -> value.inferredType ?: MCFPPBaseType.Any
        else -> value.type
    }

    fun accepts(value: Var<*>, target: MCFPPType): Boolean = conversion(argumentType(value), target) != null

    private fun conversion(source: MCFPPType, target: MCFPPType): TypeRelations.Conversion? {
        // Bare native container signatures denote a constructor pattern, not a wildcard value.
        if (target is MCFPPNotCompiledGenericType && target.type.isInstance(source)) return TypeRelations.Conversion.EXACT
        return TypeRelations.resolveImplicitConversion(source, target)
    }

    fun accepts(function: Function, key: String, readonly: List<Var<*>>, normal: List<Var<*>>, defaults: Boolean): Boolean = match(function, key, readonly, normal, defaults) != null

    private data class Match(val function: Function, val ranks: List<Int>, val targets: List<MCFPPType>, val defaults: Int)

    private fun match(function: Function, key: String, readonly: List<Var<*>>, normal: List<Var<*>>, defaults: Boolean): Match? {
        if (readonly.any { !SpecializationKeys.isConstant(it) }) return null
        return declaredMatch(function, key, readonly.map(::declarationArgument), normal.map(::argumentType), defaults)
    }

    private fun declarationArgument(value: Var<*>) = DeclarationBinding.Bound(
        argumentType(value),
        StorageAccess.snapshot(value),
        descriptors = StorageAccess.boundTypes(value)
    )

    fun select(functions: List<Function>, key: String, readonly: List<Var<*>>, normal: List<Var<*>>): Function? {
        val matches = functions.mapNotNull { match(it, key, readonly, normal, true) }
        if (matches.isEmpty()) return null
        val best = best(matches)
        if (best.size == 1) return best.single().function
        LogProcessor.error(ambiguity(key, best.map { it.function }))
        return UnknownFunction(key)
    }

    internal fun ambiguity(key: String, functions: List<Function>): String = "Ambiguous overload '$key': ${functions.joinToString { function ->
        val owner = function.owner
        if (owner == null) function.toString() else "${function} [owner ${owner.inheritanceIdentity}]"
    }}"

    sealed interface TypeSelection {
        data class Selected(val function: Function) : TypeSelection
        data class Ambiguous(val functions: List<Function>) : TypeSelection
        object Missing : TypeSelection
    }

    fun selectConstructor(constructors: List<DataTemplateConstructor>, arguments: List<Var<*>>): TypeSelection = typeSelection(constructors.mapNotNull { match(it, it.identifier, emptyList(), arguments, true) })

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
    fun selectTypes(functions: List<Function>, key: String, normal: List<MCFPPType>): TypeSelection = selectTypes(functions, key, emptyList(), normal)

    /** Declaration binding never manufactures ordinary argument values. */
    internal fun selectTypes(functions: List<Function>, key: String, suppliedReadonly: List<Var<*>>, normal: List<MCFPPType>): TypeSelection {
        if (suppliedReadonly.any { !SpecializationKeys.isConstant(it) }) return TypeSelection.Missing
        return selectDeclaredTypes(functions, key, suppliedReadonly.map(::declarationArgument), normal)
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

    internal fun selectDeclaredTypes(
        functions: List<Function>,
        key: String,
        readonlyArguments: List<DeclarationBinding.Bound>,
        normal: List<MCFPPType>,
        receiverTypes: Map<String, MCFPPType> = emptyMap()
    ): TypeSelection {
        return typeSelection(functions.mapNotNull { declaredMatch(it, key, readonlyArguments, normal, true, receiverTypes) })
    }

    internal sealed interface PreparedTypeSelection {
        class Selected(val signature: PreparedCompilerSignature) : PreparedTypeSelection
        class Ambiguous(signatures: List<PreparedCompilerSignature>) : PreparedTypeSelection {
            val signatures = java.util.Collections.unmodifiableList(ArrayList(signatures))
        }
        object Missing : PreparedTypeSelection
    }

    /** Preserve the explicit/default boundary of the actual H03 request, not just its completed values. */
    internal class PreparedCallFacts(val request: SignaturePreparationRequest, val declarations: PreparedDeclarations)

    /** Only consumes H03 results. Declaration resolution and specialization are deliberately absent. */
    internal fun selectPreparedTypes(
        facts: PreparedCallFacts,
        key: String,
        readonly: List<PreparedExpressionFact>,
        normal: List<MCFPPType>,
        declarations: Set<CallableDeclarationId>? = null
    ): PreparedTypeSelection {
        if (readonly.size != facts.request.explicitReadonly.size ||
            readonly.zip(facts.request.explicitReadonly).any { (actual, prepared) ->
                actual.type.typeId != prepared.type.typeId || actual.value != prepared.value
            }
        ) return PreparedTypeSelection.Missing
        val signatures = facts.declarations.signatures.filter {
            it.stamp.name == key &&
                (declarations == null || it.declaration in declarations)
        }
        val matches = signatures.mapNotNull { signature ->
            if (readonly.size > signature.readonly.size ||
                normal.size > signature.normal.size ||
                signature.readonly.drop(readonly.size).any { !it.hasDefault } ||
                signature.normal.drop(normal.size).any { !it.hasDefault }
            ) return@mapNotNull null
            val binder = DeclarationBinding(DeclarationPreparationContext(signature.lexical))
            // A prepared specialization belongs to its closed readonly values, not merely their types.
            for ((index, argument) in readonly.withIndex()) {
                val target = signature.readonly[index].type
                val constrained = if (target == MCFPPBaseType.Any || target == MCFPPBaseType.Object)
                    argument.bound.copy(type = target, constant = CompilerValue.Typed(target.typeId, argument.value))
                else binder.constrain(argument.bound, target)
                if (constrained.constant == null || constrained.constant != signature.readonlyArguments[index].value)
                    return@mapNotNull null
            }
            val targets = signature.readonly.take(readonly.size).map { it.type } +
                signature.normal.take(normal.size).map { it.type }
            val sources = readonly.map { it.type } + normal
            val ranks = sources.zip(targets).map { (source, target) ->
                conversion(source, target)?.rank ?: return@mapNotNull null
            }
            Match(
                signature.function,
                ranks,
                targets,
                signature.readonly.size + signature.normal.size - readonly.size - normal.size
            )
        }
        // Share exactly the same dominance/default comparison used by ordinary/native calls.
        val selected = best(matches).map { match -> signatures.single { it.function === match.function } }
        return when (selected.size) {
            0 -> PreparedTypeSelection.Missing
            1 -> PreparedTypeSelection.Selected(selected.single())
            else -> PreparedTypeSelection.Ambiguous(selected)
        }
    }

    /** Both entry points query immutable argument knowledge and rank exactly the supplied parameters. */
    private fun declaredMatch(
        function: Function,
        key: String,
        readonlyArguments: List<DeclarationBinding.Bound>,
        normal: List<MCFPPType>,
        defaults: Boolean,
        receiverTypes: Map<String, MCFPPType> = emptyMap()
    ): Match? {
        val readonly = readonlyParameters(function)
        if (!(function.identifier == key || key.isEmpty() && function is DataTemplateConstructor)) return null
        if (readonlyArguments.size > readonly.size || normal.size > function.normalParams.size) return null
        if (!defaults && (readonlyArguments.size != readonly.size || normal.size != function.normalParams.size)) return null
        if (readonly.drop(readonlyArguments.size).any { !it.hasDefault } ||
            function.normalParams.drop(normal.size).any { !it.hasDefault }
        ) return null
        val resolve = resolve@{
            val signature = SpecializationPolicy.resolveDeclaredSignature(function, readonly, readonlyArguments, receiverTypes)
                ?: return@resolve null
            val values = linkedMapOf<String, DeclarationBinding.Bound>()
            val bindings = receiverTypes.toMutableMap()
            val readonlyTargets = arrayListOf<MCFPPType>()
            for ((parameter, argument) in readonly.zip(readonlyArguments)) {
                val binder = DeclarationBinding(function, emptyMap(), values, bindings)
                val target = binder.declaredType(parameter.type)
                readonlyTargets.add(target)
                val bound = binder.constrain(argument, target)
                values[parameter.identifier] = bound
                val payload = (bound.constant as? CompilerValue.Typed)?.payload
                if (payload is CompilerValue.TypeValue)
                    bindings[parameter.identifier] = bound.descriptors[payload.id] ?: MCFPPType.resolveTypeId(payload.id)
                        ?: return@resolve null
            }
            val targets = readonlyTargets + signature.normalTypes.take(normal.size)
            val sources = readonlyArguments.map { it.type } + normal
            val ranks = sources.zip(targets).map { (source, target) -> conversion(source, target)?.rank ?: return@resolve null }
            Match(function, ranks, targets, readonly.size + function.normalParams.size - readonlyArguments.size - normal.size)
        }
        return try {
            val file = function.restoreDeclarationEnvironment()
            if (file == null) resolve() else file.withDeclarationContext(resolve)
        } catch (_: DeclarationBinding.Failure) {
            null
        }
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
