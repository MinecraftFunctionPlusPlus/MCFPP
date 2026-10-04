package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class CompilerOnlyViewTest {
    private fun typeValue(main: Function, name: String) = main.scope.getVar(name).let {
        assertIs<MCFPPTypeVar>(if (it is MCAny) it.semanticValue() else it).value
    }

    private fun compile(source: String): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().also { main ->
            assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
        }
    }

    @Test fun namedDictionaryViewsShareNestedWritesWithTheirSource() {
        val main = compile("""
            func main(){
                var source = {types:[int,float], untouched:bool};
                var first = source as dict<any>;
                var second = first as dict<any>;
                first["types"][0] = float;
                second["types"][-1] = int;
                var changedFirst = source["types"][0];
                var changedLast = source["types"][1];
                var preserved = source["untouched"];
            }
        """)
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changedFirst"))
        assertEquals(MCFPPBaseType.Int, typeValue(main, "changedLast"))
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "preserved"))
        val binding = main.scope.getVar("source")!!.storageBinding!!
        assertEquals(binding.place, main.scope.getVar("first")!!.storageBinding!!.place)
        assertEquals(binding.place, main.scope.getVar("second")!!.storageBinding!!.place)
        assertTrue(binding.data.versions.version(binding.place.field("types").index(0)) > 0)
        assertIs<ValueKnowledge.Constant>(binding.data.facts.read(binding.place.field("untouched"))!!.value)
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
    }

    @Test fun aListViewSharesItsPlaceAndOrdinaryCopiesRemainIndependent() {
        val main = compile("""
            func main(){
                var source = [int,float];
                var view = source as list<any>;
                var copied = view;
                view[0] = bool;
                copied[-1] = int;
                var changed = source[0];
                var preservedFirst = copied[0];
                var preservedLast = source[-1];
                var changedCopy = copied[1];
            }
        """)
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "changed"))
        assertEquals(MCFPPBaseType.Int, typeValue(main, "preservedFirst"))
        assertEquals(MCFPPBaseType.Float, typeValue(main, "preservedLast"))
        assertEquals(MCFPPBaseType.Int, typeValue(main, "changedCopy"))
        val source = main.scope.getVar("source")!!.storageBinding!!
        assertEquals(source.place, main.scope.getVar("view")!!.storageBinding!!.place)
        assertNotEquals(source.place, main.scope.getVar("copied")!!.storageBinding!!.place)
    }

    @Test fun anyViewsReadTheLatestCompilerOnlyPayloadAfterSharedWrites() {
        val main = compile("""
            func main(){
                var erased as any = {types:[int,float]};
                var view = erased as dict<any>;
                view["types"][0] = bool;
                var changed = erased["types"][0];
                var copied = erased;
                view["types"][-1] = int;
                var preserved = copied["types"][-1];
                var changedLast = erased["types"][-1];
            }
        """)
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "changed"))
        assertEquals(MCFPPBaseType.Float, typeValue(main, "preserved"))
        assertEquals(MCFPPBaseType.Int, typeValue(main, "changedLast"))
        assertEquals(main.scope.getVar("erased")!!.storageBinding!!.place, main.scope.getVar("view")!!.storageBinding!!.place)
        assertNotNull(ValueSnapshot.of(main.scope.getVar("erased")))
    }

    @Test fun objectViewsShareWritesAcrossMultipleExplicitInterpretations() {
        val main = compile("""
            func main(){
                var erased as object = {kind:int};
                var first = erased as dict<any>;
                var second = erased as dict<any>;
                first["kind"] = float;
                var changed = second["kind"];
            }
        """)
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changed"))
        assertEquals(main.scope.getVar("first")!!.storageBinding!!.place, main.scope.getVar("second")!!.storageBinding!!.place)
    }

    @Test fun ordinaryErasureOfAStaticViewCopiesItsLatestValueWithoutItsBinding() {
        val main = compile("""
            func main(){
                var source = {kind:int};
                var view = source as dict<any>;
                view["kind"] = bool;
                var copied as any = view;
                view["kind"] = float;
                var preserved = copied["kind"];
                var changed = source["kind"];
            }
        """)
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "preserved"))
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changed"))
        val copied = assertIs<MCAny>(main.scope.getVar("copied"))
        assertNull(copied.compilerPayload!!.storageBinding)
    }

    @Test fun ordinaryErasureOfATypeViewKeepsAnIndependentCompilerValue() {
        val main = compile("""
            func main(){
                var source = int;
                var view = source as type;
                var copied as any = view;
                view = float;
                var preserved = copied as type;
                var changed = source;
            }
        """)
        assertEquals(MCFPPBaseType.Int, typeValue(main, "preserved"))
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changed"))
        assertEquals(main.scope.getVar("source")!!.storageBinding!!.place, main.scope.getVar("view")!!.storageBinding!!.place)
    }

    @Test fun wholeStaticCollectionReplacementUpdatesExistingViews() {
        val main = compile("""
            func main(){
                var source = {kind:int};
                var view = source as dict<any>;
                source = {kind:float, other:bool};
                var changed = view["kind"];
                var added = view["other"];
            }
        """)
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changed"))
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "added"))
        assertNotNull(ValueSnapshot.of(main.scope.getVar("view")))
    }

    @Test fun runtimeCommandsCannotInvalidateOrMaterializeStaticViewPlaces() {
        val main = compile("""
            func main(){
                var source = {kind:int};
                var view = source as dict<any>;
                view["kind"] = float;
                /say barrier
                var changed = source["kind"];
            }
        """)
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changed"))
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
        assertTrue(main.commands.analyzeAll().contains("say barrier"))
    }

    @Test fun writingANewStaticDictionaryFieldPreservesTheCompleteRootValue() {
        val main = compile("""
            func main(){
                var source = {kind:int};
                var view = source as dict<any>;
                view["added"] = bool;
                var added = source["added"];
                var preserved = source["kind"];
            }
        """)
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "added"))
        assertEquals(MCFPPBaseType.Int, typeValue(main, "preserved"))
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
    }

    @Test fun nestedErasedFieldsKeepTheirSharedPlacesAndActualTypes() {
        val main = compile("""
            func main(){
                var source = {nested:{kind:int} as any};
                var view = source as dict<any>;
                view["nested"]["kind"] = float;
                var changed = source["nested"]["kind"];
            }
        """)
        assertEquals(MCFPPBaseType.Float, typeValue(main, "changed"))
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
    }

    @Test fun nativeStaticListMutationsCommitToTheSharedPlaceWithoutNbtWrites() {
        val main = compile("""
            func main(){
                var source = {types:[int,float]};
                var view = source as dict<any>;
                var elements = view["types"] as list<type>;
                elements.clear();
                elements.add(bool);
                var changed = source["types"][0];
            }
        """)
        assertEquals(MCFPPBaseType.Bool, typeValue(main, "changed"))
        assertNotNull(ValueSnapshot.of(main.scope.getVar("source")))
    }

    @Test fun readingAnOldStaticAdapterUsesTheNewWriteVersionAndLeavesOldSnapshotsFrozen() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val source = NBTDictionaryConcrete(hashMapOf("kind" to MCFPPTypeVar(MCFPPBaseType.Int)), "source")
        val view = StorageAccess.view(source, source.type)
        val old = ValueSnapshot.of(source)
        val hash = old.hashCode()
        val key = MCStringConcrete(StringTag("kind"))
        val cached = StorageAccess.read(StorageAccess.element(view, key, MCFPPBaseType.Any))
        val oldVersion = cached.storageReadVersion
        StorageAccess.element(view, key, MCFPPBaseType.Any).assignedBy(MCFPPTypeVar(MCFPPBaseType.Float))
        val updated = assertIs<MCAny>(StorageAccess.read(cached))
        assertEquals(MCFPPBaseType.Float, assertIs<MCFPPTypeVar>(updated.semanticValue()).value)
        assertNotEquals(oldVersion, updated.storageReadVersion)
        assertEquals(hash, old.hashCode())
        assertNotEquals(old, ValueSnapshot.of(source))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun unknownStaticWritesAreRejectedBeforeFactsOrCommandsChange() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val source = NBTDictionaryConcrete(hashMapOf("kind" to MCFPPTypeVar(MCFPPBaseType.Int)), "source")
        val view = StorageAccess.view(source, source.type)
        val binding = source.storageBinding!!
        val before = ValueSnapshot.of(source)
        val version = binding.data.versions.version(binding.place)
        val key = MCStringConcrete(StringTag("kind"))
        val unknown = MCInt("unknown").apply { hasAssigned = true; isDynamic = true }
        StorageAccess.element(view, key, MCFPPBaseType.Any).assignedBy(unknown)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, ValueSnapshot.of(source))
        assertEquals(version, binding.data.versions.version(binding.place))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun aDynamicIndexCannotAddressACompilerOnlyView() {
        val main = compile("func main(){ var source = [int,float]; var view = source as list<any>; }")
        Function.currFunction = main
        val source = main.scope.getVar("source")!!
        val before = ValueSnapshot.of(source)
        StorageAccess.element(main.scope.getVar("view")!!, MCIntConcrete(0).apply { isDynamic = true }, MCFPPBaseType.Any)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, ValueSnapshot.of(source))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun dynamicViewDeclarationsRejectCompilerOnlyLayoutsWithoutCallingTheirRuntimeAdapters() {
        for (source in listOf(
            "var source = int; dynamic var view = source as type;",
            "var source = {kind:int}; dynamic var view = source as dict<any>;",
            "var source = [int,float]; dynamic var view = source as list<any>;"
        )) {
            MCFPPStringTest.readFromString("func main(){ $source }", version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it }, source)
        }
    }
}
