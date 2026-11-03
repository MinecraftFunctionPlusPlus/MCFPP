package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.IScope
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPGenericObjectDataTemplateType
import top.mcfpp.type.MCFPPObjectDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.type.MCFPPTypeDataTemplateType
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniTypePrivateStateTest {
    private inline fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        try {
            Project.init()
            Function.currFunction = Function("type_checkpoint", "journal.type", null)
            action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
        }
    }

    private fun parser(text: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun alias() = MCFPPTypeAliasType(parser("TrackedType").type())
    private fun model(name: String) = DataTemplate(name, "journal.type").apply { declarationFile = null }

    /** Scope callbacks are used only by explicit resolve calls, never by checkpoint operations. */
    private class LookupScope(var target: MCFPPType) : IScopeWithType {
        override var parent = arrayListOf<IScope?>()
        var lookups = 0
        var available = true
        var onContains: (() -> Unit)? = null
        override fun containType(id: String): Boolean {
            lookups++
            onContains?.invoke()
            return available && id == "TrackedType"
        }
        override fun getType(key: String): MCFPPType? {
            lookups++
            return target.takeIf { available && key == "TrackedType" }
        }
        override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean = error("Unexpected registration")
        override fun removeType(id: String): MCFPPType? = error("Unexpected removal")
        override fun forEachType(action: (MCFPPType) -> Any?) = error("Unexpected enumeration")
        override val allTypes: Collection<MCFPPType> get() = error("Unexpected enumeration")
    }

    private class ResolvingGeneric :
        GenericDataTemplate(
            mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}")))).templateBody(),
            "Tracked",
            "journal.type"
        ) {
        var calls = 0
        lateinit var source: MCFPPGenericDataTemplateType
        var duringResolution: MCFPPDataTemplateType.PrivateStateCheckpoint? = null
        val result = CompiledGenericDataTemplate("Tracked_0", "journal.type", this, emptyList(), emptyList())
        init {
            declarationFile = null
            result.declarationFile = null
        }
        override fun compile(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate {
            calls++
            duringResolution = source.checkpointPrivateState()
            return result
        }
    }

    private fun generic(): ResolvingGeneric {
        val namespace = Namespace("journal.type")
        GlobalScope.localNamespaces["journal.type"] = namespace
        return ResolvingGeneric().also {
            namespace.scope.template["Tracked"] = it
            it.source = MCFPPGenericDataTemplateType(
                it,
                arrayListOf(),
                TypeId.Specialized(TypeId.Declaration("template", "journal.type", "Tracked"), emptyList())
            )
        }
    }

    @Test fun ordinaryTemplateTargetsRestoreByIdentityAndElementsAreImmutable() = isolated {
        val original = model("First")
        val replacement = model("Second")
        val type = MCFPPDataTemplateType(original, arrayListOf())
        val before = type.checkpointPrivateState()
        type.template = replacement
        val after = before.captureCurrent()
        assertSame(original, after.elements[0])
        assertSame(replacement, after.elements[1])
        assertFailsWith<UnsupportedOperationException> { (after.elements as MutableList<*>).clear() }
        repeat(3) {
            before.restore()
            assertSame(original, type.template)
            after.restore()
            assertSame(replacement, type.template)
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun capturingAnUnresolvedGenericDoesNotCompileButItsPublicGetterStillResolves() = isolated {
        val generic = generic()
        val type = generic.source
        val before = type.checkpointPrivateState()
        val unchanged = before.captureCurrent()
        repeat(2) {
            before.restore()
            unchanged.restore()
        }
        assertSame(generic, unchanged.elements.single())
        assertEquals(0, generic.calls)
        assertSame(generic.result, type.template)
        assertEquals(1, generic.calls)
        val after = before.captureCurrent()
        assertTrue(after.elements.any { it === generic })
        assertTrue(after.elements.any { it === generic.result })
        repeat(2) {
            after.restore()
            assertSame(generic.result, type.template)
        }
        assertEquals(1, generic.calls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun aCheckpointTakenInsideGenericResolutionRestoresTheReentrancyGuard() = isolated {
        val generic = generic()
        val type = generic.source
        val before = type.checkpointPrivateState()
        assertSame(generic.result, type.template)
        val during = requireNotNull(generic.duringResolution)
        val after = before.captureCurrent()
        repeat(2) {
            during.restore()
            // The raw target is again unresolved, but the saved active guard prevents reentry.
            assertSame(generic, type.template)
            assertEquals(1, generic.calls)
            after.restore()
            assertSame(generic.result, type.template)
        }
        before.restore()
        assertSame(generic.result, type.template)
        assertEquals(2, generic.calls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun successiveTemplateReplacementsRetainDetachedRootsWithoutReadingTheirMethods() = isolated {
        class HostTarget : DataTemplate("opaque", "journal.type") {
            var callbacks = 0
            override fun getType(): MCFPPDataTemplateType {
                callbacks++
                error("Unexpected target getter")
            }
            override fun hashCode(): Int {
                callbacks++
                error("Unexpected target hash")
            }
        }
        val first = HostTarget()
        val middle = HostTarget()
        val latest = HostTarget()
        val type = MCFPPDataTemplateType(first, arrayListOf())
        val before = type.checkpointPrivateState()
        type.template = middle
        val after = before.captureCurrent()
        type.template = latest
        val last = after.captureCurrent()
        assertEquals(3, last.elements.size)
        assertSame(first, last.elements[0])
        assertSame(middle, last.elements[1])
        assertSame(latest, last.elements[2])
        repeat(2) {
            before.restore()
            assertSame(first, type.template)
            after.restore()
            assertSame(middle, type.template)
            last.restore()
            assertSame(latest, type.template)
        }
        assertEquals(0, first.callbacks + middle.callbacks + latest.callbacks)
    }

    @Test fun allKnownTemplateTypeFamiliesCaptureTheirRawTargets() = isolated {
        val ordinary = model("Ordinary")
        val objectTarget = ObjectDataTemplate("Object", "journal.type").apply { declarationFile = null }
        val typeTarget = TypeDataTemplate(MCFPPBaseType.Int, "Typed", "journal.type").apply { declarationFile = null }
        val identity = TypeId.Specialized(TypeId.Declaration("template", "journal.type", "Raw"), emptyList())
        val types = listOf(
            MCFPPDataTemplateType(ordinary, arrayListOf()) to ordinary,
            MCFPPGenericDataTemplateType(ordinary, arrayListOf(), identity) to ordinary,
            MCFPPObjectDataTemplateType(objectTarget, arrayListOf()) to objectTarget,
            MCFPPGenericObjectDataTemplateType(objectTarget, arrayListOf(), identity) to objectTarget,
            MCFPPTypeDataTemplateType(typeTarget) to typeTarget
        )
        for ((type, original) in types) {
            val before = type.checkpointPrivateState()
            type.template = model("Changed")
            val after = before.captureCurrent()
            before.restore()
            assertSame(original, before.elements.single())
            assertTrue(after.elements.any { it === original })
            // Capture again through the raw hook; inspecting generic public getters could resolve.
            assertSame(original, type.checkpointPrivateState().elements.single())
        }
    }

    @Test fun unknownTemplateTypeSubclassesAreRejectedBeforeAnyOverridableGetter() = isolated {
        class HostType : MCFPPDataTemplateType(model("Target"), arrayListOf()) {
            var callbacks = 0
            override val typeId: TypeId get() {
                callbacks++
                error("Unexpected type getter")
            }
            override fun tryResolve() {
                callbacks++
                error("Unexpected resolution")
            }
        }
        val type = HostType()
        val failure = assertFailsWith<IllegalStateException> { type.checkpointPrivateState() }
        assertTrue(failure.message!!.contains("Template type checkpoint cannot capture"))
        assertEquals(0, type.callbacks)
    }

    @Test fun anUnresolvedAliasCanBeCapturedWithoutAnyScopeAndLaterResolvedNormally() = isolated {
        val type = alias()
        val before = type.checkpointPrivateState()
        assertTrue(before.elements.isEmpty())
        before.captureCurrent().restore()
        assertNull(type.cachedTarget)
        val scope = LookupScope(MCFPPBaseType.Int)
        assertSame(MCFPPBaseType.Int, type.resolve(scope))
        assertTrue(scope.lookups > 0)
        val after = before.captureCurrent()
        scope.lookups = 0
        repeat(2) {
            before.restore()
            assertNull(type.cachedTarget)
            after.restore()
            assertSame(MCFPPBaseType.Int, type.resolve(scope))
        }
        assertSame(MCFPPBaseType.Int, after.elements.single())
        assertEquals(0, scope.lookups)
        assertFailsWith<UnsupportedOperationException> { (after.elements as MutableList<*>).clear() }
        assertEquals(0, Project.errorCount)
    }

    @Test fun aliasFailureStateCanBeReappliedAndUndoAllowsARealRetry() = isolated {
        val type = alias()
        val before = type.checkpointPrivateState()
        val scope = LookupScope(MCFPPBaseType.Int).apply { available = false }
        scope.onContains = {
            scope.onContains = null
            assertNull(type.resolve(scope)) // Real alias reentry marks the declaration failed.
        }
        assertNull(type.resolve(scope))
        assertEquals(1, Project.errorCount)
        val failed = before.captureCurrent()
        scope.available = true
        scope.lookups = 0
        repeat(2) {
            failed.restore()
            assertNull(type.resolve(scope))
            assertEquals(0, scope.lookups)
            before.restore()
            assertSame(MCFPPBaseType.Int, type.resolve(scope))
            assertTrue(scope.lookups > 0)
            scope.lookups = 0
        }
        assertEquals(1, Project.errorCount)
    }

    @Test fun aliasResolutionInProgressCanBeCapturedAndRestoredWithoutCallingTheResolver() = isolated {
        val type = alias()
        val before = type.checkpointPrivateState()
        val scope = LookupScope(MCFPPBaseType.Int)
        var during: MCFPPTypeAliasType.PrivateStateCheckpoint? = null
        scope.onContains = {
            during = type.checkpointPrivateState()
            scope.onContains = null
        }
        assertSame(MCFPPBaseType.Int, type.resolve(scope))
        val after = before.captureCurrent()
        val active = requireNotNull(during)
        scope.lookups = 0
        active.restore()
        assertNull(type.cachedTarget)
        assertNull(type.resolve(scope)) // The restored active flag detects recursive resolution.
        assertEquals(1, Project.errorCount)
        assertEquals(0, scope.lookups)
        after.restore()
        assertSame(MCFPPBaseType.Int, type.resolve(scope))
        assertEquals(0, scope.lookups)
        before.restore()
        assertSame(MCFPPBaseType.Int, type.resolve(scope))
        assertTrue(scope.lookups > 0)
    }

    @Test fun aliasCaptureCurrentRetainsEveryDetachedTargetAndUsesIdentityInsteadOfTypeHash() = isolated {
        class HostTarget : MCFPPType() {
            var callbacks = 0
            override val typeId: TypeId get() {
                callbacks++
                error("Unexpected type identity")
            }
        }
        val first = HostTarget()
        val middle = HostTarget()
        val latest = HostTarget()
        val scope = LookupScope(first)
        val type = alias()
        val unresolved = type.checkpointPrivateState()
        assertSame(first, type.resolve(scope))
        val before = unresolved.captureCurrent()
        unresolved.restore()
        scope.target = middle
        assertSame(middle, type.resolve(scope))
        val after = before.captureCurrent()
        unresolved.restore()
        scope.target = latest
        assertSame(latest, type.resolve(scope))
        val last = after.captureCurrent()
        scope.lookups = 0
        assertEquals(3, last.elements.size)
        assertSame(first, last.elements[0])
        assertSame(middle, last.elements[1])
        assertSame(latest, last.elements[2])
        repeat(2) {
            before.restore()
            assertSame(first, type.resolve(scope))
            after.restore()
            assertSame(middle, type.resolve(scope))
            last.restore()
            assertSame(latest, type.resolve(scope))
        }
        assertEquals(0, scope.lookups)
        assertEquals(0, first.callbacks + middle.callbacks + latest.callbacks)
    }
}
