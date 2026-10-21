package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.RangeVar
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class RangeIRTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
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

    @Test fun namedAndReturnedRangesKeepIntegerPrecisionAndCaptureLoopBounds() = modes {
        val main = compile("""
            func make(): range { return 16777217 .. 16777218; }
            func forward(value: any): any { return value; }
            func main(){
                var bounds = forward(make())
                var sum = 0
                for(index : bounds){ sum += index - 16777216; bounds = 1 .. 1; }
                external result = sum
            }
        """, "1.20.1")
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun integerExtremesDoNotRoundOrOverflowTheLoopCounter() = modes {
        val main = compile("""
            func main(){
                var high = 2147483646 .. 2147483647
                var low = -2147483648 .. -2147483647
                var count = 0
                for(index : high){ count += 1; }
                for(index : low){ count += 1; }
                external result = count
            }
        """)
        assertEquals(4, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun joinedAndNestedRangesKeepOnlyTheirProvenEndpointTypes() = modes {
        val main = compile("""
            func main(){
                var bounds = 1 .. 2
                external condition = true
                if(condition){ bounds = 3 .. 4; } else { bounds = 5 .. 6; }
                var values = [bounds, 1 .. 2]
                var sum = 0
                for(index : values[0]){ sum += index; }
                for(index : values[1]){ sum += index; }
                external result = sum
            }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun missingOrUnprovenEndpointsAreRejectedBeforeCommands() {
        for (source in listOf(
            "func main(){ var bounds = 1 ..; for(index : bounds){ var value = index; }; }",
            "func main(){ var bounds = {left:false,right:2} as range; for(index : bounds){ var value = index; }; }",
            "func iterate(bounds: range){ for(index : bounds){ var value = index; }; }\nfunc main(){ iterate(1 .. 2); }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            assertNotNull(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().typedIR)
        }
        for ((source, name) in listOf(
            """
                func main(){
                    var ignored=6/2; var bounds=1 ..
                    for(index:bounds){
                        /say fixture.invalid_range_iteration
                    }
                }
            """.trimIndent() to "main",
            """
                func main(){
                    var ignored=6/2; var bounds=1.0 .. 2.0
                    for(index:bounds){
                        /say fixture.invalid_range_iteration
                    }
                }
            """.trimIndent() to "main",
            """
                func iterate(bounds: range){
                    var ignored=6/2
                    for(index:bounds){
                        /say fixture.invalid_range_iteration
                    }
                }
                func main(){}
            """.trimIndent() to "iterate"
        )) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            val function = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()
            assertTrue(function.bodyCompiled)
            assertNull(function.typedIR)
            val commands = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.functions.values.flatten() }.flatMap { it.commands }
            assertFalse(commands.any { "fixture.invalid_range_iteration" in it.toString() })
            assertFalse(Project.macroFunction.values.any { "fixture.invalid_range_iteration" in it })
        }
    }

    @Test fun legacyMaterializationAndIterationUseIndependentExactBounds() {
        MCFPPStringTest.readFromString("""
            func make(): range { var ignored = 6 / 2; return 16777217 .. 16777218; }
            func main(){
                var ignored = 6 / 2
                external first = 16777217
                var bounds = first .. 16777218
                var constantBounds = 16777217 .. 16777218
                first = 0
                external sum = 0
                for(index : constantBounds){ sum += index - 16777216; }
                external result = sum
                var returned = make()
                /data modify storage mcfpp:system temp.bounds set from storage mcfpp:system stack_frame[0].bounds
                /data modify storage mcfpp:system temp.returned set from storage mcfpp:system stack_frame[0].returned
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertNull(main.typedIR)
        val machine = execute(main)
        assertEquals(3, machine.read(main.scope.getVar("result") as MCInt))
        val bounds = assertIs<CompoundTag>(machine.readNbt("mcfpp:system", "temp.bounds"))
        assertEquals(16777217, assertIs<IntTag>(bounds.get("left")).value)
        assertEquals(16777218, assertIs<IntTag>(bounds.get("right")).value)
        assertEquals(bounds, machine.readNbt("mcfpp:system", "temp.returned"))
    }

    @Test fun legacyRangeCopiesAndSnapshotsKeepIntegerAndFloatIdentity() {
        MCFPPStringTest.readFromString("func main(){ var unused = 0.5; var bounds = 16777217 .. 16777218; }", version = "26.3")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val range = assertIs<RangeVar>(main.scope.getVar("bounds"))
        val encoded = assertIs<CompoundTag>(StorageAccess.snapshotTag(StorageAccess.snapshot(range)!!))
        assertEquals(16777217, assertIs<IntTag>(encoded.get("left")).value)
        assertEquals(16777218, assertIs<IntTag>(encoded.get("right")).value)
        val copy = RangeVar("copy").assignedBy(range)
        assertEquals(StorageAccess.snapshot(range), StorageAccess.snapshot(copy))
        assertNotEquals(StorageAccess.ensure(range).place, StorageAccess.ensure(copy).place)
        assertEquals("16777217..16777218", copy.toCommandPart().toString())
        assertEquals(3.toByte(), copy.point)
        assertTrue(copy.isIntRange())
        val float = assertIs<CompoundTag>(StorageAccess.snapshotTag(StorageAccess.snapshot(RangeVar(1.5f to 2.5f))!!))
        assertEquals(1.5f, assertIs<FloatTag>(float.get("left")).value)
        val open = RangeVar(null to 3)
        assertEquals("..3", open.toCommandPart().toString())
        assertEquals(1.toByte(), open.point)
        assertEquals("distance=1.5..2.5", top.mcfpp.lib.DistancePredicate(RangeVar(1.5f to 2.5f)).toCommandPart().toString())
        assertEquals("level=16777217..16777218", top.mcfpp.lib.LevelPredicate(copy).toCommandPart().toString())
    }
}
