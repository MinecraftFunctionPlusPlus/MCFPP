package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.Var

/**
 * 结构体是一种和类的语法极为相似的数据结构。在结构体中，只能有int类型的数据，或者说记分板的数据作为结构体的成员。
 *
 * 结构体通过记分板的命名来区分“内存”区域。
 *
 * 例如命名空间为test下的结构体foo，有成员mem，那么mcfpp就会创建一个名字为`struct_test_foo_mem`的记分板。
 * 这个结构体的实例则会根据实例变量的名字（在Minecraft中的标识符）来记分板上记录对应的值，例如`foo a`，在记分板上对应的值就是`前缀_a`
 *
 * 除此之外，结构体是一种值类型的变量，而不是引用类型。因此在赋值的时候会把整个结构体进行一次赋值。
 */
class DataStruct : FieldContainer {

    val identifier: String

    val namespace: String

    val field: StructField

    val staticField: StructField

    val constructors: ArrayList<StructConstructor>

    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        field = StructField(null,this)
        staticField = StructField(null, this)
        constructors = ArrayList()
        this.namespace = namespace
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    fun getMemberFunction(
        key: String,
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = TODO("Not yet implemented")

}