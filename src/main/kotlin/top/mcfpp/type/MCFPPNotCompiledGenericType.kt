package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor
import kotlin.reflect.KClass

class MCFPPNotCompiledGenericType(val type: KClass<out MCFPPType>): MCFPPType() {

    override val typeName: String
        get() = type.simpleName!!

    fun compile(vararg args: MCFPPType): MCFPPType {
        return type.constructors.first().call(*args)
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun build(identifier: String): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }
}