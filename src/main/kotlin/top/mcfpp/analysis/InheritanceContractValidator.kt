package top.mcfpp.analysis

import org.antlr.v4.runtime.CharStreams
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.model.Generic
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeWithGeneric
import top.mcfpp.type.TypeId
import top.mcfpp.type.UnresolvedType

/** Pure declaration checks. Keep hidden ancestors: selecting a method must not erase its contracts. */
object InheritanceContractValidator {
    data class Declaration(val owner: CompoundData, val function: Function) {
        val isAbstract: Boolean get() = function.isAbstract
        val origin: String get() = "${owner.namespaceID} [${owner.inheritanceIdentity}]"
    }

    data class Slot(val declarations: List<Declaration>, val effective: List<Declaration>)
    data class Result(val slots: List<Slot>, val diagnostics: List<String>) {
        val isValid: Boolean get() = diagnostics.isEmpty()
    }

    fun check(template: DataTemplate): Result {
        val groups = groupBySignature(collectDeclarations(template))
        val diagnostics = linkedSetOf<String>()

        // Validate every declaration before hiding ancestors behind the selected implementation.
        groups.forEach { checkOverrides(template, it, diagnostics) }
        val slots = groups.mapNotNull { inheritedSlot(template, it) }
        slots.forEach { checkEffectiveImplementation(template, it, diagnostics) }

        return Result(slots, diagnostics.toList())
    }

    private fun collectDeclarations(template: DataTemplate): List<Declaration> {
        val declarations = arrayListOf<Declaration>()
        val visited = hashSetOf<TypeId>()
        val pending = java.util.ArrayDeque<CompoundData>()
        pending.add(template)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            if (!visited.add(current.inheritanceIdentity)) continue
            for (function in current.scope.functions.values.flatten()) {
                if (function.actualCallBody) continue
                val owner = function.owner ?: current
                val alreadyDeclared = declarations.any {
                    it.owner.inheritanceIdentity == owner.inheritanceIdentity &&
                        ParameterMatcher.sameSignature(it.function, function)
                }
                if (alreadyDeclared) continue
                declarations.add(Declaration(owner, function))
            }
            current.parent.asReversed().forEach(pending::addLast)
        }
        return declarations
    }

    private fun groupBySignature(declarations: List<Declaration>): List<List<Declaration>> {
        val groups = arrayListOf<MutableList<Declaration>>()
        for (declaration in declarations) {
            val group = groups.firstOrNull { ParameterMatcher.sameSignature(it.first().function, declaration.function) }
            if (group == null) groups.add(arrayListOf(declaration)) else group.add(declaration)
        }
        return groups
    }

    private fun checkOverrides(
        template: DataTemplate,
        group: List<Declaration>,
        diagnostics: MutableSet<String>
    ) {
        // A private declaration is absent from a child's inherited view, but still has
        // to satisfy the public/protected contracts visible at its own declaration site.
        for (declaration in group) {
            val function = declaration.function
            val bases = group.filter {
                it.function.accessModifier < AccessModifier.PRIVATE &&
                    moreSpecific(declaration.owner, it.owner)
            }
            if (function.isOverride && bases.isEmpty()) diagnostics.add(
                "Function '${function.identifier}' in ${declaration.origin} overrides nothing (checked from ${template.namespaceID})"
            )
            if (!function.isOverride && bases.isNotEmpty() && function.accessorField == null) diagnostics.add(
                "Function '${function.identifier}' in ${declaration.origin} requires override; inherited from ${bases.joinToString { it.origin }}"
            )
            for (base in bases) {
                val reasons = buildList {
                    if (base.function.isFinal) add("final")
                    if (!compatibleReturn(function, base.function)) add("return type")
                    if (function.accessModifier > base.function.accessModifier) add("access")
                }
                if (reasons.isNotEmpty()) diagnostics.add(
                    "Incompatible override '${function.identifier}' in ${declaration.origin} of ${base.origin}: ${reasons.joinToString()}"
                )
            }
        }
    }

    private fun inheritedSlot(template: DataTemplate, group: List<Declaration>): Slot? {
        val visible = group.filter {
            it.owner.inheritanceIdentity == template.inheritanceIdentity ||
                it.function.accessModifier < AccessModifier.PRIVATE
        }
        if (visible.isEmpty()) return null
        val effective = visible.filter { base ->
            visible.none { derived -> moreSpecific(derived.owner, base.owner) }
        }
        return Slot(visible, effective)
    }

    private fun checkEffectiveImplementation(
        template: DataTemplate,
        slot: Slot,
        diagnostics: MutableSet<String>
    ) {
        val name = slot.declarations.first().function.identifier
        if (slot.effective.size > 1) diagnostics.add(
            "Ambiguous inherited function '$name' in ${template.namespaceID} from ${slot.effective.joinToString { it.origin }}; declare an override"
        )
        if (!template.isAbstract &&
            slot.effective.any { it.isAbstract }
        ) diagnostics.add(
            "Abstract function '$name' is not implemented in ${template.namespaceID}; inherited from ${slot.effective.filter { it.isAbstract }.joinToString { it.origin }}"
        )
    }

    private fun moreSpecific(derived: CompoundData, base: CompoundData): Boolean = derived.inheritanceIdentity != base.inheritanceIdentity &&
        derived.isSubOf(base) &&
        !base.isSubOf(derived)

    private fun compatibleReturn(derived: Function, base: Function): Boolean {
        val actual = derived.returnType
        val expected = base.returnType
        if (actual.typeId == MCFPPPrivateType.Void.typeId || expected.typeId == MCFPPPrivateType.Void.typeId)
            return actual.typeId == expected.typeId
        return returnIdentity(derived, actual) == returnIdentity(base, expected) || actual.isSubOf(expected)
    }

    /** Alpha-renaming readonly type formals must also preserve an otherwise identical return. */
    private fun returnIdentity(function: Function, type: MCFPPType): Any {
        val parameters = (function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()
        val names = parameters.mapIndexed { index, parameter -> parameter.identifier to "#readonly$index" }.toMap()
        fun identity(current: MCFPPType): Any {
            if (current is MCFPPGenericParamType && current.identifier in names) return names.getValue(current.identifier)
            if (current is MCFPPTypeWithGeneric && current.generic.isNotEmpty()) {
                val id = current.typeId
                if (id is TypeId.Applied) return id.constructor to current.generic.map(::identity)
            }
            if (current is UnresolvedType) return mcfppLexer(CharStreams.fromString(current.originalTypeString)).allTokens
                .joinToString("") { token -> if (token.type == mcfppLexer.Identifier) names[token.text] ?: token.text else token.text }
            return current.typeId
        }
        return identity(type)
    }
}
