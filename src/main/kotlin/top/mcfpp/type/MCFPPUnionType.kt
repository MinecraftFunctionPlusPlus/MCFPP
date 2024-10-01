package top.mcfpp.type

class MCFPPUnionType(vararg val types: MCFPPType): MCFPPType() {
    override val typeName: String
        get() = "UnionType(${types.joinToString(", ")})"

}