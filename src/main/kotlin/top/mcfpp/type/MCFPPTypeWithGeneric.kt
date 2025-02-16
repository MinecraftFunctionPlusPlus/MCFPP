package top.mcfpp.type

interface MCFPPTypeWithGeneric {

    fun replaceGenericParam(type: Map<String, MCFPPType>): MCFPPType

}