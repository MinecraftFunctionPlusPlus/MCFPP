package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
import java.util.*

/**
 * 一个类的实例。实例是一种特殊的变量，它永远是匿名的，对实例的访问通过指针完成。
 *
 * 类的实例的本质是一个marker实体，它在模拟堆中的地址即是一个记分板值，即[address]。指针通过记录这个记分板值获取这个实例的堆地址
 *
 * 类的实例的过程在[top.mcfpp.lib.Class.newInstance]方法中进行。
 *
 * @see Class 类的核心实现
 * @see ClassPointer 类的指针
 * @see ClassType 表示类的类型，同时也是类的静态成员的指针
 */
open class ClassObject : ClassBase {

    /**
     * 类的对象的类型
     */
    override var clsType: Class

    /**
     * 类的字符串标识符
     */
    @get:Override
    override val type: String

    /**
     * 在模拟堆中的地址
     */
    override lateinit var address: MCInt

    /**
     * 类中的成员
     */
    var cache: Cache? = null

    /**
     * 初始化类实例的时候自动生成的初始指针
     */
    var initPointer: ClassPointer

    override val tag: String
        get() = TODO("Not yet implemented")

    /**
     * 构造指定类的一个实例。注意此构造函数仅仅构造了一个对象，对于对象的地址分配等操作是在[Class.newInstance]方法中完成的。
     * 要创建一个新的mcfpp类实例时，也应当调用[Class.newInstance]而非此方法。
     */
    constructor(cls: Class) {
        this.clsType = cls
        type = cls.identifier
        initPointer = ClassPointer(cls, "INIT")
        initPointer.obj = this
    }

    /**
     * 复制一个类的对象
     */
    constructor(classObject: ClassObject) : super(classObject) {
        this.clsType = classObject.clsType
        initPointer = classObject.initPointer
        address = classObject.address
        type = classObject.type
        cache = classObject.cache
    }

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Any {
        return ClassObject(this)
    }

    @Override
    override fun getMemberVar(key: String, accessModifier: ClassMember.AccessModifier): Pair<Var, Boolean> {
        TODO()
    }

    @Override
    override fun getMemberFunction(key: String, params: List<String>, accessModifier: ClassMember.AccessModifier): Pair<Function, Boolean> {
        TODO()
    }

    @Override
    override fun getTempVar(): Var {
        return this
    }
}