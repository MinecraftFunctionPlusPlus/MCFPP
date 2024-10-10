package top.mcfpp.type

import net.querz.nbt.tag.*
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.model.Class
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.MCBool
import top.mcfpp.core.lang.bool.MCBoolConcrete
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Any: MCFPPType(parentType = listOf()){

        override val objectData: CompoundData
            get() = MCAny.data

        override val typeName: kotlin.String
            get() = "any"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = CompoundTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCAny(container, identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCAny(identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCAny(clazz, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCAny(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCAny(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCAny(clazz, identifier)

    }

    object Int: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCInt.data

        override val typeName: kotlin.String
            get() = "int"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = IntTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCIntConcrete(container, 0, identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCIntConcrete(0, identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCIntConcrete(clazz, 0, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCInt(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCInt(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCInt(clazz, identifier)


    }
    object String: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCString.data

        override val typeName: kotlin.String
            get() = "string"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = StringTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCStringConcrete(container, StringTag(""), identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCStringConcrete(StringTag(""), identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCStringConcrete(clazz, StringTag(""), identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCString(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCString(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCString(clazz, identifier)
    }

    object Float: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCFloat.data

        override val typeName: kotlin.String
            get() = "float"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = FloatTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCFloatConcrete(container, 0.0f, identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCFloatConcrete(0.0f, identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCFloatConcrete(clazz, 0.0f, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCFloat(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCFloat(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCFloat(clazz, identifier)

    }

    object Double: MCFPPType(parentType = listOf(Any)){

            override val objectData: CompoundData
                get() = MCDouble.data

            override val typeName: kotlin.String
                get() = "double"

            override val nbtType: java.lang.Class<out Tag<*>>
                get() = DoubleTag::class.java

            override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCDoubleConcrete(container, DoubleTag(0.0), identifier)
            override fun build(identifier: kotlin.String): Var<*> = MCDoubleConcrete(DoubleTag(0.0), identifier)
            override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCDoubleConcrete(clazz, DoubleTag(0.0), identifier)
            override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCDouble(container, identifier)
            override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCDouble(identifier)
            override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCDouble(clazz, identifier)
    }

    object Bool: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCBool.data

        override val typeName: kotlin.String
            get() = "bool"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = ByteTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCBoolConcrete(container, false, identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCBoolConcrete(false, identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCBoolConcrete(clazz, false, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCBool(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCBool(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCBool(clazz, identifier)
    }

    object Void: MCFPPType(parentType = listOf()){

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.Void.data

        override val typeName: kotlin.String
            get() = "void"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = top.mcfpp.core.lang.Void
        override fun build(identifier: kotlin.String): Var<*> = top.mcfpp.core.lang.Void
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = top.mcfpp.core.lang.Void
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = top.mcfpp.core.lang.Void
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = top.mcfpp.core.lang.Void
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = top.mcfpp.core.lang.Void
    }

    object JsonText: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.JsonText.data

        override val typeName: kotlin.String
            get() = "text"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = JsonTextConcrete(container, PlainChatComponent(""), identifier)
        override fun build(identifier: kotlin.String): Var<*> = JsonTextConcrete(PlainChatComponent(""), identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = JsonTextConcrete(clazz, PlainChatComponent(""), identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = JsonText(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = JsonText(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = JsonText(clazz, identifier)
    }

    object Range: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = RangeVar.data

        override val typeName: kotlin.String
            get() = "range"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = RangeVarConcrete(container, 0f to 0f, identifier)
        override fun build(identifier: kotlin.String): Var<*> = RangeVarConcrete(0f to 0f, identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = RangeVarConcrete(clazz, 0f to 0f, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = RangeVar(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = RangeVar(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = RangeVar(clazz, identifier)
    }

    object Coordinate3: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = Coordinate3Var.data

        override val typeName: kotlin.String
            get() = "coord3"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = Coordinate3Var(container, identifier)
        override fun build(identifier: kotlin.String): Var<*> = Coordinate3Var(identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = Coordinate3Var(clazz, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = Coordinate3Var(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = Coordinate3Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = Coordinate3Var(clazz, identifier)
    }

    object Coordinate2: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = Coordinate2Var.data

        override val typeName: kotlin.String
            get() = "coord2"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = Coordinate2Var(container, identifier)
        override fun build(identifier: kotlin.String): Var<*> = Coordinate2Var(identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = Coordinate2Var(clazz, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = Coordinate2Var(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = Coordinate2Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = Coordinate2Var(clazz, identifier)
    }

}
