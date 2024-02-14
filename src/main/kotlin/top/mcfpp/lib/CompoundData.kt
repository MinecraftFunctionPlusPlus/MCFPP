package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.lang.Var

open class CompoundData : FieldContainer {

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
    lateinit var field: CompoundDataField

    /**
     * 静态变量和静态函数
     */
    lateinit var staticField: CompoundDataField

    val namespaceID : String
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
    }

    protected constructor()

    open fun initialize(){
        staticField = CompoundDataField(null, this)
        field = CompoundDataField(staticField, this)
    }

    /**
     * 返回一个成员字段。如果没有，则从父类中寻找
     * @param key 字段名
     * @param isStatic 是否是静态成员
     * @return 如果字段存在，则返回此字段，否则返回null
     */
    fun getVar(key: String, isStatic: Boolean = false): Var? {
        var re = if(isStatic){
            staticField.getVar(key)
        }else{
            field.getVar(key)
        }
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
     * @param params 函数参数
     * @param isStatic 是否是静态成员
     *
     * @return 如果函数存在，则返回此函数，否则返回null
     */
    fun getFunction(key: String, params: List<String>, isStatic: Boolean = false): Function {
        var re = if(isStatic){
            staticField.getFunction(key, params)
        }else{
            field.getFunction(key, params)
        }
        val iterator = parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key,params,isStatic)
        }
        return re
    }

    /**
     * 向这个类中添加一个成员
     * @param member 要添加的成员
     */
    fun addMember(member: Member) {
        //非静态成员
        if (!member.isStatic) {
            if (member is Function) {
                field.addFunction(member, false)
            } else if (member is Var) {
                field.putVar(member.identifier, member)
            }
            return
        }
        //静态成员
        if (member is Function) {
            staticField.addFunction(member, false)
        } else if (member is Var) {
            staticField.putVar(member.identifier, member)
        }
    }

    /**
     * 指定类相对此类的访问权限。
     * 将会返回若在`cls`的函数中，能访问到此类哪一层成员
     *
     * @param compoundData
     * @return 返回指定类相对此类的访问权限
     */
    fun getAccess(compoundData: CompoundData): Member.AccessModifier{
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
    fun isSub(compoundData: CompoundData): Boolean{
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
}