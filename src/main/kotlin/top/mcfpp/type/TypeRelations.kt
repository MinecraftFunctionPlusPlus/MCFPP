package top.mcfpp.type

/** Pure language queries: these never build variables, emit commands, or mutate scopes. */
object TypeRelations {
    fun arrayElementType(type: TypeId): MCFPPType? = when (type) {
        MCFPPNBTType.ByteArray.typeId -> MCFPPNBTType.Byte
        MCFPPNBTType.IntArray.typeId -> MCFPPBaseType.Int
        MCFPPNBTType.LongArray.typeId -> MCFPPNBTType.Long
        else -> null
    }

    fun checkReinterpretation(source: MCFPPType, target: MCFPPType): ReinterpretationCompatibility.Result =
        ReinterpretationCompatibility.check(source, target)

    enum class Conversion(val rank: Int) {
        EXACT(0), NOMINAL(1), INT_TO_FLOAT(2), OBJECT(3), ANY(4)
    }

    fun isSubtype(source: MCFPPType, target: MCFPPType): Boolean =
        subtype(source, target, hashSetOf())

    private fun subtype(source: MCFPPType, target: MCFPPType, visited: MutableSet<Pair<TypeId, TypeId>>): Boolean {
        if (source.typeId == target.typeId) return true
        if (!visited.add(source.typeId to target.typeId)) return false
        if (!source.isValueType || !target.isValueType) return false
        if (target == MCFPPBaseType.Object) return true
        if (source is MCFPPDataTemplateType && target is MCFPPDataTemplateType &&
            target.typeId == top.mcfpp.model.compound.DataTemplate.baseDataTemplate.getType().typeId) return true
        if (source is MCFPPUnionType) return source.types.all { subtype(it, target, HashSet(visited)) }
        if (target is MCFPPUnionType) return target.types.any { subtype(source, it, HashSet(visited)) }
        val sourceTemplate = (source as? MCFPPDataTemplateType)?.template as? top.mcfpp.model.compound.CompiledGenericDataTemplate
        val targetTemplate = (target as? MCFPPDataTemplateType)?.template as? top.mcfpp.model.compound.CompiledGenericDataTemplate
        if (sourceTemplate != null && targetTemplate != null &&
            sourceTemplate.originTemplate !is top.mcfpp.model.compound.ObjectCompoundData &&
            sourceTemplate.originTemplate.getType().typeId == targetTemplate.originTemplate.getType().typeId) {
            return sourceTemplate.originTemplate.readOnlyParams.indices.all { index ->
                val left = sourceTemplate.args[index]
                val right = targetTemplate.args[index]
                when (sourceTemplate.originTemplate.readOnlyParams[index].variance) {
                    top.mcfpp.model.compound.DeclarationVariance.INVARIANT ->
                        (source.typeId as TypeId.Specialized).arguments[index] ==
                            (target.typeId as TypeId.Specialized).arguments[index]
                    top.mcfpp.model.compound.DeclarationVariance.OUT ->
                        left is top.mcfpp.core.lang.MCFPPTypeVar && right is top.mcfpp.core.lang.MCFPPTypeVar &&
                            subtype(left.value, right.value, HashSet(visited))
                    top.mcfpp.model.compound.DeclarationVariance.IN ->
                        left is top.mcfpp.core.lang.MCFPPTypeVar && right is top.mcfpp.core.lang.MCFPPTypeVar &&
                            subtype(right.value, left.value, HashSet(visited))
                }
            }
        }
        // Container arguments remain invariant, including the read-only list interface.
        if (source is MCFPPTypeWithGeneric && target is MCFPPTypeWithGeneric) return false
        return source.parentType.any { subtype(it, target, HashSet(visited)) }
    }

    fun resolveImplicitConversion(source: MCFPPType, target: MCFPPType): Conversion? {
        if (source.typeId == target.typeId) return Conversion.EXACT
        if (!source.isValueType || !target.isValueType) return null
        if (target == MCFPPBaseType.Object) return Conversion.OBJECT
        if (target == MCFPPBaseType.Any) return Conversion.ANY
        if (source == MCFPPBaseType.Int && target == MCFPPBaseType.Float) return Conversion.INT_TO_FLOAT
        if (isSubtype(source, target)) return Conversion.NOMINAL
        return null
    }

    fun resolveOperator(operation: String, left: TypeId, right: TypeId): TypeId? {
        val int = MCFPPBaseType.Int.typeId
        val float = MCFPPBaseType.Float.typeId
        val bool = MCFPPBaseType.Bool.typeId
        if (left == bool && right == bool && operation in setOf("&&", "||", "==", "!=")) return bool
        if (left !in setOf(int, float) || right !in setOf(int, float)) return null
        if (operation in setOf("<", ">", "<=", ">=", "==", "!=")) return bool
        if (operation !in setOf("+", "-", "*", "/", "%")) return null
        return if (left == float || right == float) float else int
    }

    /** Numeric NBT encodings have no daily arithmetic operators. */
    fun resolveNumericOperator(operation: String, left: MCFPPType, right: MCFPPType): MCFPPType? {
        if (operation !in setOf("+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!=")) return null
        val numeric = setOf(MCFPPBaseType.Int, MCFPPBaseType.Float)
        if (left !in numeric || right !in numeric) return null
        if (operation in setOf("<", ">", "<=", ">=", "==", "!=")) return MCFPPBaseType.Bool
        return if (left == MCFPPBaseType.Float || right == MCFPPBaseType.Float) MCFPPBaseType.Float else MCFPPBaseType.Int
    }
}
