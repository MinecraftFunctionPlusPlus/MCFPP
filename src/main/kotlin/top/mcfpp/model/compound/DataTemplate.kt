package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.Utils.v

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

    var companionObject: DataTemplate? = null

    var isInterface = false

    var isAbstract = false

    var isFinal = false

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_${isInterface.v({"template"}, {"interface"})}_" + identifier + "_"

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        scope = CompoundDataScope(ArrayList())
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
        for (member in scope.allVars){
            if(!compoundTag.containsKey(member.identifier)) return false
            if(!member.type.checkNBTType(compoundTag[member.identifier]!!)) return false
        }
        return true
    }

    fun checkDictionaryStruct(dict: Map<String, Var<*>>) : Boolean {
        for (member in scope.allVars.filter { !it.nullable }){
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
                if(this.isAbstract){
                    LogProcessor.error("Abstract DataTemplate Constructor")
                    return false
                }
                if (constructors.contains(member)) {
                    return false
                } else {
                    constructors.add(member)
                    return true
                }
            }
            is Function -> {
                if(member.isAbstract && !this.isAbstract){
                    LogProcessor.error("Cannot declare an abstract function in a non-abstract template")
                    return false
                }
                scope.addFunction(member, false)
            }
            is Var<*> -> {
                if(member is DataTemplateObject){
                    if(ifInfinitiveReference(member.templateType)) {
                        LogProcessor.error("Infinitive reference: ${member.templateType.identifier} -> ${this.identifier}")
                        return scope.putVar(member.identifier, UnknownVar(member.identifier))
                    }
                }
                scope.putVar(member.identifier, member)
            }
            is Property -> {
                scope.putProperty(member.identifier, member)
            }
            else -> {
                throw IllegalArgumentException("")
            }
        }
    }

    fun flatExtends(): CompoundData {
        for (compoundData in parent){
            //把所有成员都塞进去
            compoundData.scope.forEachVar {
                val b = scope.getVar(it.identifier) != null
                if(b){
                    LogProcessor.warn("Duplicate var '${it.identifier}' in template '$identifier'. Overriding it.")
                }
                scope.putVar(it.identifier, it, true)
            }
            compoundData.scope.forEachProperty {
                val b = scope.getProperty(it.identifier) != null
                if(b){
                    LogProcessor.warn("Duplicate property '${it.identifier}' in template '$identifier'. Overriding it.")
                }
                scope.putProperty(it.identifier, it, true)
            }
        }
        //函数继承检测
        for (f in this.scope.functions.values.flatten().filter { it.isOverride }){
            var isFound = false
            for (compoundData in parent){
                if(compoundData.scope.hasFunction(f, true)){
                    isFound = true
                }
            }
            if(!isFound){
                LogProcessor.error("Function '${f.identifier}' in template '$identifier' overrides nothing.")
            }
        }
        return this
    }

    //TODO 不能正常检测循环引用
    fun ifInfinitiveReference(template: DataTemplate): Boolean{
        return template == this && reference.any { it == template || it.ifInfinitiveReference(template) }
    }

    fun getConstructorByString(normalParams: List<String>): DataTemplateConstructor?{
        return getConstructorByType(
            ArrayList(normalParams.map { MCFPPType.parseFromString(it, scope)?: MCFPPBaseType.Any })
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
                extends(MCFPPBaseType.Any.instanceData)
                //在GlobalField中注册和获取函数
            }
        }

        @JvmStatic
        fun newInstance(namespace: String?, templateID: String, varID: String): DataTemplateObjectConcrete {
            return GlobalScope.getTemplate(namespace, templateID)!!.getType().build(varID) as DataTemplateObjectConcrete
        }

        @JvmStatic
        fun newInstance(namespace: String?, templateID: String) = newInstance(namespace, templateID, TempPool.getVarIdentify())

        @JvmStatic
        fun newInstance(namespace: String?, templateID: String, tag: Tag<*>): DataTemplateObjectConcrete {
            return GlobalScope.getTemplate(namespace, templateID)!!.getType().build(tag) as DataTemplateObjectConcrete
        }

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

        @JvmStatic
        fun isInstance(obj: DataTemplateObject, template: DataTemplate): Boolean{
            return obj.templateType.isSubOf(template)
        }

        @JvmStatic
        fun isInstance(obj: DataTemplateObject, namespace: String?, templateID: String): Boolean{
            return isInstance(obj, GlobalScope.getTemplate(namespace, templateID)!!)
        }

    }

}