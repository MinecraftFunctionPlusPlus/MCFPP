package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.*
import top.alumopper.mcfpp.lib.Function
import java.util.*

/**
 * 一个类的实例
 */
open class ClassObject : Var, CanSelectMember {
    /**
     * 它的类型
     */
    var clsType: Class

    /**
     * 类的字符串标识符
     */
    @get:Override
    override var type: String

    /**
     * 在模拟堆中的地址
     */
    var address: MCInt? = null

    /**
     * 类中的成员
     */
    var cache: Cache? = null

    /**
     * 初始化类实例的时候自动生成的临时指针
     */
    var initPointer: ClassPointer

    constructor(cls: Class) : super() {
        this.clsType = cls
        type = cls.identifier.toString()
        initPointer = ClassPointer(cls, cls, UUID.randomUUID().toString())
        initPointer.address = (MCInt().setObj(cls.addressSbObject!!) as MCInt)
        initPointer.obj = this
    }

    constructor(cls: Class, container: CacheContainer, identifier: String) {
        this.clsType = cls
        type = cls.identifier.toString()
        key = identifier
        this.identifier = container.prefix + identifier
        initPointer = ClassPointer(cls, cls, UUID.randomUUID().toString())
        initPointer.address = (MCInt().setObj(cls.addressSbObject!!) as MCInt)
        initPointer.obj = this
    }


    constructor(classObject: ClassObject) : super(classObject) {
        this.clsType = classObject.clsType
        initPointer = classObject.initPointer
        address = classObject.address
        type = classObject.type
        cache = classObject.cache
    }

    val tag: String
        /**
         * 获取这个类的实例的指针实体在mcfunction中拥有的tag
         * @return 返回它的tag
         */
        get() = clsType.namespace.toString() + "_class_" + clsType.identifier + "_pointer"

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
        return "null"
    }

    @Override
    override fun getVarMember(key: String?): Var? {
        return null
    }

    @Override
    override fun getFunctionMember(key: String?, params: List<String?>?): Function? {
        return null
    }
}