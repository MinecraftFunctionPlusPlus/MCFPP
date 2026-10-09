package top.mcfpp.type

import top.mcfpp.core.lang.UnionTypeVar
import top.mcfpp.core.lang.Var

class MCFPPUnionType(vararg alternatives: MCFPPType): MCFPPType() {
    var types: Array<MCFPPType> = alternatives.flatMap { if (it is MCFPPUnionType) it.types.toList() else listOf(it) }
        .distinctBy { it.typeId }.sortedBy { it.typeId.toString() }.toTypedArray()
    init { require(types.isNotEmpty()) { "A union needs at least one alternative" } }
    override val hasRuntimeRepresentation: Boolean get() = types.all { it.hasRuntimeRepresentation }
    override val typeId: TypeId get() = TypeId.Union(types.map { it.typeId }.toSet())

    override val typeName: String
        get() = "UnionType(${types.joinToString(", ")})"

    override fun defaultValue(): Any? {
        return types[0].defaultValue()
    }

    override fun defaultValueVar(): Var<*> {
        return buildUnConcrete("default")
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        return top.mcfpp.analysis.StorageAccess.literal(this, value as top.mcfpp.analysis.CompilerValue, identifier)
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        return UnionTypeVar(identifier, *types)
    }

}
