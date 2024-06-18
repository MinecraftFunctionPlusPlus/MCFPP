package top.mcfpp.lang.type

import net.querz.nbt.tag.*
import top.mcfpp.lang.NBTDictionary
import top.mcfpp.lang.NBTList
import top.mcfpp.lang.NBTMap

/**
 * 以NBT为底层的类型，包括普通的NBT类型，以及由nbt实现的map，list和dict
 */
class MCFPPNBTType{
    object NBT: MCFPPType(listOf(MCFPPBaseType.Any)){
        override val typeName: String
            get() = "nbt"
    }

    /*
    object BaseList: MCFPPType(listOf(NBT)){
        override val typeName: String
            get() = "list"
    }
    */

    object Dict: MCFPPType(listOf(NBT)){
        override val typeName: String
            get() = "dict"
    }
    object Map: MCFPPType(listOf(NBT)){
        override val typeName: String
            get() = "map"
    }

    init {
        NBT.data = top.mcfpp.lang.NBTBasedData.data
        Dict.data = NBTDictionary.data
        Map.data = NBTMap.data
    }

}


class MCFPPListType(
    val generic: MCFPPType = MCFPPBaseType.Any
): MCFPPType(listOf(MCFPPNBTType.NBT), NBTList.data, false){

    /*
    init {
        registerType({ it.contains(regex) }) {
            val matcher = regex.find(it)!!.groupValues
            MCFPPListType(parseFromTypeName(matcher[1]))
        }
    }
    */

    override val typeName: String
        get() = "list[${generic.typeName}]"

    companion object{
        val regex = Regex("^list\\(.+\\)$") //TODO：这个不一定能匹配得到嵌套的内容！
    }
}

open class MCFPPCompoundType(
    val generic: MCFPPType
): MCFPPType(listOf(MCFPPNBTType.NBT), NBTDictionary.data, false){

    override val typeName: String
        get() = "compound[${generic.typeName}]"

}

class MCFPPDictType(generic: MCFPPType):MCFPPCompoundType(generic){
    override val typeName: String
        get() = "dict[${generic.typeName}]"
}

class MCFPPMapType(generic: MCFPPType):MCFPPCompoundType(generic){
    override val typeName: String
        get() = "map[${generic.typeName}]"
}