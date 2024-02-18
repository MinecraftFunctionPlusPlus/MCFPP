package top.mcfpp.lang.type

import top.mcfpp.lib.Class
import top.mcfpp.lib.Template

class MCFPPTemplateType(
    var structType:Template,
    override var parentType: List<MCFPPType>
) :MCFPPType("template(${structType.namespace}:${structType.identifier})",parentType) {
    init {
        registerType({it.contains(regex)}){
            val matcher = regex.find(it)!!.groupValues
            MCFPPTemplateType(
                Template(matcher[2],MCFPPBaseType.Int,matcher[1]), //TODO: 这里肯定有问题
                listOf() //TODO: 没有输入父类
            )
        }
    }
    companion object{
        val regex = Regex("^template\\((.+):(.+)\\)$")
    }
}