package top.mcfpp.lang

import top.mcfpp.model.CompoundData

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
class JsonText: ChatComponent {

    /**
     * 这个原始json文本中含有的聊天组件
     */
    val components = ArrayList<ChatComponent>()

    /**
     * 向末尾添加一个聊天组件
     */
    fun append(value : MCIntConcrete){
        if(TODO() /*value.isConcrete*/){
            components.add(JsonTextPlain(value.value!!.toString()))
        }else{
            components.add(JsonTextNumber(value))
        }
    }

    override fun toJson(): String {
        val str = StringBuilder()
        for (component in components){
            str.append(component.toJson())
        }
        return str.toString()
    }

    companion object {
        val data = CompoundData("jtext","mcfpp")
    }

}

class JsonTextPlain(val value: String) : ChatComponent {
    override fun toJson(): String {
        return "{\"text\":\"$value\"}"
    }

}

class JsonTextNumber(val value: MCInt) : ChatComponent {
    override fun toJson(): String {
        return "{\"score\":{\"name\":\"${value.name}\",\"objective\":\"${value.sbObject.name}\"}}"
    }
}