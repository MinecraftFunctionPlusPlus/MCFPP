package top.mcfpp.model.function

import top.mcfpp.antlr.*
import top.mcfpp.core.lang.*

/**
 * 内联函数。
 *
 * 内联函数在编译的时候，会使用一种变量替换的方式，使用函数体的内容替换函数调用处的内容
 */
class InlineFunction : Function {

    constructor(name: String, context: mcfppParser.CurlBlockContext) : super(name, context = context)

    constructor(name: String, namespace: String, context: mcfppParser.CurlBlockContext) : super(name, namespace, context = context)

    override fun argPass(normalArgs: List<Var<*>>) {
        for (i in this.normalParams.indices) {
            if(i >= normalArgs.size){
                scope.putVar(this.normalParams[i].identifier, this.normalParams[i].defaultVar!!, true)
            }else{
                scope.putVar(this.normalParams[i].identifier, normalArgs[i], true)
            }
        }
    }

    /**
     * 调用一个变量的某个成员函数
     *
     * @param normalArgs
     * @param caller
     */
    override fun invoke(normalArgs: List<Var<*>>, caller: Var<*>){
        //基本类型
        addComment("[Inline Function ${this.namespaceID}]")
        //传入this参数
        scope.putVar("this",caller,true)
        //参数传递
        argPass(normalArgs)
        //重新遍历这个函数
        val visitor = MCFPPImVisitor()
        visitor.visit(ast)

//        val charStream: CharStream = CharStreams.fromString(context.text)
//        val lexer = mcfppLexer(charStream)
//        val tokens = CommonTokenStream(lexer)
//        val parser = mcfppParser(tokens)
//        parser.addParseListener(McfppImListener())
//        parser.functionDeclaration()
    }

    /**
     * 调用这个函数。这个函数是结构体的成员方法
     *
     * @param readOnlyArgs
     * @param normalArgs
     * @param struct
     */
    //override fun invoke(/*readOnlyArgs:ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, struct: IntTemplateBase){
    //    TODO()
    //}
}