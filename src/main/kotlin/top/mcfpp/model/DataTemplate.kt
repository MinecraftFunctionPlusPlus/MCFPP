package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.*
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

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
     * 构造函数
     */
    var constructors: ArrayList<DataTemplateConstructor> = ArrayList()

    private val reference: ArrayList<DataTemplate> = ArrayList()

    var alwaysDynamic: Boolean = false

    /**
     * 调用构造函数之前对成员进行初始化的部分
     */
    val preInit = HashMap<String, mcfppParser.ExpressionContext>()
    val preInit2 = HashMap<String, Var<*>>()    //HashMap<String, MCFPPValue<*>>


    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_template_" + identifier + "_"

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        field = CompoundDataField(ArrayList())
        this.namespace = namespace
    }

    /**
     * 获取这个类对于的classType
     */
    override fun getType(): MCFPPDataTemplateType =
        MCFPPDataTemplateType(this,
            ArrayList(parent.filterIsInstance<DataTemplate>().map { it.getType() })
        )

    /**
     * 检查给定复合标签是否符合此数据模板
     * @param compoundTag 给定的复合标签
     * @return 返回值
     */
    fun checkCompoundStruct(compoundTag: CompoundTag) : Boolean {
        for (member in field.allVars.filter { it !is ConcreteVar<*,*> }){
            if(!compoundTag.containsKey(member.identifier)) return false
            if(!member.type.checkNBTType(compoundTag[member.identifier]!!)) return false
        }
        return true
    }

    fun checkDictionaryStruct(dict: Map<String, Var<*>>) : Boolean {
        for (member in field.allVars.filter { it !is ConcreteVar<*,*> }){
            if(!dict.containsKey(member.identifier)) return false
            if(!dict[member.identifier]!!.type.isSubOf(member.type)) return false
        }
        return true
    }

    override fun isSubOf(compoundData: CompoundData): Boolean {
        if(compoundData == baseDataTemplate) return true
        return super.isSubOf(compoundData)
    }

    /**
     * 向这个类中添加一个成员
     * @param member 要添加的成员
     */
    override fun addMember(member: Member): Boolean {
        return when(member){
            is DataTemplateConstructor -> {
                if (constructors.contains(member)) {
                    return false
                } else {
                    constructors.add(member)
                    return true
                }
            }
            is Function -> {
                field.addFunction(member, false)
            }
            is Var<*> -> {
                if(member is DataTemplateObject){
                    if(ifInfinitiveReference(member.templateType)) {
                        LogProcessor.error("Infinitive reference: ${member.templateType.identifier} -> ${this.identifier}")
                        return field.putVar(member.identifier, UnknownVar(member.identifier))
                    }
                }
                field.putVar(member.identifier, member)
            }
            is Property -> {
                field.putProperty(member.identifier, member)
            }
            else -> {
                throw IllegalArgumentException("")
            }
        }
    }

    override fun extends(compoundData: CompoundData): CompoundData {
        if(parent.contains(compoundData)){
            LogProcessor.warn("Already extends template '${compoundData.identifier}'")
            return this
        }
        super.extends(compoundData)
        //把所有成员都塞进去
        compoundData.field.forEachVar {
            val b = field.getVar(it.identifier) != null
            if(b){
                LogProcessor.warn("Duplicate var '${it.identifier}' in template '${compoundData.identifier}'. Overriding it.")
            }
            field.putVar(it.identifier, it, true)
        }
        compoundData.field.forEachProperty {
            val b = field.getProperty(it.identifier) != null
            if(b){
                LogProcessor.warn("Duplicate property '${it.identifier}' in template '${compoundData.identifier}'. Overriding it.")
            }
            field.putProperty(it.identifier, it, true)
        }
        return this
    }

    //TODO 不能正常检测循环引用
    fun ifInfinitiveReference(template: DataTemplate): Boolean{
        return template == this && reference.any { it == template || it.ifInfinitiveReference(template) }
    }

    fun getConstructorByString(normalParams: List<String>): DataTemplateConstructor?{
        return getConstructorByType(
            ArrayList(normalParams.map { MCFPPType.parseFromString(it, field)?: MCFPPBaseType.Any })
        )
    }

    /**
     * 根据参数列表获取一个类的构造函数
     * @return 返回这个类的参数
     */
    fun getConstructorByType(normalParams: List<MCFPPType>): DataTemplateConstructor? {
        for (f in constructors) {
            if(f.isSelf(this, normalParams)){
                return f
            }
        }
        return null
    }

    companion object{

        var currTemplate: DataTemplate? = null

        val baseDataTemplate by lazy {
            DataTemplate("DataObject","mcfpp.lang").apply {
                extends(MCAny.data)
                //在GlobalField中注册和获取函数
            }
        }

        @JvmStatic
        fun newInstance(namespace: String?, templateID: String, varID: String): DataTemplateObjectConcrete{
            return GlobalField.getTemplate(namespace, templateID)!!.getType().build(varID) as DataTemplateObjectConcrete
        }

        @JvmStatic
        fun newInstance(namespace: String?, templateID: String) = newInstance(namespace, templateID, TempPool.getVarIdentify())

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T: Var<*>> getField(obj: DataTemplateObject, identifier: String): T?{
            return obj.getMemberVar(identifier, Member.AccessModifier.PUBLIC).first as T?
        }

        @JvmStatic
        fun assignField(obj: DataTemplateObject, identifier: String, v: Var<*>){
            val member = getField(obj, identifier)!!
            member.replacedBy(member.assignedBy(v))
        }

    }

}