package top.mcfpp.test

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceSyntaxTest {
    private fun errors(source: String): List<String> {
        val errors = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                errors += "$line:$charPositionInLine $msg"
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(source)).apply { removeErrorListeners(); addErrorListener(listener) }
        val parser = mcfppParser(CommonTokenStream(lexer)).apply { removeErrorListeners(); addErrorListener(listener) }
        parser.compilationUnit()
        return errors
    }

    @Test fun acceptsColonForEveryFunctionKindAndRequiresExplicitFields() {
        val source = """
            /// # Syntax
            namespace syntax
            func identity(value: int): int { return value; }
            inline func inlineValue(): int { return 1; }
            const func constantValue(): int { return 1; }
            func nativeValue(): int = example.Native.value
            func int.extension(): int { return this; }
            data Box {
                /// **Value** with a [link](https://example.com).
                ///
                /// ```mcfpp
                /// var value = 1;
                /// ```
                public var value: int
                const fixed: int = 1
                var inferred = 2
                func getValue(): int { return this.value; }
                operator +(other: Box): int { return this.value; }
                operator -(other: Box): int = example.Native.subtract
            }
            object data Defaults { var value: int = 1; }
            abstract data Readable { var value: int; abstract func read(): int; }
            abstract data Generic<T> { abstract func read(): T; }
            final data Finished {}
            func runtime(){ external explicit: int = 1; external inferred = 2; external empty: int; }
        """.trimIndent()
        assertEquals(emptyList(), errors(source))
        assertEquals(emptyList(), errors("// Comment-only file\n\n"))
    }

    @Test fun distinguishesCommentsFromCommandsDivisionAndStrings() {
        val source = """
            // Ordinary comment
            func sample(): int {
                // /say ignored
                var value = 8 / 2 // inline comment
                var url = "https://example.com/#heading"
                /say // still command text #tag
                return value
            }
            /// Documentation at EOF
            func documented(){}
        """.trimIndent()
        assertEquals(emptyList(), errors(source))
        val lexer = mcfppLexer(CharStreams.fromString("/// # Heading\r\n  ///     indented\r\nfunc sample(){}"))
        val doc = lexer.nextToken()
        assertEquals(mcfppLexer.DOC_COMMENT, doc.type)
        assertEquals("/// # Heading\r\n  ///     indented", doc.text)
        assertEquals(mcfppLexer.DOC_COMMENT, mcfppLexer(CharStreams.fromString("/// final line")).nextToken().type)
    }

    @Test fun rejectsRetiredSyntax() {
        for (source in listOf(
            "func old()->int { return 1; }",
            "interface Readable { abstract func read(): int; }",
            "data abstract Readable {}",
            "func sample(){ dynamic value = 1; }",
            "# ordinary comment\nfunc sample(){}",
            "#{ old documentation }#\nfunc sample(){}",
            "data Box { value as int; }",
            "data Box { const var value = 1; }",
            "data Box { value = 1; }",
            "object data Box { value as int; }",
            "func sample(){ var box as data { value as int; }; }"
        )) assertTrue(errors(source).isNotEmpty(), source)
    }

    @Test fun retiredKeywordsCanBeUsedAsIdentifiers() {
        assertEquals(emptyList(), errors("func sample(){ var dynamic = 1; var interface = 2; }"))
        assertEquals(mcfppLexer.EXTERNAL, mcfppLexer(CharStreams.fromString("external")).nextToken().type)
    }
}
