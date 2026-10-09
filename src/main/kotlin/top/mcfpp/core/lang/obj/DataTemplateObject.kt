package top.mcfpp.core.lang.obj

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

/** Public template schema plus a typed access to its unique stored producer. */
open class DataTemplateObject : Var<DataTemplateObject> {
    final override var type: MCFPPType
    var templateType: DataTemplate
        get() = (type as MCFPPDataTemplateType).also { it.tryResolve() }.template
        set(value) { type = value.getType(); instanceFieldCache = null }
    @Transient private var instanceFieldCache: CompoundDataScope? = null
    var instanceField: CompoundDataScope
        get() = instanceFieldCache ?: templateType.scope.createDataTemplateInstance(this).also { instanceFieldCache = it }
        set(value) { instanceFieldCache = value }
    constructor(template: DataTemplate, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        type = template.getType()
    }
    constructor(other: DataTemplateObject) : super(other) { type = other.type }
    override fun doAssignedBy(b: Var<*>): DataTemplateObject { StorageAccess.write(this, b); return this }
    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun implicitCast(type: MCFPPType): Var<*> = if (canImplicitCast(type)) StorageAccess.view(this, type, diagnose = false) else buildCastErrorVar(type)
    override fun canImplicitCast(type: MCFPPType) = super.canImplicitCast(type) || type is MCFPPDataTemplateType && templateType.isSubOf(type.template)
    override fun canExplicitCast(type: MCFPPType) = super.canExplicitCast(type) || type is MCFPPDataTemplateType && (templateType.isSubOf(type.template) || templateType.isParentOf(type.template))
    override fun clone() = DataTemplateObject(this)
    override fun getTempVar() = StorageAccess.capture(this)
    override fun storeToStack() { StorageAccess.materialize(this) }
    override fun getFromStack() { StorageAccess.invalidateReads(listOf(this)) }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val declaration = templateType.scope.getVar(key) ?: return null to true
        val property = templateType.scope.getProperty(key) ?: return null to true
        val binding = StorageAccess.ensure(this)
        val location = StorageAccess.inFrame(binding, stackIndex).field(key)
        StorageAccess.registerMember(declaration, location)
        val field = StorageAccess.adapter(declaration.type, key, location)
        field.accessModifier = declaration.accessModifier
        field.declaredParentTemplate = declaration.declaredParentTemplate
        field.parent = this
        field.nullable = declaration.nullable
        return PropertyVar(property, field, this) to (accessModifier >= property.accessModifier)
    }
    fun <T : Var<*>> getMemberVarWithT(key: String, clazz: Class<T>): T? {
        val member = getMemberVar(key, Member.AccessModifier.PUBLIC).first
        val value = if (member is PropertyVar) member.get() else member
        return value?.takeIf { clazz.isInstance(it) }?.let { clazz.cast(it) }
    }
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        val member = templateType.scope.getFunction(key, readOnlyArgs, normalArgs)
        return member to (member is UnknownFunction || accessModifier >= member.accessModifier)
    }
    override fun replaceMemberVar(v: Var<*>) {
        val member = getMemberVar(v.identifier, Member.AccessModifier.PUBLIC).first
        if (member is PropertyVar) member.set(v)
    }
    override fun toCommandPart(): Command {
        val resource = GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.resource")?.scope?.getTemplate("ResourceID")?.getType()
        resource?.tryResolve()
        if (resource is MCFPPDataTemplateType && templateType.isSubOf(resource.template)) {
            val field = DataTemplate.getField(this, "id")!!
            return (if (field is PropertyVar) field.get() else field).toCommandPart()
        }
        return top.mcfpp.backend.TemplateCommandFormat.build(this) ?: super.toCommandPart()
    }
    fun isInstance(template: DataTemplate) = templateType.isSubOf(template)
    fun isInstance(namespace: String?, templateID: String) = isInstance(GlobalScope.getTemplate(namespace, templateID)!!)
}
