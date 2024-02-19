package top.mcfpp.lang.type

class MCFPPNBTType{
    object Map: MCFPPType("map",listOf(Dict)){}
    object BaseList: MCFPPType("list",listOf(NBT)){}
    object Dict: MCFPPType("dict",listOf(NBT)){}
    object NBT: MCFPPType("nbt",listOf(MCFPPBaseType.Any))
}


class MCFPPListType(
    type: MCFPPType
): MCFPPType("list(${type.typeName})",listOf(MCFPPNBTType.BaseList)){
    init {
        registerType({ it.contains(regex) }) {
            val matcher = regex.find(it)!!.groupValues
            MCFPPListType(parse(matcher[1]))
        }
    }
    companion object{
        val regex = Regex("^list\\(.+\\)$") //TODO：这个不一定能匹配得到嵌套的内容！
    }
}


