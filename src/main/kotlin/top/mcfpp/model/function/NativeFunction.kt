package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.Native
import top.mcfpp.util.ValueWrapper
import java.lang.Void

/**
 * 表示了一个native方法
 * TODO Native 和 Generic 的关系
 */
class NativeFunction : Function, Native {

    /**
     * 要调用的java方法
     */
    var javaMethod: MNIMethod

    /**
     * 引用的java方法的类名。包含包路径。可能不存在
     */
    var javaClassName: String?

    /**
     * 引用的java方法名。
     */
    var javaMethodName: String

    val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    /**
     * 从一个java类中搜索一个MNI方法来创建一个NativeFunction
     *
     * @param name mcfpp方法的名字
     * @param dataClass java类
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, dataClass: MNIMethodContainer, returnType: MCFPPType, namespace: String): super(name, namespace, returnType){
        this.javaMethod = dataClass.getMNIMethod(name)
        this.javaClassName = dataClass.javaClass.name
        this.javaMethodName = name
    }

    /**
     * 通过一个java方法来构造一个NativeFunction，同时手动指定mcfpp方法的名字
     *
     * @param name mcfpp方法的名字
     * @param javaMethod java方法
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, javaMethod: MNIMethod, returnType: MCFPPType, namespace: String = Project.currNamespace) : super(name, namespace, returnType) {
        this.javaMethod = javaMethod
        this.javaClassName = null
        this.javaMethodName = name
    }

    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        invoke(ArrayList(), normalArgs, caller)
    }

    @Override
    fun invoke(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        /*
        argPass(readOnlyArgs, normalArgs)
        val normalArgsArray = arrayOfNulls<Var<*>>(field.allVars.size)
        field.allVars.toTypedArray().copyInto(normalArgsArray)
        val readOnlyArgsArray = arrayOfNulls<Var<*>>(field.allVars.size)
        field.allVars.toTypedArray().copyInto(readOnlyArgsArray)
        val valueWrapper = ValueWrapper(returnVar)
        javaMethod(readOnlyArgsArray, normalArgsArray, caller, valueWrapper)
        returnVar = valueWrapper.value
         */
        val valueWrapper = ValueWrapper(returnVar)
        javaMethod(readOnlyArgs.toTypedArray(), normalArgs.toTypedArray(), caller, valueWrapper)
        returnVar = valueWrapper.value
    }

    /*
    private fun argPass(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>,){
        for (i in this.normalParams.indices) {
            val p = field.getVar(this.normalParams[i].identifier)!!
            p.assign(normalArgs[i])
        }
        for(i in this.readOnlyParams.indices){
            if(!readOnlyArgs[i].isConcrete){
                LogProcessor.error("Cannot pass a non-concrete value to a readonly parameter")
                throw IllegalArgumentException()
            }
            //参数传递和子函数的参数进栈
            val p = field.getVar(this.readOnlyParams[i].identifier)!!
            p.assign(readOnlyArgs[i])
        }
    }
    */

    fun appendReadOnlyParam(type: MCFPPType, identifier: String, isStatic: Boolean = false) : Function {
        readOnlyParams.add(FunctionParam(type ,identifier, this, isStatic))
        return this
    }

    fun replaceGenericParams(genericParams: Map<String, MCFPPType>) : NativeFunction{
        val n = NativeFunction(this.identifier, this.javaMethod, this.returnType, this.namespace)
        for(np in normalParams){
            if(genericParams[np.typeIdentifier] != null){
                n.appendNormalParam(genericParams[np.typeIdentifier]!!, np.identifier, np.isStatic)
            }else{
                n.appendNormalParam(np.type, np.identifier, np.isStatic)
            }
        }
        for(rp in readOnlyParams){
            if(genericParams[rp.typeIdentifier] != null){
                n.appendReadOnlyParam(genericParams[rp.typeIdentifier]!!, rp.identifier, rp.isStatic)
            }else{
                n.appendReadOnlyParam(rp.type, rp.identifier, rp.isStatic)
            }
        }
        return n
    }

    @Override
    override fun toString(containClassName: Boolean, containNamespace: Boolean): String {
        return super.toString(containClassName,containNamespace ) + "->" + javaClassName + "." + javaMethodName
    }
}

//TODO 改成java用interface实现的lambda？
typealias MNIMethod = (Array<Var<*>?>, Array<Var<*>?>, CanSelectMember?, ValueWrapper<Var<*>>) -> Void

abstract class MNIMethodContainer{

    abstract fun getMNIMethod(name: String): MNIMethod

}