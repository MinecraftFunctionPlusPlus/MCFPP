package top.mcfpp.test

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.util.StringHelper.toSnakeCase
import kotlin.test.Test

class StringTest{
    @Test
    fun qwq() {
        println("a:b".split(":"))
        println("b".split(":"))
    }

    @Test
    fun test1() {
        println("你好qwq000AAA".toSnakeCase())
    }

    @Test
    fun test2(){
        val charStream: CharStream = CharStreams.fromString("int")
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        val parser = mcfppParser(tokens)
        println(parser.type().text)
    }

    @Test
    fun test3(){
        var ch: Int
        while ((System.`in`.read().also { ch = it }) != -1) {
            println("字符: " + ch.toChar() + " ASCII: " + ch)
        }
    }

}