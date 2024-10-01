package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

open class MCFPPConcreteType(objectData: CompoundData = CompoundData("unknown", "mcfpp"), parentType: List<MCFPPType> = listOf()): MCFPPType(objectData, parentType) {

    final override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build $typeName that compiler cannot track.")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build $typeName that compiler cannot track.")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build $typeName that compiler cannot track.")
        return UnknownVar(identifier)
    }

}