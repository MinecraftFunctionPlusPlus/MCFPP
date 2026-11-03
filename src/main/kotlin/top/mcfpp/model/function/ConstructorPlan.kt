package top.mcfpp.model.function

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.TypeId
import java.util.Collections

/** Declaration-only selection. Executing an owner stage must reuse the top-level receiver. */
class ConstructorPlan private constructor(
    val template: DataTemplate,
    ancestors: List<Stage>
) {
    /** Parent-first order, excluding the constructor selected by the top-level call. */
    val ancestors: List<Stage> = Collections.unmodifiableList(ArrayList(ancestors))

    /** A null constructor denotes an implicit empty stage, including abstract ancestors. */
    data class Stage(val owner: DataTemplate, val constructor: DataTemplateConstructor?)

    data class Result(val plan: ConstructorPlan?, val diagnostics: List<String>) {
        val isValid: Boolean get() = plan != null && diagnostics.isEmpty()
    }

    /** The caller has already selected this constructor using its actual arguments. */
    fun stages(constructor: DataTemplateConstructor): List<Stage> {
        require(constructor.data.inheritanceIdentity == template.inheritanceIdentity) {
            "Constructor belongs to a different initialization owner"
        }
        return Collections.unmodifiableList(ArrayList(ancestors).apply { add(Stage(template, constructor)) })
    }

    companion object {
        /** Does not evaluate defaults, create a receiver, or call canInstantiate on ancestors. */
        fun build(template: DataTemplate): Result {
            val diagnostics = linkedSetOf<String>()
            val state = hashMapOf<TypeId, Int>()
            val active = arrayListOf<DataTemplate>()
            val ordered = arrayListOf<DataTemplate>()
            val pending = ArrayDeque<Pair<DataTemplate, Boolean>>()
            pending.addLast(template to false)
            while (pending.isNotEmpty()) {
                val (owner, leaving) = pending.removeLast()
                val identity = owner.inheritanceIdentity
                if (leaving) {
                    state[identity] = 2
                    active.removeAt(active.lastIndex)
                    ordered.add(owner)
                    continue
                }
                when (state[identity]) {
                    2 -> continue
                    1 -> {
                        val start = active.indexOfFirst { it.inheritanceIdentity == identity }
                        val path = active.drop(start.coerceAtLeast(0)) + owner
                        diagnostics.add(
                            "Cannot plan initialization of ${label(template)}: inheritance cycle " +
                                path.joinToString(" -> ", transform = ::label)
                        )
                        continue
                    }
                }
                state[identity] = 1
                active.add(owner)
                pending.addLast(owner to true)
                owner.parent.asReversed().filterIsInstance<DataTemplate>().forEach {
                    pending.addLast(it to false)
                }
            }
            if (diagnostics.isNotEmpty()) return Result(null, diagnostics.toList())

            val stages = arrayListOf<Stage>()
            for (owner in ordered.dropLast(1)) {
                if (owner.constructors.isEmpty()) {
                    stages.add(Stage(owner, null))
                    continue
                }
                val access = template.getAccess(owner)
                val accessible = owner.constructors.filter { access >= it.accessModifier }
                when (val selected = ParameterMatcher.selectConstructor(accessible, emptyList())) {
                    is ParameterMatcher.TypeSelection.Selected ->
                        stages.add(Stage(owner, selected.function as DataTemplateConstructor))
                    is ParameterMatcher.TypeSelection.Ambiguous -> diagnostics.add(
                        "Ambiguous parent constructor for ${label(owner)} while initializing ${label(template)}: " +
                            ParameterMatcher.ambiguity(owner.identifier, selected.functions)
                    )
                    ParameterMatcher.TypeSelection.Missing -> {
                        val reason = if (accessible.isEmpty()) "No accessible parent constructor" else
                            "No parent constructor accepts empty arguments"
                        diagnostics.add(
                            "$reason for ${label(owner)} while initializing ${label(template)}; " +
                                "requires explicit parent constructor binding; current syntax does not provide it. " +
                                "Declared constructors: " + owner.constructors.joinToString { constructor ->
                                    "${constructor.accessModifier} ${owner.identifier}(" +
                                        constructor.normalParams.joinToString { parameter ->
                                            "${parameter.identifier}: ${parameter.type.typeName}" +
                                                if (parameter.hasDefault) " = <default>" else " (required)"
                                        } + ")"
                                }
                        )
                    }
                }
            }
            return if (diagnostics.isEmpty()) Result(ConstructorPlan(template, stages), emptyList())
            else Result(null, diagnostics.toList())
        }

        private fun label(template: DataTemplate) = "${template.namespaceID} [${template.inheritanceIdentity}]"
    }
}
