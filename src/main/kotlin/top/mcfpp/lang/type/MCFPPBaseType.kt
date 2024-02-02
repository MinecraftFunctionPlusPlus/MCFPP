package top.mcfpp.lang.type

/**
 * 类型单例
 */
enum class MCFPPBaseType(
    override var typeName:kotlin.String
):MCFPPType {
    Int("int"),
    String("string"),
    Float("float"),
    Bool("bool"),
    Any("any")
}

