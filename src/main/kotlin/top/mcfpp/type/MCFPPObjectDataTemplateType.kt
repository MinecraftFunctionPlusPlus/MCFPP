package top.mcfpp.type

import top.mcfpp.model.Class
import top.mcfpp.model.DataTemplate
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.ObjectDataTemplate
import top.mcfpp.util.LogProcessor
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var


/**
 * 模板类型
 * @see DataTemplate
 */
class MCFPPObjectDataTemplateType(
    template: ObjectDataTemplate,
    parentType: List<MCFPPType>
) : MCFPPDataTemplateType(template, parentType) {

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

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName}")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var from type: $typeName")
        return UnknownVar(identifier)
    }
}
