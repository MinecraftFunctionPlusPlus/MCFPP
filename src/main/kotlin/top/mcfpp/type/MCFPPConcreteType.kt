package top.mcfpp.type

import top.mcfpp.core.lang.JavaVar
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.util.LogProcessor

open class MCFPPConcreteType(parentType: ArrayList<MCFPPType> = arrayListOf()): MCFPPType(parentType) {

    final override val objectData: CompoundData
        get() = concreteInstanceData

    override val concreteInstanceData: CompoundData = CompoundData("unknown", "mcfpp")

    final override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build variable '$typeName' as the compiler cannot track its type.")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build variable '$typeName' as the compiler cannot track its type.")
        return UnknownVar(identifier)
    }

    object Type: MCFPPConcreteType(arrayListOf()){

        override val concreteInstanceData: CompoundData
            get() = data

        override val typeName: String
            get() = "type"

        override fun defaultValue() = MCFPPBaseType.Any

        override fun build(identifier: String, value: Any?): Var<*> = MCFPPTypeVar(value as MCFPPType, identifier)
    }

    object JavaVar: MCFPPConcreteType(arrayListOf(MCFPPBaseType.Any)){

        override val concreteInstanceData: CompoundData by lazy {
            CompoundData("JavaVar","mcfpp")
        }

        override val typeName: String
            get() = "JavaVar"

        override fun build(identifier: String, value: Any?): Var<*> = JavaVar(value, identifier)
 }

}