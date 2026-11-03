package top.mcfpp.antlr

/**
 * 解释只读参数声明，包括将 <T> 识别为 <T: type>。
 */
object ReadonlyParamsDeclarationSyntax {

    /**
     * 是否省略了类型标注，例如 `<T>`。
     */
    fun isBareTypeParameter(param: mcfppParser.ReadOnlyParameterContext): Boolean =
        param.type() == null

    /**
     * 获取只读参数的类型名称，如果是裸类型参数，则返回 "type"。
     */
    fun readonlyTypeName(param: mcfppParser.ReadOnlyParameterContext): String =
        param.type()?.text ?: "type"

    /**
     * 获取只读参数显式声明的名称。
     */
    fun readonlyParameterName(param: mcfppParser.ReadOnlyParameterContext): String =
        param.Identifier().text
}
