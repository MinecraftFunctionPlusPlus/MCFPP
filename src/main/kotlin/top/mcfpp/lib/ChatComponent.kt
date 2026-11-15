package top.mcfpp.lib

import com.alibaba.fastjson2.JSON
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.util.LogProcessor

abstract class ChatComponent {

    val styles : ArrayList<ChatComponentStyle> = ArrayList()

    /**
     * 返回这个原始json文本的字符串形式
     *
     * @return
     */
    abstract fun toCommandPart(): Command

    protected fun component(vararg fields: Pair<String, Command>): Command {
        val result = Command("{")
        fields.forEachIndexed { index, (key, value) ->
            if (index > 0) result.build(",", false)
            result.build(JSON.toJSONString(key) + ":", false).build(value, false)
        }
        if (styles.isNotEmpty()) result.build(",", false).build(styleToString(), false)
        return result.build("}", false)
    }

    fun styleToString(): Command{
        val str = Command()
        for ((index, style) in styles.withIndex()){
            str.build(style.toCommandPart(), false)
            if(index < styles.lastIndex) str.build(", ", false)
        }
        return str
    }

    fun toListComponent(): ListChatComponent{
        val list = ListChatComponent()
        list.components.add(this)
        return list
    }
}

class EncodedChatComponent(private val snbt: String) : ChatComponent() {
    override fun toCommandPart(): Command = if (styles.isEmpty()) Command(snbt)
        else component("text" to Command("\"\""), "extra" to Command("[$snbt]"))
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
        for ((index, component) in components.withIndex()){
            str.build(component.toCommandPart(), false)
            if(index < components.lastIndex) str.build(",", false)
        }
        str.build("]", false)
        return if (styles.isEmpty()) str else component("text" to Command("\"\""), "extra" to str)
    }

    fun append(c: ChatComponent){
        if(components.isEmpty()){
            components.add(c)
        }else if(c is PlainChatComponent && components.last() is PlainChatComponent &&
            c.styles.isEmpty() && components.last().styles.isEmpty()){
            (components.last() as PlainChatComponent).value += c.value
        }else{
            components.add(c)
        }
    }

    fun append(c: List<ChatComponent>){
        for (component in c){
            append(component)
        }
    }
}

class PlainChatComponent(var value: String) : ChatComponent() {
    override fun toCommandPart(): Command = component("type" to Command("\"text\""), "text" to Command(JSON.toJSONString(value)))

}

class TranslatableChatComponent(val key: String, val fallback: String? = null, val args: List<ChatComponent>? = null) : ChatComponent() {
    override fun toCommandPart(): Command {
        val fields = arrayListOf("type" to Command("\"translatable\""), "translate" to Command(JSON.toJSONString(key)))
        fallback?.let { fields += "fallback" to Command(JSON.toJSONString(it)) }
        args?.let { fields += "with" to ListChatComponent().apply { components += it }.toCommandPart() }
        return component(*fields.toTypedArray())
    }
}

class ScoreChatComponent(val value: MCInt) : ChatComponent() {

    override fun toCommandPart(): Command {
        return component("type" to Command("\"score\""), "score" to Command(JSON.toJSONString(
            mapOf("name" to value.name, "objective" to value.sbObject.name))))
    }
}

class SelectorChatComponent(val selector: String, val separator: ChatComponent? = null) : ChatComponent() {
    override fun toCommandPart(): Command {
        val fields = arrayListOf("type" to Command("\"selector\""), "selector" to Command(JSON.toJSONString(selector)))
        separator?.let { fields += "separator" to it.toCommandPart() }
        return component(*fields.toTypedArray())
    }
}

class KeybindChatComponent(val key: String) : ChatComponent() {
    override fun toCommandPart(): Command = component("type" to Command("\"keybind\""), "keybind" to Command(JSON.toJSONString(key)))
}

class NBTChatComponent(val nbt: NBTBasedData, val interpret: Boolean = false, val separator: ChatComponent? = null) : ChatComponent() {
    override fun toCommandPart(): Command {
        val c = Command("{\"type\":\"nbt\",").build(nbt.nbtPath.toChatComponentPart(), false).build(",\"interpret\":$interpret", false)
        separator?.let { c.build(",\"separator\":", false).build(it.toCommandPart(), false) }
        if (styles.isNotEmpty()) c.build(",", false).build(styleToString(), false)
        return c.build("}", false)
    }
}

interface ChatComponentStyle{
    fun toCommandPart(): Command
}

class ColorStyle(val hex: Int): ChatComponentStyle{
    override fun toCommandPart(): Command {
        require(hex in 0..0xffffff) { "Text color must fit #RRGGBB" }
        return Command("\"color\": \"#${hex.toString(16).padStart(6, '0')}\"")
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
        return Command("\"underlined\": $underline")
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
        return Command("\"insertion\": ${JSON.toJSONString(string)}")
    }
}

class ClickEventStyle(val action: ClickEventAction, val value: String): ChatComponentStyle{
    override fun toCommandPart(): Command {
        val modern = top.mcfpp.backend.StandardTextOperations.modernEvents
        val field = if (!modern) "value" else when (action) {
            ClickEventAction.OPEN_URL -> "url"
            ClickEventAction.RUN_COMMAND, ClickEventAction.SUGGEST_COMMAND -> "command"
            ClickEventAction.CHANGE_PAGE -> "page"
            else -> "value"
        }
        val payload: Any = if (modern && action == ClickEventAction.CHANGE_PAGE) value.toInt().also { require(it > 0) } else value
        return Command(JSON.toJSONString(if (modern) "click_event" else "clickEvent") + ":" +
            JSON.toJSONString(mapOf("action" to action.name.lowercase(), field to payload)))
    }

    enum class ClickEventAction{
        OPEN_URL,
        OPEN_FILE,
        RUN_COMMAND,
        SUGGEST_COMMAND,
            CHANGE_PAGE,
        COPY_TO_CLIPBOARD
    }
}

class HoverEventShowTextStyle(val content: ChatComponent): ChatComponentStyle{
    override fun toCommandPart(): Command {
        val modern = top.mcfpp.backend.StandardTextOperations.modernEvents
        val legacyValue = top.mcfpp.Project.config.version.let { it.startsWith("1.13") || it.startsWith("1.14") || it.startsWith("1.15") }
        val field = if (modern || legacyValue) "value" else "contents"
        return Command("\"${if (modern) "hover_event" else "hoverEvent"}\":{\"action\":\"show_text\",\"$field\":")
            .build(content.toCommandPart(), false).build("}", false)
    }
}

//class HoverEventShowItemStyle(val item: String): ChatComponentStyle{
//    override fun toJson(): Command {
//        return Command("\"hoverEvent\": {\"action\": \"show_item\", \"value\": \"$item\"}")
//    }
//}

class HoverEventShowEntityStyle(val name: ChatComponent?, val type: String, val uuid: NBTBasedData): ChatComponentStyle{
    override fun toCommandPart(): Command {
        val words = (top.mcfpp.analysis.StorageAccess.constantEncoding(uuid) as? IntArrayTag)?.value
        if (words == null || words.size != 4) {
            LogProcessor.error("Hover entity serialization requires a compile-time four-int UUID")
            return Command("\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"\"}")
        }
        val id = java.util.UUID((words[0].toLong() shl 32) or (words[1].toLong() and 0xffffffffL),
            (words[2].toLong() shl 32) or (words[3].toLong() and 0xffffffffL)).toString()
        val modern = top.mcfpp.backend.StandardTextOperations.modernEvents
        val c = Command("\"${if (modern) "hover_event" else "hoverEvent"}\":{\"action\":\"show_entity\",")
        if (!modern) c.build("\"contents\":{", false)
        c.build(JSON.toJSONString(if (modern) "id" else "type") + ":" + JSON.toJSONString(type), false)
            .build("," + JSON.toJSONString(if (modern) "uuid" else "id") + ":" + JSON.toJSONString(id), false)
        name?.let { c.build(",\"name\":", false).build(it.toCommandPart(), false) }
        return c.build(if (modern) "}" else "}}", false)
    }
}
