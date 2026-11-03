package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class FloatIRTest {
    private fun compile(source: String): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        functions.values.flatten().filter { it.ast != null }.forEach { assertNotNull(it.typedIR, it.identifier) }
        return functions.getValue("main").single()
    }
    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
        assertTrue(it.branchGuards.isEmpty())
    }
    private fun modes(action: () -> Unit) {
        val original = CompileSettings.foldIRConstants
        try { for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() } }
        finally { CompileSettings.foldIRConstants = original }
    }

    @Test fun arithmeticPromotionComparisonsAndLoopBackedgesUseFloatValues() = modes {
        val main = compile("""
            func main(){
                external start = 8
                var value: float = start
                value += 0.5
                value /= 2.0
                value %= 3.0
                while(value < 3.0){ value += 0.5; }
                var negated = -value
                external result = 0
                if(value == 3.25 && negated == -3.25){ result = 1; }
                /data modify storage mcfpp:system temp.float_result set from storage mcfpp:system stack_frame[0].value
            }
        """)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(FloatTag(3.25f), machine.readNbt("mcfpp:system", "temp.float_result"))
    }

    @Test fun recursiveCallsPreserveEarlierOperands() = modes {
        val main = compile("""
            func sum(value: float, depth: int): float {
                if(depth <= 0){ return value; }
                return value + sum(value + 0.5,depth - 1)
            }
            func forward(value: float, ignored: float): any { return value; }
            func main(){
                var value = 1.25
                var captured = forward(value,0.5)
                var total = sum(captured,2) + value
                external result = 0
                if(total == 6.5){ result = 1; }
            }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun collectionsPromoteElementsAndKeepViewsAndDynamicIndices() = modes {
        val main = compile("""
            func main(){
                var values: list<float> = [1,2.0]
                external index = 0
                values[index] = 2.5
                var erased: any = values[0]
                var view = erased as float
                view += 0.5
                var valuesCopy = values
                valuesCopy[1] = 4
                valuesCopy.add(3)
                external result = 0
                if(view == 3.0 && erased == 2.5 && values[1] == 2.0 && valuesCopy[1] == 4.0 && valuesCopy[2] == 3.0){ result = 1; }
                /data modify storage mcfpp:system temp.element set from storage mcfpp:system stack_frame[0].valuesCopy[2]
            }
        """)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(FloatTag(3f), machine.readNbt("mcfpp:system", "temp.element"))
    }

    @Test fun implicitReturnAndDefaultArgumentsRoundIntOnlyAtPromotion() = modes {
        val main = compile("""
            func promoted(value: int): float { return value; }
            func add(value: float, extra: float = 0.5): float { return value + extra; }
            func main(){
                external integer = 16777217
                var rounded = promoted(integer)
                var total = add(2)
                external result = 0
                if(rounded == 16777216.0 && total == 2.5 && integer == 16777217){ result = 1; }
            }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun signedZeroSurvivesNegationAndFloatRangesKeepEncodedEndpoints() = modes {
        val main = compile("""
            func main(){
                external zero = 0.0
                var negative = -zero
                var bounds = negative .. 2.5
                /data modify storage mcfpp:system temp.bounds set from storage mcfpp:system stack_frame[0].bounds
            }
        """)
        val machine = execute(main)
        assertEquals((-0.0f).toRawBits(), (machine.readNbt("mcfpp:system", "temp.bounds.left") as FloatTag).value.toRawBits())
        assertEquals(FloatTag(2.5f), machine.readNbt("mcfpp:system", "temp.bounds.right"))
    }

    @Test fun arithmeticFailuresBecomePositiveZeroAndInvalidRangesAreRejected() {
        modes {
            val main = compile("""
                func main(){
                    var value = 1.0 / 0.0
                    /data modify storage fixture:float_contract zero set from storage mcfpp:system stack_frame[0].value
                }
            """)
            assertNotNull(main.typedIR)
            val value = execute(main).readNbt("fixture:float_contract", "zero") as FloatTag
            assertEquals(0f.toRawBits(), value.value.toRawBits())
        }
        for (source in listOf(
            "func main(){ var bounds = 2.5 .. 1.5; }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0)
            assertNotNull(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().typedIR)
        }
    }
}
