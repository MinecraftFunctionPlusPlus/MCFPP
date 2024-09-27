package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.accessor.SimpleAccessor
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Native
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
import java.lang.Void
import java.lang.reflect.Method
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
     * 通过一个java方法来构造一个NativeFunction，同时手动指定mcfpp方法的名字
     *
     * @param name mcfpp方法的名字
     * @param javaMethod java方法
     * @param returnType 返回值的类型
     * @param namespace 命名空间
     */
    constructor(name: String, returnType: MCFPPType, namespace: String = Project.currNamespace, javaMethod: Method = Companion::defaultNativeFunction.javaMethod!!) : super(name, namespace, returnType, context = null) {
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

    override fun equals(other: Any?): Boolean {
        if (other is NativeFunction) {
            if (this.identifier == other.identifier && this.normalParams.size == other.normalParams.size && this.readOnlyParams.size == other.normalParams.size) {
                if (this.normalParams.size == 0) {
                    return true
                }
                //参数比对
                for (i in normalParams.indices) {
                    if (other.normalParams[i].type.typeName != this.normalParams[i].type.typeName) {
                        return false
                    }
                }
                //参数比对
                for (i in readOnlyParams.indices) {
                    if (other.readOnlyParams[i].type.typeName != this.readOnlyParams[i].type.typeName) {
                        return false
                    }
                }
            }else{
                return false
            }
        }
        return false
    }

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams()?.parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r?.parameter()?:ArrayList()){
            val (p,v) = parseParam(param)
            readOnlyParams.add(p)
            if(v !is MCFPPValue<*>){
                LogProcessor.error("ReadOnly params must have a concrete value")
                throw Exception()
            }
            field.putVar(p.identifier, v)
        }
        hasDefaultValue = false
        for (param in n.parameter()) {
            var (p,v) = parseParam(param)
            normalParams.add(p)
            if(v is MCFPPValue<*>) v = v.toDynamic(false)
            field.putVar(p.identifier, v)
        }
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