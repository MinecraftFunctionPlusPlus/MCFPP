package top.mcfpp.lib

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.*
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPBaseType

/**
 * 内联函数。
 *
 * 内联函数在编译的时候，会使用一种变量替换的方式，使用函数体的内容替换函数调用处的内容
 */
class InlineFunction : Function {

    var context: mcfppParser.InlineFunctionDeclarationContext

    constructor(name: String, context: mcfppParser.InlineFunctionDeclarationContext) : super(name) {
        this.context = context
    }

    constructor(name: String, namespace: String, context: mcfppParser.InlineFunctionDeclarationContext) : super(name, namespace,
        MCFPPBaseType.Void
    ) {
        this.context = context
    }

    override fun argPass(args: ArrayList<Var<*>>) {
        for (argi in args.withIndex()){
            field.putVar(params[argi.index].identifier,argi.value,true)
        }
    }

    /**
     * 调用一个变量的某个成员函数
     *
     * @param args
     * @param caller
     */
    override fun invoke(args: ArrayList<Var<*>>, caller: Var<*>){
        //基本类型
        addCommand("#[Inline Function ${this.namespaceID}]")
        //传入this参数
        field.putVar("this",caller,true)
        //参数传递
        argPass(args)
        //重新遍历这个函数
        val visitor = McfppImVisitor()
        visitor.visit(context)

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
     * @see top.mcfpp.antlr.McfppExprVisitor.visitVar
     */
    @InsertCommand
    override fun invoke(args: ArrayList<Var<*>>, callerClassP: ClassPointer?) {
        //基本类型
        addCommand("#[Inline Function ${this.namespaceID}]")
        //参数传递
        argPass(args)
        //重新遍历这个函数

        //这边其实直接用visitor会更好，因为visitor不用加parser，直接visitor.visit(context)
        val visitor = McfppInlineFunctionVisitor()
        visitor.visit(context)
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
     * @param args
     * @param struct
     */
    override fun invoke(args: ArrayList<Var<*>>, struct: IntTemplateBase){
        TODO()
    }
}