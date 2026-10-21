package top.mcfpp.io.info

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.type.MCFPPType

data class FieldInfo(
    var vars: ArrayList<VarInfo>,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var properties: ArrayList<PropertyInfo>,
    var operators: Map<String, Map<MCFPPType?, AbstractFunctionInfo<*>>>
): ModelInfo<CompoundDataScope> {
    override fun get(): CompoundDataScope = restore(null)

    internal fun get(owner: DataTemplate): CompoundDataScope = restore(owner)

    private fun restore(owner: DataTemplate?): CompoundDataScope {
        val field = CompoundDataScope(ArrayList())
        if (owner != null) owner.scope = field
        vars.forEach {
            field.putVar(it.identifier, it.get(owner), true)
        }
        functions.forEach {
            val function = it.get()
            if (owner != null) {
                function.owner = owner
                function.scope.parent.add(0, field)
            }
            field.addFunction(function, true)
        }
        properties.forEach {
            val restored = it.get()
            fun bind(function: top.mcfpp.model.function.Function): top.mcfpp.model.function.Function {
                if (owner != null) {
                    function.owner = owner
                    if (field !in function.scope.parent) function.scope.parent.add(0, field)
                    if (function is top.mcfpp.model.function.NativeFunction) function.caller = owner.getType()
                }
                // Only a declaration of this exact target may replace the independently
                // restored accessor. In particular native property helpers need not be
                // registered and may share their name/signature with ordinary methods.
                return field.functions[function.identifier].orEmpty().singleOrNull { candidate ->
                    candidate.owner === owner &&
                        top.mcfpp.model.function.ParameterMatcher.sameSignature(candidate, function) &&
                        candidate.returnType.typeId == function.returnType.typeId &&
                        candidate.isStatic == function.isStatic && when {
                            candidate is top.mcfpp.model.function.NativeFunction && function is top.mcfpp.model.function.NativeFunction ->
                                candidate.javaMethod == function.javaMethod && candidate.caller.typeId == function.caller.typeId
                            candidate is top.mcfpp.model.function.NativeFunction || function is top.mcfpp.model.function.NativeFunction -> false
                            else -> candidate.javaClass == function.javaClass && function.ast != null && candidate.ast === function.ast
                        }
                } ?: function
            }
            val getter = when (val accessor = restored.accessor) {
                is top.mcfpp.model.property.FunctionAccessor -> top.mcfpp.model.property.FunctionAccessor(bind(accessor.function))
                is top.mcfpp.model.property.NativeAccessor -> top.mcfpp.model.property.NativeAccessor(bind(accessor.function) as top.mcfpp.model.function.NativeFunction)
                else -> accessor
            }
            val setter = when (val mutator = restored.mutator) {
                is top.mcfpp.model.property.FunctionMutator -> top.mcfpp.model.property.FunctionMutator(bind(mutator.function))
                is top.mcfpp.model.property.NativeMutator -> top.mcfpp.model.property.NativeMutator(bind(mutator.function) as top.mcfpp.model.function.NativeFunction)
                else -> mutator
            }
            val property = top.mcfpp.model.property.Property(restored.identifier, getter, setter).apply {
                accessModifier = restored.accessModifier
                isFinal = restored.isFinal
                isStatic = field.getVar(identifier)?.isStatic ?: restored.isStatic
            }
            if (owner != null) property.declaredParentTemplate = owner
            field.putProperty(it.identifier, property, true)
        }
        operators.forEach { (symbol, overloads) ->
            overloads.forEach { (type, info) ->
                val function = info.get()
                if (owner != null) {
                    function.owner = owner
                    function.scope.parent.add(0, field)
                }
                field.addOperator(symbol, type, function, true)
            }
        }
        return field
    }

    companion object {
        fun from(field: CompoundDataScope, owner: DataTemplate): FieldInfo {
            val functions = ArrayList<AbstractFunctionInfo<*>>()
            field.forEachFunction {
                if (!it.actualCallBody && it.owner == owner) functions.add(AbstractFunctionInfo.from(it))
            }
            return FieldInfo(
                ArrayList(field.allVars.filter {
                    it.declaredParentTemplate == owner
                }.map(VarInfo::from)),
                ArrayList(functions),
                ArrayList(field.allProperties.filter {
                    it.declaredParentTemplate == owner
                }.map { PropertyInfo.from(it) }),
                field.operators.mapValues { (_, overloads) ->
                    overloads.filterValues { it.owner == owner && !it.actualCallBody }
                        .mapValues { (_, function) -> AbstractFunctionInfo.from(function) }
                },
            )
        }
    }

}
