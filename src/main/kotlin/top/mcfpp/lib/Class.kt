package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.FunctionDuplicationException
import top.mcfpp.lang.*
import java.util.ArrayList

/**
 * 一个类。
 * 类的理论部分参见：
 * [在mcfunction中实现面向对象](https://alumopper.top/%e5%9c%a8mcfunction%e4%b8%ad%e5%ae%9e%e7%8e%b0%e9%9d%a2%e5%90%91%e5%af%b9%e8%b1%a1/)
 * 和[mcfpp面向对象——类的创建和销毁](https://alumopper.top/mcfpp%e9%9d%a2%e5%90%91%e5%af%b9%e8%b1%a1-%e7%b1%bb%e7%9a%84%e5%88%9b%e5%bb%ba%e5%92%8c%e9%94%80%e6%af%81/)
 *
 * 类编译后的名字和声明时的名字是一致的。除了某些编译器硬编码的特殊的类以外，mcfpp中的类总是以大写字母开头。
 * 但是由于mcfunction包括路径在内都不能存在大写字母，因此在编译期间，会将大写字母转换为下划线加小写字母的形式，例如A转化为_a
 *
 * @see ClassPointer 类的指针
 * @see ClassObject 类的实例。指针的目标
 * @see ClassType 表示类的类型，同时也是类的静态成员的指针
 */
open class Class : CompoundData {

    /**
     * 记录这个类所有实例地址的记分板
     */
    lateinit var addressSbObject: SbObject

    /**
     * 构造函数
     */
    var constructors: ArrayList<Constructor> = ArrayList()

    /**
     * 是否是静态类。静态类将不会被垃圾处理器处理
     */
    var isStaticClass = false

    /**
     * 类的字段初始化函数
     */
    lateinit var classPreInit: Function

    /**
     * 类的静态字段的初始化函数
     */
    lateinit var classPreStaticInit: Function

    /**
     * 临时指针。所有的类都共用一个临时指针。临时指针只能在创建对象的期间使用。
     */
    lateinit var initPointer : ClassPointer

    /**
     * 生成一个类。它拥有指定的标识符和默认的命名空间
     * @param identifier 类的标识符
     */
    constructor(identifier: String) : this(identifier, Project.currNamespace)

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    @InsertCommand
    constructor(identifier: String, namespace: String) {
        this.identifier = identifier
        this.namespace = namespace
    }

    override fun initialize(){
        super.initialize()
        classPreInit = Function("_class_preinit_$identifier", this, false)
        classPreStaticInit = Function("_class_prestaticinit_$identifier", this, true)
        field.addFunction(classPreInit,true)
        staticField.addFunction(classPreStaticInit,true)
        addressSbObject = SbObject(namespace + "_class_" + identifier + "_index")
        initPointer =  ClassPointer(this, "INIT")
        //init函数的初始化置入，即地址分配，原preinit函数合并于此。同时生成新的临时指针
        classPreInit.commands.add("scoreboard players operation @s " + addressSbObject.name + " = \$index " + addressSbObject.name)
        classPreInit.commands.add("scoreboard players add \$index " + addressSbObject.name + " 1")
        classPreInit.commands.add("scoreboard players operation ${initPointer.name} ${addressSbObject.name} = @s ${addressSbObject.name}")
        //staticinit函数的初始化直入。生成static实体
        classPreStaticInit.commands.add(
            "execute in minecraft:overworld " +
                    "run summon marker 0 1 0 {Tags:[$staticTag]}"
        )
    }

    constructor()

    @get:Override
    override val prefix: String
        get() = namespace + "_class_" + identifier + "_"

    /**
     * 获取这个类指针对于的marker的tag
     */
    val tag: String
        get() = namespace + "_class_" + identifier + "_pointer"

    val staticTag: String
        get() = namespace + "_class_" + identifier + "_static_pointer"

    /**
     * 根据参数列表获取一个类的构造函数
     * @param params 构造函数的参数列表
     * @return 返回这个类的参数
     */
    fun getConstructor(params: ArrayList<String>): Constructor? {
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

    /**
     * 向这个类中添加一个构造函数
     * @param constructor 构造函数
     */
    fun addConstructor(constructor: Constructor) {
        if (constructors.contains(constructor)) {
            throw FunctionDuplicationException()
        } else {
            constructors.add(constructor)
        }
    }

    /**
     * 创建这个类的一个实例
     * @return 创建的实例
     */
    fun newInstance(): ClassObject {
        //创建实例
        val obj = ClassObject(this)
        //地址分配
        obj.address = MCInt("@s").setObj(addressSbObject) as MCInt
        return obj
    }

    companion object {
        /**
         * 当前编译器正在编译的类
         */
        var currClass: Class? = null
    }
}