package top.mcfpp.lang.type

class MCFPPVectorType(val dimension: Int): MCFPPType(parentType = listOf(MCFPPBaseType.Any)) {
    override val typeName: String
        get() = "vec$dimension"


    companion object {
        val regex = Regex("^vec\\d+$")
    }

}