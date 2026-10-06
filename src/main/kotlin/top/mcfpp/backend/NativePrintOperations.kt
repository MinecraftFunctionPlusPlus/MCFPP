package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete
import top.mcfpp.lib.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil

object NativePrintOperations {
    fun print(context: NativeCallContext) = context.withArguments { arguments ->
        val argument = arguments[0]
        val value = StorageAccess.read(if (argument is PropertyVar) argument.get() else argument)
        if (!StorageAccess.hasRuntimeRepresentation(value)) {
            LogProcessor.error("Compiler-only values cannot be printed at runtime")
            return@withArguments
        }
        val payload = when (value) {
            is JsonTextConcrete -> value.value.toCommandPart()
            is JsonText -> value.toCommandPart()
            is MCIntConcrete -> PlainChatComponent(value.value.toString()).toCommandPart()
            is MCInt -> ScoreChatComponent(value).toCommandPart()
            is ScoreBoolConcrete -> PlainChatComponent(if (value.value) "1" else "0").toCommandPart()
            is ScoreBool -> ScoreChatComponent(value.asIntVar()).toCommandPart()
            is MCStringConcrete -> PlainChatComponent(value.value.value).toCommandPart()
            is MCString -> NBTChatComponent(value, false, null).toCommandPart()
            is DataTemplateObjectConcrete -> PlainChatComponent(Tag.toSNBT(NBTUtil.varToNBT(value)!!)).toCommandPart()
            is DataTemplateObject -> NBTChatComponent(value.toNBTVar(), false, null).toCommandPart()
            is MCAny -> {
                val encoded = NBTBasedData().apply {
                    isTemp = true
                    nbtPath = NBTPath.getNormalStackPath(this)
                }
                StorageAccess.encodeTo(encoded.nbtPath, value)
                NBTChatComponent(encoded, false, null).toCommandPart()
            }
            is NBTBasedData -> if (ValueSnapshot.of(value) != null)
                PlainChatComponent(Tag.toSNBT(NBTUtil.varToNBT(value)!!)).toCommandPart()
            else NBTChatComponent(value, false, null).toCommandPart()
            else -> {
                LogProcessor.error("Cannot print ${value.type.typeName} at runtime")
                return@withArguments
            }
        }
        val command = Command.buildAll("tellraw @a", payload)
        Function.addCommands(command.buildMacroFunction())
    }
}
