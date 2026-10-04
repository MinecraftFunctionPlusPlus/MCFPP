package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class CollectionStorageTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(function: Function): ScoreCommandExecutor = ScoreCommandExecutor(function.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
    }

    @Test fun listElementsKeepActualTypesAndKnownSiblingsAfterAWritingView() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var view = values as list<any>;
                dynamic var before = view[0] + 1;
                values[0] = 7;
                dynamic var result = values[0] + view[0];
                dynamic var preserved as int = view[1];
            }
        """)
        val source = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(source.place, main.scope.getVar("view")!!.storageBinding!!.place)
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), source.data.facts.read(source.place.index(0))!!.type)
        assertIs<ValueKnowledge.Constant>(source.data.facts.read(source.place.index(1))!!.value)
        val machine = execute(main)
        assertEquals(3, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(14, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("preserved") as MCInt))
    }

    @Test fun ordinaryCollectionCopiesKeepElementFactsAndIndependentPlaces() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var view = values as list<any>;
                var copied = values;
                values[0] = 6;
                copied[0] = 10;
                dynamic var result = view[0] + copied[0] + copied[1];
            }
        """)
        assertNotEquals(main.scope.getVar("values")!!.storageBinding!!.place, main.scope.getVar("copied")!!.storageBinding!!.place)
        assertEquals(25, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun dictionaryElementsPreserveBooleanIdentityAndSiblingFacts() {
        val main = compile("""
            func main(){
                var values as dict<any> = {flag:true, first:2, second:9};
                var view = values as dict<any>;
                dynamic var flag = values["flag"] == true;
                view["first"] = 7;
                dynamic var result = values["first"] + values["second"];
            }
        """)
        val binding = main.scope.getVar("values")!!.storageBinding!!
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Bool.typeId), binding.data.facts.read(binding.place.field("flag"))!!.type)
        assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.field("second"))!!.value)
        val machine = execute(main)
        assertEquals(1, machine.read(main.scope.getVar("flag") as ScoreBool))
        assertEquals(16, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun anUnknownIndexWriteInvalidatesValuesAndWidensEachPossibleElementType() {
        val main = compile("func main() {}")
        Function.currFunction = main
        val values = NBTListConcrete(arrayListOf(MCIntConcrete(2), MCIntConcrete(9)), "values", MCFPPBaseType.Any)
            .apply { hasAssigned = true }
        val binding = StorageAccess.ensure(values)
        val index = MCInt("index").apply { hasAssigned = true; isDynamic = true }
        values.getByIndex(index).assignedBy(ScoreBoolConcrete(false))
        for (element in 0..1) {
            val fact = binding.data.facts.read(binding.place.index(element))!!
            assertEquals(ValueKnowledge.Unknown, fact.value)
            assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId), (fact.type as TypeKnowledge.Candidates).types)
        }
        assertEquals(ValueKnowledge.Unknown, binding.data.facts.read(binding.place.unknownIndex())!!.value)
        assertEquals(0, Project.errorCount)
    }

    @Test fun dynamicIndicesUseTheirCapturedRuntimeValueAndInvalidateAliases() {
        for (version in listOf("26.3", "1.20.2")) {
            val main = compile("""
                func main(){
                    var values = [2,9] as list<any>;
                    var view = values as list<any>;
                    dynamic var index = 1;
                    dynamic var before = (view[1] as int) + 0;
                    values[index] = 7;
                    dynamic var result = (view[0] as int) + (view[1] as int);
                }
            """, version)
            val machine = execute(main)
            assertEquals(9, machine.read(main.scope.getVar("before") as MCInt))
            assertEquals(9, machine.read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun partialCollectionsRetainTypesAndMaterializeEachSourceEncoding() {
        val main = compile("""
            func main(){
                dynamic var source = 5;
                var values = [2,source] as list<any>;
                dynamic var result = values[0] + values[1];
            }
        """)
        assertNull(ValueSnapshot.of(main.scope.getVar("values")))
        assertEquals(7, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun negativeAndPositiveIndicesOfTheSameElementShareWriteVersions() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var view = values as list<any>;
                dynamic var before = (values[1] as int) + 0;
                view[-1] = 11;
                dynamic var result = (values[1] as int) + (values[-1] as int);
            }
        """)
        val machine = execute(main)
        assertEquals(9, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(22, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun erasingACollectionCopiesItsElementKnowledgeWithThePayload() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var erased as any = values;
                dynamic var result = erased[0] + erased[1];
            }
        """)
        assertEquals(11, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun replacingAWholeCollectionReseedsElementKnowledgeAndInvalidatesViews() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var view = values as list<any>;
                dynamic var before = (view[0] as int) + 0;
                values = [5,6] as list<any>;
                dynamic var result = view[0] + view[-1];
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(11, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun dynamicDictionaryKeysAndQuotedConstantKeysSelectTheCorrectMember() {
        val main = compile("""
            func main(){
                var values as dict<any> = {first:2, second:9};
                dynamic var key = "second";
                dynamic var before = values[key] + 1;
                values["a.b"] = 7;
                dynamic var result = values["a.b"] + values["first"];
            }
        """)
        val machine = execute(main)
        assertEquals(10, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun aLaterRhsCallDoesNotChangeAnAlreadyEvaluatedIndex() {
        val main = compile("""
            func change(static index as int) -> int { index = 0; return 7; }
            func main(){
                var values = [2,9] as list<any>;
                dynamic var index = 1;
                values[index] = change(index);
                dynamic var result = (values[0] as int) + (values[1] as int);
                dynamic var after = index + 0;
            }
        """)
        val machine = execute(main)
        assertEquals(9, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("after") as MCInt))
    }

    @Test fun aLaterLiteralElementCallPreservesTheEarlierElementValue() {
        val main = compile("""
            func change(static value as int) -> int { value = 9; return 1; }
            func main(){
                var value = 4;
                var values = [value,change(value)] as list<any>;
                dynamic var result = values[0] + values[1];
                dynamic var after = value + 0;
            }
        """)
        val machine = execute(main)
        assertEquals(5, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(9, machine.read(main.scope.getVar("after") as MCInt))
    }

    @Test fun compilerOnlyElementsCannotBeMaterializedThroughARuntimeCollection() {
        MCFPPStringTest.readFromString("func main(){ dynamic var values = [int]; }", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun compilerOnlyCollectionCopiesAndKnownIndicesStayStatic() {
        val main = compile("""
            func main(){
                var values = [int,float];
                var copied = values;
                values[0] = float;
                var preserved = copied[0];
                var changed = values[0];
            }
        """)
        assertEquals(MCFPPBaseType.Int, (main.scope.getVar("preserved") as MCFPPTypeVar).value)
        assertEquals(MCFPPBaseType.Float, (main.scope.getVar("changed") as MCFPPTypeVar).value)
        assertFalse(main.scope.getVar("values")!!.type.hasRuntimeRepresentation)
        assertFalse(main.commands.analyzeAll().any { it.contains("set value") || it.contains("set from") })
    }

    @Test fun anEmptyLiteralStillHasARuntimeRepresentation() {
        val main = compile("func main(){ dynamic var values = []; }")
        assertTrue(main.scope.getVar("values")!!.type.hasRuntimeRepresentation)
        assertNotNull(ValueSnapshot.of(main.scope.getVar("values")))
        execute(main)
    }

    @Test fun aKnownAnyIndexBindsItsActualIntegerType() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                var index as any = 1;
                dynamic var result = values[index] + 1;
            }
        """)
        assertEquals(10, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun differentRuntimeIndicesCannotReuseTheSameUnknownElementReadCache() {
        val main = compile("""
            func main(){
                var values = [2,9] as list<any>;
                dynamic var first = 0;
                dynamic var second = 1;
                dynamic var before = (values[first] as int) + 0;
                dynamic var result = (values[first] as int) + (values[second] as int);
            }
        """)
        val machine = execute(main)
        assertEquals(2, machine.read(main.scope.getVar("before") as MCInt))
        assertEquals(11, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun nbtByteIndicesDoNotAcquireTheLanguageIntSignatureThroughInheritance() {
        MCFPPStringTest.readFromString("func main(){ var values = [2,9]; var invalid = values[0b]; }", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun targetsWithoutMacrosReportDynamicIndexAccessInsteadOfEmittingUnsupportedCommands() {
        MCFPPStringTest.readFromString("""
            func main(){
                var values = [2,9] as list<any>;
                dynamic var index = 1;
                values[index] = 7;
            }
        """.trimIndent(), version = "1.20")
        assertTrue(Project.errorCount > 0)
        assertTrue(Project.macroFunction.isEmpty())
    }
}
