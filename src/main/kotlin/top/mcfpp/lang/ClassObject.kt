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
/*
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
        initPointer = cls.initPointer
        initPointer.obj = this
    }

    /**
     * 复制一个类的对象
     */
    constructor(classObject: ClassObject) : super(classObject) {
        this.clsType = classObject.clsType
        initPointer = classObject.initPointer
        type = classObject.type
    }

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        hasAssigned = true
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Any {
        return ClassObject(this)
    }
    /**
     * 获取这个指针指向的对象中的一个成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        val member = clsType.getVar(key)?.clone(this)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个指针指向的对象中的一个成员方法。
     *
     * @param key 方法的标识符
     * @param params 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, params: List<String>, accessModifier: Member.AccessModifier): Pair<Function?, Boolean> {
        //获取函数
        val member = clsType.field.getFunction(key, params)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    @Override
    override fun getTempVar(): Var {
        return this
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
    }

    override fun toDynamic() {
        TODO("Not yet implemented")
    }
}*/