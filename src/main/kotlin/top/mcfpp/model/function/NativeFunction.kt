package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Native
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNotCompiledGenericType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeWithGeneric
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.ValueWrapper
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
    @Transient
    var javaMethod: Method

    /**
     * 引用的java方法名。
     */
    var javaMethodName: String

    val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    var caller: MCFPPType = MCFPPBaseType.Void

    /**
     * 通过一个java方法来构造一个NativeFunction，同时手动指定mcfpp方法的名字
     *
     * @param name mcfpp方法的名字
     * @param javaMethod java方法
     * @param namespace 命名空间
     */
    constructor(name: String, namespace: String = Project.currNamespace, javaMethod: Method = Companion::defaultNativeFunction.javaMethod!!) : super(name, namespace, context = null) {
        this.javaMethod = javaMethod
        this.javaMethodName = name
    }

    constructor(name: String, namespace: String = Project.currNamespace, javaMethodString: String): super(name, namespace, context = null){
        //找到方法
        val clazz = javaMethodString.substringBeforeLast(".")
        val methodName = javaMethodString.substringAfterLast(".")
        val clazzObject = Class.forName(clazz)
        javaMethod = clazzObject.getMethod(methodName)
        this.javaMethodName = name
    }

    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?): Var<*> {
        return invoke(ArrayList(), normalArgs, caller)
    }

    fun invoke(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?): Var<*> {
        val valueWrapper = ValueWrapper(returnVar)
        val list = argPass(readOnlyArgs, normalArgs)
        //一定是静态的
        try {
            javaMethod.invoke(null,
                *list.toTypedArray(),
                *if(this.caller != MCFPPBaseType.Void) arrayOf(caller) else emptyArray(),
                *if(this.returnType != MCFPPBaseType.Void) arrayOf(valueWrapper) else emptyArray()
            )
        }catch (e: Exception){
            LogProcessor.error("Error when invoking native function: ${this.identifier}", e)
        }
        returnVar = valueWrapper.value
        return returnVar
    }

    private fun argPass(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>): ArrayList<Var<*>>{
        val list = ArrayList<Var<*>>()
        for (index in readOnlyParams.indices){
            val param = readOnlyParams[index]
            val arg = if (index < readOnlyArgs.size) readOnlyArgs[index] else param.defaultVar!!.clone()
            list.add(if (param.type is MCFPPNotCompiledGenericType) arg else arg.implicitCast(param.type))

        }
        for (index in normalParams.indices){
            if(normalParams[index].type is MCFPPNotCompiledGenericType){
                list.add(normalArgs[index])
                continue
            }
            if(index < normalArgs.size){
                list.add(normalArgs[index].implicitCast(normalParams[index].type))
            }else{
                list.add(normalParams[index].defaultVar!!.clone())
            }
        }
        return list
    }

    fun appendReadOnlyParam(type: MCFPPType, identifier: String, isStatic: Boolean = false) : NativeFunction {
        readOnlyParams.add(FunctionParam(type ,identifier, this, isStatic))
        return this
    }

    fun replaceGenericParams(genericParams: Map<String, MCFPPType>) : NativeFunction{
        val n = NativeFunction(this.identifier, this.namespace, this.javaMethod)
        n.caller = this.caller
        n.returnType = this.returnType
        for(np in normalParams){
            if(np.type is MCFPPTypeWithGeneric){
                val p = FunctionParam((np.type as MCFPPTypeWithGeneric).replaceGenericParam(genericParams), np.identifier, this, np.isStatic)
                n.appendNormalParam(p)
                n.field.putVar(p.identifier, p.buildVar())
            }else{
                n.appendNormalParam(np)
                n.field.putVar(np.identifier, np.buildVar())
            }
        }
        for(rp in readOnlyParams){
            if(genericParams[rp.typeName] != null){
                n.appendReadOnlyParam(genericParams[rp.typeName]!!, rp.identifier, rp.isStatic)
            }else{
                n.appendReadOnlyParam(rp.type, rp.identifier, rp.isStatic)
            }
        }
        return n
    }

    @Override
    override fun toString(): String {
        return super.toString() + "->" + javaMethodName
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

    override fun paramCount(): Int {
        return normalParams.size + readOnlyParams.size
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + javaMethod.hashCode()
        result = 31 * result + javaMethodName.hashCode()
        result = 31 * result + readOnlyParams.hashCode()
        result = 31 * result + caller.hashCode()
        return result
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

        @Suppress("UNUSED_PARAMETER")
        internal fun defaultNativeFunction(vararg args: Any?){
            LogProcessor.error("A nativeFunction hadn't linked to a java method.")
        }

        fun methodToString(method: Method): String {
            val params = Stream.of(*method.parameterTypes)
                .map { obj: Class<*> -> obj.name }
                .collect(Collectors.joining(","))
            return method.declaringClass.name + "#" + method.name + "(" + params + ")"
        }

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

            val clazz = Project.classLoader.loadClass(className)
            val paramTypes: Array<Class<*>?> = arrayOfNulls(paramTypeNames.size)
            for (i in paramTypeNames.indices) {
                paramTypes[i] = primitiveTypes.getOrDefault(paramTypeNames[i], Class.forName(paramTypeNames[i]))
            }

            return clazz.getMethod(methodName, *paramTypes)
        }

    }

}