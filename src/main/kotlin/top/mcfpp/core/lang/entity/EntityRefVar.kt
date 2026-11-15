package top.mcfpp.core.lang.entity

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.EntityFacts
import top.mcfpp.lib.EntityTarget
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.*
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*

class EntityRefVar(refType: MCFPPEntityRefType, identifier: String) : SelectorVar(identifier) {
    init { type = refType }
    private val refType get() = type as MCFPPEntityRefType

    override val entityFacts: EntityFacts get() = EntityFacts(1,
        EntityTemplates.kind(refType.templateType.template) == "minecraft:player",
        EntityTemplates.kind(refType.templateType.template)?.let(::setOf))

    override fun entityTargetOrNull(): EntityTarget {
        (StorageAccess.constantEncoding(this) as? StringTag)?.let { return EntityTarget.UUIDText(MCString(it)) }
        val text = MCString()
        Function.addCommand(top.mcfpp.command.Commands.dataSetFrom(StorageAccess.ensure(text).path, StorageAccess.ensure(this).path))
        StorageAccess.publishNbt(text)
        return EntityTarget.UUIDText(text)
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val declaration = refType.templateType.template.scope.getVar(key)
        if (declaration?.isEntityMember != true) return if (declaration != null) null to true
            else EntityMembers.member(this, key, accessModifier)
        val reference = ReferenceValues.create(MCFPPNBTRefType(declaration.type), ReferenceKind.NBT_ENTITY,
            mapOf("path" to MCString(StringTag(EntityTemplates.fieldPath(declaration)))), this)
        val field = declaration.type.buildUnConcrete(key)
        Property.copyFieldMetadata(declaration, field)
        field.parent = this
        StorageAccess.bindIncomingParameter(field)
        val property = Property(key, object : AbstractAccessor() {
            override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
                val result = declaration.type.buildUnConcrete(top.mcfpp.util.TempPool.getVarIdentify())
                ReferenceAccess.readInto(reference, result, top.mcfpp.core.lang.bool.ScoreBool())
                return result
            }
        }, if (declaration.isConst) null else AnonymousNativeMutator { _, input ->
            ReferenceAccess.set(reference, input)
            field
        }).apply {
            declaredParentTemplate = declaration.declaredParentTemplate
            this.accessModifier = declaration.accessModifier
            isStatic = false
        }
        return PropertyVar(property, field, this).also { Property.copyFieldMetadata(declaration, it) } to
            (accessModifier >= declaration.accessModifier)
    }

    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                   accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        val candidates = refType.templateType.template.scope.getFunctionCandidates(key, Function.currFunction).filter { it.isEntityMember }
        val function = ParameterMatcher.select(candidates, key, readOnlyArgs, normalArgs)
            ?: return if (refType.templateType.template.scope.functions.containsKey(key)) UnknownFunction(key) to true
                else super.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)
        return function to (accessModifier >= function.accessModifier)
    }

    override fun canImplicitCast(type: MCFPPType) = TypeRelations.resolveImplicitConversion(this.type, type) != null
    override fun implicitCast(type: MCFPPType): Var<*> {
        if (!canImplicitCast(type)) return buildCastErrorVar(type)
        if (type is MCFPPEntityType) return SelectorVar().also { target ->
            target.type = EntityTypes.all.firstOrNull { it.entityKinds == entityFacts.knownKinds } ?: type
            StorageAccess.updateEntityTarget(target, entityTargetOrNull())
        }
        return StorageAccess.view(this, type, diagnose = false)
    }
    override fun canExplicitCast(type: MCFPPType): Boolean = type is MCFPPEntityRefType &&
        (this.type.isSubOf(type) || type.isSubOf(this.type))
    override fun explicitCast(type: MCFPPType): Var<*> = if (canExplicitCast(type))
        StorageAccess.view(this, type, diagnose = false) else buildCastErrorVar(type)
    override fun clone(): EntityRefVar = EntityRefVar(refType, identifier).also { it.setAs(this) }
    override fun getTempVar(): SelectorVar = StorageAccess.capture(this) as EntityRefVar
}
