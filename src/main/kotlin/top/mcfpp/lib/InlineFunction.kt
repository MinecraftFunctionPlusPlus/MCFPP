package top.mcfpp.lib

import top.mcfpp.lang.Var

/**
 * 内联函数
 * TODO
 */
class InlineFunction : Function {
    var context: mcfppParser.FunctionDeclarationContext

    constructor(name: String, context: mcfppParser.FunctionDeclarationContext) : super(name) {
        this.context = context
    }

    constructor(name: String, namespace: String, context: mcfppParser.FunctionDeclarationContext) : super(name, namespace) {
        this.context = context
    }

    @Override
    override operator fun invoke(args: ArrayList<Var>, caller: Var?) {
        TODO()
    }
}