package top.mcfpp.test

import com.alibaba.fastjson2.JSON
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.VersionPreprocessor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.util.Utils
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class VersionPreprocessorTest {
    private fun parse(source: String, version: String) {
        val processed = VersionPreprocessor.process(source, version)
        assertEquals(source.length, processed.length)
        assertEquals(source.count { it == '\n' }, processed.count { it == '\n' })
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(processed))))
        parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors, processed)
    }

    @Test
    fun selectsNestedBranchesBeforeParsing() {
        val source = """
            #if MC >= 26.2
            func newest(){
                #if MC == 26.3
                var value = 3;
                #else
                var value = 2;
                #endif
            }
            #elif MC >= 26.1
            func previous(){}
            #else
            func old(){}
            #endif
            #if MC < 26.1
            func older(){}
            #endif
        """.trimIndent()
        val latest = VersionPreprocessor.process(source, "26.3")
        assertTrue(latest.contains("func newest"))
        assertTrue(latest.contains("value = 3"))
        assertFalse(latest.contains("func previous"))
        assertFalse(latest.contains("func old"))
        parse(source, "26.3")
        val previous = VersionPreprocessor.process(source, "26.1")
        assertTrue(previous.contains("func previous"))
        assertFalse(previous.contains("func newest"))
        parse(source, "26.1")
        val old = VersionPreprocessor.process(source, "1.21.6")
        assertTrue(old.contains("func old"))
        assertTrue(old.contains("func older"))
        parse(source, "1.21.6")
        assertTrue(VersionPreprocessor.process("#if MC > 1.21.8\nfunc selected(){}\n#endif", "1.21.10")
            .contains("func selected"))
        assertFalse(VersionPreprocessor.process("#if MC >= 26.10\nfunc selected(){}\n#endif", "26.3")
            .contains("func selected"))
    }

    @Test
    fun skipsInvalidInactiveSourceAndPreservesLines() {
        val source = """
            #if MC >= 26.1
            func available(){}
            #else
            this is not valid MCFPP at all {{{
            #endif
        """.trimIndent()
        val processed = VersionPreprocessor.process(source, "26.1")
        assertEquals(source.length, processed.length)
        assertEquals(source.indexOf("func available"), processed.indexOf("func available"))
        assertFalse(processed.contains("this is not valid"))
        parse(source, "26.1")
    }

    @Test
    fun selectsImportsAndCommandStatements() {
        val source = """
            #if MC >= 26.1
            import example:modern
            #else
            import example:legacy
            #endif
            func selected(){
                #if MC >= 26.1
                /say modern
                #else
                /say legacy
                #endif
            }
        """.trimIndent()
        val modern = VersionPreprocessor.process(source, "26.1")
        assertTrue(modern.contains("example:modern"))
        assertTrue(modern.contains("/say modern"))
        assertFalse(modern.contains("example:legacy"))
        parse(source, "26.1")
        val legacy = VersionPreprocessor.process(source, "1.21.6")
        assertTrue(legacy.contains("example:legacy"))
        assertTrue(legacy.contains("/say legacy"))
        assertFalse(legacy.contains("example:modern"))
        parse(source, "1.21.6")
    }

    @Test
    fun directiveTextInStringsAndCommentsStaysUntouched() {
        val source = listOf(
            "func sample(){",
            "  var s = \"\"\"",
            "#if MC >= 26.3",
            "\"\"\";",
            "}",
            "#{",
            "#endif",
            "}#",
            "##",
            "#if MC >= 26.3",
            "##",
            "###",
            "#else",
            "###",
            "# ordinary comment",
            "#if MC >= 26.1",
            "func retained(){}",
            "#endif"
        ).joinToString("\n")
        val processed = VersionPreprocessor.process(source, "26.1")
        assertTrue(processed.contains("\n#if MC >= 26.3\n"))
        assertTrue(processed.contains("\n#endif\n}#"))
        assertTrue(processed.contains("\n#else\n###"))
        assertTrue(processed.contains("func retained"))
        parse(source.substringBefore("#{") + "\nfunc retained(){}", "26.1")

        val skippedString = listOf(
            "#if MC < 26.1",
            "var message = \"\"\"",
            "#if MC >= 26.3",
            "\"\"\";",
            "#else",
            "func retained(){}",
            "#endif"
        ).joinToString("\n")
        parse(skippedString, "26.1")
    }

    @Test
    fun rejectsMalformedDirectivesWithSourceLine() {
        fun error(source: String, line: Int) {
            assertEquals(line, assertFailsWith<VersionPreprocessor.Error> {
                VersionPreprocessor.process(source, "26.1")
            }.line)
        }
        error("func a(){}\n#endif", 2)
        error("#if MC >= 26.1\n#else\n#else\n#endif", 3)
        error("#if MC >= 26.1\n#else\n#elif MC < 26.2\n#endif", 3)
        error("#if MCVERSION >= 26.1\n#endif", 1)
        error("#if MC >= 21.6\n#endif", 1)
        error("#if MC >= 26.1\nfunc a(){}", 1)
    }

    @Test
    fun compilesOnlyTheSelectedFunctionAndStatements() {
        val source = """
            #if MC >= 26.1
            func selected(){
                #if MC >= 26.3
                var result = 3;
                #else
                var result = 1;
                #endif
            }
            #else
            invalid syntax {{{
            #endif
        """.trimIndent()
        MCFPPStringTest.readFromString(source, version = "26.3")
        assertEquals(0, Project.errorCount)
        val namespace = assertNotNull(GlobalScope.localNamespaces["default.test"])
        assertNotNull(namespace.scope.functions["selected"])
        assertEquals(3, (namespace.scope.functions["selected"]!!.first().scope.getVar("result") as MCIntConcrete).value)
    }

    @Test
    fun fileCompilationUsesPreprocessedSource() {
        val root = Files.createTempDirectory("mcfpp-source-")
        val oldConfig = Project.config
        try {
            val source = root.resolve("example.mcfpp")
            Files.writeString(source, "#if MC >= 26.1\nfunc selected(){}\n#else\ninvalid {{{\n#endif\n")
            Project.config = ProjectConfig(version = "26.1", sourcePath = root, rootNamespace = "default")
            val file = MCFPPFile(source.toFile())
            val parser = mcfppParser(file.token())
            parser.compilationUnit()
            assertEquals(0, parser.numberOfSyntaxErrors)
        } finally {
            Project.config = oldConfig
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun emitsNewAndLegacyPackMetadata() {
        val expected = mapOf("26.1" to listOf(101, 1), "26.2" to listOf(107, 1), "26.3" to listOf(121, 0))
        for ((version, format) in expected) {
            assertTrue(Utils.version.contains(version))
            val pack = JSON.parseObject(DatapackCreator.packMcMetaJson(version, "test")).getJSONObject("pack")
            assertEquals(format, pack.getJSONArray("min_format").map { it as Int })
            assertEquals(format, pack.getJSONArray("max_format").map { it as Int })
            assertFalse(pack.containsKey("pack_format"))
        }
        val old = JSON.parseObject(DatapackCreator.packMcMetaJson("1.21.6", "test")).getJSONObject("pack")
        assertEquals(80, old.getIntValue("pack_format"))
        assertFalse(old.containsKey("min_format"))
    }

    @Test
    fun writesMetadataForMainAndImportsPacks() {
        val output = Files.createTempDirectory("mcfpp-versions-")
        try {
            MCFPPStringTest.readFromString("func selected(){}", targetPath = output.toString(), version = "26.2")
            assertEquals(0, Project.errorCount)
            for (file in listOf(output.resolve("debug/pack.mcmeta"), output.resolve("Imports/pack.mcmeta"))) {
                val pack = JSON.parseObject(Files.readString(file)).getJSONObject("pack")
                assertEquals(listOf(107, 1), pack.getJSONArray("min_format").map { it as Int })
                assertEquals(listOf(107, 1), pack.getJSONArray("max_format").map { it as Int })
            }
        } finally {
            output.toFile().deleteRecursively()
        }
    }

    @Test
    fun unknownTargetVersionDoesNotFallBack() {
        val oldConfig = Project.config
        val oldErrors = Project.errorCount
        try {
            Project.config = ProjectConfig(version = "26.4")
            assertFalse(Project.checkConfig())
            assertEquals("26.4", Project.config.version)
            assertEquals(oldErrors + 1, Project.errorCount)
        } finally {
            Project.config = oldConfig
            Project.errorCount = oldErrors
        }
    }
}
