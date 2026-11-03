package top.mcfpp.test

import org.junit.jupiter.api.Assumptions.assumeTrue
import top.mcfpp.cli.ProjectCli
import top.mcfpp.project.ProjectTemplates
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class McfppSourceTestTest {
    private fun project(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-source-tests-")
        try {
            ProjectTemplates.create(root, false)
            Files.createDirectories(root.resolve("src/test/mcfpp"))
            action(root)
        } finally { root.toFile().deleteRecursively() }
    }

    private fun run(root: Path, vararg args: String): Pair<Int, String> {
        val output = StringWriter()
        val writer = PrintWriter(output, true)
        return ProjectCli.run(arrayOf("test", *args), writer, writer, root) to output.toString()
    }

    @Test fun discoversMcfppFunctionsWithoutRequiringSandbox() = project { root ->
        Files.writeString(root.resolve("src/test/mcfpp/arithmetic.mcfpp"), """
            namespace source.tests
            import mcfpp.test:*
            @Test
            func addition() { assertEquals(3, 1 + 2); }
            @Disabled("Later")
            @Test
            func pending() { fail(); }
        """.trimIndent())
        val result = run(root, "--list", "--offline")
        assertEquals(0, result.first, result.second)
        assertContains(result.second, "source.tests:addition")
        assertContains(result.second, "DISABLED Later")
        assertFalse(Files.exists(root.resolve("build/tests/mcfpp/TEST-mcfpp.xml")))
        val absent = run(root, "--list", "--tests", "source.tests:missing", "--offline")
        assertNotEquals(0, absent.first)
        assertContains(absent.second, "No MCFPP tests match")
    }

    @Test fun rejectsParameterizedTestDeclarations() = project { root ->
        Files.writeString(root.resolve("src/test/mcfpp/invalid.mcfpp"), """
            import mcfpp.test:*
            @Test
            func invalid(value: int) { assertEquals(1, value); }
        """.trimIndent())
        assertNotEquals(0, run(root, "--list", "--offline").first)
    }

    @Test fun executesRealSandboxWithIsolationTeardownFailureAndDisabledResults() {
        val jar = System.getenv("DSB_JAR")
        assumeTrue(jar != null && Files.isRegularFile(Path.of(jar)), "Set DSB_JAR to run the real DSB integration")
        project { root ->
            Files.writeString(root.resolve("src/test/mcfpp/runtime.mcfpp"), """
                namespace source.tests
                import mcfpp.test:*
                @BeforeEach
                func setup() {
                    /scoreboard objectives add fixture dummy
                    /scoreboard players add #visits fixture 1
                }
                @AfterEach
                func teardown() {
                    /data modify storage source:tests tornDown set value 1
                }
                func add(left: int, right: int): int { return left + right; }
                func foooo(value: int): int { return value; }
                @Test
                func succeeds() {
                    var f = { a: int -> 1 }
                    assertEquals(1, f(1))
                    f = foooo
                    assertEquals(2, f(2))
                    var reference: (int) -> int = foooo
                    assertEquals(3, reference(3))
                    assertEquals(3, add(1, 2))
                    assertTrue(true)
                    assertFalse(false)
                    assertEquals(true, true)
                    assertEquals("MCFPP", "MCFPP")
                    assertNotEquals(1, 2)
                    /execute unless score #visits fixture matches 1 run scoreboard players add #failures mcfpp_test 1
                }
                @Test
                func fails() {
                    assertEquals(4, add(1, 2), "expected failure")
                    assertEquals("expected", "actual", "string failure")
                }
                @Disabled("Example pending")
                @Test
                func pending() { fail(); }
            """.trimIndent())
            val result = run(root, "--dsb-jar", jar!!, "--dsb-java", System.getenv("DSB_JAVA") ?: "java", "--offline")
            assertEquals(1, result.first, result.second)
            assertContains(result.second, "1 passed, 1 failed, 1 skipped")
            val xml = Files.readString(root.resolve("build/tests/mcfpp/TEST-mcfpp.xml"))
            assertContains(xml, "failures=\"1\"")
            assertContains(xml, "skipped=\"1\"")
            assertContains(xml, "expected failure")
            assertContains(xml, "string failure")
            val report = Files.readString(root.resolve("build/tests/mcfpp/cases/0/dsb-report.json"))
            assertContains(report, "tornDown")
            val selected = run(root, "--tests", "source.tests:suc*", "--dsb-jar", jar,
                "--dsb-java", System.getenv("DSB_JAVA") ?: "java", "--offline")
            assertEquals(0, selected.first, selected.second)
            assertContains(selected.second, "1 passed, 0 failed, 0 skipped")
        }
    }
}
