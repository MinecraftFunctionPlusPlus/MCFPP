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
import top.mcfpp.analysis.StorageAccess

class TypeDataTemplateObject: Var<TypeDataTemplateObject> {

    val templateType: TypeDataTemplate

    var delegateVar: Var<*>
        get() {
            val binding = StorageAccess.ensure(this)
            return StorageAccess.adapter(templateType.typeAs, identifier, binding.copy(
                view = top.mcfpp.analysis.ValueRef.TypedView(templateType.typeAs.typeId,
                    top.mcfpp.analysis.ValueRef.Read(type.typeId, binding.place), binding.place))).also { it.parent = this }
        }
        set(value) { StorageAccess.write(delegateVar, value) }

    /**
     * 创建一个模板对象
     * @param template 模板的类型
     * @param identifier 标识符
     */
    constructor(template: TypeDataTemplate, identifier: String = TempPool.getVarIdentify()): super(identifier) {
        this.templateType = template
        this.identifier = identifier
        type = template.getType()
    }

    /**
     * 复制一个模板对象
     * @param templateObject 被复制的模板对象
     */
    constructor(templateObject: TypeDataTemplateObject) : super(templateObject) {
        templateType = templateObject.templateType
    }

    constructor(template: TypeDataTemplate, value: Any): super(){
        templateType = template
        type = template.getType()
        val source = templateType.typeAs.build(identifier, value)
        val snapshot = StorageAccess.snapshot(source)
        if (snapshot == null) isError = true else StorageAccess.initializeLiteral(this,
            top.mcfpp.analysis.CompilerValue.Typed(type.typeId,
                if (snapshot is top.mcfpp.analysis.CompilerValue.Typed) snapshot.payload else snapshot), StorageAccess.boundTypes(source))

    }

    override fun doAssignedBy(b: Var<*>): TypeDataTemplateObject {
        if(b is TypeDataTemplateObject && b.templateType == templateType){
            StorageAccess.write(this, b)
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

    override fun getTempVar(): Var<*> = StorageAccess.capture(this)

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        val member = templateType.scope.getFunction(key, readOnlyArgs, normalArgs)
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
        return StorageAccess.view(this, type)
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return super.canExplicitCast(type) || delegateVar.canExplicitCast(type)
    }

    override fun replaceMemberVar(v: Var<*>) {
        StorageAccess.write(delegateVar, v)
    }
}
