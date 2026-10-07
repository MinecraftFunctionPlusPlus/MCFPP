package top.mcfpp.model.function

import top.mcfpp.antlr.*


/**
 * 内联函数。
 *
 * 内联函数在编译的时候，会使用一种变量替换的方式，使用函数体的内容替换函数调用处的内容
 */
class InlineFunction : Function {

    constructor(name: String, context: mcfppParser.CurlBlockContext) : super(name, context = context)

    constructor(name: String, namespace: String, context: mcfppParser.CurlBlockContext) : super(name, namespace, context = context)

}