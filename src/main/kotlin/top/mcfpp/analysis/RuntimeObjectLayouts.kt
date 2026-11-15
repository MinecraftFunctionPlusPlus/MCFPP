package top.mcfpp.analysis

import top.mcfpp.type.*

/** A runtime union exposes only the field layout guaranteed by every alternative. */
internal object RuntimeObjectLayouts {
    fun identity(type: MCFPPType): TypeId? = if (type.hasRuntimeRepresentation && when (type) {
        is MCFPPDataTemplateType -> true
        is MCFPPUnionType -> type.types.all { identity(it) != null }
        else -> false
    }) type.typeId else null

    fun field(type: MCFPPType, name: String): ObjectFieldContract? {
        if (type !is MCFPPUnionType) return TypeCompositions.objectShape(type)?.fieldsByName?.get(name)
        val fields = type.types.map { field(it, name) ?: return null }
        return ObjectFieldContract(name, TypeCompositions.union(fields.map { it.type }),
            fields.any { it.optional },
            if (fields.any { it.kind == ObjectMemberKind.CONST }) ObjectMemberKind.CONST else ObjectMemberKind.VAR,
            fields.flatMap { it.origins }.toSet(), fields.all { it.readable }, fields.all { it.writable })
    }
}
