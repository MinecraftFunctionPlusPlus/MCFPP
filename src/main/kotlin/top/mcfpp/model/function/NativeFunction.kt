package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Native
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.type.MCFPPNotCompiledGenericType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeWithGeneric
import top.mcfpp.util.LogProcessor
import java.lang.reflect.InvocationTargetException
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

    var caller: MCFPPType = MCFPPPrivateType.Void

    var returnsConstWhenArgsConst = false

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
        javaMethod = clazzObject.getMethod(methodName, NativeCallContext::class.java)
        this.javaMethodName = name
    }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        return invoke(ArrayList(), normalArgs.values.toList(), caller)
    }

    fun invoke(readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, caller: CanSelectMember?): Var<*> {
        val list = argPass(readOnlyArgs, normalArgs,caller)
        val noWrites = javaMethod.isAnnotationPresent(top.mcfpp.mni.annotation.NoExternalWrites::class.java) ||
            javaMethod.declaringClass.isAnnotationPresent(top.mcfpp.mni.annotation.NoExternalWrites::class.java)
        val writesReceiver = javaMethod.isAnnotationPresent(top.mcfpp.mni.annotation.WritesReceiver::class.java)
        val actualCaller = if (caller is top.mcfpp.core.lang.MCAny && caller !is top.mcfpp.core.lang.MCObject)
            top.mcfpp.analysis.StorageAccess.actualView(caller) else (caller as? Var<*>)?.let(top.mcfpp.analysis.StorageAccess::read) ?: caller
        val callEffect = top.mcfpp.analysis.EffectAnalysis.recordCall(this, normalArgs, actualCaller as? Var<*>)
        val observed = if (noWrites || writesReceiver) emptyList() else top.mcfpp.analysis.StorageAccess.visibleValues(currFunction.scope) +
            readOnlyArgs + normalArgs + listOfNotNull(actualCaller as? Var<*>)
        if (!javaMethod.parameterTypes.contentEquals(arrayOf(NativeCallContext::class.java))) {
            LogProcessor.error("Native function '$identifier' must use NativeCallContext")
            return UnknownVar(identifier).apply { type = returnType; isError = true }
        }
        val receiver = if (this.caller == MCFPPPrivateType.Void) null else {
            if (actualCaller !is Var<*>) {
                LogProcessor.error("Native function '$identifier' requires a receiver")
                return UnknownVar(identifier).apply { type = returnType; isError = true }
            }
            actualCaller
        }
        if (list.any { it.isError } || receiver?.isError == true)
            return UnknownVar(identifier).apply { type = returnType; isError = true }
        top.mcfpp.analysis.StorageAccess.flush(observed)
        val context = NativeCallContext(Function.currFunction, receiver, list, returnType)
        val errors = Project.errorCount
        val invocationArgs: List<Any?> = listOf(context)
        //一定是静态的
        try {
            javaMethod.invoke(null, *invocationArgs.toTypedArray())
        } catch (e: IllegalArgumentException) {
            // 参数类型或数量不匹配
            val expected = javaMethod.parameterTypes.map { it.typeName }
            val providedTypes = invocationArgs.map { it?.javaClass?.typeName ?: "null" }
            val msg = StringBuilder().apply {
                appendLine("Error when invoking native function: ${this@NativeFunction.identifier}")
                appendLine("Reason: parameter mismatch (IllegalArgumentException)")
                appendLine("Method: ${javaMethod.declaringClass.name}.${javaMethod.name}")
                appendLine("Expected parameter types (${expected.size}): ${expected.joinToString(", ")}")
                append("Provided parameter types (${providedTypes.size}): ${providedTypes.joinToString(", ")}")
            }.toString()
            LogProcessor.error(msg, e)
        } catch (e: InvocationTargetException) {
            // 被调用方法内部抛出异常
            val target = e.targetException
            LogProcessor.error("Error when invoking native function: ${this.identifier}, caused by: ${target::class.java.name}: ${target.message}", target)
        } catch (e: Exception) {
            LogProcessor.error("Error when invoking native function: ${this.identifier}", e)
        } finally {
            if (!writesReceiver || Project.errorCount == errors)
                top.mcfpp.analysis.StorageAccess.applyEffect(observed, callEffect)
        }
        if (Project.errorCount != errors)
            return UnknownVar(identifier).apply { type = returnType; isError = true }
        returnVar = if (returnType == MCFPPPrivateType.Void) returnVar
        else context.resultAdapter() ?: run {
            LogProcessor.error("Native function '$identifier' did not publish its result")
            UnknownVar(identifier).apply { type = returnType; isError = true }
        }
        return returnVar
    }

    override fun buildParamVar() {
        for (param in normalParams) {
            if (param.type !is MCFPPNotCompiledGenericType) scope.putVar(param.identifier, param.buildVar())
        }
    }

    private fun argPass(readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, receiver: CanSelectMember?): ArrayList<Var<*>>{
        val list = ArrayList<Var<*>>()
        for (index in readOnlyParams.indices){
            val param = readOnlyParams[index]
            val bindings=readOnlyParams.take(index).mapIndexed { previous,parameter -> parameter.identifier to list[previous] }.toMap()
            val arg = if (index < readOnlyArgs.size) readOnlyArgs[index] else readonlyDefault(param,bindings)
                ?: UnknownVar(param.identifier).apply { isError=true }
            list.add(if (param.type is MCFPPNotCompiledGenericType) arg else arg.implicitCast(param.type))

        }
        val provided=LinkedHashMap<String,Var<*>>()
        normalArgs.forEachIndexed { index,value -> provided[normalParams[index].identifier]=value }
        val readonly=readOnlyParams.mapIndexed { index,param -> param.identifier to list[index] }.toMap()
        val completed=completeDefaultValue(provided,receiver,readonly)
        for (index in normalParams.indices){
            if(normalParams[index].type is MCFPPNotCompiledGenericType){
                list.add(completed.getValue(normalParams[index].identifier))
                continue
            }
            list.add(completed.getValue(normalParams[index].identifier).implicitCast(normalParams[index].type))
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
        n.owner = this.owner
        n.accessModifier = this.accessModifier
        n.returnType = (this.returnType as? MCFPPTypeWithGeneric)?.replaceGenericParam(genericParams) ?: this.returnType
        for(np in normalParams){
            val type = (np.type as? MCFPPTypeWithGeneric)?.replaceGenericParam(genericParams) ?: np.type
            val p = FunctionParam(type, np.identifier, n, np.isStatic, np.hasDefault, np.isReadOnly)
            p.defaultValue = np.defaultValue
            p.defaultTypes = np.defaultTypes
            p.defaultContext = np.defaultContext
            n.appendNormalParam(p)
            n.scope.putVar(p.identifier, p.buildVar())
        }
        for(rp in readOnlyParams){
            if (rp.identifier in genericParams) continue
            val type = (rp.type as? MCFPPTypeWithGeneric)?.replaceGenericParam(genericParams) ?: rp.type
            val p = FunctionParam(type, rp.identifier, n, rp.isStatic, rp.hasDefault, true)
            p.defaultValue = rp.defaultValue
            p.defaultTypes = rp.defaultTypes
            p.defaultContext = rp.defaultContext
            n.readOnlyParams.add(p)
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

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams()?.parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r?.parameter()?:ArrayList()){
            val (p,v) = parseParam(param, isReadOnly = true)
            readOnlyParams.add(p)
            scope.putVar(p.identifier, v)
        }
        hasDefaultValue = false
        for (param in n?.parameter().orEmpty()) {
            val (p,v) = parseParam(param)
            normalParams.add(p)
            scope.putVar(p.identifier, v)
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
        internal fun defaultNativeFunction(context: NativeCallContext){
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
                paramTypes[i] = primitiveTypes[paramTypeNames[i]]?: Class.forName(paramTypeNames[i])
            }

            return clazz.getMethod(methodName, *paramTypes)
        }

    }

}
