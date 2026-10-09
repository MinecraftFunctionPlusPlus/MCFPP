package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess

import top.mcfpp.core.lang.JsonText
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.ChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.lib.ScoreChatComponent
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.NBTUtil

object NativeTextOperations {
    fun plusText(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        context.publishResult((receiver as JsonText).plus(arguments[0]))
    }

    fun plusString(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        context.publishResult((receiver as JsonText).plus(arguments[0].implicitCast(MCFPPBaseType.JsonText)))
    }

    fun integer(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val value = receiver as MCInt
        publish(context, if (value is MCInt && top.mcfpp.analysis.StorageAccess.snapshot(value) != null) PlainChatComponent(value.value.toString())
            else ScoreChatComponent(value))
    }

    fun nbt(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val value = receiver as NBTBasedData
        publish(context, if (StorageAccess.snapshot(value) != null) PlainChatComponent(Tag.toSNBT(value.value))
            else NBTChatComponent(value, false, null))
    }

    fun string(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val value = receiver as MCString
        publish(context, if (value is MCString && top.mcfpp.analysis.StorageAccess.snapshot(value) != null) PlainChatComponent(value.value.value)
            else NBTChatComponent(value, false, null))
    }

    fun template(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val value = receiver as DataTemplateObject
        publish(context, if (top.mcfpp.analysis.StorageAccess.snapshot(value) != null) PlainChatComponent(Tag.toSNBT(NBTUtil.varToNBT(value)!!))
            else NBTChatComponent(value.toNBTVar(), false, null))
    }

    fun representation(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        publish(context, PlainChatComponent(receiver.toString()))
    }

    private fun publish(context: NativeCallContext, component: ChatComponent) {
        val text = ListChatComponent().apply { append(component) }
        context.publishResult(JsonText(text, "re"))
    }
}
