package top.mcfpp.analysis

import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId

/** Type descriptors are values only in readonly generic bindings, never ordinary storage. */
internal object TypeUsage {
    const val DIAGNOSTIC = "Type values are only allowed as generic parameters"

    fun ordinaryDiagnostic(type: MCFPPType, snapshot: CompilerValue? = null): String? {
        fun containsMeta(id: TypeId): Boolean = id == MCFPPConcreteType.Type.typeId ||
            id is TypeId.Union && id.alternatives.any(::containsMeta)
        return if (containsMeta(type.typeId) || snapshot?.containsTypeValue() == true) DIAGNOSTIC else null
    }
}

internal fun CompilerValue.containsTypeValue(): Boolean = when (this) {
    is CompilerValue.TypeValue -> true
    is CompilerValue.Typed -> payload.containsTypeValue()
    is CompilerValue.Sequence -> elements.any { it.containsTypeValue() }
    is CompilerValue.Record -> fields.values.any { it.containsTypeValue() }
    else -> false
}
