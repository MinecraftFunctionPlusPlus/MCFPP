package top.mcfpp.type

/** Pure language queries: these never build variables, emit commands, or mutate scopes. */
object TypeRelations {
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
        if (source is MCFPPDeclaredConcreteType) return subtype(source.type, target, visited)
        if (target is MCFPPDeclaredConcreteType) return subtype(source, target.type, visited)
        if (!source.isValueType || !target.isValueType) return false
        if (target == MCFPPBaseType.Object) return true
        if (source is MCFPPUnionType) return source.types.all { subtype(it, target, HashSet(visited)) }
        if (target is MCFPPUnionType) return target.types.any { subtype(source, it, HashSet(visited)) }
        // Mutable containers are invariant. ImmutableList currently exposes mutation in its MNI
        // table too, so covariance is not justified for that interface either.
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
