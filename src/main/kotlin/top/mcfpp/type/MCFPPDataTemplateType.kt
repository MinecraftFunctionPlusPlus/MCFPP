package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete
import top.mcfpp.mni.annotation.NoInstance
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.util.LogProcessor

/**
 * 模板类型
 * @see DataTemplate
 */
open class MCFPPDataTemplateType(
    var template: DataTemplate,
    parentType: ArrayList<out MCFPPType>
) : MCFPPType(parentType) {

    override val objectData: CompoundData
        get() = template.companionObject?: CompoundData(template.identifier, template.namespaceID)

    override val typeName: String
        get() = "template(${template.namespace}:${template.identifier})"

    override val simpleName: String
        get() = template.identifier

    override fun tryResolve() {
        if(template is UnsolvedTemplate){
            template = (template as UnsolvedTemplate).resolve()
        }
    }

    override fun defaultValue(): Any? {
        val map = HashMap<String, Var<*>>()
        template.scope.allVars.map {
            if(!it.nullable){
                val v = it.type.defaultValueVar()
                v.identifier = it.identifier
                map[it.identifier] = v
            }
        }
        return map
    }

    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, value as HashMap<String, Var<*>>, identifier)
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

    companion object{
        val regex = Regex("^template\\((.+):(.+)\\)$")
    }

}

