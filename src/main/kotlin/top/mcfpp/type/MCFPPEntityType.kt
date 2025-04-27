package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.PlayerVar
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.entity.SpecifiedEntityConcreteVar
import top.mcfpp.core.lang.entity.SpecifiedEntityVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.nbt.tags.primitive.StringTag

class MCFPPEntityType {

    object EntityBase: MCFPPType(arrayListOf(MCFPPBaseType.Any)){

        override val objectData: CompoundData
            get() = TODO()

        override val typeName: String
            get() = "entity"
    }

    object SpecifiedEntity: MCFPPType(arrayListOf(EntityBase)) {
        override val objectData: CompoundData
            get() = SpecifiedEntityVar.data

        override val typeName: String
            get() = "entity"

        override fun build(value: Any): Var<*> = SpecifiedEntityConcreteVar(value as StringTag)
        override fun build(identifier: String, container: FieldContainer): Var<*> = SpecifiedEntityConcreteVar(StringTag(), identifier)
        override fun build(identifier: String): Var<*> = SpecifiedEntityConcreteVar(StringTag(), identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = SpecifiedEntityConcreteVar(StringTag(), identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = SpecifiedEntityVar(identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = SpecifiedEntityVar(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = SpecifiedEntityVar(identifier)
    }

    object Player : MCFPPConcreteType(arrayListOf(EntityBase)) {
        override val objectData: CompoundData
            get() = PlayerVar.data

        override val typeName: String
            get() = "Player"
        override fun build(identifier: String, container: FieldContainer): Var<*> = PlayerVar(identifier)
        override fun build(identifier: String): Var<*> = PlayerVar(identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = PlayerVar(identifier)
   }

    class Selector(val limit: Int? = null, val types: List<String>? = null) : MCFPPConcreteType(arrayListOf(EntityBase)) {

        override val objectData: CompoundData
            get() = SelectorVar.data

        override val typeName: String
            get() {
                if(limit == null && types == null) return "entity"
                if(limit == null) return "entity[${types!!.joinToString(",")}]"
                if(types == null) return "entity[$limit]"
                return "entity[${limit},${types.joinToString(",")}]"
            }

        override fun build(identifier: String, container: FieldContainer): Var<*> {
            return build(identifier)
        }

        override fun build(identifier: String): Var<*> {
            val qwq = EntitySelector(EntitySelector.Companion.SelectorType.ALL_ENTITIES)
            if(limit != null) qwq.limit(limit)
            if(types != null){
                for (type in types)
                    if(type.startsWith('!')){
                        qwq.type(type.substring(1), true)
                    }else{
                        qwq.type(type, false)
                    }
            }
            return SelectorVar(qwq, identifier)
        }

        override fun build(identifier: String, clazz: Class): Var<*> {
            return build(identifier)
        }

        override fun equals(other: Any?): Boolean {
            if(other == this) return true
            if(other !is Selector) return false
            if(limit != other.limit) return false
            if(types == null && other.types == null) return true
            if(types == null) return false
            if(other.types == null) return false
            return types.size == other.types.size && types.zip(other.types).all { it.first == it.second }
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + (limit ?: 0)
            result = 31 * result + (types?.hashCode() ?: 0)
            return result
        }

        fun canCastTo(other: Selector): Boolean {
            if(this == other) return true
            if(this.limit != null && other.limit == null) return true
            if(this.types != null && other.types == null) return true
            if(this.types != null && other.types != null && this.types.containsAll(other.types)) return true
            return false
        }

        companion object {
            val NormalSelector = Selector()
        }

    }
}