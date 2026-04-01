package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor

abstract class MCFPPPrivateType(parentType: ArrayList<MCFPPType> = arrayListOf()) : MCFPPType(parentType) {

    abstract fun buildReturnVar(): Var<*>

    final override fun build(identifier: String, value: Any?): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    object CommandReturn: MCFPPPrivateType(parentType = arrayListOf(MCFPPBaseType.Any)){

        override fun buildReturnVar(): Var<*> {
            return top.mcfpp.core.lang.CommandReturn.empty
        }

        override val instanceData by lazy {
            CompoundData("CommandReturn","mcfpp").apply {
                extends(MCFPPBaseType.Any.instanceData)
            }
        }

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

    object Wildcard: MCFPPPrivateType(arrayListOf()){
        override val typeName: String
            get() = "*"

        override fun buildReturnVar(): Var<*> {
            throw UnsupportedOperationException("Cannot build return var for wildcard type")
        }

        override val objectData: CompoundData
            get() = throw UnsupportedOperationException("Cannot build return var for wildcard type")

        override fun isSubOf(parentType: MCFPPType): Boolean {
            return true
        }
    }

}