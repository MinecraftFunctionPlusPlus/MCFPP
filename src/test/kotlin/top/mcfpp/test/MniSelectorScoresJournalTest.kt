package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.expand.CompilerObjectJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniSelectorScoresJournalTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        val forced = Function.forcedField
        val ignoreStdLib = CompileSettings.ignoreStdLib
        val shared = requireNotNull(MCFPPEntityType.data.scope.getVar("scores"))
        val sharedSlot = MCFPPEntityType.checkpointScores(shared)
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = false
            Project.init()
            Project.readProject()
            assertEquals(0, Project.errorCount, "The real standard library must initialize without errors")
            assertEquals(0, Project.warningCount, "The real standard library must initialize without warnings")
            Function.currFunction = Function("selector_scores", "fixture.journal", null)
            action()
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
        } finally {
            sharedSlot.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
            Function.forcedField = forced
            CompileSettings.ignoreStdLib = ignoreStdLib
        }
    }

    private fun fresh() = MCFPPEntityType.newScoresDeclaration()
    private fun receiver(value: NBTDictionary) = assertIs<SelectorVar.SelectorParamMap>(value)
    private fun selector(name: String) = SelectorVar(EntitySelector('e'), name)
    private fun program(value: SelectorVar) = value.value.toCommandPart().toString()
    private fun tagged(value: SelectorVar, tag: String) {
        StorageAccess.updateSelector(value, value.value.apply { this.tag(tag, false) })
        assertTrue(program(value).contains(tag), "The actual stored selector program must contain '$tag'")
    }

    private fun uninitialized(value: NBTDictionary) {
        assertEquals(
            "lateinit property selector has not been initialized",
            assertFailsWith<UninitializedPropertyAccessException> { receiver(value).selector }.message
        )
    }

    @Test fun rawCaptureDoesNotInitializeThePublicReceiverSlot() = isolated {
        val scores = fresh()
        val type = scores.type
        val before = MCFPPEntityType.checkpointScores(scores)
        repeat(2) {
            uninitialized(scores)
            before.restore()
            assertSame(type, scores.type)
            assertSame(scores, MCFPPEntityType.scoresTypeOwner(type))
        }
        assertTrue(before.elements.any { it === scores })
        assertTrue(before.elements.any { it === type })
    }

    @Test fun rawBeforeAndAfterRestoreTheInitializedSlotAndOriginalTypeIdentity() = isolated {
        val scores = fresh()
        val type = scores.type
        val before = MCFPPEntityType.checkpointScores(scores)
        val selected = selector("selected")
        receiver(scores).selector = selected
        scores.type = MCFPPBaseType.Any
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            uninitialized(scores)
            assertSame(type, scores.type)
            after.restore()
            assertSame(selected, receiver(scores).selector)
            assertSame(MCFPPBaseType.Any, scores.type)
        }
        assertTrue(after.elements.any { it === type })
        assertTrue(after.elements.any { it === selected })
    }

    @Test fun rawCaptureRetainsMultipleDetachedReceiversAcrossGenerations() = isolated {
        val scores = fresh()
        val first = selector("first")
        val second = selector("second")
        val third = selector("third")
        receiver(scores).selector = first
        val before = MCFPPEntityType.checkpointScores(scores)
        receiver(scores).selector = second
        val after = before.captureCurrent()
        receiver(scores).selector = third
        val latest = after.captureCurrent()
        listOf(first, second, third).forEach { root -> assertTrue(latest.elements.any { it === root }) }
        repeat(2) {
            before.restore()
            assertSame(first, receiver(scores).selector)
            after.restore()
            assertSame(second, receiver(scores).selector)
            latest.restore()
            assertSame(third, receiver(scores).selector)
        }
    }

    @Test fun publishedRootsAreDefensiveAndDoNotChangeWithTheLiveSlot() = isolated {
        val scores = fresh()
        val before = MCFPPEntityType.checkpointScores(scores)
        val selected = selector("selected")
        receiver(scores).selector = selected
        assertFalse(before.elements.any { it === selected })
        assertFailsWith<UnsupportedOperationException> { (before.elements as MutableList<Any>).clear() }
        val journal = CompilerObjectJournal.checkpoint(listOf(scores))
        assertTrue(journal.elements.any { it === selected })
        assertFailsWith<UnsupportedOperationException> { (journal.elements as MutableList<Any>).clear() }
    }

    @Test fun objectJournalCanApplyAndUndoAnInitiallyUnboundMember() = isolated {
        val scores = fresh()
        val before = CompilerObjectJournal.checkpoint(listOf(scores))
        val selected = selector("selected")
        receiver(scores).selector = selected
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            uninitialized(scores)
            after.restore()
            assertSame(selected, receiver(scores).selector)
        }
        assertTrue(after.elements.any { it === scores.type })
        assertTrue(after.elements.any { it === selected })
    }

    @Test fun replacedTypeOwnerIsAnEnrolledAdapterRatherThanAComputedTypeLookup() = isolated {
        val first = fresh()
        val second = fresh()
        val third = fresh()
        val secondType = second.type
        val thirdType = third.type
        first.type = secondType
        val before = CompilerObjectJournal.checkpoint(listOf(first))
        first.type = thirdType
        second.identifier = "detached_second"
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(secondType, first.type)
            assertEquals("scores", second.identifier)
            after.restore()
            assertSame(thirdType, first.type)
            assertEquals("detached_second", second.identifier)
        }
        assertSame(second, MCFPPEntityType.scoresTypeOwner(secondType))
        assertSame(third, MCFPPEntityType.scoresTypeOwner(thirdType))
        listOf(second, third, secondType, thirdType).forEach { root -> assertTrue(after.elements.any { it === root }) }
    }

    @Test fun detachedSelectorsKeepTheirActualStoredProgramsInEveryGeneration() = isolated {
        val scores = fresh()
        val first = selector("first")
        val second = selector("second")
        val third = selector("third")
        val original = program(first)
        receiver(scores).selector = first
        val before = CompilerObjectJournal.checkpoint(listOf(scores))
        receiver(scores).selector = second
        tagged(first, "detached_first")
        tagged(second, "selected_second")
        val firstAfter = program(first)
        val secondAfter = program(second)
        assertNotEquals(original, firstAfter)
        val after = before.captureCurrent()
        receiver(scores).selector = third
        tagged(first, "older_first")
        tagged(second, "detached_second")
        tagged(third, "selected_third")
        val firstLatest = program(first)
        val secondLatest = program(second)
        val thirdLatest = program(third)
        assertNotEquals(firstAfter, firstLatest)
        assertNotEquals(secondAfter, secondLatest)
        assertTrue(firstLatest.contains("detached_first") && firstLatest.contains("older_first"))
        assertTrue(secondLatest.contains("selected_second") && secondLatest.contains("detached_second"))
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(first, receiver(scores).selector)
            assertEquals(original, program(first))
            after.restore()
            assertSame(second, receiver(scores).selector)
            assertEquals(firstAfter, program(first))
            assertEquals(secondAfter, program(second))
            latest.restore()
            assertSame(third, receiver(scores).selector)
            assertEquals(firstLatest, program(first))
            assertEquals(secondLatest, program(second))
            assertEquals(thirdLatest, program(third))
        }
        listOf(first, second, third).forEach { root -> assertTrue(latest.elements.any { it === root }) }
    }

    @Test fun failedPreparationRestoresTheReceiverAndTheActualSelectorStore() = isolated {
        val scores = fresh()
        val first = selector("first")
        val second = selector("second")
        receiver(scores).selector = first
        val original = program(first)
        val secondOriginal = program(second)
        val before = CompilerObjectJournal.checkpoint(listOf(scores, second))
        val failure = assertFailsWith<IllegalArgumentException> {
            before.prepare {
                tagged(first, "failed_write")
                tagged(second, "failed_second")
                receiver(scores).selector = second
                throw IllegalArgumentException("abort scores")
            }
        }
        assertEquals("abort scores", failure.message)
        assertSame(first, receiver(scores).selector)
        assertEquals(original, program(first))
        assertEquals(secondOriginal, program(second))
    }

    @Test fun applyingAnAfterStateDoesNotReexecuteItsHostAction() = isolated {
        val scores = fresh()
        val first = selector("first")
        val second = selector("second")
        receiver(scores).selector = first
        val original = program(first)
        val secondOriginal = program(second)
        var calls = 0
        val prepared = CompilerObjectJournal.checkpoint(listOf(scores, second)).prepare {
            calls++
            tagged(first, "prepared_first")
            tagged(second, "prepared_second")
            receiver(scores).selector = second
        }
        assertEquals(1, calls)
        assertSame(first, receiver(scores).selector)
        assertEquals(original, program(first))
        repeat(3) {
            prepared.apply()
            assertSame(second, receiver(scores).selector)
            assertTrue(program(first).contains("prepared_first"))
            assertTrue(program(second).contains("prepared_second"))
            prepared.rollback()
            assertSame(first, receiver(scores).selector)
            assertEquals(original, program(first))
            assertEquals(secondOriginal, program(second))
        }
        assertEquals(1, calls)
    }

    @Test fun realSelectorMemberAndIndexAccessKeepTheSelectedCaller() = isolated {
        val selected = selector("selected")
        val (member, accessible) = selected.getMemberVar("scores", Member.AccessModifier.PUBLIC)
        assertTrue(accessible)
        val scores = assertIs<NBTDictionary>(member)
        assertTrue(MCFPPEntityType.isOwnedScores(scores))
        assertSame(selected, receiver(scores).selector)
        val indexed = scores.getByIndex(MCString(StringTag("points")))
        assertSame(selected, indexed.caller)
        assertSame(scores, MCFPPEntityType.scoresTypeOwner(scores.type))
        CompilerObjectJournal.checkpoint(listOf(scores, indexed)).restore()
    }

    @Test fun actualAllEntityDeclarationPublishesTheSameOwnedScoresAdapter() = isolated {
        val selected = selector("selected")
        val address = assertIs<top.mcfpp.core.lang.entity.EntityDataAddress>(selected.getMemberVar("dat", Member.AccessModifier.PUBLIC).first)
        kotlin.test.assertNotSame(selected, address.target)
        assertEquals(StorageAccess.snapshot(selected), StorageAccess.snapshot(address.target))
        assertEquals(selected.entityFacts, assertIs<SelectorVar>(address.target).entityFacts)
        val declaration = assertIs<MCFPPDataTemplateType>(address.type).template
        val data = DataTemplateObject(declaration, "data")
        assertTrue(SelectorVar.isOwnedDeclaration(declaration))
        val scores = assertIs<NBTDictionary>(declaration.scope.getVar("scores"))
        assertSame(MCFPPEntityType.data.scope.getVar("scores"), scores)
        assertTrue(MCFPPEntityType.isOwnedScores(scores))
        val before = CompilerObjectJournal.checkpoint(listOf(data, scores))
        assertNull(data.journalInstanceField(), "Capturing the declaration member must not materialize an object cache")
        receiver(scores).selector = selected
        val after = before.captureCurrent()
        before.restore()
        after.restore()
        assertSame(selected, receiver(scores).selector)
        assertTrue(after.elements.any { it === scores.type })
        assertNull(data.journalInstanceField())
    }

    @Test fun unknownAdapterAndTypeShapesAreRejectedBeforeHostGettersOrHashing() = isolated {
        val unknown = UnknownScores()
        unknown.callbacks = 0
        assertFalse(MCFPPEntityType.isOwnedScores(unknown))
        assertFailsWith<IllegalStateException> { MCFPPEntityType.checkpointScores(unknown) }
        assertFailsWith<IllegalStateException> { CompilerObjectJournal.checkpoint(listOf(unknown)) }
        assertEquals(0, unknown.callbacks)
        val type = UnknownType()
        repeat(3) { assertNull(MCFPPEntityType.scoresTypeOwner(type)) }
        assertEquals(0, type.callbacks)
        assertNull(MCFPPEntityType.scoresTypeOwner(MCFPPBaseType.Int))
    }

    @Test fun anUnknownReceiverIsAnOpaqueRawRootAndIsPreflightedByComposition() = isolated {
        val scores = fresh()
        val unknown = UnknownSelector()
        unknown.callbacks = 0
        receiver(scores).selector = unknown
        val raw = MCFPPEntityType.checkpointScores(scores)
        raw.restore()
        raw.captureCurrent()
        assertTrue(raw.elements.any { it === unknown })
        assertEquals(0, unknown.callbacks)
        assertFailsWith<IllegalStateException> { CompilerObjectJournal.checkpoint(listOf(scores)) }
        assertEquals(0, unknown.callbacks)
    }

    private class UnknownScores :
        NBTDictionary("unknown"),
        SelectorVar.SelectorParamMap {
        var callbacks = 0
        override var type: MCFPPType
            get() {
                callbacks++
                error("Unknown scores type getter")
            }
            set(@Suppress("UNUSED_PARAMETER") value) {
                callbacks++
            }
        override var nbtPath: NBTPath
            get() {
                callbacks++
                error("Unknown scores path getter")
            }
            set(@Suppress("UNUSED_PARAMETER") value) {
                callbacks++
            }
        override var selector: SelectorVar
            get() {
                callbacks++
                error("Unknown scores receiver getter")
            }
            set(@Suppress("UNUSED_PARAMETER") value) {
                callbacks++
            }
        override fun hashCode(): Int {
            callbacks++
            error("Unknown scores hash")
        }
    }

    private class UnknownType : MCFPPType() {
        var callbacks = 0
        override val typeId: TypeId get() {
            callbacks++
            error("Unknown type identity")
        }
        override val objectData: top.mcfpp.model.compound.CompoundData
            get() {
                callbacks++
                error("Unknown type declaration")
            }
    }

    private class UnknownSelector : SelectorVar("unknown") {
        var callbacks = 0
        override var type: MCFPPType
            get() {
                callbacks++
                error("Unknown receiver type getter")
            }
            set(@Suppress("UNUSED_PARAMETER") value) {
                callbacks++
            }
        override fun hashCode(): Int {
            callbacks++
            error("Unknown receiver hash")
        }
    }
}
