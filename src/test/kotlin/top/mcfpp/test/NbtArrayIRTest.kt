package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class NbtArrayIRTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        for ((name, overloads) in functions) if (!name.startsWith("$"))
            overloads.filter { it.ast != null }.forEach { assertNotNull(it.typedIR, "IR missing for $name") }
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

    @Test fun copiesAndViewsPreserveArrayIdentityAndAliasing() = modes {
        val main = compile("""
            func copy(value as any) -> any { return value; }
            func main(){
                var source = {values:[I;2,3]};
                var view = source["values"] as IntArray;
                var copied = copy(view) as IntArray;
                view[0] = 7;
                copied[1] = 9;
                dynamic var result = source["values"][0]*100 + source["values"][1]*10 + copied[0];
            }
        """)
        assertEquals(732, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun byteAndLongCopiesKeepExactPayloadsAcrossFrames() = modes {
        val main = compile("""
            func putByte(values as ByteArray, index as int, value as byte) -> ByteArray { values[index] = value; return values; }
            func putLong(values as LongArray, index as int, value as long) -> LongArray { values[index] = value; return values; }
            func last(values as LongArray) -> long { return values[-1]; }
            func main(){
                var bytes = [B;-128b,127b];
                var longs = [L;9007199254740993l,9223372036854775807l];
                var changedBytes = putByte(bytes,-1,-7b);
                var changedLongs = putLong(longs,0,-9223372036854775808l);
                var scalar = last(longs);
                /data modify storage mcfpp:system temp.bytes set from storage mcfpp:system stack_frame[0].changedBytes
                /data modify storage mcfpp:system temp.longs set from storage mcfpp:system stack_frame[0].changedLongs
                /data modify storage mcfpp:system temp.original set from storage mcfpp:system stack_frame[0].longs
                /data modify storage mcfpp:system temp.scalar set from storage mcfpp:system stack_frame[0].scalar
            }
        """)
        val machine = execute(main)
        assertContentEquals(byteArrayOf(-128, -7), assertIs<ByteArrayTag>(machine.readNbt("mcfpp:system", "temp.bytes")).value)
        assertContentEquals(longArrayOf(Long.MIN_VALUE, Long.MAX_VALUE), assertIs<LongArrayTag>(machine.readNbt("mcfpp:system", "temp.longs")).value)
        assertContentEquals(longArrayOf(9007199254740993, Long.MAX_VALUE), assertIs<LongArrayTag>(machine.readNbt("mcfpp:system", "temp.original")).value)
        assertEquals(Long.MAX_VALUE, assertIs<LongTag>(machine.readNbt("mcfpp:system", "temp.scalar")).value)
    }

    @Test fun arrayIndexIsCapturedBeforeStaticRightHandSideEffects() = modes {
        val main = compile("""
            func change(static index as int) -> int { index = 1; return 7; }
            func put(static values as IntArray, index as int) { values[index] = change(index); }
            func main(){
                var values = [I;2,3];
                put(values,0);
                dynamic var result = values[0]*10 + values[1];
            }
        """)
        assertEquals(73, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun recursiveFramesAndLoopsKeepTheirOwnArrays() = modes {
        val main = compile("""
            func sum(values as IntArray, count as int) -> int {
                if(count == 0){ return 0; }
                return values[count-1] + sum(values,count-1);
            }
            func main(){
                var values = [I;2,3,4];
                var index = 0;
                while(index < 3){ values[index] = values[index] + 1; index += 1; }
                dynamic var result = sum(values,3);
            }
        """, "1.20.2")
        assertEquals(12, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun emptyArraysRetainDistinctFormatsThroughCalls() = modes {
        val main = compile("""
            func bytes(value as ByteArray) -> ByteArray { return value; }
            func ints(value as IntArray) -> IntArray { return value; }
            func longs(value as LongArray) -> LongArray { return value; }
            func main(){
                var emptyBytes = [B;]; var emptyInts = [I;]; var emptyLongs = [L;];
                var b = bytes(emptyBytes); var i = ints(emptyInts); var l = longs(emptyLongs);
                /data modify storage mcfpp:system temp.b set from storage mcfpp:system stack_frame[0].b
                /data modify storage mcfpp:system temp.i set from storage mcfpp:system stack_frame[0].i
                /data modify storage mcfpp:system temp.l set from storage mcfpp:system stack_frame[0].l
            }
        """, "1.20.1")
        val machine = execute(main)
        assertTrue(assertIs<ByteArrayTag>(machine.readNbt("mcfpp:system", "temp.b")).value.isEmpty())
        assertTrue(assertIs<IntArrayTag>(machine.readNbt("mcfpp:system", "temp.i")).value.isEmpty())
        assertTrue(assertIs<LongArrayTag>(machine.readNbt("mcfpp:system", "temp.l")).value.isEmpty())
        assertEquals(3, listOf("emptyBytes", "emptyInts", "emptyLongs").map { main.scope.getVar(it)!!.type.typeId }.toSet().size)
    }

    @Test fun knownNegativeIndicesStillWorkOnTargetsWithoutMacros() {
        MCFPPStringTest.readFromString("""
            func last(values as IntArray) -> int { return values[-1]; }
            func main(){ dynamic var result = last([I;2,9]); }
        """.trimIndent(), version = "1.20.1")
        assertEquals(0, Project.errorCount)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        assertTrue(Project.macroFunction.isEmpty())
    }

    @Test fun nbtMappedNumbersDoNotGainIntOperatorsAndArraysKeepDistinctListCodecs() {
        for (source in listOf(
            "func main(){ var values = [B;2b]; var result = values[0] + 1; }",
            "func main(){ var values = [L;2l]; var result = values[0] + 1; }",
            "func main(){ var values = [B;2b]; values[0] = true; }",
            "func main(){ var values = [I;2,3]; var result = values[-3]; }",
            "func main(){ var values = [[B;], [I;]]; }")) {
            MCFPPStringTest.readFromString(source, version = "1.20.2")
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
