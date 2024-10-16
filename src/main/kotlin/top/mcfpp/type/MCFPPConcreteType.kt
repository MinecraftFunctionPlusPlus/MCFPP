package top.mcfpp.type

import net.querz.nbt.tag.EndTag
import net.querz.nbt.tag.Tag
import top.mcfpp.core.lang.JavaVar
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.util.LogProcessor

open class MCFPPConcreteType(objectData: CompoundData = CompoundData("unknown", "mcfpp"), parentType: List<MCFPPType> = listOf()): MCFPPType(
    objectData,
    parentType
) {
    final override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build variable '$typeName' as the compiler cannot track its type.")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build variable '$typeName' as the compiler cannot track its type.")
        return UnknownVar(identifier)
    }

    final override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build variable '$typeName' as the compiler cannot track its type.")
        return UnknownVar(identifier)
    }

    object Type: MCFPPConcreteType(parentType = listOf()){

        override val objectData: CompoundData
            get() = data

        override val typeName: String
            get() = "type"

        override fun build(identifier: String, container: FieldContainer): Var<*> =
            MCFPPTypeVar(identifier = identifier)
        override fun build(identifier: String): Var<*> = MCFPPTypeVar(identifier = identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = MCFPPTypeVar(identifier = identifier)
    }

    object JavaVar: MCFPPConcreteType(parentType = listOf(MCFPPBaseType.Any)){

        override val objectData: CompoundData
            get() = top.mcfpp.core.lang.JavaVar.data
        override val typeName: String
            get() = "JavaVar"

        override fun build(identifier: String, container: FieldContainer): Var<*> =
            JavaVar(container, null, identifier)
        override fun build(identifier: String): Var<*> = JavaVar(null, identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = JavaVar(clazz, null, identifier)
 }

}