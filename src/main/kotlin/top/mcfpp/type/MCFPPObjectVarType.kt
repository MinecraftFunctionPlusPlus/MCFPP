package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

class MCFPPObjectVarType: MCFPPType(parentType = emptyList()) {
    override val typeName: String
        get() = "ObjectVar"

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun build(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var for type: $typeName")
        return UnknownVar(identifier)
    }
}