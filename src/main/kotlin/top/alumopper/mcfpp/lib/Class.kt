package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.exception.FunctionDuplicationException
import top.alumopper.mcfpp.lang.ClassObject
import top.alumopper.mcfpp.lang.MCInt
import top.alumopper.mcfpp.lang.Var
import top.alumopper.mcfpp.lang.SbObject
import java.util.ArrayList

/**
 * 一个类。
 * 类的理论部分参见：
 * [在mcfunction中实现面向对象](https://alumopper.top/%e5%9c%a8mcfunction%e4%b8%ad%e5%ae%9e%e7%8e%b0%e9%9d%a2%e5%90%91%e5%af%b9%e8%b1%a1/)
 * 和[mcfpp面向对象——类的创建和销毁](https://alumopper.top/mcfpp%e9%9d%a2%e5%90%91%e5%af%b9%e8%b1%a1-%e7%b1%bb%e7%9a%84%e5%88%9b%e5%bb%ba%e5%92%8c%e9%94%80%e6%af%81/)
 *
 * 类编译后的名字和声明时的名字是一致的。除了某些编译器硬编码的特殊的类以外，mcfpp中的类总是以大写字母开头。
 */
open class Class : CacheContainer {
    /**
     * 这个类的标识符
     */
    lateinit var identifier: String

    /**
     * 类的命名空间
     */
    lateinit var namespace: String

    /**
     * 这个类的父类
     */
    var parent: Class? = null

    /**
     * 成员变量和成员函数
     */
    var cache: Cache? = null

    /**
     * 静态变量和静态函数
     */
    var staticCache: Cache? = null

    /**
     * 记录这个类所有实例地址的记分板
     */
    lateinit var addressSbObject: SbObject

    /**
     * 成员变量对应的初始化计算表达式
     */
    var varInitExpression: HashMap<Var, mcfppParser.ExpressionContext>? = null

    /**
     * 静态变量对应的初始化计算表达式
     */
    var staticVarInitExpression: HashMap<Var, mcfppParser.ExpressionContext>? = null

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
     * 生成一个类。它拥有指定的标识符和默认的命名空间
     * @param identifier 类的标识符
     */
    constructor(identifier: String) : this(identifier, Project.currNamespace)

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    constructor(identifier: String, namespace: String) {
        this.identifier = identifier
        this.namespace = namespace
        classPreInit = Function("_class_preinit_$identifier", this, false)
        classPreStaticInit = Function("_class_preinit_$identifier", this, true)
        staticCache = Cache(Project.global.cache, this)
        cache = Cache(staticCache!!, this)
        cache!!.functions.add(classPreInit)
        staticCache!!.functions.add(classPreStaticInit)
        addressSbObject = SbObject(namespace + "_class_" + identifier + "_index")
        //init函数的初始化置入，即地址分配，原preinit函数合并于此。同时生成新的临时指针
        classPreInit.commands.add("scoreboard players operation @s " + addressSbObject.name + " = \$index " + addressSbObject.name)
        classPreInit.commands.add("scoreboard players add \$index " + addressSbObject.name + " 1")
    }

    constructor()

    @get:Override
    override val prefix: String
        get() = Project.name.toString() + "_class_" + identifier + "_"

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
     * 返回此类中的一个成员字段。优先获取成员变量中的字段
     * @param key 字段名
     * @return 如果字段存在，则返回此字段，否则返回null
     */
    fun getMemberVar(key: String?): Var? {
        return cache!!.getVar(key!!) ?: return staticCache!!.getVar(key)
    }

    /**
     * 向这个类中添加一个成员
     * @param classMember 要添加的成员
     */
    fun addMember(classMember: ClassMember) {
        //非静态成员
        if (!classMember.isStatic) {
            if (classMember is Function) {
                currClass!!.cache!!.functions.add(classMember)
            } else if (classMember is Var) {
                currClass!!.cache!!.putVar(classMember.key!!, classMember)
            }
            return
        }
        //静态成员
        if (classMember is Function) {
            currClass!!.staticCache!!.functions.add(classMember)
        } else if (classMember is Var) {
            currClass!!.staticCache!!.putVar(classMember.key!!, classMember)
        }
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
        val obj = ClassObject(this, this, "temp")
        //给这个类添加成员
        obj.cache = Cache(cache!!)
        obj.address = MCInt("@s").setObj(addressSbObject) as MCInt
        return obj
    }

    /**
     * 这个类是否可以被强制转换为目标类型
     * @param target 目标类型
     * @return 如果可以,返回true,反之返回false
     */
    fun canCastTo(target: Class): Boolean {
        if (identifier.equals(target.identifier)) {
            return true
        }
        return if (parent != null) {
            parent!!.canCastTo(target)
        } else false
    }

    companion object {
        /**
         * 当前编译器正在编译的类
         */
        var currClass: Class? = null
    }
}