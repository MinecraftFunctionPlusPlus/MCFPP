package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow
import kotlin.test.*
import kotlin.test.Test

class MathLibraryTest {
    private val data = Path.of("src/main/resources/datapack/stdlib/data")

    private fun functions(root: Path): Map<String, List<String>> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.toList().associate { file ->
            val path = root.relativize(file).joinToString("/") { it.toString() }
            path.substringBefore('/') + ":" + path.substringAfter("/function/").removeSuffix(".mcfunction") to Files.readAllLines(file)
        }
    }

    private fun compile(body: String, version: String): Function {
        Project.config = ProjectConfig()
        val source = "import mcfpp.math:*\nfunc main(){\n$body\n}"
        MCFPPStringTest.readFromString(source, version = version)
        assertEquals(0, Project.errorCount, version)
        return GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.single { it.identifier == "main" }
    }

    private fun execute(entry: Function): Pair<ScoreCommandExecutor, Map<String, List<String>>> {
        val output = Files.createTempDirectory("mcfpp-math-")
        try {
            DatapackCreator.createDatapack(output.toString())
            val emitted = functions(output.resolve(Project.config.name).resolve("data"))
            val library = functions(data)
            val constants = Files.readAllLines(data.resolve("math/function/_init.mcfunction")).filter { it.startsWith("scoreboard players set ") }
            return ScoreCommandExecutor(constants + emitted.getValue(entry.namespaceID.toString()), library + emitted,
                targetVersion = Project.config.version, commandBudget = 200000) to emitted
        } finally { output.toFile().deleteRecursively() }
    }

    private fun observe(expressions: Map<String, String>) = buildString {
        for ((name, expression) in expressions) {
            appendLine("var $name = $expression")
            appendLine("/data modify storage fixture:math $name set from storage mcfpp:system stack_frame[0].$name")
        }
    }
    private fun number(machine: ScoreCommandExecutor, name: String): Double {
        val tag = machine.readNbt("fixture:math", name)
        if (tag is FloatTag) return tag.value.toDouble()
        val value = tag as CompoundTag
        fun int(field: String) = (value[field]!!.value as Number).toInt()
        return int("sign") * (int("int0") * 10000.0 + int("int1")) * 10.0.pow(int("exp") - 8)
    }

    @Test fun scalarFunctionsExecuteWithRuntimeArgumentsOnBothBackends() {
        val expected = linkedMapOf(
            "absolute" to ("abs(x)" to 1.5),
            "minimum" to ("min(x, y)" to -1.5),
            "maximum" to ("max(x, y)" to 4.0),
            "floored" to ("floor(x)" to -2.0),
            "ceiling" to ("ceil(x)" to -1.0),
            "truncated" to ("trunc(x)" to -1.0),
            "rounded" to ("round(x)" to -1.0),
            "smallRounded" to ("round(-0.5)" to 0.0),
            "smallFloored" to ("floor(-0.125)" to -1.0),
            "largeFloored" to ("floor(3000000000.0)" to 3000000000.0),
            "root" to ("sqrt(y)" to 2.0),
            "zeroRoot" to ("sqrt(0.0)" to 0.0),
            "squareValue" to ("square(x)" to 2.25),
            "powered" to ("pow(x, exponent)" to -3.375),
            "inversePower" to ("pow(y, -2)" to 0.0625),
            "largeOddPower" to ("pow(-1.0,16777217)" to -1.0),
            "largeNegativeOddPower" to ("pow(-1.0,-16777217)" to -1.0),
            "cubeRoot" to ("cbrt(-8.0)" to -2.0),
            "fifthRoot" to ("nroot(32.0, 5)" to 2.0),
            "length2" to ("hypot(3.0, 4.0)" to 5.0),
            "length3" to ("hypot(2.0, 3.0, 6.0)" to 7.0),
            "clamped" to ("clamp(x, 0.0, y)" to 0.0),
            "interpolated" to ("lerp(2.0, y, 0.25)" to 2.5),
            "degrees" to ("toDegrees(toRadians(90.0))" to 90.0),
            "retained" to ("saved + later" to 4.0),
        )
        for (version in listOf("26.3", "1.20.1")) {
            val entry = compile("external x:float=-1.5\nexternal y:float=4.0\nexternal exponent:int=3\nvar saved=sqrt(y)\nvar later=nroot(32.0,5)\n" +
                observe(expected.mapValues { it.value.first }), version)
            val (machine, _) = execute(entry)
            for ((name, result) in expected) assertEquals(result.second, number(machine, name), 0.0001, "$version/$name")
            assertTrue(machine.failedScoreOperations.isEmpty(), machine.failedScoreOperations.toString())
            assertEquals(0, machine.stackDepth)
        }
    }

    @Test fun integerFunctionsPreserveExactValuesAndFlooring() {
        val cases = linkedMapOf(
            "absolute" to ("abs(a)" to 7), "minimum" to ("min(a,b)" to -7),
            "maximum" to ("max(a,b)" to 3), "division" to ("floorDiv(a,b)" to -3),
            "modulus" to ("floorMod(a,b)" to 2), "power" to ("pow(a,b)" to -343),
            "root" to ("isqrt(2147483647)" to 46340), "zeroRoot" to ("isqrt(0)" to 0),
            "nearSquare" to ("isqrt(16785408)" to 4096),
            "minimumPower" to ("pow(-2,31)" to Int.MIN_VALUE),
            "overflowPower" to ("pow(2,31)" to 0),
            "largeMinimum" to ("min(2147483647,2147483646)" to 2147483646),
        )
        for (version in listOf("26.3", "1.20.1")) {
            val entry = compile("external a:int=-7\nexternal b:int=3\n" + observe(cases.mapValues { it.value.first }), version)
            val (machine, _) = execute(entry)
            for ((name, expected) in cases) assertEquals(expected.second, (machine.readNbt("fixture:math", name).value as Number).toInt(), "$version/$name")
        }
    }

    @Test fun nativeMathDoesNotRetainLegacyResourcesAndSupportsRealExponents() {
        val entry = compile(observe(mapOf("value" to "pow(9.0,0.5)", "sine" to "sin(0.0)", "pair" to "sinCos(0.0)",
            "blend" to "lerp(2.0,6.0,0.25)", "angle" to "toRadians(0.0)")), "26.3")
        val (machine, emitted) = execute(entry)
        assertEquals(3.0, number(machine, "value"), 0.000001)
        assertEquals(0.0, number(machine, "sine"), 0.000001)
        assertEquals(3.0, number(machine, "blend"), 0.000001)
        assertTrue(emitted.keys.none { it.startsWith("math:") || it.startsWith("math.float:") }, emitted.keys.toString())
        assertTrue(emitted.values.flatten().none { "summon " in it || "schedule " in it })
        val pair = machine.readNbt("fixture:math", "pair") as CompoundTag
        assertEquals(0f, (pair["sin"] as FloatTag).value)
        assertEquals(1f, (pair["cos"] as FloatTag).value)
    }

    @Test fun angleFallbackUsesScopedEntitiesAndRadians() {
        for (version in listOf("26.3", "1.20.1", "1.13")) {
            compile("external x:float=0.5\n" + observe(mapOf("a" to "asin(x)", "b" to "acos(x)",
                "c" to "atan2(x,-1.0)", "pair" to "sinCos(x)")), version)
            val output = Files.createTempDirectory("mcfpp-math-angles-")
            try {
                DatapackCreator.createDatapack(output.toString())
                val linked = functions(output.resolve(Project.config.name).resolve("data"))
                assertTrue(linked.getValue("math:scalar/atan2").any { "summon armor_stand" in it })
                assertEquals("kill @s", linked.getValue("math:scalar/atan2_entity").last())
                assertTrue("math:_atan2" in linked)
                assertFalse("math:tick" in linked)
                assertTrue(linked.values.flatten().none { "forceload " in it || "schedule " in it })
                assertTrue(linked.getValue("math:_init").none { "summon " in it || "init_overworld" in it })
                assertEquals(version != "26.3", linked.keys.any { it.startsWith("math.float:") })
            } finally { output.toFile().deleteRecursively() }
        }
    }

    @Test fun linkedLegacyMathContainsEveryNumericConstantItReads() {
        val entry = compile("external x:float=4.0\n" + observe(mapOf("root" to "sqrt(x)", "power" to "pow(x,3)")), "1.20.1")
        val (_, emitted) = execute(entry)
        val initializer = emitted.getValue("math:_init").toSet()
        val startup = emitted.values.flatten().filter {
            it.startsWith("execute unless score math_version ") ||
                it.startsWith("scoreboard players set math_version ")
        }.distinct()
        assertEquals(2, startup.size)
        val upgraded = ScoreCommandExecutor(startup, emitted, initialScores = mapOf("math mcfpp_init" to 1))
        assertEquals(315, upgraded.values.getValue("math_version mcfpp_init"))
        for (constant in listOf(4750, 24703, 79249))
            assertEquals(constant, upgraded.values.getValue("$constant int"))
        val constants = Regex("""(?<![a-zA-Z_])(-?\d+) int\b""")
        for ((id, body) in emitted.filterKeys { it.startsWith("math:") || it.startsWith("math.float:") }) {
            for (line in body.filterNot { it.trimStart().startsWith("#") }) {
                for (match in constants.findAll(line)) {
                    val value = match.groupValues[1]
                    assertTrue("scoreboard players set $value int $value" in initializer, "$id: missing constant $value")
                }
            }
        }
    }

    @Test fun knownInvalidDomainsAreDiagnosedAndRealExponentOverloadIsVersioned() {
        for (version in listOf("26.3", "1.20.1")) {
            for (call in listOf("sqrt(-1.0)", "isqrt(-1)", "nroot(-4.0,2)", "nroot(4.0,1)", "pow(0,0)", "floorDiv(1,0)")) {
                Project.config = ProjectConfig()
                MCFPPStringTest.readFromString("import mcfpp.math:*\nfunc main(){var invalid=$call}", version = version)
                assertTrue(Project.errorCount > 0, "$version/$call")
            }
        }
        Project.config = ProjectConfig()
        MCFPPStringTest.readFromString("import mcfpp.math:*\nfunc main(){var invalid=pow(4.0,0.5)}", version = "1.20.1")
        assertTrue(Project.errorCount > 0)
    }
}
