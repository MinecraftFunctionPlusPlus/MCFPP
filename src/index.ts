
import { MCFPPLexer } from "./antlr/MCFPPLexer.js"
import { MCFPPParser } from "./antlr/MCFPPParser.js"
import { CharStreams, CommonTokenStream } from "antlr4ts"
import { MCFPPWorkVisitor } from "./tree/MCFPPWorkVisitor.js"

export function execute(code:string) {
    const input = CharStreams.fromString(code)
    const lexer = new MCFPPLexer(input)
    const tokens = new CommonTokenStream(lexer)
    const parser = new MCFPPParser(tokens)
    const visitor = new MCFPPWorkVisitor()
    const begin = parser.compilationUnit()
    visitor.visit(begin)
    
}

console.log("hello world, I am mcfpp");

