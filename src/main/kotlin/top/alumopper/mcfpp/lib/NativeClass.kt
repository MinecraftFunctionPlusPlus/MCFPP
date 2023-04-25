package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.lang.*
import java.lang.reflect.InvocationTargetException
import java.lang.Class

/**
 * 代表了mcfpp中声明的一个native类
 */
class NativeClass(identifier: String, namespace: String, cls: Class<INativeClass>) : top.alumopper.mcfpp.lib.Class(), Native {
    var cls: Class<INativeClass>

    constructor(identifier: String, cls: Class<INativeClass>) : this(identifier, Project.currNamespace, cls)

    init {
        this.identifier = identifier
        this.namespace = namespace
        this.cls = cls
    }

    @Throws(
        InvocationTargetException::class,
        NoSuchMethodException::class,
        InstantiationException::class,
        IllegalAccessException::class
    )
    fun newInstance(args: ArrayList<Var>): NativeClassObject {
        return NativeClassObject(this, args)
    }
}