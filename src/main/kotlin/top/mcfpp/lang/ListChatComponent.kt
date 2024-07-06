package top.mcfpp.lang

interface ChatComponent {

    /**
     * 返回这个原始json文本的字符串形式
     *
     * @return
     */
    fun toJson(): String
}

/**
 * 一个json原始文本
 *
 */
class ListChatComponent: ChatComponent {

    /**
     * 这个原始json文本中含有的聊天组件
     */
    val components = ArrayList<ChatComponent>()

    override fun toJson(): String {
        val str = StringBuilder("[")
        for (component in components){
            str.append(component.toJson())
            str.append(",")
        }
        str.replace(str.length-1,str.length,"]")
        return str.toString()
    }
}

class PlainChatComponent(val value: String) : ChatComponent {
    override fun toJson(): String {
        return """{"type": "text", "text": "$value"}"""
    }

}

class TranslatableChatComponent(val key: String, val fallback: String? = null, val args: List<ChatComponent>? = null) : ChatComponent {
    override fun toJson(): String {
        val str = StringBuilder("{\"type\": \"translatable\", \"translate\":\"$key\"")
        if(fallback != null){
            str.append(",\"fallback\":\"$fallback\"")
        }
        if(args != null){
            str.append(",\"with\":[")
            for (component in args){
                str.append(component.toJson())
                str.append(",")
            }
            str.replace(str.length-1,str.length,"]")
        }
        str.append("}")
        return str.toString()
    }
}

class ScoreChatComponent(val value: MCInt) : ChatComponent {
    override fun toJson(): String {
        return "{\"type\":\"score\",\"score\":{\"name\":\"${value.name}\",\"objective\":\"${value.sbObject.name}\"}}"
    }
}

class SelectorChatComponent(val selector: String, val separator: ChatComponent? = null) : ChatComponent {
    override fun toJson(): String {
        return if(separator != null){
            "{\"type\":\"selector\",\"selector\":\"$selector\",\"separator\":${separator.toJson()}}"
        }else{
            "{\"type\":\"selector\",\"selector\":\"$selector\"}"
        }
    }
}

class KeybindChatComponent(val key: String) : ChatComponent {
    override fun toJson(): String {
        return "{\"type\":\"keybind\",\"keybind\":\"$key\"}"
    }
}

class NBTChatComponent(val nbt: NBTBasedData<*>, val interpret: Boolean = false, val separator: ChatComponent? = null) : ChatComponent {
    override fun toJson(): String {
        return "{\"type\":\"nbt\",\"nbt\":\"$nbt\",\"interpret\":$interpret}"
    }
}