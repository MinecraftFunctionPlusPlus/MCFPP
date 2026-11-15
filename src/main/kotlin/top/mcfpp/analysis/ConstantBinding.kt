package top.mcfpp.analysis

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPLiteralType
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
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
        val literal = literalType(field.type, value)
        if (literal != null) field.type = literal
        val closed = literal?.let { CompilerValue.Typed(it.typeId, it.value) } ?: value
        field.declarationConstant = closed
        val binding = StorageAccess.ensure(field)
        binding.data.types.putAll(types)
        literal?.let { binding.data.types[it.primitive.typeId] = it.primitive }
        binding.data.types[field.type.typeId] = field.type
        field.symbol?.let { old ->
            field.symbol = old.copy(declaredType = field.type.typeId)
            binding.data.declarations[binding.place] = field.symbol!!
        }
        MCFPPType.registerSnapshotTypes(closed, binding.data.types)
        binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(field.type.typeId), ValueKnowledge.Constant(closed),
            reference = binding.data.facts.read(binding.place)?.reference))
    }

    fun literalType(type: MCFPPType, value: CompilerValue): MCFPPLiteralType? {
        val primitive = (type as? MCFPPLiteralType)?.primitive ?: type
        if (primitive !in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool, MCFPPBaseType.String,
                MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long, MCFPPNBTType.Double)) return null
        var payload = value
        while (payload is CompilerValue.Typed) payload = payload.payload
        return MCFPPLiteralType(primitive, payload)
    }

    fun protected(declaration: Symbol, place: Place, facts: FlowFacts): Boolean =
        declaration.readonly || !declaration.mutable && !declaration.isLiteral &&
            facts.read(place)?.state != ValueState.UNINITIALIZED
}
