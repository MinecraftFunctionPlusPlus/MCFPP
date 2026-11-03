package top.mcfpp.test

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import top.mcfpp.Project
import top.mcfpp.analysis.DeclarationDomainStamp
import top.mcfpp.analysis.PreparationFactFailure
import top.mcfpp.io.KryoManager
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.FieldInfo
import top.mcfpp.io.info.VarInfo
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.UnsolvedObjectTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPObjectDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.*
import kotlin.test.Test

class MniUnsolvedDeclarationPrivateStateTest {
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        try {
            Project.init()
            Function.currFunction = Function("unsolved_slot", "journal.slot", null)
            action()
            assertEquals(0, Project.errorCount)
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
        }
    }

    private fun info(name: String, objectFlag: Boolean = false, type: MCFPPType? = null) = DataTemplateInfo(
        false, false, "journal.slot", name, emptyList(), null,
        FieldInfo(arrayListOf(), arrayListOf(), arrayListOf(), emptyMap()),
        emptyList(), false, objectFlag, type, emptyMap(), null
    )
    private fun unresolved(name: String = "Pending") = UnsolvedTemplate(name, "journal.slot", false, false)
    private fun unresolvedObject(name: String = "Pending") = UnsolvedObjectTemplate(name, "journal.slot", false, false)
    private fun uninitialized(read: () -> DataTemplateInfo) {
        val failure = assertFailsWith<UninitializedPropertyAccessException> { read() }
        assertEquals("lateinit property info has not been initialized", failure.message)
    }

    @Test fun ordinaryRawSlotRestoresUninitializedAndInitializedStatesRepeatedly() = isolated {
        val pending = unresolved()
        uninitialized { pending.info }
        pending.preparationFacts()
        val unloaded = DeclarationDomainStamp.captureRoots(listOf(pending), SourceSpan.unknown())
        unloaded.requireCurrent()
        val before = pending.checkpointUnsolvedState()
        assertTrue(before.elements.isEmpty())
        val input = info("First")
        pending.info = input
        assertFailsWith<PreparationFactFailure> { unloaded.requireCurrent() }
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            uninitialized { pending.info }
            unloaded.requireCurrent()
            after.restore()
            assertSame(input, pending.info)
        }
        before.restore()
        uninitialized { pending.info }
    }

    @Test fun objectRawSlotRestoresUninitializedAndInitializedStatesRepeatedly() = isolated {
        val pending = unresolvedObject()
        uninitialized { pending.info }
        val before = pending.checkpointUnsolvedState()
        assertTrue(before.elements.isEmpty())
        val input = info("FirstObject", objectFlag = true)
        pending.info = input
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            uninitialized { pending.info }
            after.restore()
            assertSame(input, pending.info)
        }
        before.restore()
        uninitialized { pending.info }
    }

    @Test fun initializedOrdinaryConstructorAndNestedReplacementsKeepOriginalIdentity() = isolated {
        val first = info("First")
        val second = info("Second")
        val pending = UnsolvedTemplate(first)
        assertSame(first, pending.info)
        val before = pending.checkpointUnsolvedState()
        pending.info = second
        val after = before.captureCurrent()
        pending.info = info("Third")
        after.restore()
        assertSame(second, pending.info)
        before.restore()
        assertSame(first, pending.info)
        after.restore()
        assertSame(second, pending.info)
        before.restore()
        assertSame(first, pending.info)
    }

    @Test fun initializedObjectConstructorAndNestedReplacementsKeepOriginalIdentity() = isolated {
        val first = info("First", objectFlag = true)
        val second = info("Second", objectFlag = true)
        val pending = UnsolvedObjectTemplate(first)
        val before = pending.checkpointUnsolvedState()
        pending.info = second
        val after = before.captureCurrent()
        pending.info = info("Third", objectFlag = true)
        after.restore()
        assertSame(second, pending.info)
        before.restore()
        assertSame(first, pending.info)
        after.restore()
        assertSame(second, pending.info)
        before.restore()
        assertSame(first, pending.info)
    }

    @Test fun multipleGenerationsRetainDetachedInputsByIdentityIncludingEqualInputs() = isolated {
        val pending = unresolved()
        val first = info("Equal")
        val equalButDistinct = info("Equal")
        val third = info("Third")
        assertNotSame(first, equalButDistinct)
        val empty = pending.checkpointUnsolvedState()
        pending.info = first
        val one = empty.captureCurrent()
        pending.info = equalButDistinct
        val two = one.captureCurrent()
        pending.info = third
        val three = two.captureCurrent()
        assertEquals(3, three.elements.size)
        assertSame(first, three.elements[0])
        assertSame(equalButDistinct, three.elements[1])
        assertSame(third, three.elements[2])
        first.identifier = "detached input mutation"
        third.identifier = "current input mutation"
        val current = three.captureCurrent()
        assertSame(first, current.elements[0])
        assertSame(third, current.elements[2])
        one.restore()
        assertSame(first, pending.info)
        assertEquals("detached input mutation", pending.info.identifier) // Input internals are deliberately opaque.
        empty.restore()
        uninitialized { pending.info }
        current.restore()
        assertSame(third, pending.info)
        assertEquals("current input mutation", pending.info.identifier)
        val detachedAgain = current.captureCurrent()
        assertEquals(3, detachedAgain.elements.size)
        assertSame(equalButDistinct, detachedAgain.elements[1])
    }

    @Test fun objectCaptureRetainsAllDetachedRootsAndPublishesDefensiveReadonlyElements() = isolated {
        val pending = unresolvedObject()
        val before = pending.checkpointUnsolvedState()
        val first = info("First", true)
        val second = info("Second", true)
        pending.info = first
        val one = before.captureCurrent()
        pending.info = second
        val two = one.captureCurrent()
        assertEquals(1, one.elements.size)
        assertSame(first, one.elements.single())
        assertEquals(2, two.elements.size)
        assertSame(first, two.elements[0])
        assertSame(second, two.elements[1])
        assertFailsWith<UnsupportedOperationException> { (two.elements as MutableList<*>).clear() }
        before.restore()
        uninitialized { pending.info }
        val emptyCurrent = two.captureCurrent()
        assertEquals(2, emptyCurrent.elements.size)
        emptyCurrent.restore()
        uninitialized { pending.info }
        two.restore()
        assertSame(second, pending.info)
        before.restore()
        uninitialized { pending.info }
    }

    @Test fun checkpointNeverResolvesHashesOrTraversesOpaqueLoadingInputs() = isolated {
        var callbacks = 0
        val hostType = object : MCFPPType() {
            override val typeId: TypeId get() {
                callbacks++
                return TypeId.Opaque(91)
            }
            override fun tryResolve() {
                callbacks++
            }
        }
        val first = info("Opaque", type = hostType)
        first.field.vars = object : ArrayList<VarInfo>() {
            override fun iterator(): MutableIterator<VarInfo> {
                callbacks++
                return super.iterator()
            }
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override fun hashCode(): Int {
                callbacks++
                return super.hashCode()
            }
        }
        val objectInput = info("OpaqueObject", true, hostType)
        val pending = UnsolvedTemplate(first)
        val objectPending = UnsolvedObjectTemplate(objectInput)
        pending.preparationFacts()
        objectPending.preparationFacts()
        val domain = DeclarationDomainStamp.captureRoots(listOf(pending, objectPending), SourceSpan.unknown())
        domain.requireCurrent()
        val declarationBefore = DeclarationStateJournal.checkpoint(listOf(pending, objectPending))
        val before = pending.checkpointUnsolvedState()
        val objectBefore = objectPending.checkpointUnsolvedState()
        pending.info = objectInput
        objectPending.info = first
        assertFailsWith<PreparationFactFailure> { domain.requireCurrent() }
        val declarationAfter = declarationBefore.captureCurrent()
        val after = before.captureCurrent()
        val objectAfter = objectBefore.captureCurrent()
        repeat(3) {
            before.restore()
            objectBefore.restore()
            after.restore()
            objectAfter.restore()
        }
        assertEquals(0, callbacks)
        assertSame(objectInput, pending.info)
        assertSame(first, objectPending.info)
        assertTrue(after.elements.any { it === first })
        assertTrue(objectAfter.elements.any { it === objectInput })
        declarationBefore.restore()
        domain.requireCurrent()
        declarationAfter.restore()
        assertFailsWith<PreparationFactFailure> { domain.requireCurrent() }
        assertEquals(0, callbacks, "Domain checks and journals must not traverse, hash or load opaque Info inputs")
    }

    @Test fun ordinaryResolveStillPrefersCanonicalDeclarationEvenWithoutLoadingInfo() = isolated {
        val namespace = Namespace("journal.slot")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val canonical = DataTemplate("Canonical", namespace.identifier).apply { declarationFile = null }
        namespace.scope.template[canonical.identifier] = canonical
        val pending = unresolved("Canonical")
        val before = pending.checkpointUnsolvedState()
        assertSame(canonical, pending.resolve())
        pending.info = info("Different")
        assertSame(canonical, pending.resolve())
        before.restore()
        uninitialized { pending.info }
        assertSame(canonical, pending.resolve())
    }

    @Test fun objectResolveStillPrefersCanonicalDeclarationEvenWithoutLoadingInfo() = isolated {
        val namespace = Namespace("journal.slot")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val canonical = ObjectDataTemplate("Canonical", namespace.identifier).apply { declarationFile = null }
        namespace.scope.objects.add(canonical)
        val pending = unresolvedObject("Canonical")
        val before = pending.checkpointUnsolvedState()
        assertSame(canonical, pending.resolve())
        pending.info = info("Different", true)
        assertSame(canonical, pending.resolve())
        before.restore()
        uninitialized { pending.info }
        assertSame(canonical, pending.resolve())
    }

    @Test fun realTemplateAndObjectTypeSerializersStillInitializeTheirPlaceholderSlots() = isolated {
        val source = DataTemplate("Serialized", "journal.slot").apply {
            declarationFile = null
            isAbstract = true
            isFinal = true
        }
        val bytes = Output(4096, -1).use { output ->
            KryoManager.kryo.writeObject(output, source)
            output.toBytes()
        }
        val restored = Input(bytes).use { KryoManager.kryo.readObject(it, DataTemplate::class.java) }
        val pending = assertIs<UnsolvedTemplate>(restored)
        assertEquals("Serialized", pending.info.identifier)
        assertTrue(pending.info.isAbstract)
        assertTrue(pending.info.isFinal)
        val saved = pending.checkpointUnsolvedState()
        val input = pending.info
        pending.info = info("Replacement")
        saved.restore()
        assertSame(input, pending.info)
        val declaration = pending.resolve()
        assertEquals("Serialized", declaration.identifier)
        assertTrue(declaration.isAbstract)
        assertTrue(declaration.isFinal)

        val objectSource = ObjectDataTemplate("SerializedObject", "journal.slot").apply { declarationFile = null }
        val type = MCFPPObjectDataTemplateType(objectSource, arrayListOf())
        val objectBytes = Output(4096, -1).use { output ->
            KryoManager.kryo.writeObject(output, type)
            output.toBytes()
        }
        val restoredType = Input(objectBytes).use { KryoManager.kryo.readObject(it, MCFPPObjectDataTemplateType::class.java) }
        val objectPending = assertIs<UnsolvedObjectTemplate>(restoredType.template)
        assertEquals("SerializedObject", objectPending.info.identifier)
        assertTrue(objectPending.info.isObject)
        val objectSaved = objectPending.checkpointUnsolvedState()
        val objectInput = objectPending.info
        objectPending.info = info("Other", true)
        objectSaved.restore()
        assertSame(objectInput, objectPending.info)
        assertEquals("SerializedObject", objectPending.resolve().identifier)
    }
}
