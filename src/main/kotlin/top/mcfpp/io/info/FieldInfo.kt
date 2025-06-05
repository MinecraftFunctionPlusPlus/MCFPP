package top.mcfpp.io.info

import top.mcfpp.core.lang.Var
import top.mcfpp.model.field.CompoundDataField

data class FieldInfo(
    var vars: ArrayList<Var<*>>,
//    var vars: ArrayList<Quadruple<String, MCFPPType, Any?, String?>>,
    var functions: ArrayList<AbstractFunctionInfo<*>>,
    var properties: ArrayList<PropertyInfo>
): ModelInfo<CompoundDataField> {
    override fun get(): CompoundDataField {
        val field = CompoundDataField(ArrayList())
        vars.forEach {
            field.putVar(it.identifier, it, false)
        }
//        vars.forEach {(i, t, v, n) ->
//            val b = if(v == null){
//                t.buildUnConcrete(i)
//            }else{
//                t.build(i, v)
//            }
//            if(b is OnScoreboard){
//                b.name = n!!
//            }
//        }
        functions.forEach {
            field.addFunction(it.get(), true)
        }
        properties.forEach {
            field.putProperty(it.identifier, it.get(), true)
        }
        return field
    }

    companion object {
        fun from(field: CompoundDataField): FieldInfo {
            val functions = ArrayList<AbstractFunctionInfo<*>>()
            field.forEachFunction {
                functions.add(AbstractFunctionInfo.from(it))
            }
            val properties = ArrayList<PropertyInfo>()
            field.forEachProperty {
                properties.add(PropertyInfo.from(it))
            }
            return FieldInfo(
                ArrayList(field.allVars),
//                ArrayList(field.allVars.map {
//                    Quadruple(
//                        it.identifier,
//                        it.type,
//                        if(it is MCFPPValue<*>) it.value else null,
//                        if(it is OnScoreboard) it.name else null
//                    )
//                }),
                ArrayList(functions),
                ArrayList(properties)
            )
        }
    }

}