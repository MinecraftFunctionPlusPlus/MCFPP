package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.NativeClass
import java.util.*
import kotlin.collections.ArrayList

class NativeClassObject : Var {
    /**
     * 它的类型
     */
    var clsType: NativeClass? = null

    /**
     * 它的java类的实例
     */
    var javaClassObject: INativeClass

    /**
     * 类的字符串标识符
     */
    @get:Override
    override var type: String

    /**
     * 初始化类实例的时候自动生成的临时指针
     */
    var initPointer: ClassPointer

    constructor(clsType: NativeClass, args: ArrayList<Var>) {
        this.clsType = clsType
        type = clsType.identifier.toString()
        initPointer = ClassPointer(clsType, clsType, UUID.randomUUID().toString())
        //创建java实例
        //列表转数组
        val vars = arrayOfNulls<Var>(args.size)
        javaClassObject = clsType.cls.getDeclaredConstructor(Array<Var>::class.java, ClassPointer::class.java)
            .newInstance(vars, initPointer)
    }

    constructor(b: NativeClassObject) {
        cls = b.cls
        javaClassObject = b.javaClassObject
        type = b.type
        initPointer = b.initPointer
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
    override fun clone(): NativeClassObject {
        TODO()
    }
}