package top.mcfpp.type

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.IntTag
import net.querz.nbt.tag.ListTag
import net.querz.nbt.tag.Tag
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.core.lang.*

/**
 * 以NBT为底层的类型，包括普通的NBT类型，以及由nbt实现的map，list和dict
 */
class MCFPPNBTType {
    object NBT : MCFPPType(parentType = listOf(MCFPPBaseType.Any)) {

        override val objectData: CompoundData
            get() = NBTBasedData.data

        override val typeName: String
            get() = "nbt"

        override fun build(identifier: String, container: FieldContainer): Var<*> =
            NBTBasedDataConcrete(container, IntTag(0), identifier)

        override fun build(identifier: String): Var<*> = NBTBasedDataConcrete(IntTag(0), identifier)
        override fun build(identifier: String, clazz: Class): Var<*> =
            NBTBasedDataConcrete(clazz, IntTag(0), identifier)

        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> =
            NBTBasedData(container, identifier)

        override fun buildUnConcrete(identifier: String): Var<*> = NBTBasedData(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = NBTBasedData(clazz, identifier)

    }
}

class MCFPPListType(
    val generic: MCFPPType = MCFPPBaseType.Any
): MCFPPType(NBTList.data, listOf(MCFPPNBTType.NBT)){

    override val objectData: CompoundData
        get() = NBTList.data

    override val typeName: String
        get() = "list<${generic.typeName}>"

    override val nbtType: java.lang.Class<out Tag<*>>
        get() = ListTag::class.java

    override fun build(identifier: String, container: FieldContainer): Var<*> = NBTListConcrete(container, ArrayList(), identifier, generic)
    override fun build(identifier: String): Var<*> = NBTListConcrete(ArrayList(), identifier, generic)
    override fun build(identifier: String, clazz: Class): Var<*> = NBTListConcrete(clazz, ArrayList(), identifier, generic)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = NBTList(container, identifier, generic)
    override fun buildUnConcrete(identifier: String): Var<*> = NBTList(identifier, generic)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = NBTList(clazz, identifier, generic)
}

class MCFPPImmutableListType(
    val generic: MCFPPType = MCFPPBaseType.Any
): MCFPPType(NBTList.data, listOf(MCFPPNBTType.NBT)){

    override val objectData: CompoundData
        get() = NBTList.data

    override val typeName: String
        get() = "list[${generic.typeName}]"

    override val nbtType: java.lang.Class<out Tag<*>>
        get() = ListTag::class.java

    override fun build(identifier: String, container: FieldContainer): Var<*> = ImmutableListConcrete(container, ListTag(IntTag::class.java), identifier, generic)
    override fun build(identifier: String): Var<*> = ImmutableListConcrete(ListTag(IntTag::class.java), identifier, generic)
    override fun build(identifier: String, clazz: Class): Var<*> = ImmutableListConcrete(clazz, ListTag(IntTag::class.java), identifier, generic)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = ImmutableList(container, identifier, generic)
    override fun buildUnConcrete(identifier: String): Var<*> = ImmutableList(identifier, generic)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = ImmutableList(clazz, identifier, generic)

}

open class MCFPPCompoundType(
    val generic: MCFPPType
): MCFPPType(NBTDictionary.data, listOf(MCFPPNBTType.NBT)){

    override val typeName: String
        get() = "compound[${generic.typeName}]"

}

class MCFPPDictType(generic: MCFPPType): MCFPPCompoundType(generic){
    override val typeName: String
        get() = "dict[${generic.typeName}]"

    override fun build(identifier: String, container: FieldContainer): Var<*> = NBTDictionaryConcrete(container, HashMap(), identifier)
    override fun build(identifier: String): Var<*> = NBTDictionaryConcrete(HashMap(), identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = NBTDictionaryConcrete(clazz, HashMap(), identifier)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = NBTDictionary(container, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = NBTDictionary(identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = NBTDictionary(clazz, identifier)
}

class MCFPPMapType(generic: MCFPPType): MCFPPCompoundType(generic){
    override val typeName: String
        get() = "map[${generic.typeName}]"


    override fun build(identifier: String, container: FieldContainer): Var<*> = NBTMapConcrete(container, HashMap(), identifier, generic)
    override fun build(identifier: String): Var<*> = NBTMapConcrete(HashMap(), identifier, generic)
    override fun build(identifier: String, clazz: Class): Var<*> = NBTMapConcrete(clazz, HashMap(), identifier, generic)
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = NBTMap(container, identifier, generic)
    override fun buildUnConcrete(identifier: String): Var<*> = NBTMap(identifier, generic)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = NBTMap(clazz, identifier, generic)
}
