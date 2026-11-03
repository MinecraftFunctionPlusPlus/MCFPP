package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.lib.SbObject
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.model.scope.SimpleLibScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniNamespaceStateJournalTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val registrations = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        try {
            Project.init()
            action()
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
        } finally {
            registrations.restore()
            project.restore()
            Function.currFunction = caller
        }
    }

    private fun generic(name: String = "Generic") = GenericDataTemplate(mcfppParser.TemplateBodyContext(null, -1), name, "journal")

    private fun descriptor(name: String) = MacroFunctionInfo(
        "missing.provider.NeverLoad",
        "same",
        listOf(MacroContext::class.java.name),
        Block::class.java.name,
        name,
        emptyList(),
        "void"
    )

    private fun uninitialized(file: FileScope) {
        assertEquals(
            "lateinit property namespaceField has not been initialized",
            assertFailsWith<UninitializedPropertyAccessException> { file.namespaceField }.message
        )
    }

    @Test fun allFourNamespaceTablesRestoreRealKeysAliasesAndOriginalContainers() = isolated {
        val tables = listOf(GlobalScope.localNamespaces, GlobalScope.importedLibNamespaces, GlobalScope.libNamespaces, GlobalScope.stdNamespaces)
        val old = Namespace("different.identifier")
        val parents = GlobalScope.parent
        tables.forEachIndexed { index, table -> table["actual.alias.$index"] = old }
        val before = NamespaceStateJournal.checkpoint()
        val replacement = Namespace("different.identifier")
        GlobalScope.parent = arrayListOf(replacement.scope)
        val replacementParents = GlobalScope.parent
        tables.forEachIndexed { index, table ->
            table.remove("actual.alias.$index")
            table["replacement.$index"] = replacement
        }
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(tables[0], GlobalScope.localNamespaces)
            assertSame(tables[1], GlobalScope.importedLibNamespaces)
            assertSame(tables[2], GlobalScope.libNamespaces)
            assertSame(tables[3], GlobalScope.stdNamespaces)
            assertSame(parents, GlobalScope.parent)
            tables.forEachIndexed { index, table ->
                assertSame(old, table["actual.alias.$index"])
                assertFalse(table.containsKey("replacement.$index"))
            }
            after.restore()
            assertSame(replacementParents, GlobalScope.parent)
            assertSame(replacement.scope, GlobalScope.parent.single())
            tables.forEachIndexed { index, table ->
                assertSame(replacement, table["replacement.$index"])
                assertFalse(table.containsKey("actual.alias.$index"))
            }
        }
        assertTrue(after.elements.any { it === old })
        assertTrue(after.elements.any { it === replacement })
    }

    @Test fun replacedFunctionMapsAndDetachedBucketsKeepTheirOwnAfterContents() = isolated {
        val namespace = Namespace("journal").also { GlobalScope.localNamespaces["alias"] = it }
        val first = Function("declared", "journal", null)
        val detached = Function("detached", "journal", null)
        val published = Function("published", "journal", null)
        val bucket = arrayListOf(first)
        val map = namespace.scope.functions.also { it["actual.key"] = bucket }
        val before = NamespaceStateJournal.checkpoint()
        bucket.clear()
        bucket.add(detached)
        namespace.scope.functions = hashMapOf("new.key" to arrayListOf(published))
        val replacement = namespace.scope.functions
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(map, namespace.scope.functions)
            assertSame(bucket, map["actual.key"])
            assertSame(first, bucket.single())
            after.restore()
            assertSame(replacement, namespace.scope.functions)
            assertSame(detached, bucket.single())
            assertSame(published, replacement["new.key"]!!.single())
        }
        assertTrue(after.elements.any { it === first })
        assertTrue(after.elements.any { it === detached })
        assertTrue(after.elements.any { it === published })
    }

    @Test fun everyPublishedDeclarationCategoryAndRawAliasIsRetainedWithoutResolution() = isolated {
        val scope = SimpleLibScope()
        val template = DataTemplate("Template", "journal")
        val abstractTemplate = DataTemplate("Abstract", "journal").apply { isAbstract = true }
        val generic = generic()
        val abstractGeneric = generic("GenericAbstract").apply { isAbstract = true }
        val objectData = CompoundData("Object", "journal")
        val enumeration = Enum("Enumeration", "journal")
        val value = MCInt("value")
        val alias = object : MCFPPType() {
            override val typeId: TypeId get() = error("Aliases must remain opaque identity roots")
        }
        scope.template["t.alias"] = template
        scope.template["abstract.alias"] = abstractTemplate
        scope.genericTemplate["g.alias" to listOf(MCFPPBaseType.Int)] = generic
        scope.genericTemplate["ga.alias" to listOf(MCFPPBaseType.Bool)] = abstractGeneric
        scope.objects.add(objectData)
        scope.enums.add(enumeration)
        scope.vars["v.alias"] = value
        scope.annotations["a.alias"] = top.mcfpp.mni.annotation.Tick::class.java
        scope.journalTypeAliases["type.alias"] = alias
        val before = NamespaceStateJournal.checkpoint(listOf(scope))
        scope.template.clear()
        scope.genericTemplate.clear()
        scope.objects.clear()
        scope.enums.clear()
        scope.vars.clear()
        scope.annotations.clear()
        scope.journalTypeAliases.clear()
        val after = before.captureCurrent()
        listOf(template, abstractTemplate, generic, abstractGeneric, objectData, enumeration, value, alias, top.mcfpp.mni.annotation.Tick::class.java).forEach { root ->
            assertTrue(after.elements.any { it === root }, "Missing identity root ${root.javaClass.name}")
        }
        repeat(2) {
            before.restore()
            assertSame(template, scope.template["t.alias"])
            assertSame(abstractTemplate, scope.template["abstract.alias"])
            assertSame(generic, scope.genericTemplate["g.alias" to listOf(MCFPPBaseType.Int)])
            assertSame(abstractGeneric, scope.genericTemplate["ga.alias" to listOf(MCFPPBaseType.Bool)])
            assertSame(objectData, scope.objects.single())
            assertSame(enumeration, scope.enums.single())
            assertSame(value, scope.vars["v.alias"])
            assertSame(alias, scope.journalTypeAliases["type.alias"])
            assertSame(top.mcfpp.mni.annotation.Tick::class.java, scope.annotations["a.alias"])
            after.restore()
            assertTrue(scope.template.isEmpty())
            assertTrue(scope.vars.isEmpty())
            assertTrue(scope.journalTypeAliases.isEmpty())
        }
    }

    @Test fun uninitializedFilesAndBothImportTablesFollowTheReversibleNamespaceSlot() = isolated {
        val file = FileScope()
        val before = NamespaceStateJournal.checkpoint(listOf(file))
        val assigned = NamespaceScope("assigned")
        val imported = NamespaceScope("imported")
        val wildcard = NamespaceScope("wildcard")
        file.namespaceField = assigned
        file.importField["actual.import"] = imported
        file.importedNamespaceField["actual.wildcard"] = wildcard
        val function = Function("member", "journal", null)
        imported.functions["member"] = arrayListOf(function)
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            uninitialized(file)
            assertTrue(file.importField.isEmpty())
            assertTrue(file.importedNamespaceField.isEmpty())
            after.restore()
            assertSame(assigned, file.namespaceField)
            assertSame(imported, file.importField["actual.import"])
            assertSame(wildcard, file.importedNamespaceField["actual.wildcard"])
            assertSame(function, imported.functions["member"]!!.single())
        }
        assertTrue(after.elements.any { it === function })
        assertTrue(after.elements.any { it === imported })
    }

    @Test fun namespaceFileListsAndParentsCanBeReplacedAndDeletedAcrossThreeGenerations() = isolated {
        val namespace = Namespace("journal").also { GlobalScope.localNamespaces["actual"] = it }
        val file = FileScope().also { it.namespaceField = namespace.scope }
        val originalFiles = namespace.scope.journalFileFields.also { it.add(file) }
        val originalParents = file.parent.also { it.add(namespace.scope) }
        val before = NamespaceStateJournal.checkpoint()
        val second = FileScope().also { it.namespaceField = namespace.scope }
        namespace.scope.journalFileFields = arrayListOf(second)
        val secondFiles = namespace.scope.journalFileFields
        originalFiles.clear()
        originalParents.clear()
        file.parent = arrayListOf(second)
        val after = before.captureCurrent()
        GlobalScope.localNamespaces.remove("actual")
        secondFiles.clear()
        secondFiles.add(file)
        namespace.scope.journalFileFields = arrayListOf()
        val third = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(originalFiles, namespace.scope.journalFileFields)
            assertSame(file, originalFiles.single())
            assertSame(originalParents, file.parent)
            assertSame(namespace.scope, originalParents.single())
            after.restore()
            assertSame(secondFiles, namespace.scope.journalFileFields)
            assertSame(second, secondFiles.single())
            assertTrue(originalFiles.isEmpty())
            third.restore()
            assertFalse(GlobalScope.localNamespaces.containsKey("actual"))
            assertTrue(namespace.scope.journalFileFields.isEmpty())
            assertSame(file, secondFiles.single())
        }
        listOf(namespace, file, second).forEach { root -> assertTrue(third.elements.any { it === root }) }
    }

    @Test fun mutableGenericKeysRestoreBeforeRehashAndRetainTheirOriginalListIdentity() = isolated {
        val scope = SimpleLibScope()
        val parameters = arrayListOf<MCFPPType>(MCFPPBaseType.Int, MCFPPBaseType.Bool)
        val key = "real.generic.alias" to parameters
        val declaration = generic()
        val original = scope.genericTemplate.also { it[key] = declaration }
        val before = NamespaceStateJournal.checkpoint(listOf(scope))
        parameters[0] = MCFPPBaseType.Float
        parameters.removeAt(1)
        scope.genericTemplate = hashMapOf("replacement" to listOf(MCFPPBaseType.String) to generic("After"))
        val replacement = scope.genericTemplate
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(original, scope.genericTemplate)
            assertSame(key, original.keys.single())
            assertSame(parameters, original.keys.single().second)
            assertSame(declaration, original["real.generic.alias" to listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool)])
            after.restore()
            assertSame(replacement, scope.genericTemplate)
            assertEquals(1, parameters.size)
            assertSame(MCFPPBaseType.Float, parameters.single())
            assertSame(declaration, original["real.generic.alias" to listOf(MCFPPBaseType.Float)])
        }
    }

    @Test fun fixedSizeGenericKeysRestoreThroughTheirBackingArrayWithoutChangingIdentity() = isolated {
        val scope = SimpleLibScope()
        val backing = arrayOf<MCFPPType>(MCFPPBaseType.Int, MCFPPBaseType.Bool)
        val parameters = backing.asList()
        val alias = parameters as MutableList<MCFPPType>
        val key = "fixed" to parameters
        val declaration = generic()
        scope.genericTemplate[key] = declaration
        val before = NamespaceStateJournal.checkpoint(listOf(scope))
        backing[0] = MCFPPBaseType.Float
        alias[1] = MCFPPBaseType.String
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(parameters, scope.genericTemplate.keys.single().second)
            assertSame(MCFPPBaseType.Int, alias[0])
            assertSame(MCFPPBaseType.Bool, alias[1])
            assertSame(MCFPPBaseType.Int, backing[0])
            assertSame(MCFPPBaseType.Bool, backing[1])
            assertSame(declaration, scope.genericTemplate["fixed" to listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool)])
            after.restore()
            assertSame(MCFPPBaseType.Float, alias[0])
            assertSame(MCFPPBaseType.String, alias[1])
            assertSame(MCFPPBaseType.Float, backing[0])
            assertSame(MCFPPBaseType.String, backing[1])
            assertSame(declaration, scope.genericTemplate["fixed" to listOf(MCFPPBaseType.Float, MCFPPBaseType.String)])
        }
    }

    @Test fun tagStateAndDetachedFunctionListsRestoreAlongsideTheGlobalRegistrySlots() = isolated {
        val tag = FunctionTag("before", "id")
        val function = Function("first", "journal", null)
        val functions = tag.functions.also { it.add(function) }
        val tags = GlobalScope.functionTags.also { it["actual.tag.alias"] = tag }
        val before = NamespaceStateJournal.checkpoint()
        tag.namespace = "after"
        tag.identifier = "changed"
        functions.clear()
        tag.functions = arrayListOf(Function("second", "journal", null))
        val afterFunctions = tag.functions
        GlobalScope.functionTags = hashMapOf()
        val replacement = GlobalScope.functionTags
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(tags, GlobalScope.functionTags)
            assertSame(tag, tags["actual.tag.alias"])
            assertEquals("before", tag.namespace)
            assertEquals("id", tag.identifier)
            assertSame(functions, tag.functions)
            assertSame(function, functions.single())
            after.restore()
            assertSame(replacement, GlobalScope.functionTags)
            assertEquals("after", tag.namespace)
            assertSame(afterFunctions, tag.functions)
            assertTrue(functions.isEmpty())
        }
        assertTrue(after.elements.any { it === function })
        assertTrue(after.elements.any { it === afterFunctions.single() })
    }

    @Test fun scoreboardMetadataUsesTheExistingDeepStorageCapabilityIncludingDetachedDisplays() = isolated {
        val text = PlainChatComponent("before")
        val display = ListChatComponent().also { it.components.add(text) }
        val scoreboard = SbObject("journal_score", "dummy", display)
        val scores = GlobalScope.scoreboards
        val before = NamespaceStateJournal.checkpoint()
        scoreboard.name = "after"
        scoreboard.criterion = "trigger"
        text.value = "changed"
        display.components.clear()
        scoreboard.display = ListChatComponent()
        GlobalScope.scoreboards = hashMapOf()
        val replacement = GlobalScope.scoreboards
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(scores, GlobalScope.scoreboards)
            assertSame(scoreboard, scores["journal_score"])
            assertEquals("journal_score", scoreboard.name)
            assertEquals("dummy", scoreboard.criterion)
            assertSame(display, scoreboard.display)
            assertSame(text, display.components.single())
            assertEquals("before", text.value)
            after.restore()
            assertSame(replacement, GlobalScope.scoreboards)
            assertEquals("after", scoreboard.name)
            assertEquals("changed", text.value)
            assertTrue(display.components.isEmpty())
        }
    }

    @Test fun rawMacroMetadataAndOpaqueAliasesRestoreWithoutProviderLoadingOrTypeResolution() = isolated {
        val namespace = Namespace("journal").also { GlobalScope.localNamespaces["journal"] = it }
        val first = descriptor("before")
        namespace.journalMacroDeclarations.add(first)
        var resolutions = 0
        val alias = object : MCFPPType() {
            override fun tryResolve() {
                resolutions++
            }
        }
        namespace.scope.journalTypeAliases["actual.alias"] = alias
        val before = NamespaceStateJournal.checkpoint()
        namespace.journalMacroDeclarations.clear()
        namespace.journalMacroDeclarations.add(descriptor("after"))
        namespace.scope.journalTypeAliases = hashMapOf()
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(first, namespace.macroDeclarations().single())
            assertSame(alias, namespace.scope.journalTypeAliases["actual.alias"])
            after.restore()
        }
        assertEquals(0, resolutions)
        assertTrue(namespace.scope.functions.isEmpty())
        assertTrue(after.elements.any { it === first })
        assertTrue(after.elements.any { it === alias })
    }

    @Test fun relationRootsAllowExplicitFunctionDeepCompositionWithoutClaimingItImplicitly() = isolated {
        val namespace = Namespace("journal").also { GlobalScope.localNamespaces["journal"] = it }
        val function = Function("member", "journal", null)
        val command = Command("say before").also { function.commands.add(it) }
        namespace.scope.functions["actual.alias"] = arrayListOf(function)
        val before = NamespaceStateJournal.checkpoint()
        assertTrue(before.elements.any { it === function })
        val deepBefore = FunctionStateJournal.checkpoint(listOf(function))
        function.commands.clear()
        function.commands.add("say after")
        namespace.scope.functions.clear()
        val after = before.captureCurrent()
        val deepAfter = deepBefore.captureCurrent()
        before.restore()
        assertEquals("say after", function.commands.single().toString())
        repeat(2) {
            deepBefore.restore()
            before.restore()
            assertSame(command, function.commands.single())
            assertSame(function, namespace.scope.functions["actual.alias"]!!.single())
            deepAfter.restore()
            after.restore()
            assertEquals("say after", function.commands.single().toString())
            assertTrue(namespace.scope.functions.isEmpty())
        }
        assertTrue(after.elements.any { it === function })
        assertFailsWith<UnsupportedOperationException> { (after.elements as MutableList<Any>).clear() }
    }

    private class Probe {
        var calls = 0
    }
    private class HostMap<K, V>(private val probe: Probe) : HashMap<K, V>() {
        override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() {
            probe.calls++
            error("host entries")
        }
        override val size: Int get() {
            probe.calls++
            error("host size")
        }
    }
    private class HostList<E>(private val probe: Probe) : ArrayList<E>() {
        override fun iterator(): MutableIterator<E> {
            probe.calls++
            error("host iterator")
        }
        override val size: Int get() {
            probe.calls++
            error("host size")
        }
        override fun hashCode(): Int {
            probe.calls++
            return 1
        }
    }

    @Test fun unknownGlobalMapsAreRejectedBeforeEntriesOrSizeCallbacks() = isolated {
        val probe = Probe()
        GlobalScope.scoreboards = HostMap(probe)
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint() }
        assertEquals(0, probe.calls)
    }

    @Test fun unknownFunctionBucketsAndScopeSubclassesAreRejectedBeforeTheirCallbacks() = isolated {
        val probe = Probe()
        val scope = SimpleLibScope()
        scope.functions["host"] = HostList(probe)
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint(listOf(scope)) }
        assertEquals(0, probe.calls)
        val host = object : SimpleLibScope() {
            override fun getType(key: String): MCFPPType? {
                probe.calls++
                error("host resolution")
            }
        }
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint(listOf(host)) }
        assertEquals(0, probe.calls)
    }

    @Test fun unknownGenericKeyListsAreRejectedBeforeIterationOrHashing() = isolated {
        val probe = Probe()
        val key = "generic" to HostList<MCFPPType>(probe)
        val scope = SimpleLibScope()
        scope.genericTemplate[key] = generic()
        probe.calls = 0
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint(listOf(scope)) }
        assertEquals(0, probe.calls)
    }

    @Test fun erasedGenericKeyElementsAndUnknownTypeGettersAreRejectedBeforeHashCallbacks() = isolated {
        val probe = Probe()
        val host = object {
            override fun hashCode(): Int {
                probe.calls++
                return 1
            }
        }
        val scope = SimpleLibScope()
        @Suppress("UNCHECKED_CAST")
        val erased = scope.genericTemplate as MutableMap<Any?, Any?>
        erased["erased" to arrayListOf(host)] = generic()
        probe.calls = 0
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint(listOf(scope)) }
        assertEquals(0, probe.calls)
        scope.genericTemplate.clear()
        val type = object : MCFPPType() {
            override val typeId: TypeId get() {
                probe.calls++
                return TypeId.Opaque(1)
            }
        }
        scope.genericTemplate["type" to arrayListOf(type)] = generic()
        probe.calls = 0
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint(listOf(scope)) }
        assertEquals(0, probe.calls)
        scope.genericTemplate.clear()
        val nested = MCFPPListType(type)
        scope.genericTemplate["nested" to listOf(nested)] = generic()
        probe.calls = 0
        assertFailsWith<IllegalStateException> { NamespaceStateJournal.checkpoint(listOf(scope)) }
        assertEquals(0, probe.calls)
    }
}
