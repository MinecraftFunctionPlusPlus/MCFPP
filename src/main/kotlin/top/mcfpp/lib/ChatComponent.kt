package top.mcfpp.lib

import net.querz.nbt.tag.IntArrayTag
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.NBTBasedData
import top.mcfpp.core.lang.NBTBasedDataConcrete
import top.mcfpp.core.lang.resource.EntityTypeConcrete
import top.mcfpp.util.LogProcessor

abstract class ChatComponent {

    val styles : ArrayList<ChatComponentStyle> = ArrayList()

    /**
     * 返回这个原始json文本的字符串形式
     *
     * @return
     */
    abstract fun toCommandPart(): Command

    fun styleToString(): Command{
        val str = Command("")
        for (style in styles){
            str.build(style.toCommandPart(), false)
            if(style != styles.last()) str.build(", ", false)
        }
        return str
    }
}

/**
 * 一个json原始文本
 *
 */
class ListChatComponent: ChatComponent() {

    /**
     * 这个原始json文本中含有的聊天组件
     */
    val components = ArrayList<ChatComponent>()

    override fun toCommandPart(): Command {
        val str = Command("[")
        for (component in components){
            str.build(component.toCommandPart(), false)
            if(component != components.last()) str.build(",", false)
        }
        str.build("]", false)
        return str
    }
}

class PlainChatComponent(val value: String) : ChatComponent() {
    override fun toCommandPart(): Command {
        return Command("""{"type": "text", "text": "$value", ${styleToString()}""")
    }

}

class TranslatableChatComponent(val key: String, val fallback: String? = null, val args: List<ChatComponent>? = null) : ChatComponent() {
    override fun toCommandPart(): Command {
        val str = Command.build("{\"type\": \"translatable\", \"translate\":\"$key\"")
        if(fallback != null){
            str.build(",\"fallback\":\"$fallback\"", false)
        }
        if(args != null){
            str.build(",\"with\":[", false)
            for (component in args){
                str.build(component.toCommandPart(), false)
                if(component != args.last()) str.build(",", false)
            }
            str.build("]", false)
        }
        str.build(", ${styleToString()}", false)
        return str
    }
}

class ScoreChatComponent(val value: MCInt) : ChatComponent() {
    override fun toCommandPart(): Command {
        return Command("{\"type\":\"score\",\"score\":{\"name\":\"${value.name}\",\"objective\":\"${value.sbObject.name}\"}, ${styleToString()}}")
    }
}

class SelectorChatComponent(val selector: String, val separator: ChatComponent? = null) : ChatComponent() {
    override fun toCommandPart(): Command {
        return Command(if(separator != null){
            "{\"type\":\"selector\",\"selector\":\"$selector\",\"separator\":${separator.toCommandPart()}, ${styleToString()}"
        }else{
            "{\"type\":\"selector\",\"selector\":\"$selector\", ${styleToString()}"
        })
    }
}

class KeybindChatComponent(val key: String) : ChatComponent() {
    override fun toCommandPart(): Command {
        return Command("{\"type\":\"keybind\",\"keybind\":\"$key\", ${styleToString()}")
    }
}

class NBTChatComponent(val nbt: NBTBasedData, val interpret: Boolean = false, val separator: ChatComponent? = null) : ChatComponent() {
    override fun toCommandPart(): Command {
        return Command("{\"type\":\"nbt\",\"nbt\":\"").build(nbt.nbtPath.toCommandPart(), false).build("\",\"interpret\":$interpret, ${styleToString()}}", false)
    }
}

interface ChatComponentStyle{
    fun toCommandPart(): Command
}

class ColorStyle(val hex: Int): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"color\": \"#$hex\"")
    }

    companion object{
        val BLACK = ColorStyle(0x000000)
        val DARK_BLUE = ColorStyle(0x0000AA)
        val DARK_GREEN = ColorStyle(0x00AA00)
        val DARK_AQUA = ColorStyle(0x00AAAA)
        val DARK_RED = ColorStyle(0xAA0000)
        val DARK_PURPLE = ColorStyle(0xAA00AA)
        val GOLD = ColorStyle(0xFFAA00)
        val GRAY = ColorStyle(0xAAAAAA)
        val DARK_GRAY = ColorStyle(0x555555)
        val BLUE = ColorStyle(0x5555FF)
        val GREEN = ColorStyle(0x55FF55)
        val AQUA = ColorStyle(0x55FFFF)
        val RED = ColorStyle(0xFF5555)
        val LIGHT_PURPLE = ColorStyle(0xFF55FF)
        val YELLOW = ColorStyle(0xFFFF55)
        val WHITE = ColorStyle(0xFFFFFF)
    }
}

class BoldStyle(val bold: Boolean): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"bold\": $bold")
    }
}

class ItalicStyle(val italic: Boolean): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"italic\": $italic")
    }
}

class UnderlineStyle(val underline: Boolean): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"underline\": $underline")
    }
}

class StrikethroughStyle(val strikethrough: Boolean): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"strikethrough\": $strikethrough")
    }
}

class ObfuscatedStyle(val obfuscated: Boolean): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"obfuscated\": $obfuscated")
    }
}

class InsertionStyle(val string: String): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"insertion\": $string")
    }
}

class ClickEventStyle(val action: ClickEventAction, val value: String): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"clickEvent\": {\"action\": \"${action.name.lowercase()}\", \"value\": \"$value\"}")
    }

    enum class ClickEventAction{
        OPEN_URL,
        OPEN_FILE,
        RUN_COMMAND,
        SUGGEST_COMMAND,
        CHANGE_PAGE
    }
}

class HoverEventShowTextStyle(val content: ChatComponent): ChatComponentStyle{
    override fun toCommandPart(): Command {
        return Command("\"hoverEvent\": {\"action\": \"show_text\", \"value\": \"$content\"}")
    }
}

//class HoverEventShowItemStyle(val item: String): ChatComponentStyle{
//    override fun toJson(): Command {
//        return Command("\"hoverEvent\": {\"action\": \"show_item\", \"value\": \"$item\"}")
//    }
//}

class HoverEventShowEntityStyle(val name: ChatComponent?, val type: EntityTypeConcrete, val uuid: NBTBasedData): ChatComponentStyle{
    override fun toCommandPart(): Command {
        val c = Command("\"hoverEvent\":{\"action\": \"show_entity\", \"contents\": {")
        if(name != null){
            c.build("\"name\": \"$name\", ", false)
        }
        c.build("\"type\": \"${type.value}\"", false)
        if(uuid is NBTBasedDataConcrete){
            if(uuid.value is IntArrayTag && (uuid.value as IntArrayTag).value.size == 4){
                c.build("\"id\": \"${uuid.value}\"", false)
            }else{
                LogProcessor.error("Invalid UUID: $uuid")
            }
        }else{
            if(uuid.nbtType == NBTBasedData.Companion.NBTTypeWithTag.INT_ARRAY){
                c.build("\"id\": ", false).buildMacro(uuid.toJson().identifier, false)
            }
            c.build("\"id\": ").buildMacro(uuid.identifier, false)
        }
        c.build("}}")
        return c
    }
}