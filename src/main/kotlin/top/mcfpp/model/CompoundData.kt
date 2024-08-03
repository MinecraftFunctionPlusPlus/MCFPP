package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.annotations.MNIRegister
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPType
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
    var parent: ArrayList<CompoundData> = ArrayList()

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

    constructor(identifier: String, namespace: String = Project.currNamespace){
        this.identifier = identifier
        this.namespace = namespace
        field = CompoundDataField(null, this)
    }

    protected constructor(){
        field = CompoundDataField(null,this)
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
    fun addMember(member: Member): Boolean {
        return if (member is Function) {
            field.addFunction(member, false)
        } else if (member is Var<*>) {
            field.putVar(member.identifier, member)
        } else {
            TODO()
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
        parent.add(compoundData)
        return this
    }

    fun getNativeFunctionFromClass(cls: Class<*>){
        val l = Project.currNamespace
        Project.currNamespace = this.namespace
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
                    qwq[1] to MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField)
                }
                val normalType = mniRegister.normalParams.map {
                    val qwq = it.split(" ", limit = 2)
                    qwq[1] to MCFPPType.parseFromIdentifier(qwq[0], Namespace.currNamespaceField)
                }
                val returnType = MCFPPType.parseFromIdentifier(mniRegister.returnType, Namespace.currNamespaceField)
                //检查method的参数s
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
                //有继承
                if(mniRegister.override){
                    val result = field.hasFunction(nf)
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
        Project.currNamespace = l
    }


    fun forMember(operation: (Member) -> Any?){
        field.forEachFunction { operation(it) }
        field.forEachVar { operation(it) }
    }

}