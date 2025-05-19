package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData

class MCFPPEntityType(val limit: Int? = null, val types: List<String>? = null, val isName: Boolean = false) : MCFPPConcreteType(arrayListOf(MCFPPBaseType.Any)) {

    override val objectData: CompoundData
        get() = SelectorVar.data

    override val typeName: String
        get() {
            val builder = StringBuilder("entity[")
            if (limit != null) builder.append(limit).append(",")
            if (types != null) builder.append(types.joinToString(",")).append(",")
            builder.append(isName)
            builder.append("]")
            return builder.toString()
        }

    override fun defaultValue(): Var<*> {
        return SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.SELF), "default")
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        return build(identifier)
    }

    override fun build(identifier: String): Var<*> {
        val qwq = EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES)
        if (limit != null) qwq.limit(limit)
        if (types != null) {
            for (type in types)
                if (type.startsWith('!')) {
                    qwq.type(type.substring(1), true)
                } else {
                    qwq.type(type, false)
                }
        }
        return SelectorVar(qwq, identifier)
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        return build(identifier)
    }

    override fun equals(other: Any?): Boolean {
        if (other == this) return true
        if (other !is MCFPPEntityType) return false
        if (limit != other.limit) return false
        if (types == null && other.types == null) return true
        if (types == null) return false
        if (other.types == null) return false
        return types.size == other.types.size && types.zip(other.types).all { it.first == it.second }
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (limit ?: 0)
        result = 31 * result + (types?.hashCode() ?: 0)
        return result
    }

    fun canCastTo(other: MCFPPEntityType): Boolean {
        if (this == other) return true
        if (this.limit != null && other.limit == null) return true
        if (this.types != null && other.types == null) return true
        if (this.types != null && other.types != null && this.types.containsAll(other.types)) return true
        return false
    }

    companion object {
        val NormalSelector = MCFPPEntityType()
        val SpecifiedEntity = MCFPPEntityType(1, null, false)
    }

}