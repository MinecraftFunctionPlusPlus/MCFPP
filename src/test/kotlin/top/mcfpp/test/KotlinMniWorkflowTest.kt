package top.mcfpp.test

import top.mcfpp.cli.ProjectCli
import top.mcfpp.project.*
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test

class KotlinMniWorkflowTest {
    @Test fun kotlinProviderDependenciesExtensionsTestsAndRegeneration() {
        val root = Files.createTempDirectory("mcfpp-kotlin-workflow-")
        fun run(vararg args: String): Pair<Int, String> {
            val output = StringWriter()
            val writer = PrintWriter(output, true)
            return ProjectCli.run(arrayOf(*args), writer, writer, root) to output.toString()
        }
        try {
            ProjectTemplates.create(root, true)
            val repository = root.resolve("repository")
            val module = Files.createDirectories(repository.resolve("example/fixture-helper/1.0.0"))
            val java = Files.writeString(root.resolve("Helper.java"), "package fixture; public class Helper { public static int number() { return 42; } }")
            val classes = Files.createDirectories(root.resolve("fixture-classes"))
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "-d", classes.toString(), java.toString()))
            JarOutputStream(Files.newOutputStream(module.resolve("fixture-helper-1.0.0.jar"))).use { jar ->
                jar.putNextEntry(JarEntry("fixture/Helper.class")); jar.write(Files.readAllBytes(classes.resolve("fixture/Helper.class"))); jar.closeEntry()
            }
            Files.writeString(module.resolve("fixture-helper-1.0.0.pom"), "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>fixture-helper</artifactId><version>1.0.0</version></project>")
            val definition = ProjectDefinition.load(root.resolve("mcfpp.toml"))
            val values = LinkedHashMap(definition.values)
            values["mni"] = mapOf("enabled" to true, "repositories" to listOf(repository.toUri().toString(), "https://repo.maven.apache.org/maven2/"),
                "plugins" to mapOf("org.gradle.test-retry" to "1.6.2"), "extensions" to listOf("custom.gradle.kts"))
            // An external script uses Gradle's ordinary APIs, and is reloaded on every build.
            Files.writeString(root.resolve("custom.gradle.kts"), "tasks.register(\"extensionProof\") { doLast { file(\"${root.toString().replace('\\', '/')}/extension-proof\").writeText(\"ok\") } }\ntasks.named(\"classes\") { dependsOn(\"extensionProof\") }\n")
            Files.writeString(root.resolve("mcfpp.toml"), toml(values))
            val provider = root.resolve("src/main/kotlin/example/mni/Example.kt")
            Files.writeString(provider, Files.readString(provider).replace("= 42", "= fixture.Helper.number()"))
            var result = run("add", "--mni", "example:fixture-helper:1.0.0")
            assertEquals(0, result.first, result.second)
            assertFalse(Files.exists(root.resolve("build/.mcfpp/mni/classes/kotlin/main/example/mni/Example.class")), "install must not compile Kotlin")
            result = run("build")
            assertEquals(0, result.first, result.second)
            assertTrue(Files.exists(root.resolve("build/.mcfpp/mni/classes/kotlin/main/example/mni/Example.class")))
            assertTrue(Files.exists(root.resolve("extension-proof")))
            result = run("test")
            assertEquals(0, result.first, result.second)
            val mcfLock = Files.readAllBytes(root.resolve("mcfpp.lock"))
            val mniLock = Files.readAllBytes(root.resolve("mcfpp-mni.lock"))
            root.resolve(".mcfpp").toFile().deleteRecursively()
            result = run("build", "--locked", "--offline")
            assertEquals(0, result.first, result.second)
            assertContentEquals(mcfLock, Files.readAllBytes(root.resolve("mcfpp.lock")))
            assertContentEquals(mniLock, Files.readAllBytes(root.resolve("mcfpp-mni.lock")))
            Files.writeString(provider, Files.readString(provider).replace("fixture.Helper.number()", "43"))
            result = run("build", "--offline")
            assertEquals(0, result.first, result.second)
            result = run("test", "--offline")
            assertNotEquals(0, result.first, "Changed Kotlin implementation must fail the generated assertion")
            Files.writeString(provider, Files.readString(provider).replace("= 43", "= "))
            result = run("build", "--offline")
            assertNotEquals(0, result.first)
        } finally { root.toFile().deleteRecursively() }
    }
}
