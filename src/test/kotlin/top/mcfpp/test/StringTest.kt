package top.mcfpp.test

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.checkerframework.checker.units.qual.A
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.util.StringHelper
import kotlin.test.Test

class StringTest{
    @Test
    fun qwq() {
        println("a:b".split(":"))
        println("b".split(":"))
    }

    @Test
    fun test1() {
        println(StringHelper.toLegalIdentifier("你好qwq000AAA"))
    }

    @Test
    fun test2(){
        val charStream: CharStream = CharStreams.fromString("int")
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        val parser = mcfppParser(tokens)
        println(parser.type().text)
    }

}