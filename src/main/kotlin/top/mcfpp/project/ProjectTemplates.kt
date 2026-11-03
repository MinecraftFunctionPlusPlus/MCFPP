package top.mcfpp.project

import java.nio.file.Files
import java.nio.file.Path

/** Shared scaffolding entry point for the CLI and future editor integrations. */
object ProjectTemplates {
    const val JUNIT = "5.10.1"

    fun create(directory: Path, mni: Boolean): ProjectDefinition {
        val root = directory.toAbsolutePath().normalize()
        require(!Files.exists(root.resolve("mcfpp.toml"))) { "mcfpp.toml already exists" }
        val name = root.fileName.toString()
        val namespace = name.lowercase().replace(Regex("[^a-z0-9_.-]"), "_").ifEmpty { "example" }
        val files = linkedMapOf(
            "mcfpp.toml" to toml(linkedMapOf("schema" to 1, "name" to name, "namespace" to namespace,
                "version" to "1.21.8", "description" to "A new MCFPP datapack",
                "sourcePath" to "src/main/mcfpp", "targetPath" to "build", "dataPath" to "src/main/resources/data",
                "assetsPath" to "src/main/resources/assets",
                "toolchain" to mapOf("version" to ProjectDefinition.TOOLCHAIN), "mni" to mapOf("enabled" to mni))),
            "mcfpp-packages.json" to String(PackageDeclarations(mni = if (mni) listOf(
                MniDependency("org.junit.jupiter:junit-jupiter:$JUNIT", "testImplementation"),
                MniDependency("org.junit.platform:junit-platform-launcher:1.10.1", "testRuntimeOnly")) else emptyList()).bytes()),
            "src/main/mcfpp/main.mcfpp" to if (mni) "func answer(): int = example.mni.Example.answer\n\nfunc main() {\n    print(answer())\n}\n" else "func main() {\n    print(\"Hello MCFPP!\")\n}\n",
            "README.md" to "# $name\n\nRun `mcfpp build` to compile the datapack.\n" +
                if (mni) "Run `mcfpp test` for Java tests. Generated Gradle files are in `.mcfpp`.\n" else ""
        )
        if (mni) files.putAll(mniSources())
        files.keys.forEach { require(!Files.exists(root.resolve(it))) { "Will not overwrite ${root.resolve(it)}" } }
        files.forEach { (path, text) -> atomicWrite(root.resolve(path), text.toByteArray()) }
        Files.createDirectories(root.resolve("src/main/resources/data"))
        Files.createDirectories(root.resolve("src/main/resources/assets"))
        Files.createDirectories(root.resolve("src/test/mcfpp"))
        val ignore = root.resolve(".gitignore")
        val previous = if (Files.exists(ignore)) Files.readString(ignore) else ""
        atomicWrite(ignore, (previous.trimEnd() + "\n/.mcfpp/\n/build/\n").toByteArray())
        return ProjectDefinition.load(root.resolve("mcfpp.toml"))
    }

    internal fun mniSources(): Map<String, String> = mapOf(
        "src/main/java/example/mni/Example.java" to """
            package example.mni;

            import top.mcfpp.annotations.MNIFunction;
            import top.mcfpp.core.lang.MCInt;
            import top.mcfpp.util.ValueWrapper;

            public final class Example {
                public static int answerValue() { return 42; }

                @MNIFunction(returnType = "int")
                public static void answer(ValueWrapper<MCInt> result) {
                    result.set(new MCInt(answerValue(), "answer"));
                }
            }
        """.trimIndent() + "\n",
        "src/test/java/example/mni/ExampleTest.java" to """
            package example.mni;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            class ExampleTest {
                @Test void answerIs42() { assertEquals(42, Example.answerValue()); }
            }
        """.trimIndent() + "\n"
    )
}
