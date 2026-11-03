package top.mcfpp.io.info

import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.FunctionMutator
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.NativeMutator
import top.mcfpp.type.TypeId

/** Completed declaration effects, separate from true local fields and initializer ASTs. */
data class CompletedInheritedFieldInfo(
    val origin: TypeId,
    val field: VarInfo,
    val property: PropertyInfo?,
    val getterOwner: InheritedPropertyOwnerInfo?,
    val setterOwner: InheritedPropertyOwnerInfo?
) {
    internal fun get(): DataTemplate.CompletedInheritedField {
        val restored = property?.get()
        when (val accessor = restored?.accessor) {
            is FunctionAccessor -> accessor.function.owner = requireNotNull(getterOwner).reference()
            is NativeAccessor -> accessor.function.owner = requireNotNull(getterOwner).reference()
            else -> Unit
        }
        when (val mutator = restored?.mutator) {
            is FunctionMutator -> mutator.function.owner = requireNotNull(setterOwner).reference()
            is NativeMutator -> mutator.function.owner = requireNotNull(setterOwner).reference()
            else -> Unit
        }
        // Registration is not publication: DataTemplate validates the real inherited slot
        // and resolves every function to a receiving declaration after normal flattening.
        return DataTemplate.CompletedInheritedField(origin, field.get(null), restored)
    }

    companion object {
        internal fun from(effect: DataTemplate.CompletedInheritedField): CompletedInheritedFieldInfo {
            val getter = when (val accessor = effect.property?.accessor) {
                is FunctionAccessor -> accessor.function
                is NativeAccessor -> accessor.function
                else -> null
            }
            val setter = when (val mutator = effect.property?.mutator) {
                is FunctionMutator -> mutator.function
                is NativeMutator -> mutator.function
                else -> null
            }
            return CompletedInheritedFieldInfo(
                effect.origin,
                VarInfo.from(effect.field),
                effect.property?.let(PropertyInfo::from),
                getter?.let(InheritedPropertyOwnerInfo::from),
                setter?.let(InheritedPropertyOwnerInfo::from)
            )
        }
    }
}

/** Full canonical identity includes a generic owner's frozen readonly arguments. */
data class InheritedPropertyOwnerInfo(val identity: TypeId, val isObject: Boolean) {
    internal fun reference(): CompoundData = if (isObject) InheritedObjectPropertyOwnerReference(identity)
    else InheritedPropertyOwnerReference(identity)

    companion object {
        /** Exact compiler-owned locator shapes; no declaration getter or identity hash is read. */
        internal fun isOwnedReference(value: CompoundData): Boolean = value.javaClass === InheritedPropertyOwnerReference::class.java ||
            value.javaClass === InheritedObjectPropertyOwnerReference::class.java

        internal fun ownedReferenceIdentity(value: CompoundData): TypeId? = if (isOwnedReference(value)) (value as InheritedPropertyOwnerReference).rawIdentity else null

        fun from(function: Function): InheritedPropertyOwnerInfo {
            val owner = requireNotNull(function.owner) { "Inherited accessor '${function.identifier}' has no declaration owner" }
            return InheritedPropertyOwnerInfo(owner.inheritanceIdentity, owner is ObjectCompoundData)
        }
    }
}

/** An unregistered locator, never a receiving declaration or a callable function owner. */
private open class InheritedPropertyOwnerReference(val rawIdentity: TypeId) : DataTemplate("<inherited-property-owner>", "<metadata>") {
    init {
        declarationFile = null
    }
    internal override val inheritanceIdentity: TypeId get() = rawIdentity
}

private class InheritedObjectPropertyOwnerReference(identity: TypeId) :
    InheritedPropertyOwnerReference(identity),
    ObjectCompoundData
