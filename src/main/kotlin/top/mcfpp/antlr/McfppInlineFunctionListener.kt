package top.mcfpp.antlr

class McfppInlineFunctionListener : McfppImListener() {
    override fun enterFunctionBody(ctx: mcfppParser.FunctionBodyContext) {}

    override fun exitFunctionBody(ctx: mcfppParser.FunctionBodyContext?) {}

}