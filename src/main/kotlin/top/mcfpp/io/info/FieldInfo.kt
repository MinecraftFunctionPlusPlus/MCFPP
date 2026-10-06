package top.mcfpp.io.info

import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.CompoundDataScope

data class FieldInfo(
    var vars: ArrayList<Var<*>>,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var properties: ArrayList<PropertyInfo>
): ModelInfo<CompoundDataScope> {
    override fun get(): CompoundDataScope = restore(null)

    internal fun get(owner: DataTemplate): CompoundDataScope = restore(owner)

    private fun restore(owner: DataTemplate?): CompoundDataScope {
        val field = CompoundDataScope(ArrayList())
        if (owner != null) owner.scope = field
        vars.forEach {
            if (owner != null) it.declaredParentTemplate = owner
            field.putVar(it.identifier, it, true)
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
            val property = it.get()
            if (owner != null) property.declaredParentTemplate = owner
            field.putProperty(it.identifier, property, true)
        }
        return field
    }

    companion object {
        fun from(field: CompoundDataScope, owner: DataTemplate): FieldInfo {
            val functions = ArrayList<AbstractFunctionInfo<*>>()
            field.forEachFunction {
                functions.add(AbstractFunctionInfo.from(it))
            }
            return FieldInfo(
                ArrayList(field.allVars.filter {
                    it.declaredParentTemplate == owner
                }),
                ArrayList(functions),
                ArrayList(field.allProperties.filter {
                    it.declaredParentTemplate == owner
                }.map { PropertyInfo.from(it) }),
            )
        }
    }

}
