package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.Instruction
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.*
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class ConversionIRTest {
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

    @Test fun narrowAndWidenConversionsPreserveSourceAndPhysicalWidths() = modes {
        for (version in listOf("26.3", "1.20.1")) {
            val main = compile("""
                func main(){
                    dynamic var input = 65535;
                    var small = toByte(input);
                    var medium = toShort(input);
                    var wide = toLong(input);
                    var precise = toDouble(input);
                    dynamic var result = toInt(small) + toInt(medium) + input;
                    /data modify storage mcfpp:system temp.small set from storage mcfpp:system stack_frame[0].small
                    /data modify storage mcfpp:system temp.medium set from storage mcfpp:system stack_frame[0].medium
                    /data modify storage mcfpp:system temp.wide set from storage mcfpp:system stack_frame[0].wide
                    /data modify storage mcfpp:system temp.precise set from storage mcfpp:system stack_frame[0].precise
                }
            """, version)
            val machine = execute(main)
            assertEquals(65533, machine.read(main.scope.getVar("result") as MCInt))
            assertEquals(ByteTag(-1), machine.readNbt("mcfpp:system", "temp.small"))
            assertEquals(ShortTag(-1), machine.readNbt("mcfpp:system", "temp.medium"))
            assertEquals(LongTag(65535), machine.readNbt("mcfpp:system", "temp.wide"))
            assertEquals(DoubleTag(65535.0), machine.readNbt("mcfpp:system", "temp.precise"))
        }
    }

    @Test fun nativeFloatConversionParticipatesInLoopsCallsAndRecursion() = modes {
        val main = compile("""
            func sum(depth as int) -> float {
                if(depth <= 0){ return toFloat(-7s); }
                return toFloat(depth) + sum(depth - 1);
            }
            func expose() -> any { return -1.8; }
            func main(){
                var value = sum(3);
                while(value < 2.5){ value += 0.5; }
                dynamic var result = toInt(value) + toInt(expose());
            }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun nbtEncodingCopiesPayloadAndDirectViewsKeepTheEncodedValue() = modes {
        val main = compile("""
            func forward(value as nbt) -> nbt { return value; }
            func main(){
                dynamic var source = 12s;
                var encoded = toNBT(source);
                source = 3s;
                var returned = forward(toNBT(toLong(9007199254740993L)));
                dynamic var result = (toNBT(7) as int) + toInt(source);
                /data modify storage mcfpp:system temp.encoded set from storage mcfpp:system stack_frame[0].encoded
                /data modify storage mcfpp:system temp.returned set from storage mcfpp:system stack_frame[0].returned
            }
        """)
        val machine = execute(main)
        assertEquals(10, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(ShortTag(12), machine.readNbt("mcfpp:system", "temp.encoded"))
        assertEquals(LongTag(9007199254740993L), machine.readNbt("mcfpp:system", "temp.returned"))
    }

    @Test fun longAndDoubleReadsRetainBackendRoundingEvenForLiterals() = modes {
        val main = compile("""
            func main(){
                var wide = toInt(17L);
                var precise = toInt(-1.8d);
                dynamic var result = wide + precise;
            }
        """, "1.20.1")
        assertEquals(15, execute(main).read(main.scope.getVar("result") as MCInt))
        assertTrue(main.commands.analyzeAll().any { it.contains("run data get") })
        assertEquals(2, main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Convert>().size)
    }

    @Test fun constantNbtSnapshotRetainsTagAndNumericIdentity() {
        val main = compile("""
            func main(){
                var payload = toNBT(toShort(65535));
                var precise = toDouble(2147483647);
            }
        """)
        assertEquals(ShortTag(-1), (StorageAccess.read(main.scope.getVar("payload")!!) as NBTBasedDataConcrete).value)
        val restored = StorageAccess.read(main.scope.getVar("precise")!!) as top.mcfpp.core.lang.nbt.MCDoubleConcrete
        assertEquals(DoubleTag(2147483647.0), restored.value)
    }

    @Test fun userFunctionsNamedLikeConversionsKeepTheirOwnBehavior() = modes {
        val main = compile("""
            func toInt(value as int) -> int { return value + 10; }
            func main(){ dynamic var result = toInt(2); }
        """)
        assertEquals(12, execute(main).read(main.scope.getVar("result") as MCInt))
        assertTrue(main.typedIR!!.blocks.flatMap { it.instructions }.any { it is Instruction.Call })
    }

    @Test fun legacyAndIRCallersCanReadNarrowIntegerReturns() {
        for (legacy in listOf(true, false)) {
            MCFPPStringTest.readFromString("""
                func narrow(value as int) -> short { return toShort(value); }
                func narrowByte(value as int) -> byte { return toByte(value); }
                func main(){
                    ${if (legacy) "var ignored = 6 / 2;" else ""}
                    var small = narrow(65535);
                    var tiny = narrowByte(128);
                    dynamic var result = toInt(small) + toInt(tiny);
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
            assertNotNull(functions.getValue("narrow").single().typedIR)
            assertNotNull(functions.getValue("narrowByte").single().typedIR)
            val main = functions.getValue("main").single()
            if (legacy) assertNull(main.typedIR) else assertNotNull(main.typedIR)
            assertEquals(-129, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun unsupportedConversionsAndFloatOverflowAreDiagnosedBeforeEmission() {
        for (source in listOf(
            "func main(){ var result = toFloat(7L); }",
            "func main(){ var result = toByte(1.5); }",
            "func main(){ var result = toInt(2147483648.0); }",
            "func main(){ var value as any = 2; if(true){ value = false; }; var result = toInt(value); }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
