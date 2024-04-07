package top.mcfpp.lib.function

import top.mcfpp.Project
import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.Native
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
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

    val readOnlyParams: ArrayList<FunctionParam> = ArrayList()


    /**
     * 通过一个java方法的字符串来构造一个NativeFunction
     *
     * @param name mcfpp方法的名字
     * @param javaMethod java方法的字符串。格式为：包名.类名.方法名
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, javaMethod: String, returnType: MCFPPType, namespace: String = Project.currNamespace) : super(name, namespace, returnType){
        val strs = javaMethod.split(".")
        try {
            javaMethodName = strs[strs.size - 1]
            javaClassName = javaMethod.substring(0, javaMethod.lastIndexOf(javaMethodName) - 1)
        } catch (e: StringIndexOutOfBoundsException) {
            LogProcessor.error("Illegal format of java method: $javaMethod")
            throw IllegalFormatException(javaMethod)
        }
        try{
            val cls: Class<*> = Class.forName(javaClassName)
            this.javaMethod = cls.getMethod(javaMethodName, (arrayOf<Var<*>?>())::class.java, CanSelectMember::class.java, ValueWrapper::class.java)
        } catch (e: NoSuchMethodException) {
            throw NoSuchMethodException(javaMethodName)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundException(javaClassName)
        }
    }

    /**
     * 从一个java类中搜索一个方法来创建一个NativeFunction
     *
     * @param name mcfpp方法的名字
     * @param dataClass java类
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, dataClass: Class<*>, returnType: MCFPPType, namespace: String): super(name, namespace, returnType){
        this.javaMethod = dataClass.getMethod(name, (arrayOf<Var<*>?>())::class.java, CanSelectMember::class.java, ValueWrapper::class.java)
        this.javaClassName = dataClass.`package`.name + "." + dataClass.name
        this.javaMethodName = name
    }

    /**
     * 通过一个java方法来构造一个NativeFunction。mcfpp方法的名字将会和java方法的名字相同
     *
     * @param javaMethod java方法
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(javaMethod: Method, returnType: MCFPPType, namespace: String = Project.currNamespace): super(javaMethod.name, namespace, returnType){
        this.javaMethod = javaMethod
        this.javaClassName = javaMethod.declaringClass.`package`.name + "." + javaMethod.declaringClass.name
        this.javaMethodName = javaMethod.name
    }

    /**
     * 通过一个java方法来构造一个NativeFunction，同时手动指定mcfpp方法的名字
     *
     * @param name mcfpp方法的名字
     * @param javaMethod java方法
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, javaMethod: Method, returnType: MCFPPType, namespace: String = Project.currNamespace) : super(name, namespace, returnType) {
        this.javaMethod = javaMethod
        this.javaClassName = javaMethod.declaringClass.`package`.name + "." + javaMethod.declaringClass.name
        this.javaMethodName = name
    }

    @Override
    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        argPass(/*readOnlyArgs, */normalArgs)
        val argsArray = arrayOfNulls<Var<*>>(field.allVars.size)
        field.allVars.toTypedArray().copyInto(argsArray)
        try {
            val valueWrapper = ValueWrapper(returnVar)
            javaMethod.invoke(null, argsArray, caller, valueWrapper)
            returnVar = valueWrapper.value
        } catch (e: IllegalAccessException) {
            LogProcessor.error("Cannot access method: ${javaMethod.name}")
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            LogProcessor.error("Exception occurred in method: ${javaMethod.name}")
            throw RuntimeException(e)
        } catch (e: NullPointerException){
            LogProcessor.error("Method should be static: ${javaMethod.name}")
            throw RuntimeException(e)
        }
    }

    override fun argPass(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>,){
        for (i in this.normalParams.indices) {
            val p = field.getVar(this.normalParams[i].identifier)!!
            p.assign(normalArgs[i])
        }
        /*
        for(i in this.readOnlyParams.indices){
            if(!readOnlyArgs[i].isConcrete){
                LogProcessor.error("Cannot pass a non-concrete value to a readonly parameter")
                throw IllegalArgumentException()
            }
            //参数传递和子函数的参数进栈
            val p = field.getVar(this.readOnlyParams[i].identifier)!!
            p.assign(readOnlyArgs[i])
        }
         */
    }
    @Override
    override fun toString(containClassName: Boolean, containNamespace: Boolean): String {
        return super.toString(containClassName,containNamespace ) + "->" + javaClassName + "." + javaMethodName
    }
}