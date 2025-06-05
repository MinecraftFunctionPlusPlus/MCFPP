package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.VectorVar
import top.mcfpp.core.lang.VectorVarConcrete
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag

class MCFPPVectorType(val dimension: Int): MCFPPType(arrayListOf(MCFPPBaseType.Any)) {

    override val instanceData by lazy {
        CompoundData("vector", "mcfpp")
    }

    override val typeName: String
        get() = "vec$dimension"

    override val nbtType: java.lang.Class<out Tag<*>>
        get() = ListTag::class.java

    override fun defaultValue() = Array(dimension){0}

    companion object {
        val regex = Regex("^vec\\d+$")
    }

    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, container: FieldContainer, value: Any?): Var<*> = VectorVarConcrete(value as Array<Int>, container, identifier)
    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> = VectorVarConcrete(value as Array<Int>, identifier)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = VectorVar(dimension, container, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = VectorVar(dimension, identifier)

}