package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor

abstract class MCFPPPrivateType(parentType: ArrayList<MCFPPType> = arrayListOf()) : MCFPPType(parentType) {

    abstract fun buildReturnVar(): Var<*>

    final override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    final override fun build(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    final override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    object CommandReturn: MCFPPPrivateType(parentType = arrayListOf(MCFPPBaseType.Any)){

        override fun buildReturnVar(): Var<*> {
            return top.mcfpp.core.lang.CommandReturn.empty
        }

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.CommandReturn.data

        override val typeName: String
            get() = "CommandReturn"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = CompoundTag::class.java
    }

    object MCFPPObjectVarType: MCFPPPrivateType() {
        override val typeName: String
            get() = "ObjectVar"

        override fun buildReturnVar(): Var<*> {
            TODO("Not yet implemented")
        }
    }

    object MCFPPCoordinateDimension: MCFPPPrivateType(){
        override val typeName: String
            get() = "CoordinateDimension"

        override fun buildReturnVar(): Var<*> {
            TODO("Not yet implemented")
        }
    }


    object Void: MCFPPPrivateType(arrayListOf()){
        override fun buildReturnVar(): Var<*> {
            return top.mcfpp.core.lang.Void
        }

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.Void.data

        override val typeName: String
            get() = "void"

    }

    object Null: MCFPPPrivateType(arrayListOf()){
        override fun buildReturnVar(): Var<*> {
            return top.mcfpp.core.lang.Null
        }

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.Null.data

        override val typeName: String
            get() = "null"

    }

}