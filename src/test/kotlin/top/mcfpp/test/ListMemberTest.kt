package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.backend.ListOperations
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class ListMemberTest {
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

    @Test fun constantAndRuntimeListsExposeTheSameMemberSignatures() {
        compile("func main(){}")
        assertSame(NBTList.data, NBTListConcrete.data)
    }

    @Test fun prependAllKeepsSourceOrderAndInsertAndRemoveAcceptNegativeIndices() {
        val main = compile("""
            func main(){
                var values = [3,4];
                values.prependAll([1,2]);
                values.insert(-1,5);
                values.removeAt(-2);
                dynamic var result = values[0]*1000 + values[1]*100 + values[2]*10 + values[3];
            }
        """)
        assertEquals(1235, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun mutatingAStaticViewUpdatesItsRootAndCopiesIncomingElements() {
        val main = compile("""
            func main(){
                var values = [int];
                var view = values as list<any>;
                var extra = [float,bool];
                var incoming = extra as list<any>;
                view.prependAll(incoming);
                extra[0] = int;
                view.insert(-1,string);
                view.removeAt(-2);
                var first = values[0] as type;
                var second = values[1] as type;
                var last = values[-1] as type;
            }
        """)
        assertEquals(MCFPPBaseType.Float, assertIs<MCFPPTypeVar>(main.scope.getVar("first")).value)
        assertEquals(MCFPPBaseType.Bool, assertIs<MCFPPTypeVar>(main.scope.getVar("second")).value)
        assertEquals(MCFPPBaseType.String, assertIs<MCFPPTypeVar>(main.scope.getVar("last")).value)
        assertNotNull(ValueSnapshot.of(main.scope.getVar("values")))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun appendingAnUnknownPrimitiveKeepsItsEncodingAndExistingElementFacts() {
        val main = compile("""
            func append(value as int) -> int {
                var values = [2,3];
                values.add(value);
                return values[0]*100 + values[1]*10 + values[2];
            }
            func main(){ dynamic var result = append(7); }
        """)
        assertEquals(237, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun unknownInsertionAndRemovalIndicesUseCurrentValues() {
        val main = compile("""
            func edit(index as int, value as int) -> int {
                var values = [2,4];
                values.insert(index,value);
                values.removeAt(-1);
                return values[0]*10 + values[1];
            }
            func main(){ dynamic var result = edit(1,3); }
        """)
        assertEquals(23, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimePrependAndPrependAllPreserveOrder() {
        val main = compile("""
            func edit(extra as list<int>, value as int) -> int {
                var values = [4];
                values.prepend(value);
                values.prependAll(extra);
                return values[0]*1000 + values[1]*100 + values[2]*10 + values[3];
            }
            func main(){ var extra = [1,2]; dynamic var result = edit(extra,3); }
        """)
        assertEquals(1234, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun constantQueriesAndRemovalUseValuesInsteadOfAdapterIdentity() {
        val main = compile("""
            func main(){
                var values = [2,4,2];
                var first = values.indexOf(2);
                var last = values.lastIndexOf(2);
                var found = values.contains(4);
                values.remove(2);
                dynamic var result = first*100 + last*10 + values[0];
            }
        """)
        assertEquals(24, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(CompilerValue.Bool(true), assertIs<CompilerValue.Typed>(ValueSnapshot.of(main.scope.getVar("found"))).payload)
    }

    @Test fun runtimeQueriesHaveIndependentResultsAndLeaveTheSourceIntact() {
        val main = compile("""
            func find(values as list<int>, needle as int) -> int {
                var first = values.indexOf(needle);
                var last = values.lastIndexOf(needle);
                var missing = values.indexOf(9);
                return first*1000 + last*100 + missing*10 + values[0];
            }
            func main(){ var values = [2,4,2]; dynamic var result = find(values,2); }
        """)
        assertEquals(192, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimeContainsAndRemoveWorkWithoutReturnCommands() {
        val main = compile("""
            func edit(values as list<int>, needle as int) -> int {
                values.remove(needle);
                return values[0]*10 + values[1];
            }
            func has(values as list<int>, needle as int) -> bool { return values.contains(needle); }
            func main(){
                var values = [2,4,2];
                dynamic var result = edit(values,2);
                dynamic var found = has(values,4);
            }
        """, "1.20.1")
        val machine = execute(main)
        assertEquals(42, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("found") as ScoreBool))
    }

    @Test fun runtimeListsRejectCompilerOnlyElementsBeforeEmittingAppendCommands() {
        val main = compile("func main(){ dynamic var values as list<any> = [2 as any]; }")
        val values = main.scope.getVar("values") as NBTList
        val before = StorageAccess.ensure(values).data.facts.fork()
        val commands = main.commands.analyzeAll().filterNot { it.startsWith("#") }
        Function.currFunction = main
        ListOperations.add(values, MCFPPTypeVar(MCFPPBaseType.Int), false)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, values.storageBinding!!.data.facts)
        assertEquals(commands, main.commands.analyzeAll().filterNot { it.startsWith("#") })
    }

    @Test fun legacyTargetsRejectMixedElementEncodingsBeforeChangingTheList() {
        val main = compile("func main(){ var values as list<any> = [2 as any]; }", "1.20.1")
        val values = main.scope.getVar("values") as NBTList
        val before = StorageAccess.ensure(values).data.facts.fork()
        val commands = main.commands.analyzeAll().filterNot { it.startsWith("#") }
        Function.currFunction = main
        ListOperations.add(values, ScoreBoolConcrete(true), false)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, values.storageBinding!!.data.facts)
        assertEquals(commands, main.commands.analyzeAll().filterNot { it.startsWith("#") })
    }

    @Test fun legacyTargetsDiagnoseUnknownIndicesBeforeWriting() {
        MCFPPStringTest.readFromString("""
            func edit(index as int){ var values = [2]; values.insert(index,3); }
            func main(){ edit(0); }
        """.trimIndent(), version = "1.20.1")
        assertTrue(Project.errorCount > 0)
        assertTrue(Project.macroFunction.isEmpty())
    }

    @Test fun aSpliceThroughAnErasedViewPreservesKnownSiblingTypesAndCopiesSubtrees() {
        val main = compile("""
            func edit(value as int) -> int {
                var values as list<any> = [2 as any,3 as any];
                var view = values as list<any>;
                view.prepend(value);
                view.removeAt(1);
                return (values[0] as int)*10 + (values[1] as int);
            }
            func main(){ dynamic var result = edit(7); }
        """)
        assertEquals(73, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun runtimeSearchSeparatesBoolFromByteWithoutRuntimeTypeTags() {
        val main = compile("""
            func find(needle as bool) -> int {
                var values as list<any> = [toByte(1) as any,true as any,false as any];
                return values.indexOf(needle);
            }
            func main(){ dynamic var result = find(true); }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun queriesAfterClearAndSelfAppendUseTheCurrentShape() {
        val main = compile("""
            func main(){
                var values = [2,4];
                values.addAll(values);
                var last = values.lastIndexOf(4);
                values.clear();
                values.add(7);
                var missing = values.indexOf(4);
                dynamic var result = last*100 + missing*10 + values[0];
            }
        """)
        assertEquals(297, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun anErasedRuntimeListWithUnknownElementTypesRequiresAnExplicitViewForLookup() {
        MCFPPStringTest.readFromString("""
            func find(values as list<any>, needle as int) -> int { return values.indexOf(needle); }
            func main(){ var values as list<any> = [2 as any]; find(values,2); }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun aDynamicSpliceKeepsAUniformErasedElementTypeWithoutKeepingOldConstants() {
        val main = compile("""
            func edit(index as int, value as int) -> int {
                var values as list<any> = [2 as any,4 as any];
                values.insert(index,value);
                return values[0]*10 + values[1];
            }
            func main(){ dynamic var result = edit(0,7); }
        """)
        assertEquals(72, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun floatLookupUsesTheBackendEncodingForBothKnownAndUnknownNeedles() {
        for (version in listOf("26.3", "1.20.1")) {
            val main = compile("""
                func find(needle as float) -> int {
                    var values = [1.0,2.0,1.0];
                    return values.lastIndexOf(needle);
                }
                func main(){
                    var values = [1.0,2.0,1.0];
                    dynamic var known = values.indexOf(2.0);
                    dynamic var unknown = find(2.0);
                }
            """, version)
            val machine = execute(main)
            assertEquals(1, machine.read(main.scope.getVar("known") as MCInt), version)
            assertEquals(1, machine.read(main.scope.getVar("unknown") as MCInt), version)
        }
    }

    @Test fun legacyFloatLookupDoesNotFoldFromBitsThatItsStorageEncodingCannotDistinguish() {
        val main = compile("""
            func find(needle as float) -> int { var values = [0.0]; return values.indexOf(needle); }
            func main(){
                var values = [0.0];
                dynamic var known = values.indexOf(-0.0);
                dynamic var unknown = find(-0.0);
            }
        """, "1.20.1")
        val machine = execute(main)
        assertEquals(0, machine.read(main.scope.getVar("unknown") as MCInt))
        assertEquals(0, machine.read(main.scope.getVar("known") as MCInt))
    }
}
