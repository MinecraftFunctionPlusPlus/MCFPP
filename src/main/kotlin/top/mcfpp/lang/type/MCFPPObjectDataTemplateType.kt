package top.mcfpp.lang.type

import top.mcfpp.model.DataTemplate
import top.mcfpp.model.ObjectDataTemplate


/**
 * 模板类型
 * @see DataTemplate
 */
class MCFPPObjectDataTemplateType(
    template: ObjectDataTemplate,
    parentType: List<MCFPPType>
) :MCFPPDataTemplateType(template, parentType) {

    override val typeName: String
        get() = "template(${template.namespace}:${template.identifier})"

    init {
        //registerType({it.contains(regex)}){
        //    val matcher = regex.find(it)!!.groupValues
        //    MCFPPTemplateType(
        //        Template(matcher[2], LazyWrapper(MCFPPBaseType.Int),matcher[1]), //TODO: 这里肯定有问题
        //        parentType
        //    )
        //}
    }
}
