package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete
import top.mcfpp.mni.annotation.NoInstance
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/**
 * 模板类型
 * @see DataTemplate
 */
open class MCFPPDataTemplateType(
    var template: DataTemplate,
    parentType: ArrayList<out MCFPPType>
) : MCFPPType(parentType) {

    override val objectData: CompoundData
        get() = template

    override val typeName: String
        get() = "template(${template.namespace}:${template.identifier})"

    override val simpleName: String
        get() = template.identifier

    override fun tryResolve() {
        if(template is UnsolvedTemplate){
            template = (template as UnsolvedTemplate).resolve()
        }
    }

    override fun defaultValue(): Var<*> {
        val map = HashMap<String, Var<*>>()
        template.field.allVars.map {
            if(!it.nullable){
                map[it.identifier] = it.type.defaultValue()
            }
        }
        return DataTemplateObjectConcrete(template, map, "default")
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, (defaultValue() as DataTemplateObjectConcrete).value, identifier)
        }
    }

    override fun build(identifier: String): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, (defaultValue() as DataTemplateObjectConcrete).value, identifier)
        }
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, (defaultValue() as DataTemplateObjectConcrete).value, identifier)
        }
    }
    @Suppress("UNCHECKED_CAST")
    override fun build(value: Any): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(TempPool.getVarIdentify())
        }else{
            return DataTemplateObjectConcrete(template, value as HashMap<String, Var<*>>, TempPool.getVarIdentify())
        }
    }
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObject(template, identifier)
        }
    }
    override fun buildUnConcrete(identifier: String): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObject(template, identifier)
        }
    }
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObject(template, identifier)
        }
    }

    companion object{
        val regex = Regex("^template\\((.+):(.+)\\)$")
    }

}

