package top.mcfpp.lib

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.McfppImListener
import top.mcfpp.antlr.McfppInlineFunctionListener
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.lang.*
import top.mcfpp.util.AntlrUtil

/**
 * 内联函数
 */
class InlineFunction : Function {

    var context: mcfppParser.FunctionDeclarationContext

    constructor(name: String, context: mcfppParser.FunctionDeclarationContext) : super(name) {
        this.context = context
    }

    constructor(name: String, namespace: String, context: mcfppParser.FunctionDeclarationContext) : super(name, namespace, "void") {
        this.context = context
    }

    override fun argPass(args: ArrayList<Var>) {
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
    override fun invoke(args: ArrayList<Var>, caller: Var){
        //基本类型
        addCommand("#[Inline Function ${this.namespaceID}]")
        //传入this参数
        field.putVar("this",caller,true)
        //参数传递
        argPass(args)
        //重新遍历这个函数
        val charStream: CharStream = CharStreams.fromString(AntlrUtil.getReadableText(context.functionBody()))
        val lexer = mcfppLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = mcfppParser(tokens)
        parser.addParseListener(McfppImListener())
        parser.functionDeclaration()
    }

    /**
     * 调用这个函数。
     *
     * @param args 函数的参数
     * @param callerClassP 调用函数的实例
     * @see top.mcfpp.antlr.McfppExprVisitor.visitVar
     */
    @InsertCommand
    override fun invoke(args: ArrayList<Var>, callerClassP: ClassBase?) {
        //基本类型
        addCommand("#[Inline Function ${this.namespaceID}]")
        //参数传递
        argPass(args)
        //重新遍历这个函数
        val charStream: CharStream = CharStreams.fromString(AntlrUtil.getReadableText(context.functionBody()))
        val lexer = mcfppLexer(charStream)
        val tokens = CommonTokenStream(lexer)
        val parser = mcfppParser(tokens)
        parser.addParseListener(McfppInlineFunctionListener())
        parser.functionBody()
    }

    /**
     * 调用这个函数。这个函数是结构体的成员方法
     *
     * @param args
     * @param struct
     */
    override fun invoke(args: ArrayList<Var>, struct: IntTemplateBase){
        TODO()
    }
}