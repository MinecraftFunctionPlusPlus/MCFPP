package top.mcfpp.lang.type

import top.mcfpp.lang.NBT
import top.mcfpp.lang.NBTDictionary
import top.mcfpp.lang.NBTList
import top.mcfpp.lang.NBTMap

/**
 * 以NBT为底层的类型，包括普通的NBT类型，以及由nbt实现的map，list和dict
 */
class MCFPPNBTType{
    object NBT: MCFPPType("nbt",listOf(MCFPPBaseType.Any))
    object BaseList: MCFPPType("list",listOf(NBT))
    object Dict: MCFPPType("dict",listOf(NBT))
    object Map: MCFPPType("map",listOf(Dict))

    init {
        NBT.data = top.mcfpp.lang.NBT.data
        BaseList.data = NBTList.data
        Dict.data = NBTDictionary.data
        Map.data = NBTMap.data
    }

}


class MCFPPListType(
    type: MCFPPType
): MCFPPType("list(${type.typeName})",listOf(MCFPPNBTType.BaseList), NBTList.data){
    init {
        registerType({ it.contains(regex) }) {
            val matcher = regex.find(it)!!.groupValues
            MCFPPListType(parseFromTypeName(matcher[1]))
        }
    }
    companion object{
        val regex = Regex("^list\\(.+\\)$") //TODO：这个不一定能匹配得到嵌套的内容！
    }
}


