package top.mcfpp.lang.type

import top.mcfpp.lib.Class
import top.mcfpp.lib.GlobalField

/**
 * 用于标识由mcfpp class定义出来的类
 * 这里还得找出父类是哪些
 */
class MCFPPClassType(
    var cls:Class,
    override var parentType: List<MCFPPType>
):MCFPPType("class(${cls.namespace}:${cls.identifier})",parentType) {
    init {
        registerType({it.contains(regex)}){
            val matcher = regex.find(it)!!.groupValues
            val clazz = GlobalField.getClass(matcher[1],matcher[2])
            if(clazz!=null)
                MCFPPClassType(
                    clazz,
                    listOf() //TODO: 没有输入父类，这个父类应该要和那个CompoundData那个合并！
                )
            else
                MCFPPBaseType.Any //TODO: 找不到该类型
        }
    }
    companion object{
        val regex = Regex("^class\\((.+):(.+)\\)$")
    }
}