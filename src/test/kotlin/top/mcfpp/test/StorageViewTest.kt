package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class StorageViewTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(function: Function, prefix: List<String> = emptyList()): ScoreCommandExecutor =
        ScoreCommandExecutor(prefix + function.commands.analyzeAll(), GlobalScope.localNamespaces.values
            .flatMap { it.scope.functions.values.flatten() }.associate { it.namespaceID.toString() to it.commands.analyzeAll() })
            .also { assertEquals(0, it.stackDepth) }

    private fun reset(version: String = "26.3") {
        val function = compile("func main() {}", version)
        Function.currFunction = function
        function.commands.clear()
    }

    @Test fun anUnusedNumericViewDoesNotConvertOrMaterializeItsSource() {
        val function = compile("func main(){ var value = 7; var view = value as float; }")
        val view = function.scope.getVar("view")!!
        assertIs<ValueRef.TypedView>(view.valueRef())
        assertEquals(function.scope.getVar("value")!!.storageBinding!!.place, view.storageBinding!!.place)
        val commands = function.commands.analyzeAll().filterNot { it.startsWith("#") }
        assertFalse(commands.any { it.contains("set value") || it.contains("from_int") || it.contains("_scoreto") }, commands.toString())
        assertEquals(1, Project.warningCount)
    }

    @Test fun templateViewsShareWritesAndPreserveSiblingFacts() {
        val main = compile("""
            data Source { value as int; sibling as int; }
            data Target { value as int; sibling as int; }
            func main(){
                var source = {value:2, sibling:9} as Source;
                source.sibling = 9;
                var first = source as Target;
                var second = source as Target;
                dynamic var before = second.value;
                first.value = 7;
                dynamic var result = source.value + second.value;
                dynamic var preserved = second.sibling;
            }
        """)
        val source = main.scope.getVar("source")!!
        assertEquals(source.storageBinding!!.place, main.scope.getVar("first")!!.storageBinding!!.place)
        assertEquals(source.storageBinding!!.place, main.scope.getVar("second")!!.storageBinding!!.place)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(14, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("preserved") as MCInt))
        assertIs<ValueKnowledge.Constant>(source.storageBinding!!.data.facts.read(source.storageBinding!!.place.field("sibling"))!!.value)
    }

    @Test fun aViewNeverAddsMissingFieldsOrCallsATemplateConstructor() {
        val main = compile("""
            data Target {
                present as int;
                missing as int;
                constructor(){
                    /say constructor
                }
            }
            func main(){ var source = {present:4}; var view = source as Target; }
        """)
        val commands = main.commands.analyzeAll().joinToString("\n")
        assertFalse(commands.contains("constructor"), commands)
        assertFalse(commands.contains("missing set"), commands)
        assertFalse(commands.contains("set from"), commands)
        assertEquals(1, Project.warningCount)
    }

    @Test fun ordinaryTemplateAssignmentCopiesThePayloadIntoAnIndependentPlace() {
        val main = compile("""
            data Source { value as int; sibling as int; }
            func main(){
                var source = {value:2, sibling:9} as Source;
                var copied = source;
                copied.value = 10;
                dynamic var result = source.value + copied.value;
                source.sibling = 4;
                dynamic var preserved = copied.sibling;
            }
        """)
        assertNotEquals(main.scope.getVar("source")!!.storageBinding!!.place,
            main.scope.getVar("copied")!!.storageBinding!!.place)
        val machine = execute(main)
        assertEquals(12, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("preserved") as MCInt))
    }

    @Test fun unprovenButAccessibleReinterpretationWarnsAndUsesSourceEncoding() {
        val main = compile("func main(){ var value = 7; dynamic var view = value as bool; dynamic var result = view == true; }")
        assertEquals(1, Project.warningCount)
        val commands = main.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("set value 7"), commands)
        assertFalse(commands.contains("from_float") || commands.contains("from_int"), commands)
        val value = main.scope.getVar("value")!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), value.storageBinding!!.data.facts.read(value.storageBinding!!.place)!!.type)
    }

    @Test fun legacyFloatRejectsScalarLayoutAccessWithoutUsingNumericConversion() {
        reset("1.20.2")
        val view = StorageAccess.view(MCIntConcrete(7), MCFPPBaseType.Float)
        assertTrue(Function.currFunction.commands.isEmpty())
        assertTrue(StorageAccess.read(view).isError)
        assertEquals(1, Project.errorCount)
        assertFalse(Function.currFunction.commands.analyzeAll().any { it.contains("_scoreto") })
    }

    @Test fun unknownAnyCanBeCopiedAndReinterpretedWithoutATagOrCheck() {
        reset()
        val source = MCAny("source").apply { hasAssigned = true; nbtPath = top.mcfpp.lib.NBTPath.temp.memberIndex(identifier) }
        val copied = MCAny("copied").apply { hasAssigned = true; nbtPath = top.mcfpp.lib.NBTPath.temp.memberIndex(identifier) }.assignedBy(source)
        assertNull(copied.inferredType)
        val view = StorageAccess.view(copied, MCFPPBaseType.Int)
        assertEquals(copied.storageBinding!!.place, view.storageBinding!!.place)
        StorageAccess.read(view)
        assertEquals(0, Project.warningCount)
        val commands = Function.currFunction.commands.analyzeAll().joinToString("\n")
        assertTrue(commands.contains("set from"), commands)
        assertFalse(commands.contains("type:") || commands.contains("if data"), commands)
    }

    @Test fun materializationKeepsConstantsAndRepeatedReadsReuseTheirVersion() {
        reset()
        val source = MCIntConcrete(4)
        val view = StorageAccess.view(source, MCFPPBaseType.Int).apply { isDynamic = true }
        val before = ValueSnapshot.of(source)
        StorageAccess.read(view)
        val size = Function.currFunction.commands.size
        StorageAccess.read(view)
        assertEquals(size, Function.currFunction.commands.size)
        assertEquals(before, ValueSnapshot.of(source))
        view.assignedBy(MCIntConcrete(8))
        val read = StorageAccess.read(source).apply { isDynamic = true }
        assertEquals(8, (ValueSnapshot.of(source) as CompilerValue.Typed).payload.let { (it as CompilerValue.Integral).value.toInt() })
        assertEquals(source.storageBinding!!.place, read.storageBinding!!.place)
    }

    @Test fun erasedBranchKnowledgeIsIndependentOfConstantFolding() {
        val old = CompileSettings.foldIRConstants
        try {
            for (enabled in listOf(true, false)) for (sameType in listOf(true, false)) {
                CompileSettings.foldIRConstants = enabled
                val main = compile("""
                    func main(){
                        dynamic var flag = true;
                        var value as any = 1;
                        if(flag){ value = 2; } else { value = ${if (sameType) "3" else "false"}; }
                        dynamic var result = (value as int) + 5;
                    }
                """)
                assertNotNull(main.typedIR)
                val value = main.scope.getVar("value") as MCAny
                if (sameType) assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), value.typeKnowledge)
                else assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId), (value.typeKnowledge as TypeKnowledge.Candidates).types)
                assertEquals(7, execute(main).read(main.scope.getVar("result") as MCInt))
            }
        } finally { CompileSettings.foldIRConstants = old }
    }

    @Test fun erasedParametersReturnsAndSequentialCallsKeepSeparatePayloads() {
        val main = compile("""
            func identity(value as any) -> any { return value; }
            func main(){
                var first = identity(4);
                var second = identity(9);
                dynamic var result = first + second;
            }
        """)
        assertEquals(13, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(MCFPPBaseType.Int, (main.scope.getVar("first") as MCAny).inferredType)
        assertNull(ValueSnapshot.of(main.scope.getVar("first")))
        assertNotEquals((main.scope.getVar("first") as MCAny).nbtPath.toCommandPart().toString(),
            (main.scope.getVar("second") as MCAny).nbtPath.toCommandPart().toString())
    }

    @Test fun compilerOnlyPayloadsUseStaticSpecializationAndNeverWriteNbt() {
        val main = compile("""
            func identity(value as object) -> type { return value as type; }
            func main(){ var returned = identity(int); }
        """)
        assertIs<MCFPPTypeVar>(main.scope.getVar("returned"))
        assertFalse(main.commands.analyzeAll().any { it.contains("set value") || it.contains("set from") })
    }

    @Test fun erasedEncodingRetainsBooleanByteAndIntegerTagIdentity() {
        reset()
        val integer = MCAny("integer").assignedBy(MCIntConcrete(4))
        val boolean = MCAny("boolean").assignedBy(top.mcfpp.core.lang.bool.ScoreBoolConcrete(true))
        assertIs<IntTag>(StorageAccess.constantEncoding(integer))
        assertIs<ByteTag>(StorageAccess.constantEncoding(boolean))
        assertEquals(MCFPPBaseType.Int, integer.inferredType)
        assertEquals(MCFPPBaseType.Bool, boolean.inferredType)
    }

    @Test fun returnedTypeKnowledgeDoesNotEvaluateAnOrdinaryFunctionForConstantArguments() {
        val main = compile("""
            func choose(flag as bool) -> any {
                if(flag){ return 2; } else { return false; }
            }
            func main(){ var value = choose(true); dynamic var result = (value as int) + 4; }
        """)
        val value = main.scope.getVar("value") as MCAny
        assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId),
            (value.typeKnowledge as TypeKnowledge.Candidates).types)
        assertNull(ValueSnapshot.of(value))
        assertEquals(6, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun anOpaqueUserCallInvalidatesAViewAndAProvenPureCallKeepsItsFacts() {
        val main = compile("""
            func pure() {}
            func change(){
                /data modify storage mcfpp:system stack_frame[1].value set value 8
            }
            func main(){
                var value = 4;
                var view = value as int;
                pure();
                dynamic var before = view;
                change();
                dynamic var result = value + 1;
            }
        """)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        assertEquals(Effect.Pure, functions.getValue("pure").single().runtimeEffect)
        assertEquals(Effect.Unknown, functions.getValue("change").single().runtimeEffect)
        assertNull(ValueSnapshot.of(main.scope.getVar("value")))
        val machine = execute(main)
        assertEquals(4, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun anUnannotatedNativeCallFlushesAndInvalidatesObservedData() {
        reset()
        val source = MCIntConcrete(4, "value").apply { hasAssigned = true; bindDeclaration() }
        Function.currFunction.scope.putVar("value", source)
        val view = StorageAccess.view(source, MCFPPBaseType.Int)
        top.mcfpp.model.function.NativeFunction("opaque", javaMethod = StorageViewTest::class.java.getMethod("opaqueWrite"))
            .invoke(emptyList(), null)
        assertNull(ValueSnapshot.of(source))
        val read = StorageAccess.read(view) as MCInt
        val commands = listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
            Function.currFunction.commands.analyzeAll() + "data remove storage mcfpp:system stack_frame[0]"
        val machine = ScoreCommandExecutor(commands)
        assertEquals(8, machine.read(read))
        assertEquals(0, machine.stackDepth)
    }

    companion object {
        @JvmStatic fun opaqueWrite() {
            Function.addCommand("data modify storage mcfpp:system stack_frame[0].value set value 8")
        }
    }

    @Test fun erasedStaticParametersWriteBackToTheCallingFrame() {
        val main = compile("""
            func replace(static value as any){ value = 9; }
            func main(){
                var value as any = 4;
                replace(value);
                dynamic var result = (value as int) + 1;
            }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun templateParametersAndReturnsCopyThePayloadAcrossFrames() {
        val main = compile("""
            data Payload { value as int; }
            func adjust(value as Payload) -> Payload { value.value += 3; return value; }
            func main(){
                var source = {value:2} as Payload;
                var first = adjust(source);
                var second = adjust(source);
                dynamic var result = first.value + second.value;
                dynamic var preserved = source.value;
            }
        """)
        val machine = execute(main)
        assertEquals(10, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(2, machine.read(main.scope.getVar("preserved") as MCInt))
        assertNotEquals(main.scope.getVar("first")!!.storageBinding!!.place, main.scope.getVar("second")!!.storageBinding!!.place)
    }

    @Test fun recursiveCallsPreserveAnUnconsumedErasedReturn() {
        val main = compile("""
            func identity(value as int) -> any { return value; }
            func sum(value as int) -> int {
                if(value <= 0){ return 0; }
                return (identity(value) as int) + sum(value - 1);
            }
            func main(){ dynamic var result = sum(4); }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun evaluatingALaterOperandKeepsTheEarlierValueAndTheCallsWriteBack() {
        val main = compile("""
            func change(static value as int) -> int { value = 9; return 1; }
            func main(){
                var value = 4;
                var view = value as int;
                dynamic var result = value + change(value);
                dynamic var after = value;
            }
        """)
        val machine = execute(main)
        assertEquals(5, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("after") as MCInt))
    }

    @Test fun anEarlierArgumentSurvivesARecursiveCallInALaterArgument() {
        val main = compile("""
            func identity(value as int) -> any { return value; }
            func add(left as int, right as int) -> int { return left + right; }
            func sum(value as int) -> int {
                if(value <= 0){ return 0; }
                return add(identity(value) as int, sum(value - 1));
            }
            func main(){ dynamic var result = sum(4); }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun aNativeConcreteContainerMutationCommitsBeforeItsFactsAreInvalidated() {
        val main = compile("""
            func main(){
                var values as list<int> = [1,2];
                values.add(3);
                /data modify storage mcfpp:system temp.observed set from storage mcfpp:system stack_frame[0].values
            }
        """)
        assertNull(ValueSnapshot.of(main.scope.getVar("values")))
        val payload = execute(main).readNbt("mcfpp:system", "temp.observed") as top.mcfpp.nbt.tags.collection.ListTag
        assertEquals(listOf(1, 2, 3), payload.map { (it as IntTag).value })
    }
}
