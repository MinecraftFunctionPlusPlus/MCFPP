package top.mcfpp.type

import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.mni.*
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.StringTag

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Object: MCFPPType() {
        override val typeId: TypeId = TypeId.Builtin("object")
        override val typeName: kotlin.String get() = "object"
        override val instanceData by lazy { CompoundData("object", "mcfpp.lang") }
        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = MCObject(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCObject(identifier)
    }

    object Any: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Any")

        override val instanceData by lazy {
            CompoundData("any","mcfpp.lang").apply {
                injectedBy(MCAnyData::class.java)
            }
        }

        override val concreteInstanceData: CompoundData by lazy {
            CompoundData("any","mcfpp.lang").apply {
                injectedBy(MCAnyConcreteData::class.java)
            }
        }

        override val typeName: kotlin.String
            get() = "any"

        override val nbtType: Class<out Tag<*>>
            get() = CompoundTag::class.java

        override fun defaultValue() = null

        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = MCAnyConcrete(value, identifier)
        override fun build(identifier: kotlin.String, container: FieldContainer, value: kotlin.Any?): Var<*> {
            return MCAnyConcrete(value, identifier).apply { this.container = container }
        }
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCAny(identifier)

    }

    object Int: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Int")

        override val instanceData by lazy {
            CompoundData("int","mcfpp").apply {
                this.commonType = Int
                extends(Any.instanceData)
                injectedBy(MCIntData::class.java)
            }
        }

        override val concreteInstanceData get() = instanceData

        override val typeName: kotlin.String
            get() = "int"

        override val nbtType: Class<out Tag<*>>
            get() = IntTag::class.java

        override fun defaultValue() = 0

        override fun build(identifier: kotlin.String, container: FieldContainer, value: kotlin.Any?): Var<*> = MCIntConcrete(container, value as kotlin.Int, identifier)
        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = MCIntConcrete(value as kotlin.Int, identifier)
        override fun build(value: kotlin.Any?): Var<*> = MCIntConcrete(value as kotlin.Int)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCInt(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCInt(identifier)

    }
    object String: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.String")

        override val objectData: CompoundData
            get() = MCString.data

        override val instanceData by lazy {
            CompoundData("string", "mcfpp").apply {
                commonType = String
                extends(Any.instanceData)
                injectedBy(MCStringData::class.java)
            }
        }

        override val concreteInstanceData by lazy {
            CompoundData("string", "mcfpp").apply {
                commonType = String
                extends(Any.instanceData)
                injectedBy(MCStringData::class.java)
            }
        }

        override val typeName: kotlin.String
            get() = "string"

        override val nbtType: Class<out Tag<*>>
            get() = StringTag::class.java

        override fun defaultValueVar(): Var<*> {
            return MCStringConcrete(StringTag(""),"default")
        }

        override fun defaultValue() = StringTag("")

        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = MCStringConcrete(value as StringTag, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCString(identifier)
    }

    object Float: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Float")

        override val instanceData by lazy {
            CompoundData("float","mcfpp").apply {
                this.commonType = Float
                extends(Any.instanceData)
                injectedBy(MCFloatData::class.java)
            }
        }

        override val concreteInstanceData get() = instanceData

        override val typeName: kotlin.String
            get() = "float"

        override val nbtType: Class<out Tag<*>>
            get() = FloatTag::class.java

        override fun defaultValue() = 0.0f

        override fun build(identifier: kotlin.String, container: FieldContainer, value: kotlin.Any?): Var<*> = MCFloatConcrete(container, value as kotlin.Float, identifier)
        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = MCFloatConcrete(value as kotlin.Float, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = MCFloat(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = MCFloat(identifier)

    }

    object Bool: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Bool")

        override val instanceData by lazy {
            CompoundData("bool","mcfpp.lang").apply {
                this.commonType = Bool
                extends(Any.instanceData)
                injectedBy(MCBoolData::class.java)
            }
        }

        override val concreteInstanceData get() = instanceData

        override val typeName: kotlin.String
            get() = "bool"

        override val nbtType: Class<out Tag<*>>
            get() = ByteTag::class.java

        override fun defaultValue() = false

        override fun build(identifier: kotlin.String, container: FieldContainer, value: kotlin.Any?): Var<*> = ScoreBoolConcrete(container, value as Boolean, identifier)
        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = ScoreBoolConcrete(value as Boolean, identifier)
        override fun buildUnConcrete(identifier: kotlin.String, container: FieldContainer): Var<*> = ScoreBool(container, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = ScoreBool(identifier)
    }

    object JsonText: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.JsonText")

        override val instanceData by lazy {
            CompoundData("text","mcfpp.lang").apply {
                commonType = JsonText
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(JsonTextData::class.java)

                addMember(MCInt("color"))
                addMember(ScoreBool("bold"))
                addMember(ScoreBool("italic"))
                addMember(ScoreBool("underlined"))
                addMember(ScoreBool("strikethrough"))
                addMember(ScoreBool("obfuscated"))
                addMember(MCString("insertion"))
            }
        }

        override val concreteInstanceData: CompoundData by lazy {
            CompoundData("text","mcfpp.lang").apply {
                commonType = JsonText
                extends(MCFPPNBTType.NBT.concreteInstanceData)
                injectedBy(JsonTextData::class.java)

                addMember(MCInt("color"))
                addMember(ScoreBool("bold"))
                addMember(ScoreBool("italic"))
                addMember(ScoreBool("underlined"))
                addMember(ScoreBool("strikethrough"))
                addMember(ScoreBool("obfuscated"))
                addMember(MCString("insertion"))

            }
        }

        override val typeName: kotlin.String
            get() = "text"

        override val nbtType: Class<out Tag<*>>
            get() = CompoundTag::class.java

        override fun defaultValue() = PlainChatComponent("")

        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = JsonTextConcrete(value as ChatComponent, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = JsonText(identifier)
    }

    object Range: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Range")

        override val instanceData by lazy {
            CompoundData("range","mcfpp.lang").apply {
                extends(Any.instanceData)
                injectedBy(RangeVarData::class.java)
            }
        }

        override val typeName: kotlin.String
            get() = "range"

        override fun defaultValue() = 0f to 0f

        @Suppress("UNCHECKED_CAST")
        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = RangeVarConcrete(value as Pair<Number?, Number?>, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = RangeVar(identifier)
    }

    object Pos3: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Pos3")

        override val instanceData by lazy {
            CompoundData("pos3", "mcfpp").apply {
                extends(Any.instanceData)
            }
        }

        override val typeName: kotlin.String
            get() = "pos3"

        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = Pos3Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = Pos3Var(identifier)
    }

    object Pos2: MCFPPType(arrayListOf(Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPBaseType.Pos2")

        override val instanceData by lazy {
            CompoundData("pos2", "mcfpp").apply {
                extends(Any.instanceData)
            }
        }

        override val typeName: kotlin.String
            get() = "pos2"

        override fun defaultValueVar(): Var<*> {
            return Pos2Var("default")
        }

        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = Pos2Var(identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = Pos2Var(identifier)
    }

}
