package top.mcfpp.lang.type

/**
 * 类型单例
 */
enum class MCFPPBaseType(
    override var typeName:kotlin.String,
    override var parentType: List<MCFPPType>
):MCFPPType {
    Any("any", listOf(Any)),
    Int("int",listOf(Any)),
    String("string",listOf(Any)),
    Float("float",listOf(Any)),
    Bool("bool",listOf(Any)),
}

