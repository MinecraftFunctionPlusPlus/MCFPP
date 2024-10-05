package top.mcfpp.type

import net.querz.nbt.tag.Tag
import top.mcfpp.core.lang.UnionTypeVar
import top.mcfpp.core.lang.UnionTypeVarConcrete
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

class MCFPPUnionType(vararg val types: MCFPPType): MCFPPType() {
    override val typeName: String
        get() = "UnionType(${types.joinToString(", ")})"

    override fun defaultValue(): Tag<*> {
        return types[0].defaultValue()
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        return UnionTypeVarConcrete(container.prefix + identifier, defaultValue(), *types.map { it.build(identifier) }.toTypedArray())
    }

    override fun build(identifier: String): Var<*> {
        return UnionTypeVarConcrete(identifier, defaultValue(), *types.map { it.build(identifier) }.toTypedArray())
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("UnionType can only be used in data template")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        return UnionTypeVarConcrete(container.prefix + identifier, defaultValue(), *types.map { it.build(identifier) }.toTypedArray())
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        return UnionTypeVar(identifier, *types.map { it.buildUnConcrete(identifier) }.toTypedArray())
    }

    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("UnionType can only be used in data template")
        return UnknownVar(identifier)
    }

}