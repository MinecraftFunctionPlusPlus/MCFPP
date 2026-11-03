package top.mcfpp.type

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.ref.ReferenceVar
import top.mcfpp.model.compound.CompoundData

/** Public references describe explicit Minecraft addresses and never inherit the pointee type. */
sealed class MCFPPReferenceType : MCFPPType(arrayListOf(MCFPPBaseType.Object)) {
    abstract val valueType: MCFPPType
    override fun buildUnConcrete(identifier: String): ReferenceVar = ReferenceVar(identifier, this)
    override fun build(identifier: String, value: Any?): Var<*> =
        StorageAccess.literal(this, value as CompilerValue, identifier)
}

object MCFPPScoreRefType : MCFPPReferenceType() {
    override val typeId: TypeId = TypeId.Builtin("ScoreRef")
    override val typeName: String = "ScoreRef"
    override val valueType: MCFPPType get() = MCFPPBaseType.Int
    override val instanceData by lazy { top.mcfpp.mni.ReferenceData.members(this) }
}

/** Writable references are invariant: identity includes the exact stored type. */
class MCFPPNBTRefType(override val valueType: MCFPPType) : MCFPPReferenceType(), MCFPPTypeWithGeneric {
    init {
        require(valueType is MCFPPGenericParamType || valueType.hasRuntimeRepresentation) {
            "NBTRef requires a type with a runtime storage layout, got $valueType"
        }
    }
    override val generic: List<MCFPPType> = listOf(valueType)
    override val typeId: TypeId get() = TypeId.Applied(TypeId.Builtin("NBTRef"), listOf(valueType.typeId))
    override val typeName: String get() = "NBTRef<${valueType.typeName}>"
    override val simpleName: String get() = typeName
    override val instanceData by lazy { top.mcfpp.mni.ReferenceData.members(this) }
    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPType =
        MCFPPNBTRefType((valueType as? MCFPPTypeWithGeneric)?.replaceGenericParam(type) ?: valueType)
}
