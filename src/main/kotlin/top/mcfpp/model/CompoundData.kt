package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIBinaryOperator
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.Var
import top.mcfpp.doc.Document
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.io.Serializable
import java.lang.Class
import java.lang.reflect.Method
import java.lang.reflect.Modifier

open class CompoundData : FieldContainer, Serializable, WithDocument {

    /**
     * 父结构
     */
    var parent = ArrayList<CompoundData>()

    /**
     * 子结构
     */
    var children = ArrayList<CompoundData>()

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
    var field: CompoundDataField

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

    open fun getType(): MCFPPType {
        if(commonType!= null) return commonType!!
        return object :MCFPPType(ArrayList(parent.map { it.getType() }.toList())){
            override val objectData: CompoundData
                get() = this@CompoundData
        }
    }

    @Transient
    override var document: Document = Document()

    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        this.namespace = namespace
        field = CompoundDataField(ArrayList())
    }

    protected constructor(){
        field = CompoundDataField(ArrayList())
    }

    open fun initialize(){}

    /**
     * 返回一个成员字段。如果没有，则从父类中寻找
     * @param key 字段名
     * @param isStatic 是否是静态成员
     * @return 如果字段存在，则返回此字段，否则返回null
     */
    fun getVar(key: String, isStatic: Boolean = false): Var<*>? {
        var re = field.getVar(key)
        val iterator = parent.iterator()
        while (re == null && iterator.hasNext()){
            re = iterator.next().getVar(key,isStatic)
        }
        return re
    }

    /**
     * 返回一个成员函数。如果没有，则从父类中寻找
     *
     * @param key 函数名
     * @param normalArgs 函数参数
     * @param isStatic 是否是静态成员
     *
     * @return 如果函数存在，则返回此函数，否则返回null
     */
    fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, isStatic: Boolean = false): Function {
        var re = field.getFunction(key, readOnlyArgs, normalArgs)
        val iterator = parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key,readOnlyArgs , normalArgs ,isStatic)
        }
        return re
    }

    /**
     * 向这个类中添加一个成员
     * @param member 要添加的成员
     */
    open fun addMember(member: Member): Boolean {
        return when (member) {
            is Function -> field.addFunction(member, false)
            is Var<*> -> field.putVar(member.identifier, member)
            is Property -> field.putProperty(member.identifier, member)
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
    open fun getAccess(compoundData: CompoundData): Member.AccessModifier{
        //是否是本类
        return if(compoundData.namespaceID == namespaceID){
            Member.AccessModifier.PRIVATE
        }else{
            //是否是子类
            if(this.isSubOf(compoundData)){
                Member.AccessModifier.PROTECTED
            }else{
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
    open fun isSubOf(compoundData: CompoundData): Boolean{
        if(this == compoundData) return true
        if(parent.size != 0){
            for (p in parent){
                if(p.namespaceID == compoundData.namespaceID || p.isSubOf(compoundData)){
                    return true
                }
            }
        }
        return false
    }

    open fun isParentOf(compoundData: CompoundData): Boolean {
        return compoundData.isSubOf(this)
    }

    open fun extends(compoundData: CompoundData): CompoundData{
        parent.add(compoundData)
        compoundData.children.add(this)
        field.parent.add(compoundData.field)
        return this
    }

    fun ifExtends(compoundData: CompoundData): Boolean{
        return parent.contains(compoundData)
    }

    fun unExtends(compoundData: CompoundData): CompoundData{
        parent.remove(compoundData)
        field.parent.remove(compoundData.field)
        return this
    }

    fun <T> mapParent(operation: (CompoundData) -> T): List<T>{
        return parent.map(operation)
    }

    fun getNativeFromClass(cls: Class<*>){
        val l = Project.currNamespace
        Project.currNamespace = this.namespace
        //获取所有带有注解MNIMethod的Java方法
        val methods = cls.methods
        for(method in methods){
            val mniFunction = method.getAnnotation(MNIFunction::class.java)
            if(mniFunction != null){
                addMNIMethod(method, mniFunction)
                continue
            }
            val mniOperator = method.getAnnotation(MNIBinaryOperator::class.java)
            if(mniOperator!= null){
                addMNIOperator(method, mniOperator)
                continue
            }
        }
        //尝试获取static ArrayList<Var<?>> getMembers()方法
        try {
            val method = cls.getMethod("getMembers")
            method.isAccessible = true
            if(Modifier.isStatic(method.modifiers) && method.returnType == ArrayList::class.java){
                val list = method.invoke(null) as ArrayList<*>
                for (item in list){
                    if(item is Member){
                        this.addMember(item)
                    }
                }
            }else{
                LogProcessor.error("Method getMembers in class ${cls.name} should be static and return ArrayList<Var<?>>")
            }
        }catch (_: NoSuchMethodException){ }
        Project.currNamespace = l
    }

    private fun addMNIOperator(method: Method, mniBinaryOperator: MNIBinaryOperator, tag: Array<String>? = null){
        if(!Modifier.isStatic(method.modifiers)) {
            LogProcessor.error("MNIMethod ${method.name} in class ${method.declaringClass.name} must be static")
            return
        }
        if(tag!= null &&!tag.contentEquals(mniBinaryOperator.tag)){
            LogProcessor.error("Tag not match in method ${method.name} in class ${method.declaringClass.name}")
            return
        }
        if(this is ObjectCompoundData){
            LogProcessor.error("Operator definition ${method.name} in class ${method.declaringClass.name} is not allowed in ObjectCompoundData")
        }
        val nf = NativeFunction(method.name, javaMethod = method)
        //解析MNIMethod注解成员
        val paramType = MCFPPType.parseFromString(mniBinaryOperator.paramType, nf.field)?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniBinaryOperator.paramType))
            MCFPPBaseType.Any
        }
        nf.appendNormalParam(paramType, "b")
        nf.returnType = MCFPPType.parseFromString(mniBinaryOperator.returnType, nf.field)?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniBinaryOperator.returnType))
            MCFPPBaseType.Any
        }
        if(nf.returnType == MCFPPBaseType.Void){
            LogProcessor.error("Operator definition ${method.name} in class ${method.declaringClass.name} must return a value")
            return
        }
        //检查method的参数
        if(method.parameterCount != 3){
            LogProcessor.error("Method ${method.name} in class ${method.declaringClass.name} has wrong parameter count")
            return
        }
        nf.caller = getType()
        field.addOperator(mniBinaryOperator.operator, nf, false)
    }

    private fun addMNIMethod(method: Method, mniRegister: MNIFunction, tag: Array<String>? = null){
        if(!Modifier.isStatic(method.modifiers)) {
            LogProcessor.error("MNIMethod ${method.name} in class ${method.declaringClass.name} must be static")
            return
        }
        if(tag != null && !tag.contentEquals(mniRegister.tag)){
            LogProcessor.error("Tag not match in method ${method.name} in class ${method.declaringClass.name}")
            return
        }
        val nf = NativeFunction(method.name, javaMethod = method)
        //解析MNIMethod注解成员
        mniRegister.genericType.map {
            nf.field.putType(it, MCFPPGenericParamType(it, arrayListOf()))
        }
        val callerType = MCFPPType.parseFromString(mniRegister.caller, nf.field)
        nf.caller = callerType?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.caller))
            MCFPPBaseType.Void
        }
        val readOnlyType = mniRegister.readOnlyParams.map {
            val qwq = it.split(" ", limit = 2)
            val type = MCFPPType.parseFromString(qwq.last(), nf.field)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(qwq[0]))
                MCFPPBaseType.Any
            }
            type to it.startsWith("static")
        }
        val normalType = mniRegister.normalParams.map {
            val qwq = it.split(" ", limit = 2)
            val type = MCFPPType.parseFromString(qwq.last(), nf.field)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(qwq[0]))
                MCFPPBaseType.Any
            }
            type to it.startsWith("static")
        }
        val returnType = MCFPPType.parseFromString(mniRegister.returnType, nf.field)?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(mniRegister.returnType))
            MCFPPBaseType.Any
        }
        nf.returnType = returnType
        var exceptedParamCount = readOnlyType.size + normalType.size
        if(returnType != MCFPPBaseType.Void){
            exceptedParamCount++
        }
        if(mniRegister.caller != "void"){
            exceptedParamCount++
        }
        //检查method的参数
        if(method.parameterCount != exceptedParamCount){
            LogProcessor.error("Method ${method.name} in class ${method.declaringClass.name} has wrong parameter count")
            return
        }
        for(rt in readOnlyType){
            nf.appendReadOnlyParam(rt.first, "p${nf.paramCount()}", rt.second)
        }
        for(nt in normalType){
            nf.appendNormalParam(nt.first, "p${nf.paramCount()}", nt.second)
        }
        //有继承
        if(mniRegister.override){
            val result = field.hasFunction(nf, true)
            if(!result){
                LogProcessor.error("Method ${nf.identifier} in class ${method.declaringClass.name} overrides nothing")
                return
            }else{
                nf.isOverride = true
                this.field.addFunction(nf, true)
            }
        }else {
            val result = this.field.addFunction(nf, false)
            if(!result){
                LogProcessor.warn("Duplicate method ${nf.identifier} in class ${method.declaringClass.name}. If you want to override it, please add @MNIRegister(override = true) to the method")
                this.field.addFunction(nf, true)
            }
        }
    }


    fun forMember(operation: (Member) -> Any?){
        field.forEachFunction { operation(it) }
        field.forEachVar { operation(it) }
        field.forEachProperty { operation(it) }
    }

    companion object {
        val emptyData = CompoundData("empty", "mcfpp")
    }

}