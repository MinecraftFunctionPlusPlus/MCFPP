package top.mcfpp.type

import top.mcfpp.core.lang.UnionTypeVar
import top.mcfpp.core.lang.UnionTypeVarConcrete
import top.mcfpp.core.lang.Var

class MCFPPUnionType(vararg val types: MCFPPType): MCFPPType() {
    override val typeName: String
        get() = "UnionType(${types.joinToString(", ")})"

    override fun defaultValue(): Any? {
        return types[0].defaultValue()
    }

    override fun defaultValueVar(): Var<*> {
        return types[0].defaultValueVar()
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        return UnionTypeVarConcrete(identifier, value, *types)
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        return UnionTypeVar(identifier, *types)
    }

}