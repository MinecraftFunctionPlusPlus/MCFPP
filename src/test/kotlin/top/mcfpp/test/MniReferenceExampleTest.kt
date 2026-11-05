package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.command.Commands
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MniReferenceExampleTest {
    @Test fun scoreReferenceExampleUpdatesTargetsThroughNestedCallbacks() {
        val machine = compileAndExecute("damage", "default.test")
        assertEquals(70, machine.score("#normal", "hp"))
        assertEquals(0, machine.score("#low", "hp"))
        assertEquals(0, machine.score("#alias", "hp"))
    }

    @Test fun nbtReferenceExampleUpdatesFieldsAndListElementsThroughNestedCallbacks() {
        val machine = compileAndExecute("nbt-reference", "example.nbtreference")
        fun number(path: String) = (machine.readStorage("example:health", path).value as Number).toInt()
        assertEquals(70, number("normal.hp"))
        assertEquals(10, number("alias.hp"))
        assertEquals(10, number("values[0]"))
        assertEquals(15, number("values[1]"))
    }

    private fun compileAndExecute(example: String, namespace: String): ContextCommandExecutor {
        val source = Files.readString(Path.of("examples/mni/$example/project/src/main.mcfpp"))
        val diagnostics = LogProcessor.beginDiagnosticCapture().use { capture ->
            MCFPPStringTest.readFromString(source, version = "26.3", afterFileCreated = { file ->
                file.namespace = GlobalScope.getOrCreateNamespace(namespace)
            })
            capture.seal().diagnostics.map { it.message }
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
        val output = Path.of("build/mni-reference-examples/$example")
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
                val relative = data.relativize(file)
                if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                    val name = relative.subpath(2, relative.nameCount).joinToString("/").removeSuffix(".mcfunction")
                    functions["${relative.getName(0)}:$name"] = Files.readAllLines(file)
                }
            }
        }
        val main = GlobalScope.localNamespaces.getValue(namespace).scope.functions.getValue("main").single()
        return ContextCommandExecutor(listOf(Commands.stackIn().analyze(), "function ${main.namespaceID}"), functions,
            macros = Project.macroFunction.toMap())
    }
}
