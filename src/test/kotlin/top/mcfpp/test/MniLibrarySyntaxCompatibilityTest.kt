package top.mcfpp.test

import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MniLibrarySyntaxCompatibilityTest {
    @Test fun lexerAndParserShareTheSerializedVocabulary() {
        // Only the parser's unreachable accessor shim is outside the serialized lexer vocabulary.
        assertEquals(mcfppLexer.VOCABULARY.maxTokenType + 1, mcfppParser.VOCABULARY.maxTokenType)
        assertEquals(mcfppParser.VOCABULARY.maxTokenType, mcfppParser.LEGACY_ACCESSOR_SHIM)
        assertEquals("LEGACY_ACCESSOR_SHIM", mcfppParser.VOCABULARY.getSymbolicName(mcfppParser.LEGACY_ACCESSOR_SHIM))
        for (token in 1..mcfppLexer.VOCABULARY.maxTokenType) {
            assertEquals(mcfppLexer.VOCABULARY.getSymbolicName(token), mcfppParser.VOCABULARY.getSymbolicName(token))
            assertEquals(mcfppLexer.VOCABULARY.getLiteralName(token), mcfppParser.VOCABULARY.getLiteralName(token))
        }
        assertEquals(listOf("DEFAULT_MODE", "OrgCommand", "MultiLineString"), mcfppLexer.modeNames.take(3))
    }

    @Test fun bundledTreesKeepIdentifierGettersAndCompileExecutableCommands() = withLibrary { output ->
        Project.config.version = "26.3"
        Project.init()
        Project.readProject()
        var storedIdentifiers = 0
        fun inspect(node: ParseTree) {
            if (node is mcfppParser.VarWithSuffixContext && node.text == "SlotType") {
                val identifier = assertNotNull(node.Identifier(), "bundled SlotType must retain its Identifier token")
                assertEquals(mcfppLexer.Identifier, identifier.symbol.type)
                storedIdentifiers++
            }
            for (index in 0 until node.childCount) inspect(node.getChild(index))
        }
        GlobalScope.libNamespaces.values.flatMap { it.scope.objects }.filterIsInstance<DataTemplate>()
            .forEach { template -> template.preInit.values.forEach(::inspect) }
        assertTrue(storedIdentifiers > 0, "must inspect existing serialized library expressions")

        MCFPPStringTest.readFromString(
            """
            import mcfpp.minecraft.other:*
            func main(){
                var slot = Slot(3, SlotType.container)
                external result = slot.index
            }
            """.trimIndent(),
            version = "26.3"
        )
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val result = assertIs<MCInt>(main.scope.getVar("result"))
        val functions = exported(output)
        val machine = ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions)
        assertEquals(3, machine.read(result))
        assertEquals(0, machine.stackDepth)
    }

    private fun exported(output: Path): Map<String, List<String>> {
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        return linkedMapOf<String, List<String>>().also { functions ->
            Files.walk(data).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mcfunction") }.forEach { file ->
                    val relative = data.relativize(file)
                    if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                        val name = relative.subpath(2, relative.nameCount).joinToString("/").removeSuffix(".mcfunction")
                        functions["${relative.getName(0)}:$name"] = Files.readAllLines(file).map(String::trim)
                    }
                }
            }
        }
    }

    private fun withLibrary(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-library-syntax-")
        val config = Project.config
        val settings = listOf(
            CompileSettings.isDebug,
            CompileSettings.ignoreStdLib,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = false
            action(output)
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            output.toFile().deleteRecursively()
        }
    }
}
