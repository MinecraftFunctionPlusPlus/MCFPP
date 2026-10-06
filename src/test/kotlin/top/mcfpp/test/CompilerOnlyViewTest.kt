package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class CompilerOnlyViewTest {
    private fun compile(source: String): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().also { main ->
            assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
        }
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
        val main = compile("func main(){}")
        Function.currFunction = main
        val source = NBTListConcrete(arrayListOf(MCFPPTypeVar(MCFPPBaseType.Int), MCFPPTypeVar(MCFPPBaseType.Float)), "source", top.mcfpp.type.MCFPPConcreteType.Type)
        val view = StorageAccess.view(source, source.type)
        val before = ValueSnapshot.of(source)
        StorageAccess.element(view, MCIntConcrete(0).apply { isDynamic = true }, MCFPPBaseType.Any)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, ValueSnapshot.of(source))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun dynamicViewDeclarationsRejectCompilerOnlyLayoutsWithoutCallingTheirRuntimeAdapters() {
        for (source in listOf(
            "dynamic var view = int as type;",
            "dynamic var view = {kind:int} as dict<any>;",
            "dynamic var view = [int,float] as list<any>;"
        )) {
            MCFPPStringTest.readFromString("func main(){ $source }", version = "26.3")
            assertTrue(Project.errorCount > 0, source)
            val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it }, source)
        }
    }
}
