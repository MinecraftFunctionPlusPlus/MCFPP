package top.mcfpp.test

import top.mcfpp.*
import top.mcfpp.optimization.PassIds
import java.nio.file.Files
import kotlin.test.*
import kotlin.test.Test

class OptimizationConfigTest {
    @Test fun passSelectionIsAnImmutableCacheAndJournalValue() {
        val selection = linkedSetOf(PassIds.INLINE)
        val config = top.mcfpp.optimization.OptimizationConfig(disabledPasses = selection)
        val hash = config.hashCode()
        selection.clear()
        assertEquals(setOf(PassIds.INLINE), config.disabledPasses)
        assertEquals(hash, config.hashCode())
        assertEquals(config, config.copy())
    }

    @Test fun projectWeightsAndCliOverridesAreReadWithoutLeakingBetweenInvocations() {
        val root = Files.createTempDirectory("mcfpp-optimization-config-")
        val file = root.resolve("mcfpp.toml")
        try {
            Files.writeString(file, """
                schema = 1
                name = "optimization"
                args = ["-O1"]
                [optimization]
                level = 0
                [optimization.costs]
                storage = 14
                macroFunctionCommand = 2
            """.trimIndent())
            val config = readProjectConfig(parseCompilerArgs(arrayOf(file.toString(), "-O2", "--disable-pass=${PassIds.INLINE}")))
            assertEquals(2, config.optimization.level)
            assertEquals(14.0, config.optimization.costs.storage)
            assertEquals(2.0, config.optimization.costs.macroFunctionCommand)
            assertEquals(setOf(PassIds.INLINE), config.optimization.disabledPasses)
            assertEquals(1, readProjectConfig(parseCompilerArgs(arrayOf(file.toString()))).optimization.level)
            Files.writeString(file, "schema = 1\nname = \"optimization\"\n")
            assertEquals(2, readProjectConfig(parseCompilerArgs(arrayOf(file.toString()))).optimization.level)
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(root) }
    }
}
