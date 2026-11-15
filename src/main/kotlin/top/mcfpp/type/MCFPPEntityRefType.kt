package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.EntityRefVar
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.primitive.StringTag

/** A persistent entity identity. The stored value is UUID text, independent of a template instance. */
class MCFPPEntityRefType(val templateArgument: MCFPPType) : MCFPPType(arrayListOf(MCFPPBaseType.Object)), MCFPPTypeWithGeneric {
    val templateType: MCFPPDataTemplateType get() = templateArgument as MCFPPDataTemplateType
    override val generic: List<MCFPPType> get() = listOf(templateArgument)
    override val typeId: TypeId get() = TypeId.Applied(TypeId.Builtin("EntityRef"), listOf(templateArgument.typeId))
    override val typeName: String get() = "EntityRef<${templateArgument.simpleName}>"
    override val nbtType = StringTag::class.java
    override val instanceData: CompoundData get() = templateType.template
    override fun buildUnConcrete(identifier: String): Var<*> = EntityRefVar(this, identifier)
    override fun build(identifier: String, value: Any?): Var<*> = top.mcfpp.analysis.StorageAccess.initializeLiteral(
        EntityRefVar(this, identifier), when (value) {
            is top.mcfpp.analysis.CompilerValue -> value
            is StringTag -> top.mcfpp.analysis.CompilerValue.Text(value.value)
            else -> error("EntityRef requires a UUID text representation")
        })
    override fun defaultValueVar(): Var<*> = build("default", StringTag(""))
    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPType =
        MCFPPEntityRefType((templateArgument as? MCFPPTypeWithGeneric)?.replaceGenericParam(type) ?: templateArgument)
}
