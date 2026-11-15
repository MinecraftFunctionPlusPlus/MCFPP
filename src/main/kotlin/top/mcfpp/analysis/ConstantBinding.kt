package top.mcfpp.analysis

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

/** Language-level constant rules are independent of optional backend folding. */
internal object ConstantBinding {
    fun semanticSnapshot(value: Var<*>): CompilerValue? {
        return StorageAccess.snapshot(value)
    }
    fun requireValue(name: String, value: CompilerValue?): Boolean {
        if (value != null) return true
        LogProcessor.error("Constant '$name' requires a complete compile-time initializer")
        return false
    }

    fun restore(field: Var<*>): Var<*>? = field.declarationConstant?.let {
        StorageAccess.restore(field.type, it, field.identifier, StorageAccess.boundTypes(field))
    }

    fun bindDeclaration(field: Var<*>, value: CompilerValue, types: Map<top.mcfpp.type.TypeId, MCFPPType>) {
        field.declarationConstant = value
        val binding = StorageAccess.ensure(field)
        binding.data.types.putAll(types)
        MCFPPType.registerSnapshotTypes(value, binding.data.types)
        binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(field.type.typeId), ValueKnowledge.Constant(value)))
    }

    fun protected(declaration: Symbol, place: Place, facts: FlowFacts): Boolean =
        declaration.readonly || !declaration.mutable && !declaration.isLiteral &&
            facts.read(place)?.state != ValueState.UNINITIALIZED
}
