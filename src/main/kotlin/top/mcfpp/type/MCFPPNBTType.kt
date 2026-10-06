package top.mcfpp.type

import top.mcfpp.core.lang.ImmutableList
import top.mcfpp.core.lang.ImmutableListConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.mni.*
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.LongTag

/**
 * 以NBT为底层的类型，包括普通的NBT类型，以及由nbt实现的map，list和dict
 */
class MCFPPNBTType {
    object NBT : MCFPPType(arrayListOf(MCFPPBaseType.Object)) {

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.NBT")

        override val instanceData by lazy {
            CompoundData("nbt","mcfpp").apply {
                commonType = NBT
                extends(MCFPPBaseType.Any.instanceData)
                injectedBy(NBTBasedDataData::class.java)
            }
        }

        override val concreteInstanceData by lazy {
            CompoundData("nbt","mcfpp").apply {
                commonType = NBT
                extends(MCFPPBaseType.Any.instanceData)
                injectedBy(NBTBasedDataConcreteData::class.java)
            }
        }

        override val typeName: String
            get() = "nbt"

        override fun defaultValue() = IntTag(0)

        override fun build(identifier: String, value: Any?): Var<*> = NBTBasedDataConcrete(value as Tag<*>, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = NBTBasedData(identifier)

    }

    object Byte: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.Byte")

        override val instanceData by lazy {
            CompoundData("byte","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData by lazy {
            CompoundData("byte","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val typeName: String
            get() = "byte"

        override fun defaultValue() = 0.toByte()

        override fun build(identifier: String, value: Any?): Var<*> = MCByteConcrete(value as kotlin.Byte, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = MCByte(identifier)
    }

    object Short: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.Short")

        override val instanceData by lazy {
            CompoundData("short","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData by lazy {
            CompoundData("short","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val typeName: String
            get() = "short"

        override fun defaultValue() = 0.toShort()

        override fun build(identifier: String, value: Any?): Var<*> = MCShortConcrete(value as kotlin.Short, identifier)

        override fun buildUnConcrete(identifier: String): Var<*> = MCShort(identifier)
    }

    object Long: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.Long")

        override val instanceData by lazy {
            CompoundData("long","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData by lazy {
            CompoundData("long","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }


        override val typeName: String
            get() = "long"

        override fun defaultValue() = LongTag(0)

        override fun build(identifier: String, value: Any?): Var<*> = MCLongConcrete(value as LongTag, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = MCLong(identifier)
    }

    object Double: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.Double")

        override val instanceData by lazy {
            CompoundData("double","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData by lazy {
            CompoundData("double","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val typeName: String
            get() = "double"

        override fun defaultValue() = DoubleTag(0.0)

        override fun build(identifier: String, value: Any?): Var<*> = MCDoubleConcrete(value as DoubleTag, identifier)

        override fun buildUnConcrete(identifier: String): Var<*> = MCDouble(identifier)
    }

    object ByteArray: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.ByteArray")

        override val instanceData by lazy {
            CompoundData("ByteArray","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData get() = instanceData


        override val typeName: String
            get() = "ByteArray"

        override fun defaultValue() = ByteArrayTag()

        override fun build(identifier: String, value: Any?): Var<*> = NBTByteArrayConcrete(value as ByteArrayTag, identifier)

        override fun buildUnConcrete(identifier: String): Var<*> = NBTByteArray(identifier)
    }

    object IntArray: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.IntArray")

        override val instanceData by lazy {
            CompoundData("IntArray","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData get() = instanceData


        override val typeName: String
            get() = "IntArray"

        override fun defaultValue() = IntArrayTag()

        override fun build(identifier: String, value: Any?): Var<*> = NBTIntArrayConcrete(value as IntArrayTag, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = NBTIntArray(identifier)

    }

    object LongArray: MCFPPType(arrayListOf(MCFPPBaseType.Object)){

        override val typeId: TypeId = TypeId.Builtin("MCFPPNBTType.LongArray")

        override val instanceData by lazy {
            CompoundData("LongArray","mcfpp").apply {
                extends(NBT.instanceData)
            }
        }

        override val concreteInstanceData get() = instanceData


        override val typeName: String
            get() = "LongArray"

        override fun defaultValue() = LongArrayTag()
        override fun build(identifier: String, value: Any?): Var<*> = NBTLongArrayConcrete(value as LongArrayTag, identifier)

        override fun buildUnConcrete(identifier: String): Var<*> = NBTLongArray(identifier)
    }

}

class MCFPPListType(
    g: MCFPPType = MCFPPBaseType.Any
): MCFPPType(arrayListOf(MCFPPBaseType.Object)), MCFPPTypeWithGeneric{

    // Wildcard is the internal empty-list literal, which has an ordinary empty NBT encoding.
    override val hasRuntimeRepresentation: Boolean get() = generic[0] === MCFPPPrivateType.Wildcard || generic.all { it.hasRuntimeRepresentation }

    override val typeId: TypeId
        get() = TypeId.Applied(TypeId.Builtin(typeName), generic.map { it.typeId })

    override val generic: List<MCFPPType> = listOf(g)

    override val objectData: CompoundData
        get() = NBTList.data

    override val instanceData get() = NBTList.data
    override val concreteInstanceData get() = instanceData

    override val typeName: String
        get() = "list"

    override val nbtType: Class<out Tag<*>>
        get() = ListTag::class.java

    override fun defaultValue() = ArrayList<Var<*>>()
    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> = NBTListConcrete(value as ArrayList<Var<*>>, identifier, generic[0])
    override fun buildUnConcrete(identifier: String): Var<*> = NBTList(identifier, generic[0])

    override fun toString(): String {
        return "list[${generic[0].typeName}]"
    }

    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPListType {
        if (generic[0] is MCFPPGenericParamType) {
            val g = generic[0] as MCFPPGenericParamType
            if(type.containsKey(g.identifier)){
                return MCFPPListType(type[g.identifier]!!)
            }else{
                throw IllegalArgumentException("No generic param ${g.identifier}")
            }
        }else if(generic[0] is MCFPPTypeWithGeneric){
            return MCFPPListType((generic[0] as MCFPPTypeWithGeneric).replaceGenericParam(type))
        }
        return this
    }




}

class MCFPPImmutableListType(
    g: MCFPPType = MCFPPBaseType.Any
): MCFPPType(arrayListOf(MCFPPBaseType.Object)), MCFPPTypeWithGeneric{

    override val hasRuntimeRepresentation: Boolean get() = generic.all { it.hasRuntimeRepresentation }

    override val typeId: TypeId
        get() = TypeId.Applied(TypeId.Builtin(typeName), generic.map { it.typeId })

    override val generic: List<MCFPPType> = listOf(g)

    override val instanceData get() = ImmutableList.data
    override val concreteInstanceData get() = instanceData

    override val typeName: String
        get() = "ImmutableList"

    override val nbtType: Class<out Tag<*>>
        get() = ListTag::class.java

    override fun defaultValue() = ArrayList<Var<*>>()
    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> = ImmutableListConcrete(value as ArrayList<Var<*>>, identifier, generic[0])
    override fun buildUnConcrete(identifier: String): Var<*> = ImmutableList(identifier, generic[0])

    override fun toString(): String {
        return "ImmutableList[${generic[0].typeName}]"
    }

    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPImmutableListType {
        if (generic[0] is MCFPPGenericParamType) {
            val g = generic[0] as MCFPPGenericParamType
            if(type.containsKey(g.identifier)){
                return MCFPPImmutableListType(type[g.identifier]!!)
            }else{
                throw IllegalArgumentException("No generic param ${g.identifier}")
            }
        }else if(generic[0] is MCFPPTypeWithGeneric){
            return MCFPPImmutableListType((generic[0] as MCFPPTypeWithGeneric).replaceGenericParam(type))
        }
        return this
    }



}

open class MCFPPCompoundType(
    g: MCFPPType
): MCFPPType(arrayListOf(MCFPPBaseType.Object)), MCFPPTypeWithGeneric{

    override val hasRuntimeRepresentation: Boolean get() = generic.all { it.hasRuntimeRepresentation }

    override val typeId: TypeId
        get() = TypeId.Applied(TypeId.Builtin(typeName), generic.map { it.typeId })

    override val generic: List<MCFPPType> = listOf(g)

    override val typeName: String
        get() = "compound"

    override fun toString(): String {
        return "compound[${generic[0].typeName}]"
    }

    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPCompoundType {
        if (generic[0] is MCFPPGenericParamType) {
            val g = generic[0] as MCFPPGenericParamType
            if(type.containsKey(g.identifier)){
                return MCFPPCompoundType(type[g.identifier]!!)
            }else{
                throw IllegalArgumentException("No generic param ${g.identifier}")
            }
        }else if(generic[0] is MCFPPTypeWithGeneric){
            return MCFPPCompoundType((generic[0] as MCFPPTypeWithGeneric).replaceGenericParam(type))
        }
        return this
    }



}

class MCFPPDictType(generic: MCFPPType): MCFPPCompoundType(generic){
    override val typeName: String
        get() = "dict"

    override fun toString(): String {
        return "dict[${generic[0].typeName}]"
    }

    override val objectData: CompoundData
        get() = NBTDictionary.data

    override val nbtType: Class<out Tag<*>>
        get() = CompoundTag::class.java

    override fun defaultValue() = HashMap<String, Var<*>>()
    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> = NBTDictionaryConcrete(value as HashMap<String, Var<*>>, identifier).apply { type = this@MCFPPDictType }
    override fun buildUnConcrete(identifier: String): Var<*> = NBTDictionary(identifier).apply { type = this@MCFPPDictType }

    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPDictType {
        if (generic[0] is MCFPPGenericParamType) {
            val g = generic[0] as MCFPPGenericParamType
            if(type.containsKey(g.identifier)){
                return MCFPPDictType(type[g.identifier]!!)
            }else{
                throw IllegalArgumentException("No generic param ${g.identifier}")
            }
        }else if(generic[0] is MCFPPTypeWithGeneric){
            return MCFPPDictType((generic[0] as MCFPPTypeWithGeneric).replaceGenericParam(type))
        }
        return this
    }



}

class MCFPPMapType(generic: MCFPPType): MCFPPCompoundType(generic){
    override val typeName: String
        get() = "map"

    override fun toString(): String {
        return "map[${generic[0].typeName}]"
    }

    override val objectData: CompoundData
        get() = NBTMap.data

    override val nbtType: Class<out Tag<*>>
        get() = CompoundTag::class.java

    override fun defaultValue() = HashMap<String, Var<*>>()
    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> = NBTMapConcrete(value as HashMap<String, Var<*>>, identifier, generic[0])
    override fun buildUnConcrete(identifier: String): Var<*> = NBTMap(identifier, generic[0])

    override fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPMapType {
        if (generic[0] is MCFPPGenericParamType) {
            val g = generic[0] as MCFPPGenericParamType
            if(type.containsKey(g.identifier)){
                return MCFPPMapType(type[g.identifier]!!)
            }else{
                throw IllegalArgumentException("No generic param ${g.identifier}")
            }
        }else if(generic[0] is MCFPPTypeWithGeneric){
            return MCFPPMapType((generic[0] as MCFPPTypeWithGeneric).replaceGenericParam(type))
        }
        return this
    }



}
