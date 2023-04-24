import groovy.lang.GroovyShell
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

fun main(args: Array<String>) {
    println("Hello World!")

    //val code = ""
    //val input = CharStreams.fromString(code)
    //val lexer = mcfppLexer(input)
    //val tokens = CommonTokenStream(lexer)
    //val parser = mcfppParser(tokens).apply {
    //    buildParseTree = true
    //}
    //val tree = parser.compilationUnit()
    //val visitor = mcfppBaseVisitor<String>()
    //visitor.visit(tree)

    println("Program arguments: ${args.joinToString()}")
    evalute("""
        def sayHello(name){
            println "hello "+ name + "."
        }
        sayHello("MCFPP")
    """.trimIndent())
}

fun evalute(code:String) {
    val shell = GroovyShell()
    shell.evaluate(code)
}