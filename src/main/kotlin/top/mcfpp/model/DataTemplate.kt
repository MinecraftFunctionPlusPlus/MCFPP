package top.mcfpp.model

import net.querz.nbt.tag.CompoundTag
import top.mcfpp.Project
import top.mcfpp.lang.type.MCFPPDataTemplateType
import top.mcfpp.model.field.CompoundDataField

/**
 * 结构体是一种和类的语法极为相似的数据结构。在结构体中，只能有int类型的数据，或者说记分板的数据作为结构体的成员。
 *
 * 结构体通过记分板的命名来区分“内存”区域。
 *
 * 例如命名空间为test下的结构体foo，有成员mem，那么mcfpp就会创建一个名字为`test_struct_foo_mem`的记分板。
 * 这个结构体的实例则会根据实例变量的名字（在Minecraft中的标识符）来记分板上记录对应的值，例如`foo a`，在记分板上对应的值就是`前缀_a`
 *
 * 如果一个结构体的对象作为类的成员被引用，那么mcfpp会创建一个名字为`<namespace>_class_<classname>_<classMember>_struct_<structMember>`
 *的记分板，并让实体指针在相应的记分板上拥有值
 *
 * 除此之外，结构体是一种值类型的变量，而不是引用类型。因此在赋值的时候会把整个结构体进行一次赋值。
 */
open class DataTemplate : FieldContainer, CompoundData {

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_template_" + identifier + "_"


    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        field = CompoundDataField(staticField,this)
        staticField = CompoundDataField(null, this)
        this.namespace = namespace
    }

    /**
     * 获取这个类对于的classType
     */
    fun getType() : MCFPPDataTemplateType {
        return MCFPPDataTemplateType(this,
            parent.filterIsInstance<DataTemplate>().map { it.getType() }
        )
    }

    /**
     * 检查给定复合标签是否符合此数据模板
     * @param compoundTag 给定的复合标签
     * @return 返回值
     */
    fun checkCompoundStruct(compoundTag: CompoundTag) : Boolean{
        for (member in field.allVars){
            if(!compoundTag.containsKey(member.identifier)) return false
            if(!member.type.checkNBTType(compoundTag[member.identifier]!!)) return false
        }
        return true
    }

    override fun isSub(compoundData: CompoundData): Boolean {
        if(compoundData == baseDataTemplate) return true
        return super.isSub(compoundData)
    }

    companion object{
        var currTemplate: DataTemplate? = null

        val baseDataTemplate = DataTemplate("DataObject","mcfpp.lang")

    }

}