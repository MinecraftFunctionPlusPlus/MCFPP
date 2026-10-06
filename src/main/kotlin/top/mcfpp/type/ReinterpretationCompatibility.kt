package top.mcfpp.type

import top.mcfpp.model.Member

/** Shape compatibility affects the warning for `as`, never ordinary subtyping. */
object ReinterpretationCompatibility {
    sealed interface Result {
        object Compatible : Result
        data class Unproven(val reason: String) : Result
    }

    fun check(source: MCFPPType, target: MCFPPType): Result = check(source, target, hashSetOf())

    private fun check(source: MCFPPType, target: MCFPPType, visited: MutableSet<Pair<TypeId, TypeId>>): Result {
        if (source is MCFPPTypeDataTemplateType && source.typeAs == target ||
            target is MCFPPTypeDataTemplateType && target.typeAs == source) return Result.Compatible
        if (source == MCFPPBaseType.Any || TypeRelations.isSubtype(source, target)) return Result.Compatible
        if (!visited.add(source.typeId to target.typeId)) return Result.Compatible
        if (source !is MCFPPDataTemplateType || target !is MCFPPDataTemplateType)
            return Result.Unproven("${source.typeName} and ${target.typeName} have no proven common representation")
        if (TypeRelations.isSubtype(target, source))
            return Result.Unproven("nominal downcast has no proof of the actual source type")
        if (target.template.scope.functions.values.flatten().any { it.isAbstract && !it.isStatic })
            return Result.Unproven("target template requires an abstract implementation")
        val sourceFields = source.template.scope.allVars.filterNot { it.isStatic }.associateBy { it.identifier }
        for (required in target.template.scope.allVars.filterNot { it.isStatic }) {
            val supplied = sourceFields[required.identifier]
            if (supplied == null) {
                if (required.nullable) continue
                return Result.Unproven("missing field '${required.identifier}'")
            }
            if (supplied.accessModifier != Member.AccessModifier.PUBLIC)
                return Result.Unproven("source field '${required.identifier}' is not public")
            if (supplied.nullable && !required.nullable)
                return Result.Unproven("field '${required.identifier}' may be absent")
            if (!required.isConst) {
                if (supplied.isConst || supplied.type != required.type)
                    return Result.Unproven("writable field '${required.identifier}' requires the same type and writability")
            } else {
                // There are deliberately no numeric conversions in a representation query.
                val compatible = check(supplied.type, required.type, visited)
                if (compatible is Result.Unproven)
                    return Result.Unproven("field '${required.identifier}': ${compatible.reason}")
            }
        }
        return Result.Compatible
    }
}
