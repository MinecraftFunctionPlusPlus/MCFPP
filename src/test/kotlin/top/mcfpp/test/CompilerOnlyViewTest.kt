package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPDictType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class CompilerOnlyViewTest {
    private fun dictionary(): Var<*> {
        val type = MCFPPDictType(MCFPPConcreteType.Type)
        val literal = StorageAccess.dictionaryLiteral(type, mapOf("kind" to MCFPPTypeVar(MCFPPBaseType.Int)))
        val result = type.buildUnConcrete("source")
        StorageAccess.declare(result, Symbol(SymbolId.fresh(), "source", type.typeId, mutable = true))
        StorageAccess.write(result, literal)
        return result
    }
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
        val source = dictionary()
        val view = StorageAccess.view(source, source.type)
        val old = StorageAccess.snapshot(source)
        val hash = old.hashCode()
        val key = MCString(StringTag("kind"))
        val cached = StorageAccess.read(StorageAccess.element(view, key, MCFPPBaseType.Any))
        val oldVersion = cached.storageReadVersion
        StorageAccess.element(view, key, MCFPPBaseType.Any).assignedBy(MCFPPTypeVar(MCFPPBaseType.Float))
        val updated = assertIs<MCAny>(StorageAccess.read(cached))
        assertEquals(MCFPPBaseType.Float, StorageAccess.resolveTypeValue(updated))
        assertNotEquals(oldVersion, updated.storageReadVersion)
        assertEquals(hash, old.hashCode())
        assertNotEquals(old, StorageAccess.snapshot(source))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun unknownStaticWritesAreRejectedBeforeFactsOrCommandsChange() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val source = dictionary()
        val view = StorageAccess.view(source, source.type)
        val binding = source.storageBinding!!
        val before = StorageAccess.snapshot(source)
        val version = binding.data.versions.version(binding.place)
        val key = MCString(StringTag("kind"))
        val unknown = MCInt("unknown")
        StorageAccess.bindIncomingParameter(unknown)
        StorageAccess.element(view, key, MCFPPBaseType.Any).assignedBy(unknown)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, StorageAccess.snapshot(source))
        assertEquals(version, binding.data.versions.version(binding.place))
        assertFalse(main.commands.analyzeAll().any { "set value" in it || "set from" in it })
    }

    @Test fun aDynamicIndexCannotAddressACompilerOnlyView() {
        val main = compile("func main(){}")
        Function.currFunction = main
        val source = StorageAccess.listLiteral(MCFPPListType(MCFPPConcreteType.Type), listOf(MCFPPTypeVar(MCFPPBaseType.Int), MCFPPTypeVar(MCFPPBaseType.Float)), "source")
        val view = StorageAccess.view(source, source.type)
        val before = StorageAccess.snapshot(source)
        val index = MCInt("index")
        StorageAccess.bindIncomingParameter(index)
        StorageAccess.element(view, index, MCFPPBaseType.Any)
        assertTrue(Project.errorCount > 0)
        assertEquals(before, StorageAccess.snapshot(source))
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
