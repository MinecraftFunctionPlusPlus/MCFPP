package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIOperator
import top.mcfpp.core.lang.Var
import top.mcfpp.doc.Document
import top.mcfpp.mni.NativeSignatureValidator
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.WithDocument
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.splitMNIParam
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.io.Serializable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private class OwnedCompoundDataType(
    val owner: CompoundData,
    parentTypes: ArrayList<out MCFPPType>
) : MCFPPType(parentTypes) {
    override val objectData: CompoundData get() = owner
}

open class CompoundData :
    FieldContainer,
    Serializable,
    WithDocument {

    /**
     * 父结构
     */
    var parent = ArrayList<CompoundData>()

    var parentID = ArrayList<String>()

    /**
     * 标识符
     */
    lateinit var identifier: String

    /**
     * 命名空间
     */
    lateinit var namespace: String

    /**
     * 成员变量和成员函数
     */
    @Transient
    var scope: CompoundDataScope

    /**
     * 注解
     */
    val annotations = ArrayList<Annotation>()

    open val namespaceID : String
        get() = "$namespace:$identifier"

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_data_" + identifier

    var commonType: MCFPPType? = null

    /** Canonical declaration identity, independent of temporary model copies and display names. */
    internal open val inheritanceIdentity: TypeId
        get() = commonType?.typeId ?: TypeId.Declaration("compound", namespace, identifier)

    open fun getType(): MCFPPType {
        if (commonType != null) return commonType!!
        val visiting = buildingTypes.get()
        val entered = visiting.add(this)
        try {
            return OwnedCompoundDataType(this, if (entered) ArrayList(parent.map { it.getType() }) else arrayListOf())
        } finally {
            if (entered) visiting.remove(this)
            if (visiting.isEmpty()) buildingTypes.remove()
        }
    }

    @Transient
    override var document: Document = Document()

    var processedExtends: Boolean = false

    constructor(identifier: String, namespace: String = Project.currNamespace) {
        this.identifier = identifier
        this.namespace = namespace
        scope = CompoundDataScope(ArrayList())
    }

    protected constructor() {
        scope = CompoundDataScope(ArrayList())
    }

    open fun initialize() {}

    /**
     * 返回一个成员字段。如果没有，则从父类中寻找
     * @param key 字段名
     * @param isStatic 是否是静态成员
     * @return 如果字段存在，则返回此字段，否则返回null
     */
    fun getVar(key: String, isStatic: Boolean = false): Var<*>? {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
        val pending = java.util.ArrayDeque<CompoundData>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (!visited.add(current)) continue
            current.scope.getVar(key)?.let { return it }
            current.parent.asReversed().forEach(pending::addLast)
        }
        return null
    }

    /**
     * 从本地及全部继承候选中共同选择成员函数。
     *
     * @param key 函数名
     * @param normalArgs 函数参数
     * @param isStatic 是否是静态成员
     *
     * @return 唯一匹配的函数；没有匹配或存在歧义时返回 UnknownFunction
     */
    fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, isStatic: Boolean = false): Function {
        return scope.getFunction(key, readOnlyArgs, normalArgs)
    }

    /**
     * 向这个类中添加一个成员
     * @param member 要添加的成员
     */
    open fun addMember(member: Member): Boolean {
        return when (member) {
            is Function -> scope.addFunction(member, false)
            is Var<*> -> scope.putVar(member.identifier, member)
            is Property -> scope.putProperty(member.identifier, member)
            else -> TODO()
        }
    }

    /**
     * 指定类相对此类的访问权限。
     * 将会返回若在`cls`的函数中，能访问到此类哪一层成员
     *
     * @param compoundData
     * @return 返回指定类相对此类的访问权限
     */
    open fun getAccess(compoundData: CompoundData): Member.AccessModifier {
        //是否是本类
        return if (compoundData.namespaceID == namespaceID) {
            Member.AccessModifier.PRIVATE
        } else {
            //是否是子类
            if (this.isSubOf(compoundData)) {
                Member.AccessModifier.PROTECTED
            } else {
                Member.AccessModifier.PUBLIC
            }
        }
    }

    /**
     * 这个复合类型是否是指定类型的子类
     *
     * @param compoundData 指定类型
     * @return 是否是指定类型的子类型
     */
    open fun isSubOf(compoundData: CompoundData): Boolean {
        return inheritancePathTo(compoundData) != null
    }

    /** Iterative traversal also terminates for malformed graphs restored by old libraries. */
    private fun inheritancePathTo(target: CompoundData): List<CompoundData>? {
        val previous = java.util.IdentityHashMap<CompoundData, CompoundData?>()
        val pending = java.util.ArrayDeque<CompoundData>()
        previous[this] = null
        pending.add(this)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (current === target || current.inheritanceIdentity == target.inheritanceIdentity) {
                val path = arrayListOf<CompoundData>()
                var step: CompoundData? = current
                while (step != null) {
                    path.add(step)
                    step = previous[step]
                }
                path.reverse()
                return path
            }
            for (parent in current.parent) {
                if (previous.containsKey(parent)) continue
                previous[parent] = current
                pending.add(parent)
            }
        }
        return null
    }

    open fun isParentOf(compoundData: CompoundData): Boolean {
        return compoundData.isSubOf(this)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CompoundData) return false
        return this.namespaceID == other.namespaceID && this.identifier == other.identifier
    }

    open fun extends(compoundData: CompoundData): CompoundData {
        val cycle = compoundData.inheritancePathTo(this)
        if (cycle != null) {
            LogProcessor.error("Cyclic inheritance: " + (listOf(this) + cycle).joinToString(" -> ") { it.namespaceID })
            return this
        }
        if (parent.any { it.inheritanceIdentity == compoundData.inheritanceIdentity }) {
            LogProcessor.warn("Already extends template '${compoundData.identifier}'")
            return this
        }
        parent.add(compoundData)
        scope.parent.add(compoundData.scope)
        return this
    }

    fun ifExtends(compoundData: CompoundData): Boolean {
        return parent.any { it.inheritanceIdentity == compoundData.inheritanceIdentity }
    }

    fun unExtends(compoundData: CompoundData): CompoundData {
        val index = parent.indexOfFirst { it.inheritanceIdentity == compoundData.inheritanceIdentity }
        if (index >= 0) {
            val removed = parent.removeAt(index)
            scope.parent.remove(removed.scope)
        }
        return this
    }

    fun <T> mapParent(operation: (CompoundData) -> T): List<T> {
        return parent.map(operation)
    }

    fun injectedBy(cls: Class<*>) {
        val l = Project.currNamespace
        Project.currNamespace = this.namespace
        //获取所有带有注解MNIMethod的Java方法
        val methods = NativeSignatureValidator.methods(cls)
        val registration = NativeSignatureValidator.Registration()
        for (method in methods) {
            val mniFunction = method.getAnnotation(MNIFunction::class.java)
            if (mniFunction != null) {
                addMNIMethod(method, mniFunction, registration)
                continue
            }
            val mniOperator = method.getAnnotation(MNIOperator::class.java)
            if (mniOperator != null) {
                addMNIOperator(method, mniOperator)
                continue
            }
        }
        //尝试获取static ArrayList<Var<?>> getMembers()方法
        try {
            val method = cls.getMethod("getMembers")
            method.isAccessible = true
            if (Modifier.isStatic(method.modifiers) && method.returnType == ArrayList::class.java) {
                val list = method.invoke(null) as ArrayList<*>
                for (item in list) {
                    if (item is Member) {
                        this.addMember(item)
                    }
                }
            } else {
                LogProcessor.error("Method getMembers in class ${cls.name} should be static and return ArrayList<Var<?>>")
            }
        } catch (_: NoSuchMethodException) { }
        Project.currNamespace = l
    }

    private fun addMNIOperator(method: Method, mniBinaryOperator: MNIOperator, tag: Array<String>? = null) {
        if (!Modifier.isStatic(method.modifiers)) {
            LogProcessor.error("MNIMethod ${method.name} in class ${method.declaringClass.name} must be static")
            return
        }
        if (tag != null && !tag.contentEquals(mniBinaryOperator.tag)) {
            LogProcessor.error("Tag not match in method ${method.name} in class ${method.declaringClass.name}")
            return
        }
        if (!method.parameterTypes.contentEquals(arrayOf(top.mcfpp.mni.NativeCallContext::class.java))) {
            LogProcessor.error("Method ${method.name} in class ${method.declaringClass.name} must use NativeCallContext")
            return
        }
        if (this is ObjectCompoundData) {
            LogProcessor.error("Operator definition ${method.name} in class ${method.declaringClass.name} is not allowed in ObjectCompoundData")
        }
        val nf = NativeFunction(method.name, javaMethod = method)
        nf.owner = this
        //解析MNIMethod注解成员
        val paramType = if (mniBinaryOperator.paramType.isEmpty()) null else MCFPPType.parseFromString(mniBinaryOperator.paramType, nf.scope) ?: run {
            if (mniBinaryOperator.paramType == "null") {
                MCFPPPrivateType.Null
            } else {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniBinaryOperator.paramType) + " in method ${method.name} in class ${method.declaringClass.name}")
                MCFPPBaseType.Any
            }
        }
        paramType?.let { nf.appendNormalParam(paramType, "b") }
        nf.returnType = MCFPPType.parseFromString(mniBinaryOperator.returnType, nf.scope) ?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniBinaryOperator.returnType) + " in method ${method.name} in class ${method.declaringClass.name}")
            MCFPPBaseType.Any
        }
        if (nf.returnType == MCFPPPrivateType.Void) {
            LogProcessor.error("Operator definition ${method.name} in class ${method.declaringClass.name} must return a value")
            return
        }
        nf.caller = getType()
        nf.returnsConstWhenArgsConst = mniBinaryOperator.returnsConstWhenArgsConst
        scope.addOperator(mniBinaryOperator.operator, paramType, nf, false)
    }

    private fun parseMNIType(typeName: String, function: NativeFunction): MCFPPType? {
        MCFPPType.parseFromString(typeName, function.scope)?.let { return it }
        val (namespace, identifier) = typeName.splitNamespaceID()
        if (namespace == null) return null
        return (
            GlobalScope.getCanonicalTemplate(namespace, identifier)
            )?.getType()
    }

    fun getOperator(identifier: String, type: MCFPPType?): Function? {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
        var level = listOf(this)
        while (level.isNotEmpty()) {
            val nodes = level.filter { visited.add(it) }
            val functions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
            val candidates = nodes.flatMap { it.scope.operators[identifier]?.map { entry -> entry.key to entry.value } ?: emptyList() }
                .filter { functions.add(it.second) }
            when (val selected = ParameterMatcher.selectOperatorTypes(candidates, type)) {
                is ParameterMatcher.TypeSelection.Selected -> return selected.function
                is ParameterMatcher.TypeSelection.Ambiguous -> {
                    LogProcessor.error("Ambiguous operator '$identifier'")
                    return UnknownFunction(identifier)
                }
                ParameterMatcher.TypeSelection.Missing -> level = nodes.flatMap { it.parent }
            }
        }
        return null
    }

    private fun addMNIMethod(method: Method, mniRegister: MNIFunction, registration: NativeSignatureValidator.Registration, tag: Array<String>? = null) {
        if (tag != null && !tag.contentEquals(mniRegister.tag)) {
            LogProcessor.error("Tag not match in method ${method.name} in class ${method.declaringClass.name}")
            return
        }
        val nf = NativeFunction(mniRegister.identifier.ifEmpty { method.name }, javaMethod = method)
        nf.owner = this
        //解析MNIMethod注解成员
        mniRegister.genericType.map {
            nf.scope.putType(it, MCFPPGenericParamType(it, arrayListOf()))
        }
        val callerType = parseMNIType(mniRegister.caller, nf)
        nf.caller = callerType ?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.caller) + " in method ${method.name} in class ${method.declaringClass.name}")
            MCFPPPrivateType.Void
        }
        val readOnlyType = mniRegister.readOnlyParams.map {
            val typeName = it.splitMNIParam().first
            parseMNIType(typeName, nf) ?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(typeName) + " in method ${method.name} in class ${method.declaringClass.name}")
                MCFPPBaseType.Any
            }
        }
        val normalType = mniRegister.normalParams.map {
            val typeName = it.splitMNIParam().first
            parseMNIType(typeName, nf) ?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(typeName) + " in method ${method.name} in class ${method.declaringClass.name}")
                MCFPPBaseType.Any
            }
        }
        val returnType = parseMNIType(mniRegister.returnType, nf) ?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.returnType) + " in method ${method.name} in class ${method.declaringClass.name}")
            MCFPPBaseType.Any
        }
        nf.returnType = returnType
        for (rt in readOnlyType) {
            nf.appendReadOnlyParam(rt, "p${nf.paramCount()}")
        }
        for (nt in normalType) {
            nf.appendNormalParam(nt, "p${nf.paramCount()}")
        }
        if (!registration.accept(nf)) return
        nf.returnsConstWhenArgsConst = mniRegister.returnsConstWhenArgsConst
        //有继承
        if (mniRegister.override) {
            val result = scope.hasFunction(nf, true)
            if (!result) {
                LogProcessor.error("Method ${nf.identifier} in class ${method.declaringClass.name} overrides nothing")
                return
            } else {
                nf.isOverride = true
                this.scope.addFunction(nf, true)
            }
        } else {
            val result = this.scope.addFunction(nf, false)
            if (!result) {
                LogProcessor.warn("Duplicate method ${nf.identifier} in class ${method.declaringClass.name}. If you want to override it, please add @MNIRegister(override = true) to the method")
                this.scope.addFunction(nf, true)
            }
        }
    }

    fun forMember(operation: (Member) -> Any?) {
        scope.forEachFunction { operation(it) }
        scope.forEachVar { operation(it) }
        scope.forEachProperty { operation(it) }
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    companion object {
        /** Recognizes only the compiler-owned type returned by this class's default getType implementation. */
        internal fun isOwnedCompoundDataType(type: MCFPPType): Boolean = type.javaClass === OwnedCompoundDataType::class.java

        /** Reads the raw owner slot only for the exact compiler-owned type shape. */
        internal fun ownedCompoundDataTypeOwner(type: MCFPPType): CompoundData? = if (type.javaClass === OwnedCompoundDataType::class.java) (type as OwnedCompoundDataType).owner else null

        private val buildingTypes = ThreadLocal.withInitial {
            java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
        }

        val emptyData = CompoundData("empty", "mcfpp")
    }
}
