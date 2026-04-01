package top.mcfpp.io.info

import top.mcfpp.core.lang.Var
import top.mcfpp.model.scope.CompoundDataScope

data class FieldInfo(
    var vars: ArrayList<Var<*>>,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var properties: ArrayList<PropertyInfo>
): ModelInfo<CompoundDataScope> {
    override fun get(): CompoundDataScope {
        val field = CompoundDataScope(ArrayList())
        vars.forEach {
            field.putVar(it.identifier, it, true)
        }
        functions.forEach {
            field.addFunction(it.get(), true)
        }
        properties.forEach {
            field.putProperty(it.identifier, it.get(), true)
        }
        return field
    }

    companion object {
        fun from(field: CompoundDataScope): FieldInfo {
            val functions = ArrayList<AbstractFunctionInfo<*>>()
            field.forEachFunction {
                functions.add(AbstractFunctionInfo.from(it))
            }
            return FieldInfo(
                ArrayList(field.allVars.filter {
                    it.declaredParentTemplate == (DataTemplateInfo.currTemplate ?: GenericDataTemplateInfo.currTemplate)
                }),
                ArrayList(functions),
                ArrayList(field.allProperties.filter {
                    it.declaredParentTemplate == (DataTemplateInfo.currTemplate ?: GenericDataTemplateInfo.currTemplate)
                }.map { PropertyInfo.from(it) }),
            )
        }
    }

}