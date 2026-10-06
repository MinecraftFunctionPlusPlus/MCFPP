package top.mcfpp.backend

import top.mcfpp.core.lang.JsonTextConcrete
import top.mcfpp.core.lang.JsonText
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.lib.ScoreChatComponent
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPBaseType

object NativeTextOperations {
    fun plusText(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        context.publishResult((receiver as JsonText).plus(arguments[0]))
    }

    fun plusString(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        context.publishResult((receiver as JsonText).plus(arguments[0].implicitCast(MCFPPBaseType.JsonText)))
    }

    fun integer(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val value = receiver as MCInt
        publish(context, if (value is MCIntConcrete) PlainChatComponent(value.value.toString())
            else ScoreChatComponent(value))
    }

    fun nbt(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        publish(context, NBTChatComponent(receiver as NBTBasedData, false, null))
    }

    fun stringValue(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        publish(context, PlainChatComponent((receiver as MCStringConcrete).value.value))
    }

    fun nbtValue(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        publish(context, PlainChatComponent(Tag.toSNBT((receiver as NBTBasedDataConcrete).value)))
    }

    fun representation(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        publish(context, PlainChatComponent(receiver.toString()))
    }

    private fun publish(context: NativeCallContext, component: ChatComponent) {
        val text = ListChatComponent().apply { append(component) }
        context.publishResult(JsonTextConcrete(text, "re"))
    }
}
