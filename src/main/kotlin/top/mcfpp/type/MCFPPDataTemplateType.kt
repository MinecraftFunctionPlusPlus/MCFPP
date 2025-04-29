package top.mcfpp.type

import top.mcfpp.core.lang.*
import top.mcfpp.mni.annotation.NoInstance
import top.mcfpp.model.*
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.compound.Class
import top.mcfpp.nbt.tags.CompoundTag
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

    override fun tryResolve() {
        if(template is UnsolvedTemplate){
            template = (template as UnsolvedTemplate).resolve()
        }
    }

    override fun defaultValue(): CompoundTag {
        val tag = CompoundTag()
        for (member in template.field.allVars){
            if(member.nullable || member is ConcreteVar<*,*>) continue
            tag.put(member.identifier, member.type.defaultValue())
        }
        return tag
    }

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
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, CompoundTag(), identifier)
        }
    }

    override fun build(identifier: String): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, CompoundTag(), identifier)
        }
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObjectConcrete(template, CompoundTag(), identifier)
        }
    }
    override fun build(value: Any): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(TempPool.getVarIdentify())
        }else{
            return DataTemplateObjectConcrete(template, value as CompoundTag, TempPool.getVarIdentify())
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