package top.mcfpp.core.lang

import top.mcfpp.core.lang.nbt.NBTList
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
