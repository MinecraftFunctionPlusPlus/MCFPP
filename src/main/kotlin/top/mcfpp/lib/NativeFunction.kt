package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.Class

/**
 * 表示了一个native方法
 */
class NativeFunction : Function, Native {

    /**
     * 要调用的java方法
     */
    var javaMethod: Method

    /**
     * 引用的java方法的类名。包含包路径
     */
    var javaClassName: String

    /**
     * 引用的java方法名。
     */
    var javaMethodName: String

    constructor(name: String, javaMethod: String, namespace: String = Project.currNamespace) : super(name, namespace, "void"){
        val strs = javaMethod.split(".")
        try {
            javaMethodName = strs[strs.size - 1]
            javaClassName = javaMethod.substring(0, javaMethod.lastIndexOf(javaMethodName) - 1)
        } catch (e: StringIndexOutOfBoundsException) {
            throw IllegalFormatException(javaMethod)
        }
        try{
            val cls: Class<*> = Class.forName(javaClassName)
            this.javaMethod = cls.getMethod(javaMethodName, Array<Var?>::class.java, ClassPointer::class.java)
        } catch (e: NoSuchMethodException) {
            throw NoSuchMethodException(javaMethodName)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundException(javaClassName)
        }
    }

    constructor(name: String, javaMethod: Method, namespace: String = Project.currNamespace) : super(name, namespace, "void") {
        this.javaMethod = javaMethod
        this.javaClassName = javaMethod.declaringClass.`package`.name + "." + javaMethod.declaringClass.name
        this.javaMethodName = javaMethod.name
    }

    @Override
    override fun invoke(args: ArrayList<Var>, cls: ClassBase?) {
        val argsArray = arrayOfNulls<Var>(args.size)
        args.toArray(argsArray)
        try {
            javaMethod.invoke(null, argsArray, cls)
        } catch (e: IllegalAccessException) {
            Project.error("Cannot access method: ${javaMethod.name}")
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            Project.error("Exception occurred in method: ${javaMethod.name}")
            throw RuntimeException(e)
        } catch (e: NullPointerException){
            Project.error("Method should be static: ${javaMethod.name}")
            throw RuntimeException(e)
        }
    }

    @Override
    override fun toString(containClassName: Boolean, containNamespace: Boolean): String {
        return super.toString(containClassName,containNamespace ) + "->" + javaClassName + "." + javaMethodName
    }
}