package top.mcfpp.type

import top.mcfpp.core.lang.FunctionValue
import top.mcfpp.core.lang.Var
import java.util.Collections

/**
 * The language type of a function value: parameter types, result type and execution
 * category. The type carries no `throws` contract.
 *
 * Identity is canonical and structural through [TypeId.function]. Two independently
 * constructed instances with the same parameters, result and category have the same
 * [typeId] and compare equal; no object identity or declaration site is involved.
 *
 * Compatibility is queried through [TypeRelations.isSubtype] and
 * [TypeRelations.resolveImplicitConversion]. Parameters are contravariant, the result
 * is covariant, and the execution category and arity are invariant. Constituent types
 * still use the ordinary nominal subtype relation, so this type never turns an ordinary
 * object into a structurally compatible one.
 *
 * Runtime values contain a compiler-owned target and an owned environment payload.
 */
class MCFPPFunctionType(
    parameters: List<MCFPPType>,
    val result: MCFPPType,
    val executionCategory: ExecutionCategory = ExecutionCategory.NORMAL
) : MCFPPType(), MCFPPTypeWithGeneric {

    /** Parameter types in declaration order. The list is a defensive, read-only copy. */
    val parameters: List<MCFPPType> = Collections.unmodifiableList(ArrayList(parameters))

    override val typeId: TypeId
        get() = TypeId.function(parameters.map { it.typeId }, result.typeId, executionCategory.identityName)

    /** Function payloads use the existing NBT-backed value storage. */
    override val isValueType: Boolean get() = true

    override val hasRuntimeRepresentation: Boolean get() = true

    /** Parameters followed by the result, for type traversal and generic substitution. */
    override val generic: List<MCFPPType> get() = parameters + result

    override val typeName: String
        get() = buildString {
            append(executionCategory.displayPrefix)
            append("(")
            append(parameters.joinToString(", ") { it.typeName })
            append(") -> ")
            append(result.typeName)
        }

    override fun toString(): String = typeName

    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPFunctionType =
        MCFPPFunctionType(
            parameters.map { substitute(it, type) },
            substitute(result, type),
            executionCategory
        )

    override fun build(identifier: String, value: Any?): Var<*> = when (value) {
        is top.mcfpp.model.function.Function -> FunctionValue(identifier, this, value)
        else -> FunctionValue(identifier, this)
    }

    override fun buildUnConcrete(identifier: String): Var<*> = FunctionValue(identifier, this)

    /** Execution category of a function value. Categories never interchange implicitly. */
    enum class ExecutionCategory(
        /** Stable spelling used in the canonical [TypeId] identity. */
        val identityName: String,
        /** Prefix used only for the human-readable [MCFPPFunctionType.typeName]. */
        val displayPrefix: String
    ) {
        /** Ordinary synchronous call. */
        NORMAL("normal", ""),

        /** Caller has to pause; the design document writes this capability as `suspend`. */
        PAUSED("paused", "paused "),

        /** Calling creates a generator instance whose successive values are requested later. */
        GENERATOR("generator", "generator ")
    }

    companion object {
        private fun substitute(candidate: MCFPPType, bindings: Map<String, MCFPPType>): MCFPPType =
            when (candidate) {
                is MCFPPGenericParamType -> bindings[candidate.identifier] ?: candidate
                is MCFPPTypeWithGeneric -> candidate.replaceGenericParam(bindings)
                else -> candidate
            }

        /**
         * Restores a function type from a [TypeId.function] identity. Returns null when a
         * constituent nominal type cannot be resolved in the current compilation.
         */
        fun fromIdentity(id: TypeId): MCFPPFunctionType? {
            if (id !is TypeId.Applied || id.constructor != TypeId.Builtin(TypeId.FUNCTION_CONSTRUCTOR)) return null
            if (id.arguments.size != 3) return null
            val category = ExecutionCategory.values().firstOrNull {
                id.arguments[0] == TypeId.Builtin(TypeId.FUNCTION_EXECUTION_PREFIX + it.identityName)
            } ?: return null
            val parameterList = id.arguments[1] as? TypeId.Applied ?: return null
            if (parameterList.constructor != TypeId.Builtin(TypeId.FUNCTION_PARAMETERS)) return null
            val parameterTypes = parameterList.arguments.map { resolve(it) ?: return null }
            val resultType = resolve(id.arguments[2]) ?: return null
            return MCFPPFunctionType(parameterTypes, resultType, category)
        }

        private fun resolve(id: TypeId): MCFPPType? =
            if (id is TypeId.Applied && id.constructor == TypeId.Builtin(TypeId.FUNCTION_CONSTRUCTOR))
                fromIdentity(id)
            else
                MCFPPType.resolveTypeId(id)
    }
}
