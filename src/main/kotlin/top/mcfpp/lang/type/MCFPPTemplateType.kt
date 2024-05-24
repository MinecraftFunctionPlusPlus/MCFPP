package top.mcfpp.lang.type

import top.mcfpp.model.Template
import top.mcfpp.util.LazyWrapper

/**
 * 模板类型
 * @see Template
 */
class MCFPPTemplateType(
    var template: Template,
    override var parentType: List<MCFPPType>
) : MCFPPType(parentType, template) {

    override val typeName: String
        get() = "template(${template.namespace}:${template.identifier})"

    init {
        registerType({ it.contains(regex) }) {
            val matcher = regex.find(it)!!.groupValues
            MCFPPTemplateType(
                Template(matcher[2], LazyWrapper(MCFPPBaseType.Int), matcher[1]), //TODO: 这里肯定有问题
                parentType
            )
        }
    }

    companion object {
        val regex = Regex("^template\\((.+):(.+)\\)$")
    }


}