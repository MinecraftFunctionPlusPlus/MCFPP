package top.mcfpp.core.lang.obj

import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class TypeDataTemplateObject: Var<TypeDataTemplateObject> {

    val templateType: TypeDataTemplate

    var delegateVar: Var<*>

    /**
     * 创建一个模板对象
     * @param template 模板的类型
     * @param identifier 标识符
     */
    constructor(template: TypeDataTemplate, identifier: String = TempPool.getVarIdentify()): super(identifier) {
        this.templateType = template
        this.identifier = identifier
        delegateVar = templateType.typeAs.build(identifier)
        delegateVar.parent = this
    }

    /**
     * 复制一个模板对象
     * @param templateObject 被复制的模板对象
     */
    constructor(templateObject: TypeDataTemplateObject) : super(templateObject) {
        templateType = templateObject.templateType
        delegateVar = templateObject.delegateVar.clone()
        delegateVar.parent = this
    }

    constructor(template: TypeDataTemplate, value: Any): super(){
        templateType = template
        delegateVar = templateType.typeAs.build(value)
        delegateVar.parent = this

    }

    override fun doAssignedBy(b: Var<*>): TypeDataTemplateObject {
        if(b is TypeDataTemplateObject && b.templateType == templateType){
            delegateVar = delegateVar.assignedBy(b)
        }else{
            LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun getFromStack() {
        delegateVar.getFromStack()
    }

    override fun storeToStack() {
        delegateVar.storeToStack()
    }

    override fun clone(): TypeDataTemplateObject {
        return TypeDataTemplateObject(this)
    }

    override fun getTempVar(): TypeDataTemplateObject {
        if(isTemp) return this
        return TypeDataTemplateObject(templateType).assignedBy(this).apply {
            isTemp = true
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        val member = templateType.field.getFunction(key, readOnlyArgs, normalArgs)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        if(key == "value"){
            return delegateVar to (accessModifier >= Member.AccessModifier.PRIVATE)
        }
        return null to true
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val qwq = super.explicitCast(type)
        if(!qwq.isError) return qwq
        val pwp = delegateVar.explicitCast(type)
        if(!pwp.isError) return pwp
        return qwq
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return super.canExplicitCast(type) || delegateVar.canExplicitCast(type)
    }

    override fun replaceMemberVar(v: Var<*>) {
        delegateVar = v
    }
}