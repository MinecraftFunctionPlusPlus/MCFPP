package top.mcfpp.type

import net.querz.nbt.tag.CompoundTag
import top.mcfpp.model.*
import top.mcfpp.`var`.lang.ClassPointer
import top.mcfpp.`var`.lang.DataTemplateObject
import top.mcfpp.`var`.lang.DataTemplateObjectConcrete
import top.mcfpp.`var`.lang.Var

/**
 * 模板类型
 * @see DataTemplate
 */
open class MCFPPDataTemplateType(
    var template: DataTemplate,
    override var parentType: List<MCFPPType>
) : MCFPPType(template, parentType) {

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

    override fun build(identifier: String, container: FieldContainer): Var<*> = DataTemplateObjectConcrete(template, CompoundTag(), identifier)
    override fun build(identifier: String): Var<*> = DataTemplateObjectConcrete(template, CompoundTag(), identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = DataTemplateObjectConcrete(template, CompoundTag(), identifier)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = DataTemplateObject(template, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = DataTemplateObject(template, identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = DataTemplateObject(template, identifier)

    companion object{
        val regex = Regex("^template\\((.+):(.+)\\)$")

        val DataObject = MCFPPDataTemplateType(DataTemplate.baseDataTemplate, listOf(MCFPPNBTType.NBT))
    }

}