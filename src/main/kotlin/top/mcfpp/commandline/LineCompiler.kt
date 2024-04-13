package top.mcfpp.commandline

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.McfppFieldVisitor
import top.mcfpp.antlr.McfppImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lib.function.Function

class LineCompiler {

    val COMMAND_COLOR = "\u001b[38;5;7m"
    val OUTPUT_COLOR = "\u001B[38;5;15m"
    val RESET_COLOR = "\u001B[0m"

    val default = Function("default","mcfpp")

    var unmatchedBraces : String = ""
    var leftBraces = 0

    fun compile(line: String) {
        //处理未匹配的括号
        for (char in line) {
            when (char) {
                '{' -> leftBraces++
                '}' -> {
                    if (leftBraces == 0) {
                        throw Exception("Unmatched right brace")
                    }
                    leftBraces--
                }
            }
        }
        if (leftBraces > 0) {
            unmatchedBraces += line
            return
        }
        leftBraces = 0
        val input = unmatchedBraces + line
        unmatchedBraces = ""
        Function.currFunction = default
        val charStream: CharStream = CharStreams.fromString(input + if(!line.endsWith(';')) ";" else "")
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        val unit = mcfppParser(tokens).compilationUnit()
        if(unit.topStatement().statement().size != 0){
            McfppImVisitor().visit(unit)
        }else{
            McfppFieldVisitor().visit(unit)
        }
        for (i in Function.currFunction.commands){
            printOutput(i.toString())
        }
        Function.currFunction.commands.clear()
        Function.currFunction = Function.nullFunction
    }

    private fun printCommand(string: String){
        println(COMMAND_COLOR + string + RESET_COLOR)
    }

    private fun printOutput(string: String){
        println(OUTPUT_COLOR + string + RESET_COLOR)
    }
}