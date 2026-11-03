package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import kotlin.test.*
import kotlin.test.Test

class AnnotationSyntaxTest {
    @Test fun annotationArgumentsUseParentheses() {
        val parser = parser("@Disabled(\"Later\")\n@Test()\nfunc pending(){}")
        val source = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertTrue(source.text.contains("Disabled(\"Later\")"))
    }

    @Test fun angleBracketsAreNoLongerAnnotationArguments() {
        val parser = parser("@Disabled<\"Later\">\nfunc pending(){}")
        parser.removeErrorListeners()
        parser.compilationUnit()
        assertTrue(parser.numberOfSyntaxErrors > 0)
    }

    private fun parser(source: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
}
