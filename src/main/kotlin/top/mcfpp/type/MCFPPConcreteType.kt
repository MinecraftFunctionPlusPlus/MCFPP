package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.util.LogProcessor

open class MCFPPConcreteType(parentType: ArrayList<MCFPPType> = arrayListOf()): MCFPPType(parentType) {

    override val hasRuntimeRepresentation: Boolean get() = false

    final override val objectData: CompoundData
        get() = instanceData

    override val instanceData: CompoundData = CompoundData("unknown", "mcfpp")

    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build variable '$typeName' as the compiler cannot track its type.")
        return UnknownVar(identifier)
    }

    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = buildUnConcrete(identifier)

    object Type: MCFPPConcreteType(arrayListOf()){

        override fun buildUnConcrete(identifier: String): Var<*> = MCFPPTypeVar(identifier)

        override val typeId: TypeId = TypeId.Builtin("MCFPPConcreteType.Type")

        override val instanceData: CompoundData
            get() = data

        override val typeName: String
            get() = "type"

        override fun defaultValue() = MCFPPBaseType.Any

        override fun build(identifier: String, value: Any?): Var<*> = MCFPPTypeVar(value as MCFPPType, identifier)
    }

    object JavaVar: MCFPPConcreteType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPConcreteType.JavaVar")

        override val instanceData: CompoundData by lazy {
            CompoundData("JavaVar","mcfpp")
        }

        override val typeName: String
            get() = "JavaVar"

        override fun build(identifier: String, value: Any?): Var<*> {
            top.mcfpp.util.LogProcessor.error("Java host values have no compiler value representation")
            return top.mcfpp.core.lang.UnknownVar(identifier).apply { type = this@JavaVar; isError = true }
        }
 }

}
