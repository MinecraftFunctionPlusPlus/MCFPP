package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.Var
import top.mcfpp.model.accessor.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import java.io.Serializable
import java.lang.Class
import java.lang.reflect.Modifier

open class CompoundData : FieldContainer, Serializable {

    /**
     * 父结构
     */
    var parent: List<CompoundData> = ArrayList()

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
    var field: CompoundDataField

    open val namespaceID : String
        get() = "$namespace:$identifier"

    /**
     * 获取这个容器中变量应该拥有的前缀
     * @return 其中的变量将会添加的前缀
     */
    override val prefix: String
        get() = namespace + "_data_" + identifier

    /**
     * 注解
     */
    val annotations = ArrayList<Annotation>()

    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        this.namespace = namespace
        field = CompoundDataField(ArrayList(), this)
    }

    protected constructor(){
        field = CompoundDataField(ArrayList(),this)
    }

    open fun initialize(){}

    open val getType :() -> MCFPPType = {
        MCFPPType(this, parent.map { it.getType() }.toList())
    }

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
     * @param normalParams 函数参数
     * @param isStatic 是否是静态成员
     *
     * @return 如果函数存在，则返回此函数，否则返回null
     */
    fun getFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>, isStatic: Boolean = false): Function {
        var re = field.getFunction(key, readOnlyParams, normalParams)
        val iterator = parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key,readOnlyParams , normalParams ,isStatic)
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
            is Property -> field.putProperty(member.field.identifier, member)
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
            if(this.isSub(compoundData)){
                Member.AccessModifier.PROTECTED
            }else{
                Member.AccessModifier.PUBLIC
            }
        }
    }

    /**
     * 这个复合类型是否可以被强制转换为目标类型。
     *
     * TODO
     *
     * @param compoundData 目标类型
     * @return 如果可以,返回true,反之返回false
     */
    fun canCastTo(compoundData: CompoundData): Boolean {
        if (namespaceID == compoundData.namespaceID) {
            return true
        }
        if (parent.size != 0) {
            for(p in parent){
                if(p.canCastTo(compoundData)) return true
            }
        }
        return false
    }

    /**
     * 这个复合类型是否是指定类型的子类
     *
     * @param compoundData 指定类型
     * @return 是否是指定类型的子类型
     */
    open fun isSub(compoundData: CompoundData): Boolean{
        if(parent.size != 0){
            for (p in parent){
                if(p.namespaceID == compoundData.namespaceID || p.isSub(compoundData)){
                    return true
                }
            }
        }
        return false
    }

    open fun extends(compoundData: CompoundData): CompoundData{
        (parent as ArrayList).add(compoundData)
        field.parent.add(compoundData.field)
        return this
    }

    fun ifExtends(compoundData: CompoundData): Boolean{
        return parent.contains(compoundData)
    }

    fun unExtends(compoundData: CompoundData): CompoundData{
        (parent as ArrayList).remove(compoundData)
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
            val mniRegister = method.getAnnotation(MNIFunction::class.java)
            if(mniRegister != null){
                if(!Modifier.isStatic(method.modifiers)) {
                    LogProcessor.error("MNIMethod ${method.name} in class ${cls.name} must be static")
                    continue
                }
                if(this is ObjectCompoundData && !mniRegister.isObject) continue
                //解析MNIMethod注解成员
                val readOnlyType = mniRegister.readOnlyParams.map {
                    var qwq = it.split(" ", limit = 3)
                    if(qwq.size == 3) qwq = qwq.subList(1, 3)
                    qwq[1] to MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField) to it.startsWith("static")
                }
                val normalType = mniRegister.normalParams.map {
                    var qwq = it.split(" ", limit = 3)
                    if(qwq.size == 3) qwq = qwq.subList(1, 3)
                    qwq[1] to MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField) to it.startsWith("static")
                }
                val returnType = MCFPPType.parseFromIdentifier(mniRegister.returnType, Namespace.currNamespaceField)
                var exceptedParamCount = readOnlyType.size + normalType.size
                if(returnType != MCFPPBaseType.Void){
                    exceptedParamCount++
                }
                if(mniRegister.caller != "void"){
                    exceptedParamCount++
                }
                //检查method的参数
                if(method.parameterCount != exceptedParamCount){
                    LogProcessor.error("Method ${method.name} in class ${cls.name} has wrong parameter count")
                    continue
                }
                val nf = NativeFunction(method.name, returnType, javaMethod = method)
                nf.caller = mniRegister.caller
                for(rt in readOnlyType){
                    nf.appendReadOnlyParam(rt.first.second, rt.first.first, rt.second)
                }
                for(nt in normalType){
                    nf.appendNormalParam(nt.first.second, nt.first.first, nt.second)
                }
                //有继承
                if(mniRegister.override){
                    val result = field.hasFunction(nf, true)
                    if(!result){
                        LogProcessor.error("Method ${nf.identifier} in class ${cls.name} overrides nothing")
                        continue
                    }else{
                        this.field.addFunction(nf, true)
                    }
                }else {
                    val result = this.field.addFunction(nf, false)
                    if(!result){
                        LogProcessor.warn("Duplicate method ${nf.identifier} in class ${cls.name}. If you want to override it, please add @MNIRegister(override = true) to the method")
                        this.field.addFunction(nf, true)
                    }
                }
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


    fun forMember(operation: (Member) -> Any?){
        field.forEachFunction { operation(it) }
        field.forEachVar { operation(it) }
    }

    companion object {
        val emptyData = CompoundData("empty", "mcfpp")
    }

}