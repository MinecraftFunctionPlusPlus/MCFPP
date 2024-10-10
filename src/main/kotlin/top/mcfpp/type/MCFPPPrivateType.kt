package top.mcfpp.type

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.Tag
import top.mcfpp.core.lang.CommandReturn
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

open class MCFPPPrivateType(
    override val objectData: CompoundData = CompoundData("unknown", "mcfpp"),
    override var parentType: List<MCFPPType> = listOf()
) : MCFPPType(objectData, parentType) {

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

    object CommandReturn: MCFPPPrivateType(parentType = listOf(MCFPPBaseType.Any)){

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.CommandReturn.data

        override val typeName: kotlin.String
            get() = "CommandReturn"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = CompoundTag::class.java
    }

    object MCFPPObjectVarType: MCFPPPrivateType() {
        override val typeName: String
            get() = "ObjectVar"
    }

    object MCFPPCoordinateDimension: MCFPPPrivateType(){
        override val typeName: String
            get() = "CoordinateDimension"
    }
}