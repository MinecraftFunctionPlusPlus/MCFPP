package top.mcfpp.core.lang

import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.mni.ImmutableListData
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.type.*
import top.mcfpp.util.TempPool

/** A read-only list interpretation; aliases still observe changes to the same underlying place. */
open class ImmutableList : NBTList {
    constructor(identifier: String = TempPool.getVarIdentify(), genericType: MCFPPType) : super(identifier, genericType) {
        type = MCFPPImmutableListType(genericType)
    }
    constructor(source: NBTList) : super(source) { type = MCFPPImmutableListType(source.genericType) }
    override fun clone() = ImmutableList(this)

    companion object {
        val data by lazy {
            CompoundData("ImmutableList", "mcfpp.lang").apply {
                scope.putType("E", MCFPPGenericParamType("E", arrayListOf(MCFPPBaseType.Any)))
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(ImmutableListData::class.java)
            }
        }
    }
}

/** Shares the list value codec without a second set of constant-only members. */
class ImmutableListConcrete : NBTListConcrete {
    constructor(value: ArrayList<Var<*>>, identifier: String, genericType: MCFPPType) : super(value, identifier, genericType) {
        type = MCFPPImmutableListType(genericType)
    }
    constructor(source: NBTList, value: ArrayList<Var<*>>) : super(source, value) {
        type = MCFPPImmutableListType(source.genericType)
    }
    constructor(source: ImmutableListConcrete) : super(source) { type = source.type }
    override fun clone() = ImmutableListConcrete(this)

    companion object {
        val data get() = ImmutableList.data
        val empty get() = ImmutableListConcrete(arrayListOf(), "empty", MCFPPBaseType.Any)
    }
}
