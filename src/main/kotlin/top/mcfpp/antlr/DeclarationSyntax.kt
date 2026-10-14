package top.mcfpp.antlr

/** A bare identifier in a readonly list declares a type parameter; in () it remains a positional type. */
object DeclarationSyntax {
    fun isBareTypeParameter(param: mcfppParser.ParameterContext): Boolean =
        param.Identifier() == null && param.type().start.type == mcfppLexer.Identifier &&
            param.type().start.tokenIndex == param.type().stop.tokenIndex

    fun readonlyTypeName(param: mcfppParser.ParameterContext): String =
        if (isBareTypeParameter(param)) "type" else param.type().text

    fun parameterName(param: mcfppParser.ParameterContext, isReadonly: Boolean, fallback: String): String =
        param.Identifier()?.text ?: if (isReadonly && isBareTypeParameter(param)) param.type().text else fallback
}
