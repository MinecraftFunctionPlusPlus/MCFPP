package top.mcfpp.test

import top.mcfpp.cli.ProjectCli
import top.mcfpp.project.ProjectDefinition
import top.mcfpp.project.ProjectTemplates
import top.mcfpp.project.toml
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.util.jar.JarOutputStream
import kotlin.test.*
import kotlin.test.Test

class MniDependencyWorkflowTest {
    @Test fun javaDependencyChangesEnableMniReuseLocksUpdateAndRollback() {
        val root = Files.createTempDirectory("mcfpp-mni-dependencies-")
        fun run(vararg arguments: String): Pair<Int, String> {
            val output = StringWriter()
            val writer = PrintWriter(output, true)
            return ProjectCli.run(arrayOf(*arguments), writer, writer, root) to output.toString()
        }
        fun success(vararg arguments: String) {
            val result = run(*arguments)
            assertEquals(0, result.first, result.second)
        }
        try {
            val repository = Files.createDirectories(root.resolve("repository"))
            fun publish(versions: List<String>) {
                val module = Files.createDirectories(repository.resolve("example/helper"))
                Files.writeString(module.resolve("maven-metadata.xml"), "<metadata><groupId>example</groupId><artifactId>helper</artifactId><versioning><versions>" +
                    versions.joinToString("") { "<version>$it</version>" } + "</versions></versioning></metadata>")
                versions.forEach { version ->
                    val directory = Files.createDirectories(module.resolve(version))
                    Files.writeString(directory.resolve("helper-$version.pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>helper</artifactId><version>$version</version></project>")
                    JarOutputStream(Files.newOutputStream(directory.resolve("helper-$version.jar"))).use { }
                }
            }
            val project = ProjectTemplates.create(root, false)
            Files.writeString(project.file, toml(project.values + ("mni" to mapOf("enabled" to false, "repositories" to listOf(repository.toUri().toString())))))
            publish(listOf("1.0.0"))
            success("add", "--mni", "example:helper:1.+")
            assertTrue(ProjectDefinition.load(project.file).mniEnabled)
            val lock = root.resolve("mcfpp-mni.lock")
            assertContains(Files.readString(lock), "example:helper:1.0.0=")
            publish(listOf("1.0.0", "1.1.0"))
            success("install")
            assertContains(Files.readString(lock), "example:helper:1.0.0=")
            success("update")
            assertContains(Files.readString(lock), "example:helper:1.1.0=")
            val tracked = listOf(project.file, root.resolve("mcfpp-packages.json"), root.resolve("mcfpp.lock"), lock)
            val before = tracked.associateWith(Files::readAllBytes)
            assertNotEquals(0, run("add", "--mni", "missing:helper:9.0.0").first)
            before.forEach { (file, bytes) -> assertContentEquals(bytes, Files.readAllBytes(file)) }
            success("remove", "--mni", "example:helper")
            assertFalse(Files.readString(lock).contains("example:helper:"))
            assertTrue(ProjectDefinition.load(project.file).mniEnabled)
            success("install", "--locked", "--offline")
        } finally { root.toFile().deleteRecursively() }
    }
}
