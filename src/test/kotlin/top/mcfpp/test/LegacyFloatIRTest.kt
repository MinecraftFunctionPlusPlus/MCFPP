package top.mcfpp.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.Instruction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor

class LegacyFloatIRTest {
    private val datapack = Path.of("src/main/resources/datapack/stdlib/data")
    private val library = Files.walk(datapack.resolve("math.float/function")).use { paths ->
        paths.filter { it.toString().endsWith(".mcfunction") }.iterator().asSequence().associate { path ->
            val relative = datapack.resolve("math.float/function").relativize(path).toString().replace('\\', '/')
            "math.float:" + relative.removeSuffix(".mcfunction") to Files.readAllLines(path)
        }
    }
    private val constants = Files.readAllLines(datapack.resolve("math/function/_init.mcfunction"))
        .filter { it.startsWith("scoreboard players set ") }
    private fun function(name: String) =
        GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()

    private fun compile(source: String, version: String = "1.20.1", ir: Boolean = true): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return function("main").also { if (ir) assertNotNull(it.typedIR) else assertNull(it.typedIR) }
    }
    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
        assertFalse(functions.values.flatten().any { "compute default" in it })
        return ScoreCommandExecutor(constants + main.commands.analyzeAll(), library + functions).also {
            assertEquals(0, it.stackDepth)
            assertTrue(it.branchGuards.isEmpty())
            assertTrue(it.failedScoreOperations.isEmpty(), it.failedScoreOperations.toString())
        }
    }
    private fun result(machine: ScoreCommandExecutor, main: Function) = machine.read(main.scope.getVar("result") as MCInt)
    private fun components(machine: ScoreCommandExecutor, path: String): List<Int> {
        val compound = machine.readNbt("mcfpp:system", path) as CompoundTag
        return listOf("sign", "int0", "int1", "exp").map { (compound[it] as IntTag).value }
    }
    private fun modes(action: () -> Unit) {
        val previous = CompileSettings.foldIRConstants
        try { for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() } }
        finally { CompileSettings.foldIRConstants = previous }
    }

    @Test fun arithmeticComparisonsAndLoopsUseTheDecimalBackendWithEitherFoldSetting() = modes {
        for (version in listOf("1.20.1", "1.20.3")) {
            val main = compile("""
                func main(){
                    var quotient = 1.0000025 / 1.0000024;
                    var value = quotient + 0.5;
                    value *= 2;
                    value -= 0.5;
                    while(value < 4.0){ value += 0.5; }
                    var negative = -value;
                    dynamic var result = 0;
                    if(quotient == 1.0 && value >= 4.0 && value <= 4.0 && negative < 0.0 &&
                        value > negative && negative != value){ result = 1; }
                    /data modify storage mcfpp:system temp.decimal set from storage mcfpp:system stack_frame[0].value
                }
            """, version)
            val machine = execute(main)
            assertEquals(1, result(machine, main))
            assertEquals(listOf(1, 4000, 0, 1), components(machine, "temp.decimal"))
            assertTrue(main.typedIR!!.blocks.flatMap { it.instructions }.any { it is Instruction.Binary && it.operation == "/" })
        }
    }

    @Test fun promotionAndExplicitConversionKeepEightDecimalDigitsInsteadOfHostFloatPrecision() = modes {
        val main = compile("""
            func promote(value as int) -> float { return value; }
            func main(){
                var promoted as float = 16777217;
                var returned = promote(16777217);
                var maximum = toFloat(2147483647);
                var small = toFloat(-7s);
                var encoded = toNBT(promoted);
                dynamic var result = 0;
                if(toInt(promoted) == 16777217 && toInt(returned) == 16777217 &&
                    toInt(maximum) == 2147483600 && toInt(small) == -7){ result = 1; }
                /data modify storage mcfpp:system temp.promoted set from storage mcfpp:system stack_frame[0].promoted
                /data modify storage mcfpp:system temp.maximum set from storage mcfpp:system stack_frame[0].maximum
                /data modify storage mcfpp:system temp.encoded set from storage mcfpp:system stack_frame[0].encoded
            }
        """)
        val machine = execute(main)
        assertEquals(1, result(machine, main))
        assertEquals(listOf(1, 1677, 7217, 8), components(machine, "temp.promoted"))
        assertEquals(listOf(1, 2147, 4836, 10), components(machine, "temp.maximum"))
        assertEquals(components(machine, "temp.promoted"), components(machine, "temp.encoded"))
        assertNotNull(function("promote").typedIR)
    }

    @Test fun recursiveCallsCaptureEarlierArgumentsAndStaticWritebackSeparately() = modes {
        val main = compile("""
            func sum(value as float, depth as int) -> float {
                if(depth <= 0){ return value; }
                return value + sum(value + 0.5, depth - 1);
            }
            func change(static value as float) -> float { value = 10.0; return 0.5; }
            func forward(value as float, ignored as float) -> any { return value; }
            func main(){
                var value = 1.25;
                var earlier = forward(value, change(value));
                var total = sum(earlier, 2) + value;
                dynamic var result = 0;
                if(total == 15.25 && value == 10.0 && earlier == 1.25){ result = 1; }
            }
        """)
        assertEquals(1, result(execute(main), main))
        for (name in listOf("sum", "change", "forward")) assertNotNull(function(name).typedIR)
    }

    @Test fun legacyAndIRCallersKeepMultipleFloatParametersAndConsecutiveReturnsIndependent() = modes {
        for (legacy in listOf(true, false)) {
            val main = compile("""
                func identity(value as float) -> float { return value; }
                func literal() -> float { return 1.25; }
                func mix(left as float, right as float = 0.75) -> float { return left * 10.0 + right; }
                func main(){
                    ${if (legacy) "var ignored = 6 / 2;" else ""}
                    var left = 1.25;
                    var right = 2.5;
                    var first = mix(literal(), identity(right));
                    var second = mix(identity(4.0));
                    dynamic var result = toInt(first + second);
                }
            """, ir = !legacy)
            assertNotNull(function("identity").typedIR)
            assertNotNull(function("literal").typedIR)
            assertNotNull(function("mix").typedIR)
            assertEquals(55, result(execute(main), main), "legacy=$legacy, fold=${CompileSettings.foldIRConstants}")
        }
    }

    @Test fun IRCallerReadsLegacyReturnsAndStaticParametersFromTheSameCompoundABI() = modes {
        val main = compile("""
            func oldMix(left as float, right as float) -> float {
                var ignored = 6 / 2;
                return left * 10.0 + right;
            }
            func oldChange(static value as float) -> float {
                var ignored = 6 / 2;
                value += 1.25;
                return value;
            }
            func main(){
                var value = 2.0;
                var total = oldMix(value, oldChange(value));
                dynamic var result = toInt(total * 10.0 + value);
            }
        """)
        assertNull(function("oldMix").typedIR)
        assertNull(function("oldChange").typedIR)
        assertEquals(235, result(execute(main), main))
    }

    @Test fun zeroDivisionAndMultiplicationRemainCanonicalWithoutHostFolding() = modes {
        val main = compile("""
            func main(){
                var quotient = 1.0 / 0.0;
                var both = 0.0 / 0.0;
                var product = 999.0 * 0.0;
                dynamic var result = 0;
                if(quotient == 0.0 && both == 0.0 && product == 0.0){ result = 1; }
                /data modify storage mcfpp:system temp.zero set from storage mcfpp:system stack_frame[0].quotient
            }
        """)
        val machine = execute(main)
        assertEquals(1, result(machine, main))
        assertEquals(listOf(0, 0, 0, 0), components(machine, "temp.zero"))
    }

    @Test fun remainderIsDiagnosedBeforeGeneratingCommands() {
        for (source in listOf("func main(){ var value = 1.0 % 0.5; }")) {
            MCFPPStringTest.readFromString(source, version = "1.20.1")
            assertTrue(Project.errorCount > 0, source)
            assertNotNull(function("main").typedIR)
            val executable = function("main").commands.map { it.toString().trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            assertEquals(listOf(
                "data modify storage mcfpp:system stack_frame prepend value {}",
                "data remove storage mcfpp:system stack_frame[0]"
            ), executable, executable.joinToString("\n"))
        }
    }

    @Test fun erasedAndUnknownFloatViewsShareThePayloadWithoutRuntimeTypeChecks() = modes {
        val main = compile("""
            func expose(value as float) -> any { return value; }
            func main(){
                var source as any = expose(1.25);
                var view = source as float;
                view += 0.5;
                /say barrier
                var unknown = source as float;
                unknown += 0.25;
                dynamic var result = toInt(unknown);
                /data modify storage mcfpp:system temp.view set from storage mcfpp:system stack_frame[0].source
            }
        """)
        val machine = execute(main)
        assertEquals(2, result(machine, main))
        assertEquals(listOf(1, 2000, 0, 1), components(machine, "temp.view"))
    }

    @Test fun scalarFloatViewsKeepTheExistingUnusedAndReadLayoutBoundary() {
        compile("func main(){ var source as any = 1; var unused = source as float; }", ir = false)
        for (source in listOf(
            "func main(){ var source as any = 1; var view = source as float; var result = toInt(view); }",
            "func main(){ var source as any = 1.25; var view = source as float; source = 2; var result = toInt(view); }")) {
            MCFPPStringTest.readFromString(source, version = "1.20.1")
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
