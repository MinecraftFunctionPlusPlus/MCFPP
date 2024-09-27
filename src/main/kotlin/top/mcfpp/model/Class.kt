package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.core.lang.ClassPointer
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.ClassConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.GenericClass
import top.mcfpp.util.LogProcessor

/**
 * 一个类。在mcfpp中一个类通常类似下面的样子
 * ```java
 * class People{
 *      entity target;
 *
 *      public People(entity target){
 *          this.target = target;
 *      }
 * }
 * ```
 * mcfpp中的类和Java中的类基本一致。
 * 在minecraft的原生命令中并不存在类这个概念。类是mcfpp强行引入的一个概念，勉强地达到了类的效果。
 * 在声明一个类的变量的时候，这个变量实际上起到的是一个指针的作用。我们在访问类的成员时，就通过这个指针来访问成员。
 *指针与成员之间的对应是通过记分板的数值来对应的，这个记分板的名字由类决定。接下来，还是以student类为例，详细说明类的建立和访问过程。
 * 一个学生类有两个变量，一个是实体，一个是分数。在编译到类的声明部分时，编译器知道了这个类，就创建一个名为mcfpp_cs_student的记分板，用于对应指针
 *以及对象。接下来，我们用student stu = new stu(100,@p);来创建一个student对象。首先，编译器根据变量的名字，生成了一个名为
 *$obj_student_p_stu的实体，并将这个实体的分数设置为0。这个0可以看作一个类似与地址的东西。接着就是生成这个对象。实体的保存相对简单。将这个实体
 *（这个例子中是最近的玩家）的mcfpp_cs_student记分板分数设置为0，然后加上obj_student_m_p，表示这个实体是stu对象的p变量。像这种数值的声明则略
 *麻烦。对于每一个数值的变量，都会单独生成一个记分板。对我们现在的例子，即score变量来说，就会生成一个名为obj_student_m_score的变量。同时，刚刚的
 *指针实体，即名为$obj_student_p_stu的实体的obj_student_m_score也会被赋值为100。接着，在访问的时候，如果访问数值，则直接根据指针实体取出记分
 *板中的数值，如果访问实体，则找到和指针实体mcfpp_cs_student分数相同的带有obj_student_m_p标签的实体。
 * 和[mcfpp面向对象——类的创建和销毁](https://alumopper.top/mcfpp%e9%9d%a2%e5%90%91%e5%af%b9%e8%b1%a1-%e7%b1%bb%e7%9a%84%e5%88%9b%e5%bb%ba%e5%92%8c%e9%94%80%e6%af%81/)
 *
 * 类编译后的名字和声明时的名字是一致的。除了某些编译器硬编码的特殊的类以外，mcfpp中的类总是以大写字母开头。
 * 但是由于mcfunction包括路径在内都不能存在大写字母，因此在编译期间，会将大写字母转换为下划线加小写字母的形式，例如A转化为_a
 *
 * @see ClassPointer 类的指针
 * @see MCFPPClassType 表示类的类型，同时也是类的静态成员的指针
 */
open class Class : CompoundData {

    /**
     * 构造函数
     */
    var constructors: ArrayList<ClassConstructor> = ArrayList()

    /**
     * 是否是静态类。静态类将不会被垃圾处理器处理
     */
    var isStaticClass = false

    /**
     * 是否是抽象类
     */
    var isAbstract = false

    /**
     * 类的字段初始化函数
     */
    var classPreInit: Function

    /**
     * 临时指针。所有的类都共用一个临时指针。临时指针只能在创建对象的期间使用。
     */
    lateinit var initPointer : ClassPointer

    /**
     * 是否已经继承了一个类
     */
    private var hasParentClass = false

    /**
     * 这个类的伴随对象
     */
    var objectClass : ObjectClass? = null

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace) {
        this.identifier = identifier
        this.namespace = namespace
        classPreInit = Function("_class_preinit_$identifier", this, false, context = null)
        field.addFunction(classPreInit,true)
    }

    override fun initialize(){
        super.initialize()
        initPointer =  ClassPointer(this, "INIT")
    }

    @get:Override
    override val prefix: String
        get() = namespace + "_class_" + identifier + "_"

    /**
     * 获取这个类指针对于的marker的tag
     */
    open val tag: String
        get() = namespace + "_class_" + identifier + "_pointer"

    fun getConstructorByString(normalParams: List<String>): ClassConstructor?{
        return getConstructorByType(
            ArrayList(normalParams.map { MCFPPType.parseFromIdentifier(it, field) })
        )
    }

    /**
     * 根据参数列表获取一个类的构造函数
     * @return 返回这个类的参数
     */
    fun getConstructorByType(normalParams: List<MCFPPType>): ClassConstructor? {
        for (f in constructors) {
            if(f.isSelf(this, normalParams)){
                return f
            }
        }
        return null
    }

    /**
     * 向这个类中添加一个构造函数
     * @param constructor 构造函数
     */
    fun addConstructor(constructor: ClassConstructor) : Boolean {
        if (constructors.contains(constructor)) {
            return false
        } else {
            constructors.add(constructor)
            return true
        }
    }

    /**
     * 创建这个类的一个实例
     * @return 创建的实例
     */
    open fun newInstance(): ClassPointer {
        if (isAbstract) {
            LogProcessor.error("Abstract classes cannot be instantiated: $identifier")
        }
        //创建实例
        return ClassPointer(this, "init")
    }

    /**
     * 继承某个类或接口的字段和方法
     *
     * @param compoundData
     */
    override fun extends(compoundData: CompoundData) : CompoundData{
        if(compoundData is Class){
            if(hasParentClass){
                LogProcessor.error("A class can only inherit one class")
                throw Exception()
            }
            hasParentClass = true
        }
        super.extends(compoundData)
        return this
    }

    /**
     * 获取这个类对于的classType
     */
    override var getType: () -> MCFPPType = {
        MCFPPClassType(this,
            parent.filterIsInstance<Class>().map { it.getType() }
        )
    }

    override fun isSub(compoundData: CompoundData): Boolean {
        if(compoundData == baseClass) return true
        return super.isSub(compoundData)
    }

    companion object {

        val baseClass = Class("Object","mcfpp.lang")

        class UndefinedClassOrInterface(identifier: String, namespace: String?)
            : Class(identifier, namespace?:Project.currNamespace) {

            fun getDefinedClassOrInterface(): CompoundData?{
                var re : CompoundData? = GlobalField.getClass(namespace, identifier)
                if(re == null){
                    re = GlobalField.getInterface(namespace, identifier)
                }
                return re
            }

        }
        /**
         * 当前编译器正在编译的类
         */
        var currClass: Class? = null
    }
}

class CompiledGenericClass(identifier: String, namespace: String = Project.currNamespace,
                           var originClass: GenericClass) : Class(identifier, namespace) {

}