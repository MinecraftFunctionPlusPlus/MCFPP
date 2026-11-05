package top.mcfpp.test

import top.mcfpp.cli.ProjectCli
import top.mcfpp.project.*
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class ProjectWorkflowTest {
    private fun project(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-workflow-")
        try { action(root) } finally { root.toFile().deleteRecursively() }
    }
    private fun run(root: Path, vararg args: String): Pair<Int, String> {
        val output = StringWriter()
        val writer = PrintWriter(output, true)
        return ProjectCli.run(arrayOf(*args), writer, writer, root) to output.toString()
    }

    @Test fun pureProjectBuildsAndRecoversGeneratedStateFromLocks() = project { root ->
        assertEquals(0, run(root, "init").first)
        val build = run(root, "build")
        assertEquals(0, build.first, build.second)
        assertTrue(Files.exists(root.resolve("build/bin.mclib")))
        val name = ProjectDefinition.load(root.resolve("mcfpp.toml")).values["name"]
        assertTrue(Files.exists(root.resolve("build/$name/pack.mcmeta")))
        val lock = Files.readAllBytes(root.resolve("mcfpp.lock"))
        root.resolve(".mcfpp").toFile().deleteRecursively()
        val restored = run(root, "build", "--locked", "--offline")
        assertEquals(0, restored.first, restored.second)
        assertContentEquals(lock, Files.readAllBytes(root.resolve("mcfpp.lock")))
        assertEquals(0, run(root, "clean").first)
        assertFalse(Files.exists(root.resolve("build")))
        assertTrue(Files.exists(root.resolve("mcfpp.lock")))
    }

    @Test fun dependencyFailureKeepsDeclarationsAndLocks() = project { root ->
        ProjectTemplates.create(root, false)
        assertEquals(0, run(root, "install").first)
        val packages = Files.readAllBytes(root.resolve("mcfpp-packages.json"))
        val lock = Files.readAllBytes(root.resolve("mcfpp.lock"))
        val failure = run(root, "add", "missing:package@^1.0.0", "--offline")
        assertNotEquals(0, failure.first)
        assertContentEquals(packages, Files.readAllBytes(root.resolve("mcfpp-packages.json")))
        assertContentEquals(lock, Files.readAllBytes(root.resolve("mcfpp.lock")))
    }

    @Test fun lockedInstallRejectsMissingLock() = project { root ->
        ProjectTemplates.create(root, false)
        assertNotEquals(0, run(root, "install", "--locked").first)
        assertFalse(Files.exists(root.resolve("mcfpp.lock")))
    }

    @Test fun dependenciesUseStrictJsonAndCompleteCoordinates() = project { root ->
        val file = root.resolve("packages.json")
        for (text in listOf("{\"schema\":1,//comment\n\"mni\":[]}", "{\"schema\":1,}", "{\"schema\":1,\"schema\":1}")) {
            Files.writeString(file, text)
            assertFails { PackageDeclarations.read(file) }
        }
        assertFails { PackageDeclarations(mapOf("short-name" to "1.0.0")) }
        assertFails { MniDependency("group:artifact") }
    }

    @Test fun onlyTomlProjectsAreAcceptedAndPathsAreProjectRelative() = project { root ->
        Files.writeString(root.resolve("mcfpp.json"), "{}")
        assertFails { ProjectDefinition.readToml(root.resolve("mcfpp.json")) }
        ProjectTemplates.create(root, false)
        val child = Files.createDirectories(root.resolve("src/main/mcfpp"))
        assertEquals(root.resolve("mcfpp.toml"), ProjectDefinition.find(child))
        assertFails { ProjectTemplates.create(root, false) }
        val values = ProjectDefinition.readToml(root.resolve("mcfpp.toml"))
        assertFails { ProjectDefinition(root.resolve("mcfpp.toml"), values + ("targetPath" to ".."), PackageDeclarations()) }
    }

    @Test fun kotlinTemplateContainsProviderAndTests() = project { root ->
        val definition = ProjectTemplates.create(root, true)
        assertTrue(definition.mniEnabled)
        assertTrue(Files.isRegularFile(root.resolve("src/main/kotlin/example/mni/Example.kt")))
        assertTrue(Files.isRegularFile(root.resolve("src/test/kotlin/example/mni/ExampleTest.kt")))
        assertEquals(setOf("testImplementation", "testRuntimeOnly"), definition.packages.mni.map { it.configuration }.toSet())
    }
}
