package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.nbt.tags.Tag

class MCFPPDeclaredConcreteType(val type: MCFPPType): MCFPPConcreteType(arrayListOf(type)) {
    override val hasRuntimeRepresentation: Boolean get() = type.hasRuntimeRepresentation

    override val typeId: TypeId get() = type.typeId

    override val typeName: String
        get() = type.typeName

    override val nbtType: Class<out Tag<*>>
        get() = type.nbtType

    override fun defaultValue() = type.defaultValue()

    override fun build(identifier: String, value: Any?): Var<*> = type.build(identifier, value).apply { type = this@MCFPPDeclaredConcreteType }
}