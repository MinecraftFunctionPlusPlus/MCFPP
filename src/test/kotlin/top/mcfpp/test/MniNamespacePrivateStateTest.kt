package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.model.scope.SimpleLibScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniNamespacePrivateStateTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val current = Function.currFunction
        try {
            Project.init()
            action()
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
        } finally {
            project.restore()
            Function.currFunction = current
        }
    }

    private fun descriptor(identifier: String, result: String = "void") = MacroFunctionInfo(
        "missing.provider.NeverLoad",
        "sameMethod",
        listOf(MacroContext::class.java.name),
        Block::class.java.name,
        identifier,
        emptyList(),
        result
    )

    @Test fun macroRollbackRestoresConflictingRawMetadataWithoutRegistrationOrProviderLoading() = isolated {
        val namespace = Namespace("journal")
        val original = descriptor("before")
        val changed = descriptor("after", "int")
        namespace.restoreMacroDeclarations(listOf(original))
        val container = namespace.journalMacroDeclarations
        val before = namespace.checkpointMacroDeclarations()
        container.clear()
        container.add(changed)
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(container, namespace.journalMacroDeclarations)
            assertSame(original, namespace.macroDeclarations().single())
            after.restore()
            assertSame(changed, namespace.macroDeclarations().single())
        }
        before.restore()
        assertSame(original, container.single())
        assertTrue(namespace.scope.functions.isEmpty())
    }

    @Test fun deletedMacroDescriptorsRemainPublishedAsIdentityRootsForComposition() = isolated {
        val namespace = Namespace("journal")
        val first = descriptor("first")
        val second = descriptor("second")
        namespace.journalMacroDeclarations.add(first)
        val before = namespace.checkpointMacroDeclarations()
        namespace.journalMacroDeclarations.clear()
        namespace.journalMacroDeclarations.add(second)
        val after = before.captureCurrent()
        assertEquals(2, after.elements.size)
        assertSame(first, after.elements[0])
        assertSame(second, after.elements[1])
        assertFailsWith<UnsupportedOperationException> { (after.elements as MutableList<*>).clear() }
        before.restore()
        after.restore()
        before.restore()
        assertSame(first, namespace.macroDeclarations().single())
    }

    @Test fun replacedFileListsRestoreTheirOriginalAliasesAndActualLookupRelations() = isolated {
        val scope = NamespaceScope("journal")
        val first = FileScope()
        val second = FileScope()
        val originalFunction = Function("member", "journal", null)
        val changedFunction = Function("member", "changed", null)
        first.functions["actual.alias"] = arrayListOf(originalFunction)
        second.functions["actual.alias"] = arrayListOf(changedFunction)
        val original = scope.journalFileFields.apply { add(first) }
        val alias = NamespaceScope(scope)
        assertSame(original, alias.journalFileFields)
        val before = scope.checkpointFileFields()
        original.clear()
        original.add(second)
        val replacement = arrayListOf(first, second)
        scope.journalFileFields = replacement
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(original, scope.journalFileFields)
            assertSame(original, alias.journalFileFields)
            assertSame(first, original.single())
            assertSame(originalFunction, scope.getFunctionCandidates("actual.alias").single())
            after.restore()
            assertSame(replacement, scope.journalFileFields)
            assertSame(second, original.single())
            assertSame(changedFunction, alias.getFunctionCandidates("actual.alias").single())
            assertEquals(2, replacement.size)
        }
        before.restore()
    }

    @Test fun recapturingMultipleFileListReplacementsKeepsEveryDetachedContainer() = isolated {
        val scope = NamespaceScope("journal")
        val first = FileScope()
        val second = FileScope()
        val third = FileScope()
        val original = scope.journalFileFields.apply { add(first) }
        val before = scope.checkpointFileFields()
        val middle = arrayListOf(second)
        scope.journalFileFields = middle
        original.clear()
        val after = before.captureCurrent()
        scope.journalFileFields = arrayListOf(third)
        original.add(third)
        middle.clear()
        middle.add(first)
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(first, original.single())
            after.restore()
            assertTrue(original.isEmpty())
            assertSame(second, middle.single())
            latest.restore()
            assertSame(third, original.single())
            assertSame(first, middle.single())
            assertSame(third, scope.journalFileFields.single())
        }
        assertTrue(latest.elements.any { it === first })
        assertTrue(latest.elements.any { it === second })
        assertTrue(latest.elements.any { it === third })
        before.restore()
    }

    @Test fun aliasMapsKeepRealKeysOriginalContainersAndDetachedAfterContents() = isolated {
        val scope = NamespaceScope("journal")
        val original = scope.journalTypeAliases
        original["actual.alias"] = MCFPPBaseType.Int
        original["second.alias"] = MCFPPBaseType.Int
        val before = scope.checkpointTypeAliases()
        original.clear()
        original["detached.after"] = MCFPPBaseType.Bool
        val replacement = linkedMapOf<String, MCFPPType>("published.alias" to MCFPPBaseType.Bool)
        scope.journalTypeAliases = replacement
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertSame(original, scope.journalTypeAliases)
            assertEquals(setOf("actual.alias", "second.alias"), original.keys)
            assertSame(MCFPPBaseType.Int, scope.getType("actual.alias"))
            assertSame(MCFPPBaseType.Int, scope.getType("second.alias"))
            assertFalse(scope.containType("published.alias"))
            after.restore()
            assertSame(replacement, scope.journalTypeAliases)
            assertSame(MCFPPBaseType.Bool, original.getValue("detached.after"))
            assertEquals(1, original.size)
            assertSame(MCFPPBaseType.Bool, scope.getType("published.alias"))
        }
        before.restore()
    }

    @Test fun aliasCaptureNeverResolvesUnpreparedDeclarationsOrCallsTypeGettersAndHashing() = isolated {
        val scope = SimpleLibScope()
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("MissingType"))))
        val alias = MCFPPTypeAliasType(parser.type())
        assertEquals(0, parser.numberOfSyntaxErrors)
        var callbacks = 0
        val opaque = object : MCFPPType() {
            override val typeName: String get() {
                callbacks++
                error("Type name must remain opaque")
            }
            override val instanceData: CompoundData get() {
                callbacks++
                error("No declaration preparation")
            }
            override val typeId: TypeId get() {
                callbacks++
                error("Type identity must remain opaque")
            }
            override fun tryResolve() {
                callbacks++
                error("No type resolution")
            }
        }
        scope.journalTypeAliases["raw"] = alias
        scope.journalTypeAliases["opaque"] = opaque
        val before = scope.checkpointTypeAliases()
        scope.journalTypeAliases.clear()
        val after = before.captureCurrent()
        before.restore()
        after.restore()
        before.restore()
        assertNull(alias.cachedTarget)
        assertSame(alias, scope.journalTypeAliases["raw"])
        assertSame(opaque, scope.journalTypeAliases["opaque"])
        assertTrue(after.elements.any { it === alias })
        assertTrue(after.elements.any { it === opaque })
        assertEquals(0, callbacks)
    }

    @Test fun allCompilerLibScopeShapesSupportRawAliasCheckpoints() = isolated {
        val file = FileScope().apply { namespaceField = NamespaceScope("journal") }
        for (scope in listOf(SimpleLibScope(), NamespaceScope("journal"), file)) {
            scope.putResolvedAlias("actual.alias", MCFPPBaseType.Int)
            val before = scope.checkpointTypeAliases()
            scope.removeType("actual.alias")
            val after = before.captureCurrent()
            before.restore()
            assertSame(MCFPPBaseType.Int, scope.journalTypeAliases["actual.alias"])
            after.restore()
            assertTrue(scope.journalTypeAliases.isEmpty())
            before.restore()
        }
    }

    @Test fun unknownFileListIsRejectedBeforeSizeIterationOrArrayCallbacks() = isolated {
        val scope = NamespaceScope("journal")
        val before = scope.checkpointFileFields()
        var callbacks = 0
        scope.journalFileFields = object : ArrayList<FileScope>() {
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override fun iterator(): MutableIterator<FileScope> {
                callbacks++
                return super.iterator()
            }
            override fun toArray(): Array<Any?> {
                callbacks++
                return super.toArray()
            }
        }
        assertFailsWith<IllegalStateException> { scope.checkpointFileFields() }
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, callbacks)
        before.restore()
    }

    @Test fun unknownAliasMapIsRejectedBeforeReadingEntriesOrAnyCollectionCallback() = isolated {
        val scope = SimpleLibScope()
        val before = scope.checkpointTypeAliases()
        var callbacks = 0
        scope.journalTypeAliases = object : HashMap<String, MCFPPType>() {
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override val entries: MutableSet<MutableMap.MutableEntry<String, MCFPPType>>
                get() {
                    callbacks++
                    return super.entries
                }
        }
        assertFailsWith<IllegalStateException> { scope.checkpointTypeAliases() }
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, callbacks)
        before.restore()
    }

    @Test fun unknownLibScopeSubclassIsRejectedWithoutCallingItsResolutionOverrides() = isolated {
        var callbacks = 0
        val scope = object : SimpleLibScope() {
            override fun getType(key: String): MCFPPType? {
                callbacks++
                return null
            }
            override fun containType(id: String): Boolean {
                callbacks++
                return false
            }
        }
        assertFailsWith<IllegalStateException> { scope.checkpointTypeAliases() }
        assertEquals(0, callbacks)
    }

    @Test fun erasedAliasEntriesAreRejectedBeforeHostHashEqualsOrStringCallbacks() = isolated {
        val scope = SimpleLibScope()
        val before = scope.checkpointTypeAliases()
        var callbacks = 0
        val host = object {
            override fun hashCode(): Int {
                callbacks++
                return 5
            }
            override fun equals(other: Any?): Boolean {
                callbacks++
                return this === other
            }
            override fun toString(): String {
                callbacks++
                return "host"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val erased = scope.journalTypeAliases as MutableMap<Any?, Any?>
        erased[host] = MCFPPBaseType.Int
        callbacks = 0
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, callbacks)
        before.restore()
        erased["invalid.value"] = host
        assertFailsWith<IllegalStateException> { scope.checkpointTypeAliases() }
        assertEquals(0, callbacks)
        before.restore()
    }

    @Test fun erasedMacroAndFileElementsAreRejectedBeforeHostCallbacks() = isolated {
        val namespace = Namespace("journal")
        val macros = namespace.checkpointMacroDeclarations()
        val files = namespace.scope.checkpointFileFields()
        var callbacks = 0
        val host = object {
            override fun hashCode(): Int {
                callbacks++
                return 5
            }
            override fun toString(): String {
                callbacks++
                return "host"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val erasedMacros = namespace.journalMacroDeclarations as MutableList<Any?>
        erasedMacros.add(host)
        assertFailsWith<IllegalStateException> { macros.captureCurrent() }
        macros.restore()
        @Suppress("UNCHECKED_CAST")
        val erasedFiles = namespace.scope.journalFileFields as MutableList<Any?>
        erasedFiles.add(host)
        assertFailsWith<IllegalStateException> { files.captureCurrent() }
        files.restore()
        assertEquals(0, callbacks)
    }
}
