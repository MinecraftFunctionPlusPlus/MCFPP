package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.lang.Var

/**
 * 内联函数
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
    operator fun invoke(args: ArrayList<Var?>?, lineNo: Int) {
        TODO()
    }
}