package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.Class
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.ObjectClass
import top.mcfpp.model.compound.UnsolvedObjectClass
import top.mcfpp.util.LogProcessor

class MCFPPObjectClassType(
    cls: ObjectClass,
    parentType: ArrayList<out MCFPPType>
): MCFPPClassType(cls, parentType) {

    override val typeName: String
        get() = "object_class(${cls.namespace}:${cls.identifier})"

    override fun tryResolve() {
        if(cls is UnsolvedObjectClass){
            cls = (cls as UnsolvedObjectClass).resolve()
        }
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName}")
        return UnknownVar(identifier)
    }

    override fun build(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var from type: $typeName")
        return UnknownVar(identifier)
    }
}