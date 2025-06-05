package top.mcfpp.type

import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.mni.MCAnyConcreteData
import top.mcfpp.mni.MCAnyData
import top.mcfpp.mni.MCIntConcreteData
import top.mcfpp.mni.MCIntData
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
    object Any: MCFPPType(arrayListOf()){

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

    object Int: MCFPPType(arrayListOf(Any)){

        override val instanceData by lazy {
                CompoundData("int","mcfpp").apply {
                    this.commonType = Int
                    extends(Any.instanceData)
                    injectedBy(MCIntData::class.java)
                }
            }

        override val concreteInstanceData by lazy {
            CompoundData("int","mcfpp").apply {
                this.commonType = Int
                extends(Any.concreteInstanceData)
                injectedBy(MCIntConcreteData::class.java)
            }
        }

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
    object String: MCFPPType(arrayListOf(Any)){

        override val objectData: CompoundData
            get() = MCString.data

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

    object Float: MCFPPType(arrayListOf(Any)){

        override val instanceData by lazy {
            CompoundData("float","mcfpp")
        }

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

    object Bool: MCFPPType(arrayListOf(Any)){

        override val instanceData by lazy {
            CompoundData("bool","mcfpp.lang").apply {
                extends(Any.instanceData)
            }
        }

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

    object JsonText: MCFPPType(arrayListOf(MCFPPNBTType.NBT)){

        override val instanceData by lazy {
            CompoundData("JsonText","mcfpp.lang").apply {
                extends(MCFPPNBTType.NBT.instanceData)

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
            CompoundData("JsonTextConcrete","mcfpp.lang").apply {
                extends(instanceData)
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

    object Range: MCFPPType(arrayListOf(Any)){

        override val instanceData by lazy {
            CompoundData("range","mcfpp.lang").apply {
                extends(Any.instanceData)
            }
        }

        override val typeName: kotlin.String
            get() = "range"

        override fun defaultValue() = 0f to 0f

        @Suppress("UNCHECKED_CAST")
        override fun build(identifier: kotlin.String, value: kotlin.Any?): Var<*> = RangeVarConcrete(value as Pair<kotlin.Float?, kotlin.Float?>, identifier)
        override fun buildUnConcrete(identifier: kotlin.String): Var<*> = RangeVar(identifier)
    }

    object Pos3: MCFPPType(arrayListOf(Any)){

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

    object Pos2: MCFPPType(arrayListOf(Any)){

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
