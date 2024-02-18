package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.lang.type.MCFPPType

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
class Template : FieldContainer, CompoundData {

    val dataType : MCFPPType

    /**
     * 结构体的构造函数
     */
    val constructors: ArrayList<TemplateConstructor>

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_template_" + identifier + "_"


    constructor(identifier: String, dataType: MCFPPType, namespace: String = Project.currNamespace){
        this.identifier = identifier
        field = CompoundDataField(null,this)
        staticField = CompoundDataField(null, this)
        constructors = ArrayList()
        this.dataType = dataType
        this.namespace = namespace
    }

    /**
     * 向这个类中添加一个构造函数
     * @param constructor 构造函数
     */
    fun addConstructor(constructor: TemplateConstructor) : Boolean {
        return if (constructors.contains(constructor)) {
            false
        } else {
            constructors.add(constructor)
            true
        }
    }
    fun getConstructor(params: ArrayList<String>): TemplateConstructor?{
        return getConstructorInner(ArrayList<MCFPPType>(params.map { MCFPPType.parse(it) }))
    }
    /**
     * 根据参数列表获取一个类的构造函数
     * @param params 构造函数的参数列表
     * @return 返回这个类的参数
     */
    fun getConstructorInner(params: ArrayList<MCFPPType>): TemplateConstructor? {
        for (f in constructors) {
            if (f.params.size == params.size) {
                if (f.params.size == 0) {
                    return f
                }
                //参数比对
                for (i in 0 until params.size) {
                    if (params[i] == f.params[i].type) {
                        return f
                    }
                }
            }
        }
        return null
    }
    companion object{
        var currTemplate: Template? = null
    }

}