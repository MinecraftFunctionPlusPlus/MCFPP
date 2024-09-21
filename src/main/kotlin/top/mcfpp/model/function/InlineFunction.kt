package top.mcfpp.model.function

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.*
import top.mcfpp.core.lang.*
import top.mcfpp.type.MCFPPBaseType

/**
 * 内联函数。
 *
 * 内联函数在编译的时候，会使用一种变量替换的方式，使用函数体的内容替换函数调用处的内容
 */
class InlineFunction : Function {

    constructor(name: String, context: mcfppParser.FunctionBodyContext) : super(name, context = context)

    constructor(name: String, namespace: String, context: mcfppParser.FunctionBodyContext) : super(name, namespace, MCFPPBaseType.Void, context = context)

    override fun argPass(normalArgs: ArrayList<Var<*>>) {
        for (i in this.normalParams.indices) {
            if(i >= normalArgs.size){
                field.putVar(this.normalParams[i].identifier, this.normalParams[i].defaultVar!!, true)
            }else{
                field.putVar(this.normalParams[i].identifier, normalArgs[i], true)
            }
        }
    }

    /**
     * 调用一个变量的某个成员函数
     *
     * @param normalArgs
     * @param caller
     */
    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: Var<*>){
        //基本类型
        addComment("[Inline Function ${this.namespaceID}]")
        //传入this参数
        field.putVar("this",caller,true)
        //参数传递
        argPass(/*readOnlyArgs, */normalArgs)
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
     * 调用这个函数。
     *
     * @param args 函数的参数
     * @param callerClassP 调用函数的实例
     * @see top.mcfpp.antlr.MCFPPExprVisitor.visitVar
     */
    @InsertCommand
    override fun invoke(normalArgs: ArrayList<Var<*>>, callerClassP: ClassPointer) {
        //基本类型
        addComment("[Inline Function ${this.namespaceID}]")
        //参数传递
        argPass(/*readOnlyArgs, */normalArgs)
        //重新遍历这个函数

        //这边其实直接用visitor会更好，因为visitor不用加parser，直接visitor.visit(context)
        val visitor = MCFPPInlineFunctionVisitor()
        visitor.visit(ast)
//        val charStream: CharStream = CharStreams.fromString(AntlrUtil.getReadableText(context.functionBody()))
//        val lexer = mcfppLexer(charStream)
//        val tokens = CommonTokenStream(lexer)
//        val parser = mcfppParser(tokens)
//        parser.addParseListener(McfppInlineFunctionListener())
//        parser.functionBody()
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