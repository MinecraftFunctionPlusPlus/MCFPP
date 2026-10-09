package top.mcfpp.test

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.PrimitiveEvaluation
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor

class FloatArithmeticContractTest {
    private data class Inputs(val left: String, val right: String) {
        val a get() = left.toFloat()
        val b get() = right.toFloat()
    }
    private val inputs = listOf(Inputs("7.5", "2.0"), Inputs("-7.5", "2.0"),
        Inputs("1.0", "0.0"), Inputs("0.0", "0.0"), Inputs("-0.0", "-0.0"),
        Inputs("-0.0", "0.0"), Inputs("0.0", "-0.0"),
        Inputs("3.4028235e38", "2.0"), Inputs("3.4028235e38", "-3.4028235e38"),
        Inputs("1.4e-45", "2.0"), Inputs("16777216.0", "1.0"), Inputs("1.0000025", "1.0000024"))
    private val arithmetic = linkedMapOf("sum" to "+", "difference" to "-", "product" to "*", "quotient" to "/", "remainder" to "%")
    private val comparisons = linkedMapOf("greater" to ">", "less" to "<", "atLeast" to ">=", "atMost" to "<=", "equal" to "==", "unequal" to "!=")
    private val precision = MathContext(8, RoundingMode.DOWN)

    @Test
    fun floatOperatorsPreserveTargetResultsAndLiveOperandsAcrossNativeIRFoldingAndFreshLibraries() = isolated { output ->
        for (version in listOf("26.3", "1.20.2", "1.20.1")) for (ir in listOf(false, true)) for (fold in listOf(false, true)) {
            CompileSettings.foldIRConstants = fold
            val library = output.resolve("float-$version-$ir-$fold.mclib")
            val main = buildString {
                appendLine(if (ir) "func main(){" else "func main(){var box=Box();")
                for ((index, input) in inputs.withIndex()) for (known in listOf(false, true)) {
                    appendLine("${if (ir) "" else "box."}${if (known) "known_$index()" else "observe(${input.left},${input.right})"};")
                    appendLine("/data modify storage fixture:float_contract case_${index}_${if (known) "known" else "runtime"} set from storage fixture:float_contract current")
                }
                appendLine("}")
            }
            val source = buildString {
                appendLine("namespace fixture.floats;")
                if (!ir) appendLine("data Box {")
                appendLine("func observe(left as float,right as float)->int {")
                append(body(null, version)); appendLine("}")
                for ((index, input) in inputs.withIndex()) {
                    appendLine("func known_$index()->int {")
                    append(body(input, version)); appendLine("}")
                }
                if (!ir) appendLine("}")
                append(main)
            }
            for (fresh in listOf(false, true)) {
                Project.config.includes = if (fresh) arrayListOf(library.toString()) else arrayListOf()
                MCFPPStringTest.readFromString(if (fresh) "import fixture.floats:*;\n$main" else source,
                    targetPath = if (fresh) null else library.toString(), version = version)
                assertEquals(0, Project.errorCount, "$version/IR=$ir/fold=$fold/fresh=$fresh")
                val entry = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.single { it.identifier == "main" }
                val observe = if (ir) (if (fresh) GlobalScope.libNamespaces else GlobalScope.localNamespaces).getValue("fixture.floats").scope.functions.getValue("observe").single()
                    else (entry.scope.getVar("box") as DataTemplateObject).templateType.scope.functions.getValue("observe").single()
                if (ir) assertNotNull(observe.typedIR) else assertNull(observe.typedIR)
                assertTrue(observe.compiledFunctions.isEmpty())
                if (version != "26.3") assertTrue(Project.modules.single { it.id == "stdlib" }
                    .packages.entries.single { it.key.id == "math.float" }.value)
                val (machine, functions) = try {
                    execute(output.resolve("$version-$ir-$fold-$fresh"), entry)
                } catch (failure: IllegalStateException) {
                    throw IllegalStateException("$version/IR=$ir/fold=$fold/fresh=$fresh: ${failure.message}", failure)
                }
                val emitted = functions.getValue(observe.namespaceID.toString())
                if (version == "26.3") assertEquals(11, emitted.count { it.contains("set compute default float") })
                else {
                    assertTrue(emitted.any { it.contains("math.float:hpo/float/_add") })
                    assertTrue(emitted.any { it.contains("math.float:hpo/float/_rmv") })
                    assertTrue(emitted.any { it.contains("math.float:hpo/float/_mult") })
                    assertTrue(emitted.any { it.contains("math.float:hpo/float/_div") })
                    assertTrue(emitted.none { it.contains("compute default") })
                }
                for ((index, input) in inputs.withIndex()) for (phase in listOf("known", "runtime")) {
                    val base = "case_${index}_$phase"
                    val expected = expected(input, version)
                    for ((field, tag) in expected) {
                        val actual = machine.readNbt("fixture:float_contract", "$base.$field")
                        if (tag is FloatTag) assertEquals(tag.value.toRawBits(), (actual as FloatTag).value.toRawBits(),
                            "$version/$ir/$fold/$fresh/$base/$field")
                        else assertEquals(tag, actual, "$version/$ir/$fold/$fresh/$base/$field")
                    }
                }
                assertEquals(0, machine.stackDepth)
                assertTrue(machine.failedComputations.isEmpty())
            }
        }
    }

    private fun body(known: Inputs?, version: String) = buildString {
        appendLine("/data modify storage fixture:float_contract current set value {}")
        if (known != null) {
            appendLine("var left=${known.left};")
            appendLine("var right=${known.right};")
        }
        appendLine("var originalLeft=left;var originalRight=right;")
        for ((field, operation) in arithmetic) if (version == "26.3" || operation != "%") appendLine("var $field=left $operation right;")
        appendLine("var negativeLeft=-left;var negativeRight=-right;")
        for ((field, operation) in comparisons) appendLine("var $field=left $operation right;")
        appendLine("var nested=(left+right)*right;var saved=left+right;var materialized=saved*right;")
        appendLine("left=11.0;right=12.0;")
        val fields = arithmetic.keys.filter { version == "26.3" || it != "remainder" } +
            listOf("negativeLeft", "negativeRight", "nested", "saved", "materialized", "originalLeft", "originalRight", "left", "right") + comparisons.keys
        for (field in fields) appendLine("/data modify storage fixture:float_contract current.$field set from storage mcfpp:system stack_frame[0].$field")
        appendLine("return 7;")
    }

    private fun safe(value: Float) = if (value.isFinite()) value else 0f
    private fun native(operation: String, a: Float, b: Float): Float = safe(when (operation) {
        "+" -> (0f + a) + b
        "-" -> a - b
        "*" -> (1f * a) * b
        "/" -> a / b
        "%" -> a % b
        else -> error(operation)
    })
    private fun decimal(value: Float): BigDecimal {
        val encoded = MCFloat.floatToMCFloat(value)
        return BigDecimal(encoded[0].toLong() * (encoded[1] * 10000L + encoded[2])).scaleByPowerOfTen(encoded[3] - 8)
    }
    private fun legacy(operation: String, a: BigDecimal, b: BigDecimal): BigDecimal = when (operation) {
        "+" -> (a + b).round(precision)
        "-" -> (a - b).round(precision)
        "*" -> (a * b).round(precision)
        "/" -> if (b.signum() == 0) BigDecimal.ZERO else a.divide(b, precision)
        else -> error(operation)
    }
    private fun components(value: BigDecimal): CompoundTag {
        val rounded = value.round(precision)
        if (rounded.signum() == 0) return CompoundTag("sign" to 0, "int0" to 0, "int1" to 0, "exp" to 0)
        val exponent = rounded.precision() - rounded.scale()
        val mantissa = rounded.abs().scaleByPowerOfTen(8 - exponent).intValueExact()
        return CompoundTag("sign" to rounded.signum(), "int0" to mantissa / 10000, "int1" to mantissa % 10000, "exp" to exponent)
    }
    private fun expected(input: Inputs, version: String): Map<String, Tag<*>> = buildMap {
        val a = decimal(input.a); val b = decimal(input.b)
        for ((field, operation) in arithmetic) if (version == "26.3") put(field, FloatTag(native(operation, input.a, input.b)))
            else if (operation != "%") put(field, components(legacy(operation, a, b)))
        if (version == "26.3") {
            val sum = native("+", input.a, input.b)
            put("negativeLeft", FloatTag(-input.a)); put("negativeRight", FloatTag(-input.b))
            put("saved", FloatTag(sum)); put("nested", FloatTag(native("*", sum, input.b))); put("materialized", FloatTag(native("*", sum, input.b)))
            put("originalLeft", FloatTag(input.a)); put("originalRight", FloatTag(input.b))
            put("left", FloatTag(11f)); put("right", FloatTag(12f))
        } else {
            val sum = legacy("+", a, b)
            put("negativeLeft", components(-a)); put("negativeRight", components(-b))
            put("saved", components(sum)); put("nested", components(legacy("*", sum, b))); put("materialized", components(legacy("*", sum, b)))
            put("originalLeft", components(a)); put("originalRight", components(b))
            put("left", components(BigDecimal(11))); put("right", components(BigDecimal(12)))
        }
        val compared = if (version == "26.3") input.a.compareTo(input.b).let { if (input.a == input.b) 0 else it } else a.compareTo(b)
        for ((field, operation) in comparisons) put(field, ByteTag(if (when (operation) {
            ">" -> compared > 0; "<" -> compared < 0; ">=" -> compared >= 0; "<=" -> compared <= 0; "==" -> compared == 0; "!=" -> compared != 0; else -> error(operation)
        }) 1 else 0))
    }

    @Test
    fun illegalKnownInputsAreRejectedSeparatelyFromSafeArithmeticResults() = isolated {
        for (version in listOf("26.3", "1.20.2", "1.20.1")) {
            MCFPPStringTest.readFromString("func main(){}", version = version)
            val entry = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }.single { it.identifier == "main" }
            Function.currFunction = entry
            for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                for (operation in listOf("+", "-", "*", "/")) {
                    val errors = Project.errorCount
                    val commands = entry.commands.size
                    val value = MCFloat(invalid)
                    val operand = MCFloat(1f)
                    val result = when (operation) { "+" -> value.plus(operand); "-" -> value.minus(operand); "*" -> value.times(operand); else -> value.div(operand) }
                    assertTrue(result.isError)
                    assertEquals(errors + 1, Project.errorCount)
                    assertEquals(commands, entry.commands.size)
                    assertNull(PrimitiveEvaluation.binary(operation, CompilerValue.FloatBits(invalid.toRawBits()), CompilerValue.FloatBits(1f.toRawBits())))
                }
                for (operation in comparisons.values) {
                    val errors = Project.errorCount
                    val commands = entry.commands.size
                    val value = MCFloat(invalid)
                    val operand = MCFloat(1f)
                    val result = when (operation) {
                        ">" -> value.isBigger(operand); "<" -> value.isSmaller(operand)
                        ">=" -> value.isBiggerOrEqual(operand); "<=" -> value.isSmallerOrEqual(operand)
                        "==" -> value.isEqual(operand); else -> value.isNotEqual(operand)
                    }
                    assertTrue(result.isError)
                    assertEquals(errors + 1, Project.errorCount)
                    assertEquals(commands, entry.commands.size)
                }
                if (version == "26.3") {
                    val errors = Project.errorCount
                    assertTrue(FloatProviders.negate(MCFloat(invalid)).isError)
                    assertTrue(FloatProviders.compare(MCFloat(invalid), MCFloat(1f), "==").isError)
                    assertEquals(errors + 2, Project.errorCount)
                }
            }
        }
    }

    private fun execute(output: Path, entry: Function): Pair<ScoreCommandExecutor, Map<String, List<String>>> {
        DatapackCreator.createDatapack(output.toString())
        val data = output.resolve(Project.config.name).resolve("data")
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(data).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val relative = data.relativize(file)
            if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") functions["${relative.getName(0)}:${relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        val legacy = Path.of("src/main/resources/datapack/stdlib/data/math.float/function")
        Files.walk(legacy).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            functions["math.float:${legacy.relativize(file).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        val constants = Files.readAllLines(Path.of("src/main/resources/datapack/stdlib/data/math/function/_init.mcfunction")).filter { it.startsWith("scoreboard players set ") }
        // This entry executes every input twice (known/runtime), including the actual component library.
        // Keep the executor's ordinary per-program limit for each sequential case, plus entry setup.
        return ScoreCommandExecutor(constants + functions.getValue(entry.namespaceID.toString()), functions,
            targetVersion = Project.config.version, commandBudget = 10000 * (inputs.size * 2 + 1)) to functions
    }
    private fun isolated(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-float-contract-")
        val config = Project.config; val folding = CompileSettings.foldIRConstants
        try { Project.config = ProjectConfig(); action(output) }
        finally { Project.config = config; CompileSettings.foldIRConstants = folding; output.toFile().deleteRecursively() }
    }
}
