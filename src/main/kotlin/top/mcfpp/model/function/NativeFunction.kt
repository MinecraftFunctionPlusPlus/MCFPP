package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.annotations.MNIRegister
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import java.lang.Void
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.jvm.javaMethod


/**
 * 表示了一个native方法
 * TODO Native 和 Generic 的关系
 */
class NativeFunction : Function, Native {

    /**
     * 要调用的java方法
     */
    var javaMethod: Method

    /**
     * 引用的java方法的类名。包含包路径。可能不存在
     */
    var javaClassName: String?

    /**
     * 引用的java方法名。
     */
    var javaMethodName: String

    val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    var caller: String = "void"

    /**
     * 从一个java类中搜索一个MNI方法来创建一个NativeFunction
     *
     * @param name mcfpp方法的名字
     * @param dataClass java类
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    //constructor(name: String, dataClass: MNIMethodContainer, returnType: MCFPPType, namespace: String): super(name, namespace, returnType){
    //    this.javaMethod = dataClass.getMNIMethod(name)
    //    this.javaClassName = dataClass.javaClass.name
    //    this.javaMethodName = name
    //}

    /**
     * 通过一个java方法来构造一个NativeFunction，同时手动指定mcfpp方法的名字
     *
     * @param name mcfpp方法的名字
     * @param javaMethod java方法
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, returnType: MCFPPType, namespace: String = Project.currNamespace, javaMethod: Method = Companion::defaultNativeFunction.javaMethod!!) : super(name, namespace, returnType) {
        this.javaMethod = javaMethod
        this.javaClassName = null
        this.javaMethodName = name
    }

    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        invoke(ArrayList(), normalArgs, caller)
    }

    @Override
    fun invoke(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        val valueWrapper = ValueWrapper(returnVar)
        //一定是静态的
        javaMethod.invoke(null,
            *readOnlyArgs.toTypedArray(),
            *normalArgs.toTypedArray(),
            *if(this.caller != "void") arrayOf(caller) else emptyArray(),
            *if(this.returnType != MCFPPBaseType.Void) arrayOf(valueWrapper) else emptyArray()
        )
        returnVar = valueWrapper.value
    }

    fun appendReadOnlyParam(type: MCFPPType, identifier: String, isStatic: Boolean = false) : NativeFunction {
        readOnlyParams.add(FunctionParam(type ,identifier, this, isStatic))
        return this
    }

    fun replaceGenericParams(genericParams: Map<String, MCFPPType>) : NativeFunction{
        val n = NativeFunction(this.identifier, this.returnType, this.namespace, this.javaMethod)
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

    fun isSelf(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>) : Boolean{
        if (this.identifier == key && this.readOnlyParams.size == readOnlyParams.size && this.normalParams.size == normalParams.size) {
            if (normalParams.isEmpty() && readOnlyParams.isEmpty()) {
                return true
            }
            var hasFoundFunc = true
            //参数比对
            for (i in normalParams.indices) {
                if (!FunctionParam.isSubOf(normalParams[i],this.normalParams[i].type)) {
                    hasFoundFunc = false
                    break
                }
            }
            for (i in readOnlyParams.indices) {
                if (!FunctionParam.isSubOf(readOnlyParams[i],this.readOnlyParams[i].type)) {
                    hasFoundFunc = false
                    break
                }
            }
            return hasFoundFunc
        }else{
            return false
        }
    }

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams()?.parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r?.parameter()?:ArrayList()){
            val param1 = FunctionParam(
                MCFPPType.parseFromContext(param.type(), this.field),
                param.Identifier().text,
                this,
                param.STATIC() != null
            )
            readOnlyParams.add(param1)
        }
        for (param in n?.parameter()?:ArrayList()) {
            val param1 = FunctionParam(
                MCFPPType.parseFromContext(param.type(), this.field),
                param.Identifier().text,
                this,
                param.STATIC() != null
            )
            normalParams.add(param1)
        }
        parseParams()
    }

    companion object {

        private val primitiveTypes: MutableMap<String, Class<*>> = HashMap()
        init {
            primitiveTypes["boolean"] = Boolean::class.java
            primitiveTypes["byte"] = Byte::class.java
            primitiveTypes["char"] = Char::class.java
            primitiveTypes["short"] = Short::class.java
            primitiveTypes["int"] = Int::class.java
            primitiveTypes["long"] = Long::class.java
            primitiveTypes["float"] = Float::class.java
            primitiveTypes["double"] = Double::class.java
            primitiveTypes["void"] = Void::class.java
        }

        fun defaultNativeFunction(vararg args: Any?){
            throw Exception("A nativeFunction hadn't linked to a java method.")
        }

        fun getFromClass(cls: Class<*>) : ArrayList<NativeFunction>{
            val res = ArrayList<NativeFunction>()
            //获取所有带有注解MNIMethod的Java方法
            val methods = cls.methods
            for(method in methods){
                val mniRegister = method.getAnnotation(MNIRegister::class.java)
                if(mniRegister != null){
                    if(!Modifier.isStatic(method.modifiers)) {
                        LogProcessor.error("MNIMethod ${method.name} in class ${cls.name} must be static")
                        continue
                    }
                    //解析MNIMethod注解成员
                    val readOnlyType = mniRegister.readOnlyParams.map {
                        val qwq = it.split(" ", limit = 2)
                        qwq[0] to MCFPPType.parseFromIdentifier(qwq[1], Namespace.currNamespaceField)
                    }
                    val normalType = mniRegister.normalParams.map {
                        val qwq = it.split(" ", limit = 2)
                        qwq[0] to MCFPPType.parseFromIdentifier(qwq[1], Namespace.currNamespaceField)
                    }
                    val returnType = MCFPPType.parseFromIdentifier(mniRegister.returnType, Namespace.currNamespaceField)
                    //检查method的参数
                    if(method.parameterCount != readOnlyType.size + normalType.size + 1){
                        LogProcessor.error("Method ${method.name} in class ${cls.name} has wrong parameter count")
                        continue
                    }
                    val nf = NativeFunction(method.name, returnType, javaMethod = method)
                    nf.caller = mniRegister.caller
                    for(rt in readOnlyType){
                        nf.appendReadOnlyParam(rt.second, rt.first)
                    }
                    for(nt in normalType){
                        nf.appendNormalParam(nt.second, nt.first)
                    }
                    res.add(nf)
                }
            }
            return res
        }

        fun methodToString(method: Method): String {
            val params = Stream.of(*method.parameterTypes)
                .map { obj: Class<*> -> obj.name }
                .collect(Collectors.joining(","))
            return method.declaringClass.name + "#" + method.name + "(" + params + ")"
        }

        @Throws(Exception::class)
        fun stringToMethod(methodString: String): Method {
            val parts = methodString.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val className = parts[0]
            val methodSignature = parts[1]

            val paramStart = methodSignature.indexOf('(')
            val paramEnd = methodSignature.indexOf(')')

            val methodName = methodSignature.substring(0, paramStart)
            val paramTypeNames =
                methodSignature.substring(paramStart + 1, paramEnd).split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            val clazz = Class.forName(className)
            val paramTypes: Array<Class<*>?> = arrayOfNulls(paramTypeNames.size)
            for (i in paramTypeNames.indices) {
                paramTypes[i] = primitiveTypes.getOrDefault(paramTypeNames[i], Class.forName(paramTypeNames[i]))
            }

            return clazz.getMethod(methodName, *paramTypes)
        }
    }

}

//TODO 改成java用interface实现的lambda？
typealias MNIMethod = (Array<Var<*>?>, Array<Var<*>?>, CanSelectMember?, ValueWrapper<Var<*>>) -> Void

abstract class MNIMethodContainer{

    abstract fun getMNIMethod(name: String): MNIMethod

}