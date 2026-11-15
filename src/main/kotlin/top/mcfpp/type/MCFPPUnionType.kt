package top.mcfpp.type

import top.mcfpp.core.lang.UnionTypeVar
import top.mcfpp.core.lang.Var

class MCFPPUnionType private constructor(alternatives: Collection<MCFPPType>, internal val deferredComposition: Boolean): MCFPPType() {
    constructor(vararg alternatives: MCFPPType) : this(alternatives.toList(), false)

    var types: Array<MCFPPType> = TypeCompositions.normalizeUnionAlternatives(alternatives, !deferredComposition).toTypedArray()
    init { require(types.isNotEmpty()) { "A union needs at least one alternative" } }
    override val hasRuntimeRepresentation: Boolean get() = types.all { it.hasRuntimeRepresentation }
    override val typeId: TypeId get() = if (types.size == 1) types.single().typeId else TypeId.Union(types.map { it.typeId }.toSet())

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
        return UnionTypeVar(identifier, *types).also { it.type = this }
    }

    companion object {
        internal fun deferred(alternatives: Collection<MCFPPType>): MCFPPUnionType = MCFPPUnionType(alternatives, true)
    }

}
