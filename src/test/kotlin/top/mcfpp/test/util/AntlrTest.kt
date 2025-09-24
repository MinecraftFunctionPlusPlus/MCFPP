package top.mcfpp.test.util

import top.mcfpp.antlr.mcfppLexer
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.mcfppParser
import kotlin.test.Test

class AntlrTest {
    @Test
    fun test(){
        val input = """
        /scoreboard players qwq
            qwq
            qwq/
        var i = 1;
    """.trimIndent()

        val inputStream = ANTLRInputStream(input)
        val lexer = mcfppLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = mcfppParser(tokens)

        // 生成语法树
        val tree = parser.compilationUnit() // 你的起始规则

        // 打印树结构
        println(tree.toStringTree(parser))

        // 或者使用ANTLR的GUI工具（需要添加依赖）
        // tree.inspect(parser)
    }
}