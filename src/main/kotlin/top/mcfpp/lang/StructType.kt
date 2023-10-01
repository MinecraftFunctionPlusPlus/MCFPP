package top.mcfpp.lang

import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import top.mcfpp.lib.Struct

class StructType: CompoundDataType {

    override val type: String
        get() = "StructType@${dataType.identifier}"

    constructor(type: Struct):super(type)
}