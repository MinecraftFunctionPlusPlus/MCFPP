package top.mcfpp.io.info

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCNumber
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId

/** Declaration metadata; an ordinary field's initializer remains in its owner's AST. */
data class VarInfo(
    val identifier: String,
    val type: MCFPPType,
    val accessModifier: Member.AccessModifier,
    val isStatic: Boolean,
    val isConst: Boolean,
    val nullable: Boolean,
    val forceRuntime: Boolean,
    val annotations: List<Annotation>,
    val dataOnly: Boolean?,
    val readonlyValue: CompilerValue?,
    val readonlyTypes: Map<TypeId, MCFPPType>
) {
    fun get(owner: DataTemplate?): Var<*> {
        val value = if (readonlyValue != null) {
            val descriptors = readonlyTypes.toMutableMap().apply { putIfAbsent(type.typeId, type) }
            MCFPPType.registerSnapshotTypes(readonlyValue, descriptors)
            val restored = requireNotNull(StorageAccess.restore(type, readonlyValue, identifier, descriptors)) {
                "Cannot restore readonly declaration '$identifier'"
            }
            requireNotNull(StorageAccess.freezeReadonly(restored, identifier)) {
                "Cannot freeze restored readonly declaration '$identifier'"
            }
        } else type.buildUnConcrete(identifier)
        if (readonlyValue == null) value.type = type
        value.accessModifier = accessModifier
        value.isStatic = isStatic
        value.isConst = isConst
        value.nullable = nullable
        value.annotations.addAll(annotations)
        if (value is MCNumber<*> && dataOnly != null) value.isDataOnly = dataOnly
        value.declaredParentTemplate = owner
        if (readonlyValue == null) value.bindDeclaration(forceRuntime = forceRuntime)
        return value
    }

    companion object {
        fun from(value: Var<*>): VarInfo {
            val readonly = value.symbol?.readonly == true
            val frozen = if (readonly) requireNotNull(StorageAccess.snapshot(value)) {
                "Readonly declaration '${value.identifier}' has no complete immutable value"
            } else null
            return VarInfo(value.identifier, value.type, value.accessModifier, value.isStatic,
                value.isConst, value.nullable, value.symbol?.forceRuntime == true,
                ArrayList(value.annotations), (value as? MCNumber<*>)?.isDataOnly, frozen,
                if (readonly) LinkedHashMap(StorageAccess.boundTypes(value)) else emptyMap())
        }
    }
}
