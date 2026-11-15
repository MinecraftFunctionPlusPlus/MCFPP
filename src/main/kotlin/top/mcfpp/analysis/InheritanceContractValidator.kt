package top.mcfpp.analysis

import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function

/** Data inheritance composes fields. Receiver functions retain their own declaration contracts. */
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
        val declarations = template.scope.functions.values.flatten()
            .filter { !it.actualCallBody && (it.owner == null || it.owner?.inheritanceIdentity == template.inheritanceIdentity) }
            .sortedWith(compareBy({ it.identifier }, { it.normalParams.joinToString { parameter -> parameter.type.typeId.toString() } }))
            .map { Declaration(template, it) }
        val diagnostics = declarations.flatMap { declaration -> buildList {
            val function = declaration.function
            if (function.isOverride) add(
                "Function '${function.identifier}' in ${declaration.origin} cannot override: template functions are receiver functions"
            )
            if (function.isAbstract && !template.isAbstract) add(
                "Abstract function '${function.identifier}' is not implemented in ${template.namespaceID}; declared in ${declaration.origin}"
            )
        } }
        return Result(declarations.map { Slot(listOf(it), listOf(it)) }, diagnostics)
    }
}
