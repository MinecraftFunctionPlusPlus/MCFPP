package top.mcfpp.type

import net.querz.nbt.tag.*
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.MCDouble
import top.mcfpp.core.lang.nbt.MCDoubleConcrete
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.TempPool

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Any: MCFPPType(arrayListOf()){

        override val objectData: CompoundData
            get() = MCAny.data

        override val typeName: kotlin.String
            get() = "any"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = CompoundTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCAny(identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCAny(identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCAny(identifier)
        override fun build(value: kotlin.Any): Var<*> = MCAny(TempPool.getVarIdentify())
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCAny(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCAny(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCAny(identifier)

    }

    object Int: MCFPPType(arrayListOf(Any)){

        override val objectData: CompoundData
            get() = MCInt.data

        override val typeName: kotlin.String
            get() = "int"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = IntTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCIntConcrete(container, 0, identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCIntConcrete(0, identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCIntConcrete(clazz, 0, identifier)
        override fun build(value: kotlin.Any): Var<*> = MCIntConcrete(value as kotlin.Int)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCInt(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCInt(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCInt(clazz, identifier)


    }
    object String: MCFPPType(arrayListOf(Any)){

        override val objectData: CompoundData
            get() = MCString.data

        override val typeName: kotlin.String
            get() = "string"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = StringTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCStringConcrete(StringTag(""), identifier)
        override fun build(identifier: kotlin.String): Var<*> = MCStringConcrete(StringTag(""), identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCStringConcrete(StringTag(""), identifier)
        override fun build(value: kotlin.Any): Var<*> = MCStringConcrete(value as StringTag)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCString(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCString(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCString(identifier)
    }

    object Float: MCFPPType(arrayListOf(Any)){

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

    object Double: MCFPPType(arrayListOf(Any)){

            override val objectData: CompoundData
                get() = MCDouble.data

            override val typeName: kotlin.String
                get() = "double"

            override val nbtType: java.lang.Class<out Tag<*>>
                get() = DoubleTag::class.java

            override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = MCDoubleConcrete(container, DoubleTag(0.0), identifier)
            override fun build(identifier: kotlin.String): Var<*> = MCDoubleConcrete(DoubleTag(0.0), identifier)
            override fun build(identifier: kotlin.String, clazz: Class): Var<*> = MCDoubleConcrete(clazz, DoubleTag(0.0), identifier)
            override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCDouble(identifier)
            override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCDouble(identifier)
            override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = MCDouble(identifier)
    }

    object Bool: MCFPPType(arrayListOf(Any)){

        override val objectData: CompoundData
            get() = ScoreBool.data

        override val typeName: kotlin.String
            get() = "bool"

        override val nbtType: java.lang.Class<out Tag<*>>
            get() = ByteTag::class.java

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = ScoreBoolConcrete(container, false, identifier)
        override fun build(identifier: kotlin.String): Var<*> = ScoreBoolConcrete(false, identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = ScoreBoolConcrete(clazz, false, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = ScoreBool(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = ScoreBool(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = ScoreBool(clazz, identifier)
    }

    object Void: MCFPPType(arrayListOf()){

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

    object JsonText: MCFPPType(arrayListOf(MCFPPNBTType.NBT)){

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.JsonText.data

        override val typeName: kotlin.String
            get() = "text"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = JsonTextConcrete(PlainChatComponent(""), identifier)
        override fun build(identifier: kotlin.String): Var<*> = JsonTextConcrete(PlainChatComponent(""), identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = JsonTextConcrete(PlainChatComponent(""), identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = JsonText(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = JsonText(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = JsonText(identifier)
    }

    object Range: MCFPPType(arrayListOf(Any)){

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

    object Pos3: MCFPPType(arrayListOf(Any)){

        override val objectData: CompoundData
            get() = Pos3Var.data

        override val typeName: kotlin.String
            get() = "pos3"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = Pos3Var(identifier)
        override fun build(identifier: kotlin.String): Var<*> = Pos3Var(identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = Pos3Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = Pos3Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = Pos3Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = Pos3Var(identifier)
    }

    object Pos2: MCFPPType(arrayListOf(Any)){

        override val objectData: CompoundData
            get() = Pos2Var.data

        override val typeName: kotlin.String
            get() = "pos2"

        override fun build(identifier: kotlin.String, container: FieldContainer): Var<*> = Pos2Var(identifier)
        override fun build(identifier: kotlin.String): Var<*> = Pos2Var(identifier)
        override fun build(identifier: kotlin.String, clazz: Class): Var<*> = Pos2Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = Pos2Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = Pos2Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String, clazz: Class): Var<*> = Pos2Var(identifier)
    }

}
