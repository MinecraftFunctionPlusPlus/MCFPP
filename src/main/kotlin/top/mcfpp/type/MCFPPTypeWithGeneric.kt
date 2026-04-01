package top.mcfpp.type

interface MCFPPTypeWithGeneric {

    val generic: List<MCFPPType>

    fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPType

}