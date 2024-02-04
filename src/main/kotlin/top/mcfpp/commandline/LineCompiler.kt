package top.mcfpp.commandline

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.McfppFileVisitor
import top.mcfpp.antlr.McfppImVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lib.Function

class LineCompiler {

    val COMMAND_COLOR = "\u001b[38;5;7m"
    val OUTPUT_COLOR = "\u001B[38;5;15m"
    val RESET_COLOR = "\u001B[0m"

    val default = Function("default","mcfpp")

    fun compile(line: String) {
        Function.currFunction = default
        val charStream: CharStream = CharStreams.fromString(line + if(!line.endsWith(';')) ";" else "")
        val tokens = CommonTokenStream(mcfppLexer(charStream))
        val unit = mcfppParser(tokens).compilationUnit()
        if(unit.statement().size != 0){
            McfppImVisitor().visit(unit)
        }else{
            McfppFileVisitor().visit(unit)
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