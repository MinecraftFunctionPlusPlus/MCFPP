package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.*
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.UnsolvedObjectTemplate
import top.mcfpp.model.compound.Class
import top.mcfpp.util.LogProcessor


/**
 * 模板类型
 * @see DataTemplate
 */
class MCFPPObjectDataTemplateType(
    template: ObjectDataTemplate,
    parentType: ArrayList<out MCFPPType>
) : MCFPPDataTemplateType(template, parentType) {

    override val typeName: String
        get() = "template(${template.namespace}:${template.identifier})"

    override fun tryResolve() {
        if(template is UnsolvedObjectTemplate){
            template = (template as UnsolvedObjectTemplate).resolve()
        }
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
