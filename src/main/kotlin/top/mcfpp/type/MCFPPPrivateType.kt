package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.CompoundData
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

    object StaticMemberViewType: MCFPPPrivateType() {
        override val isValueType: Boolean get() = false

        override val typeId: TypeId = TypeId.Builtin("MCFPPPrivateType.StaticMemberViewType")
        override val typeName: String
            get() = "StaticMemberView"

        override fun buildReturnVar(): Var<*> {
            TODO("Not yet implemented")
        }
    }

    object MCFPPCoordinateDimension: MCFPPPrivateType(){

        override val hasRuntimeRepresentation: Boolean get() = false

        override val typeId: TypeId = TypeId.Builtin("MCFPPPrivateType.MCFPPCoordinateDimension")
        override val typeName: String
            get() = "CoordinateDimension"

        override fun buildReturnVar(): Var<*> {
            TODO("Not yet implemented")
        }
    }


    object Void: MCFPPPrivateType(arrayListOf()){
        override val isValueType: Boolean get() = false

        override val typeId: TypeId = TypeId.Builtin("MCFPPPrivateType.Void")
        override fun buildReturnVar(): Var<*> {
            return top.mcfpp.core.lang.Void
        }

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.Void.data

        override val typeName: String
            get() = "void"

    }

    object Null: MCFPPPrivateType(arrayListOf()){

        override val typeId: TypeId = TypeId.Builtin("MCFPPPrivateType.Null")
        override fun buildReturnVar(): Var<*> {
            return top.mcfpp.core.lang.Null
        }

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.Null.data

        override val typeName: String
            get() = "null"

    }

    object Wildcard: MCFPPPrivateType(arrayListOf()){
        override val isValueType: Boolean get() = false

        override val typeId: TypeId = TypeId.Builtin("MCFPPPrivateType.Wildcard")
        override val typeName: String
            get() = "*"

        override fun buildReturnVar(): Var<*> {
            throw UnsupportedOperationException("Cannot build return var for wildcard type")
        }

        override val objectData: CompoundData
            get() = throw UnsupportedOperationException("Cannot build return var for wildcard type")

    }

}
