package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class NbtArrayTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
    }

    @Test fun literalsKeepArrayIdentityAndReadTheirActualElements() {
        val main = compile("""
            func main(){
                var bytes = [B;1b,2b];
                var ints = [I;3,4];
                var longs = [L;5l,6l];
                dynamic var result = toInt(bytes[-1])*100 + ints[0]*10 + toInt(longs[-1]);
            }
        """)
        assertEquals(MCFPPNBTType.ByteArray.typeId, main.scope.getVar("bytes")!!.type.typeId)
        assertEquals(MCFPPNBTType.IntArray.typeId, main.scope.getVar("ints")!!.type.typeId)
        assertEquals(MCFPPNBTType.LongArray.typeId, main.scope.getVar("longs")!!.type.typeId)
        assertEquals(236, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun ordinaryArrayCopiesAreIndependentAndViewsShareWrites() {
        val main = compile("""
            func main(){
                var values = [I;2,3];
                var view = values as IntArray;
                var copied = values;
                view[0] = 7;
                copied[-1] = 9;
                dynamic var result = values[0]*100 + values[1]*10 + copied[0];
            }
        """)
        assertEquals(732, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimeArrayParametersIndicesAndReturnsKeepTheirEncoding() {
        val main = compile("""
            func put(values as IntArray, index as int, value as int) -> IntArray { values[index] = value; return values; }
            func main(){
                var values = [I;2,3];
                var result = put(values,1,7);
                dynamic var number = values[1]*100 + result[0]*10 + result[1];
            }
        """)
        assertEquals(MCFPPNBTType.IntArray.typeId, main.scope.getVar("result")!!.type.typeId)
        assertEquals(327, execute(main).read(main.scope.getVar("number") as MCInt))
    }

    @Test fun byteAndLongArrayElementsKeepTheirLanguageTypesAcrossRuntimeCalls() {
        val main = compile("""
            func putByte(values as ByteArray, index as int, value as byte) -> ByteArray { values[index] = value; return values; }
            func putLong(values as LongArray, index as int, value as long) -> LongArray { values[index] = value; return values; }
            func main(){
                var bytes = [B;2b,3b];
                var longs = [L;4l,5l];
                var changedBytes = putByte(bytes,0,7b);
                var changedLongs = putLong(longs,1,8l);
                dynamic var number = toInt(changedBytes[0])*10 + toInt(changedLongs[-1]);
            }
        """)
        assertEquals(78, execute(main).read(main.scope.getVar("number") as MCInt))
    }

    @Test fun arrayWriteCapturesTheIndexBeforeRightHandCalls() {
        val main = compile("""
            func change(static index as int) -> int { index = 1; return 7; }
            func put(values as IntArray, index as int) -> IntArray { values[index] = change(index); return values; }
            func main(){
                var result = put([I;2,3],0);
                dynamic var number = result[0]*10 + result[1];
            }
        """)
        assertEquals(73, execute(main).read(main.scope.getVar("number") as MCInt))
    }

    @Test fun multipleUnknownIndicesUseIndependentReadsAndAliasCachesReload() {
        val main = compile("""
            func read(values as IntArray, left as int, right as int) -> int { return values[left] + values[right]; }
            func main(){
                var values = [I;2,3];
                var view = values as IntArray;
                dynamic var before = view[0] + 0;
                values[-2] = 7;
                dynamic var after = view[0] + 0;
                dynamic var result = read(values,0,1);
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(7, machine.read(main.scope.getVar("after") as MCInt))
        assertEquals(10, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun emptyArraysHaveDistinctSnapshotsAndSharedMemberTables() {
        val main = compile("func main(){ var bytes = [B;]; var ints = [I;]; var longs = [L;]; }")
        val values = listOf("bytes", "ints", "longs").map { main.scope.getVar(it)!! }
        val snapshots = values.map { assertIs<CompilerValue.Typed>(StorageAccess.snapshot(it)) }
        assertEquals(3, snapshots.toSet().size)
        assertEquals(values.map { it.type.typeId }, snapshots.map { it.type })
        for ((value, snapshot) in values.zip(snapshots)) {
            assertEquals(CompilerValue.Sequence(emptyList()), snapshot.payload)
            val tag = assertNotNull(StorageAccess.constantEncoding(value))
            when (snapshot.type) {
                top.mcfpp.type.MCFPPNBTType.ByteArray.typeId -> assertTrue(assertIs<ByteArrayTag>(tag).value.isEmpty())
                top.mcfpp.type.MCFPPNBTType.IntArray.typeId -> assertTrue(assertIs<IntArrayTag>(tag).value.isEmpty())
                top.mcfpp.type.MCFPPNBTType.LongArray.typeId -> assertTrue(assertIs<LongArrayTag>(tag).value.isEmpty())
                else -> fail("Unexpected array identity ${snapshot.type}")
            }
        }
        for (value in values) {
            val member = value.getMemberFunction("toText", emptyList(), emptyList(), top.mcfpp.model.Member.AccessModifier.PUBLIC).first
            assertIs<top.mcfpp.model.function.NativeFunction>(member)
            val runtime = value.type.buildUnConcrete("runtime")
            assertSame(value.type.instanceData, runtime.type.instanceData)
            val resolved = assertIs<top.mcfpp.model.function.NativeFunction>(runtime.getMemberFunction("toText", emptyList(), emptyList(), top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
            assertTrue(top.mcfpp.model.function.ParameterMatcher.sameSignature(member, resolved))
            assertEquals(member.returnType.typeId, resolved.returnType.typeId)
            assertEquals(member.javaMethod, resolved.javaMethod)
        }
    }

    @Test fun emptyArraysKeepTheirFormatAcrossRuntimeParametersAndReturns() {
        val main = compile("""
            func bytes(value as ByteArray) -> ByteArray { return value; }
            func ints(value as IntArray) -> IntArray { return value; }
            func longs(value as LongArray) -> LongArray { return value; }
            func main(){
                var bytesResult = bytes([B;]);
                var intsResult = ints([I;]);
                var longsResult = longs([L;]);
                /data modify storage mcfpp:system temp.bytesResult set from storage mcfpp:system stack_frame[0].bytesResult
                /data modify storage mcfpp:system temp.intsResult set from storage mcfpp:system stack_frame[0].intsResult
                /data modify storage mcfpp:system temp.longsResult set from storage mcfpp:system stack_frame[0].longsResult
            }
        """)
        val machine = execute(main)
        assertTrue(assertIs<ByteArrayTag>(machine.readNbt("mcfpp:system", "temp.bytesResult")).value.isEmpty())
        assertTrue(assertIs<IntArrayTag>(machine.readNbt("mcfpp:system", "temp.intsResult")).value.isEmpty())
        assertTrue(assertIs<LongArrayTag>(machine.readNbt("mcfpp:system", "temp.longsResult")).value.isEmpty())
    }

    @Test fun nestedAndErasedCopiesKeepArrayElementsAndIndependentPayloads() {
        val main = compile("""
            func copy(value as any) -> any { return value; }
            func main(){
                var source = {values:[I;2,3]};
                var copied = source;
                copied["values"][0] = 9;
                var erased = copy(source["values"]) as IntArray;
                erased[1] = 7;
                dynamic var result = source["values"][0]*100 + source["values"][1]*10 + copied["values"][0];
                dynamic var returned = erased[0]*10 + erased[1];
            }
        """)
        val machine = execute(main)
        assertEquals(239, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(27, machine.read(main.scope.getVar("returned") as MCInt))
    }

    @Test fun knownOutOfBoundsAndNbtMappedArithmeticAreDiagnosed() {
        for (source in listOf(
            "func main(){ var values = [I;2,3]; var value = values[-3]; }",
            "func main(){ var values = [B;2b]; var value = values[0] + 1; }",
            "func main(){ var values = [L;2l]; var value = values[0] + 1; }",
            "func main(){ var values = [B;2b]; values[0] = true; }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0)
        }
    }

    @Test fun targetsWithoutMacrosRejectUnknownArrayIndices() {
        MCFPPStringTest.readFromString("""
            func put(values as IntArray, index as int) { values[index] = 7; }
            func main(){ put([I;2,3],0); }
        """.trimIndent(), version = "1.20.1")
        assertTrue(Project.errorCount > 0)
        assertTrue(Project.macroFunction.isEmpty())
    }
}
