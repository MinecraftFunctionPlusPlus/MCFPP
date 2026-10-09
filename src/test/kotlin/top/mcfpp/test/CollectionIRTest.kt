package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class CollectionIRTest {
    private fun function(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return function("main").also { assertNotNull(it.typedIR) }
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
        try {
            for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() }
        } finally { CompileSettings.foldIRConstants = original }
    }

    @Test fun listLoopKeepsActualElementTypesAndUnchangedSiblingFacts() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func main(){
                    var values = [1,9] as list<any>;
                    var i = 0;
                    while(i < 3){ values[0] = values[0] + 1; i += 1; }
                    dynamic var result = values[0] + values[1];
                }
            """, version)
            assertEquals(13, execute(main).read(main.scope.getVar("result") as MCInt))
            val binding = main.scope.getVar("values")!!.storageBinding!!
            assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), binding.data.facts.read(binding.place.index(0))!!.type)
            assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.index(1))!!.value)
        }
    }

    @Test fun dictionaryBranchesJoinElementTypesWithoutLosingUnchangedFields() = modes {
        val main = compile("""
            func main(){
                var values = {first:2, second:9} as dict<any>;
                dynamic var condition = true;
                if(condition){ values["first"] = 3; } else { values["first"] = 5; }
                dynamic var result = values["first"] + values["second"];
            }
        """)
        assertEquals(12, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(ValueKnowledge.Unknown, binding.data.facts.read(binding.place.field("first"))!!.value)
        assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.field("second"))!!.value)
    }

    @Test fun loopBackedgesPreventOperationsFromUsingTheInitialElementType() = modes {
        MCFPPStringTest.readFromString("""
            func main(){
                var values = [2] as list<any>;
                var i = 0;
                while(i < 2){ var result = values[0] + 1; values[0] = false; i += 1; }
            }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun nestedViewsSharePlacesWhileCopiesKeepTheirOwnElementFacts() = modes {
        val main = compile("""
            func main(){
                var values = {field:[2,9]};
                var view = values as dict<list<int>>;
                var copied = view;
                var i = 0;
                while(i < 2){ view["field"][0] += 1; i += 1; }
                copied["field"][0] = 7;
                dynamic var result = values["field"][0] + copied["field"][0] + copied["field"][1];
            }
        """)
        assertEquals(20, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(main.scope.getVar("values")!!.storageBinding!!.place, main.scope.getVar("view")!!.storageBinding!!.place)
        assertNotEquals(main.scope.getVar("values")!!.storageBinding!!.place, main.scope.getVar("copied")!!.storageBinding!!.place)
    }

    @Test fun staticFieldEffectsPreserveOtherFieldsAndOrdinaryParameterCopies() {
        val main = compile("""
            func put(static value as dict<int>) { value["first"] = 7; }
            func privateChange(value as dict<int>) -> int { value["first"] = 4; return value["first"]; }
            func main(){
                var values = {first:2, second:9};
                var changed = privateChange(values);
                put(values);
                dynamic var result = values["first"] + values["second"] + changed;
            }
        """)
        assertEquals(20, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        val call = main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>().last()
        assertEquals(setOf(binding.place.field("first")), assertIs<Effect.Writes>(call.effect).places)
        assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.field("second"))!!.value)
        assertEquals(Effect.Pure, function("privateChange").runtimeEffect)
    }

    @Test fun recursiveCollectionParametersAndReturnsPreserveIndependentPayloads() = modes {
        val main = compile("""
            func change(value as list<int>, count as int) -> list<int> {
                if(count <= 0){ return value; }
                value[0] += 1;
                return change(value, count - 1);
            }
            func main(){
                var original = [2,9];
                var copied = change(original, 3);
                original[0] = 7;
                dynamic var result = copied[0] + original[0] + copied[1];
            }
        """)
        assertEquals(21, execute(main).read(main.scope.getVar("result") as MCInt))
        assertNotNull(function("change").typedIR)
        assertEquals(Effect.Pure, function("change").runtimeEffect)
    }

    @Test fun collectionLiteralOperandsAreCapturedBeforeLaterCalls() {
        val main = compile("""
            func change(static value as int) -> int { value = 7; return 5; }
            func main(){
                var value = 2;
                var values = [value,change(value)];
                var fields = {first:value, second:change(value)};
                dynamic var result = values[0]*100 + values[1]*10 + fields["first"] + fields["second"];
            }
        """)
        assertEquals(262, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun negativeAndConstantVariableIndicesUseJoinedLengthsAndPreserveCopyShapes() = modes {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var copied = values;
                var index = 0;
                values[index] = 7;
                var sum = values[-1] + copied[0];
                dynamic var result = sum;
            }
        """)
        assertEquals(11, execute(main).read(main.scope.getVar("result") as MCInt))
        for (name in listOf("values", "copied")) {
            val binding = main.scope.getVar(name)!!.storageBinding!!
            assertEquals(2, binding.data.facts.length(binding.place))
        }
        MCFPPStringTest.readFromString("""
            func main(){ var values = [2,9]; values[0] = 7; var result = values[2]; }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun erasedSourcesAndCollectionViewsExportOneSharedStorageRecord() {
        val main = compile("""
            func main(){
                var values as any = [2,9];
                var view = values as list<any>;
                dynamic var result = view[0] + view[1];
            }
        """)
        val source = main.scope.getVar("values")!!.storageBinding!!
        val view = main.scope.getVar("view") as NBTList
        assertSame(source.data, view.storageBinding!!.data)
        assertEquals(2, source.data.facts.length(source.place))
        assertEquals(11, execute(main).read(main.scope.getVar("result") as MCInt))
        Function.currFunction = main
        view.getByIndex(MCInt(0)).assignedBy(MCInt(6))
        val changed = source.data.facts.read(source.place.index(0))!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), changed.type)
        assertEquals(6, (StorageAccess.snapshotTag(assertIs<ValueKnowledge.Constant>(changed.value).value) as top.mcfpp.nbt.tags.primitive.IntTag).value)
    }

    @Test fun staticElementWritesReplaceOldActualTypesAndKeepOtherFieldsKnown() = modes {
        val main = compile("""
            func change(static value as dict<any>) { value["first"] = false; }
            func main(){
                var values = {first:2, second:9} as dict<any>;
                change(values);
                dynamic var result = values["first"] == false;
            }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as top.mcfpp.core.lang.bool.ScoreBool))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Bool.typeId), binding.data.facts.read(binding.place.field("first"))!!.type)
        assertEquals(TypeKnowledge.Exact(top.mcfpp.type.MCFPPDictType(MCFPPBaseType.Int).typeId), binding.data.facts.read(binding.place)!!.type)
        assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.field("second"))!!.value)
        MCFPPStringTest.readFromString("""
            func change(static value as list<any>) { value[0] = false; }
            func main(){ var values = [2] as list<any>; change(values); var result = values[0] + 1; }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun conditionalStaticElementWritesCannotBorrowTheUntouchedInputType() = modes {
        MCFPPStringTest.readFromString("""
            func change(static value as dict<any>, condition as bool) {
                if(condition){ value["first"] = false; }
            }
            func main(){
                var values = {first:2, second:9} as dict<any>;
                change(values, true);
                var result = values["first"] + 1;
            }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
        val binding = function("main").scope.getVar("values")!!.storageBinding!!
        assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.field("second"))!!.value)
    }

    @Test fun nestedLiteralShapesBelongToCapturedOperandsBeforeLaterStaticCalls() = modes {
        val main = compile("""
            func replace(static value as list<any>) -> list<any> {
                value = [false,false] as list<any>;
                return [7] as list<any>;
            }
            func main(){
                var values = [2] as list<any>;
                var nested = [values,replace(values)];
                dynamic var result = nested[0][-1] + (nested[1][0] as int);
            }
        """)
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("nested")!!.storageBinding!!
        assertEquals(1, binding.data.facts.length(binding.place.index(0)))
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), binding.data.facts.read(binding.place.index(0).index(0))!!.type)
    }

    @Test fun emptyDictionaryLiteralsKeepRuntimeIdentityAndAcceptNewFields() {
        val main = compile("""
            func main(){ var values = {}; values["first"] = 2; dynamic var result = values["first"] + 1; }
        """)
        val values = main.scope.getVar("values")!!
        assertEquals(top.mcfpp.type.MCFPPDictType(MCFPPBaseType.Any).typeId, values.type.typeId)
        assertTrue(values.type.hasRuntimeRepresentation)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun readOnlyCollectionViewsAllowNestedMutableValuesButRejectSlotReplacement() = modes {
        val main = compile("""
            func main(){
                var values = [[2]];
                var view = values as ImmutableList<list<int>>;
                var copied = view;
                view[0][0] = 7;
                dynamic var result = values[0][0] + copied[0][0];
            }
        """)
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        MCFPPStringTest.readFromString("""
            func main(){ var values = [[2]]; var view = values as ImmutableList<list<int>>; view[0] = [7]; }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun wholeCollectionReplacementInLoopsWithdrawsOldChildrenAndUnequalLengths() = modes {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var i = 0;
                while(i < 2){ values = [4] as list<any>; i += 1; }
                dynamic var result = values[0] + 1;
            }
        """)
        assertEquals(5, execute(main).read(main.scope.getVar("result") as MCInt))
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertNull(binding.data.facts.length(binding.place))
        assertNotEquals(ValueState.INITIALIZED, binding.data.facts.read(binding.place.index(1))?.state)
    }

    @Test fun unknownCallsWithdrawCollectionElementTypesAndLengths() = modes {
        MCFPPStringTest.readFromString("""
            func unknown(){
                /data remove storage fixture:external value
            }
            func main(){ var values = [2] as list<any>; unknown(); var result = values[0] + 1; }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
        val binding = function("main").scope.getVar("values")!!.storageBinding!!
        assertEquals(TypeKnowledge.Unknown, binding.data.facts.read(binding.place.index(0))!!.type)
        assertNull(binding.data.facts.length(binding.place))
    }

    @Test fun contextualLiteralsDoNotMakeOrdinaryMutableCollectionAssignmentsCovariant() {
        val main = compile("""
            func main(){ var values as list<any> = [2]; values[0] = false; dynamic var result = values[0] == false; }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as top.mcfpp.core.lang.bool.ScoreBool))
        MCFPPStringTest.readFromString("""
            func main(){ var values = [2]; var copied as list<any> = values; }
        """.trimIndent(), version = "26.3")
        assertNotNull(function("main").typedIR)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun voidCallsInCollectionLiteralsProduceDiagnosticsWithoutPublishingCommands() {
        for (literal in listOf("[noop()]", "{value:noop()}")) {
            MCFPPStringTest.readFromString("""
                func noop(){}
                func main(){ var values = $literal; }
            """.trimIndent(), version = "26.3")
            val main = function("main")
            assertNotNull(main.typedIR)
            assertTrue(Project.errorCount > 0)
            assertFalse(main.commands.analyzeAll().any { it.startsWith("function default.test:noop") || it.contains("append from") })
        }
    }
}
