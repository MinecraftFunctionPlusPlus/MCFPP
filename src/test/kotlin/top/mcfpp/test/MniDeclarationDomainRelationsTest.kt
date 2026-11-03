package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.IScope
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.type.TypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniDeclarationDomainRelationsTest {
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val function = Function.currFunction
        try {
            Project.init()
            Function.currFunction = Function("relations", "domain.raw", null)
            action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = function
        }
    }

    private fun scope() = CompoundDataScope(arrayListOf(null))
    private fun parser(text: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun alias() = MCFPPTypeAliasType(parser("TrackedType").type())
    private fun model(name: String) = DataTemplate(name, "domain.raw").apply { declarationFile = null }
    private fun rows(facts: RawPreparationFact) = (facts as RawPreparationFact.Unordered).values.map { it as RawPreparationFact.Sequence }
    private fun role(facts: RawPreparationFact, name: String): RawPreparationFact.Sequence = rows(facts).single {
        (it.values[0] as RawPreparationFact.Scalar).value == name
    }
    private fun entries(facts: RawPreparationFact, name: String) = (role(facts, name).values[2] as RawPreparationFact.Unordered).values.map { it as RawPreparationFact.Sequence }

    @Test fun identicalVariableValuesUnderDifferentActualKeysChangeTheFacts() = isolated {
        val scope = scope()
        val value = MCInt(2, "declared")
        scope.putVar("AliasA", value)
        val symbol = value.symbol
        val before = scope.preparationRelations()
        val hash = before.hashCode()
        assertSame(symbol, value.symbol)
        scope.removeVar("AliasA")
        scope.putVar("AliasB", value)
        val rebound = value.symbol
        val after = scope.preparationRelations()
        assertNotEquals(before, after)
        val first = entries(before, "map.VARIABLE").single()
        val second = entries(after, "map.VARIABLE").single()
        assertEquals("AliasA", (first.values[0] as RawPreparationFact.Scalar).value)
        assertEquals("AliasB", (second.values[0] as RawPreparationFact.Scalar).value)
        assertSame(value, (first.values[1] as RawPreparationFact.Identity).value)
        assertSame(value, (second.values[1] as RawPreparationFact.Identity).value)
        assertEquals(symbol?.id, value.symbol?.id)
        assertSame(rebound, value.symbol)
        assertEquals(hash, before.hashCode())
    }

    @Test fun typeAndPropertyAliasesKeepKeysRolesAndOriginalObjectIdentity() = isolated {
        val scope = scope()
        val type = MCFPPBaseType.Int
        val property = Property.buildSimpleProperty(MCInt(3, "field"))
        scope.putType("Same", type)
        scope.putProperty("Same", property)
        val before = scope.preparationRelations()
        assertSame(type, (entries(before, "map.TYPE").single().values[1] as RawPreparationFact.Identity).value)
        assertSame(property, (entries(before, "map.PROPERTY").single().values[1] as RawPreparationFact.Identity).value)
        scope.removeType("Same")
        scope.putType("Different", type)
        val middle = scope.preparationRelations()
        assertNotEquals(before, middle)
        scope.removeProperty("Same")
        scope.putProperty("Different", property)
        assertNotEquals(middle, scope.preparationRelations())
        assertEquals("Same", (entries(before, "map.PROPERTY").single().values[0] as RawPreparationFact.Scalar).value)
        assertEquals(0, Project.errorCount)
    }

    @Test fun factsAreImmutableAndIgnoreMapTraversalOrder() = isolated {
        val scope = scope()
        val first = MCInt(1, "first")
        val second = MCInt(2, "second")
        scope.putVar("Aa", first)
        scope.putVar("BB", second) // Same String hash, different insertion order.
        val before = scope.preparationRelations()
        scope.removeVar("Aa")
        scope.removeVar("BB")
        scope.putVar("BB", second)
        scope.putVar("Aa", first)
        assertEquals(before, scope.preparationRelations())
        assertEquals(before.hashCode(), scope.preparationRelations().hashCode())
        assertFailsWith<UnsupportedOperationException> { ((before as RawPreparationFact.Unordered).values as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (role(before, "map.VARIABLE").values as MutableList<*>).clear() }
    }

    @Test fun currentFactsExcludeDetachedJournalRootsAndRecoverAcrossGenerations() = isolated {
        val scope = scope()
        val first = Function("first", "domain.raw", null)
        val oldFunctions = scope.functions
        oldFunctions["old.alias"] = arrayListOf(first)
        val oldFacts = scope.preparationRelations()
        val before = scope.checkpointRelations()
        val replacement = Function("replacement", "domain.raw", null)
        scope.functions = hashMapOf("new.alias" to arrayListOf(replacement))
        oldFunctions["lost.only"] = arrayListOf(replacement)
        val currentFacts = scope.preparationRelations()
        val after = before.captureCurrent()
        assertEquals(1, entries(currentFacts, "map.OVERLOADS").size)
        assertEquals("new.alias", (entries(currentFacts, "map.OVERLOADS").single().values[0] as RawPreparationFact.Scalar).value)
        oldFunctions.clear()
        assertEquals(currentFacts, scope.preparationRelations())
        repeat(3) {
            before.restore()
            assertEquals(oldFacts, scope.preparationRelations())
            assertSame(oldFunctions, scope.functions)
            after.restore()
            assertEquals(currentFacts, scope.preparationRelations())
            assertTrue(oldFunctions.containsKey("lost.only"))
        }
    }

    @Test fun theSameMapMustValidateEveryRegistrationRole() = isolated {
        val scope = scope()
        val shared = hashMapOf("method" to arrayListOf(Function("method", "domain.raw", null)))
        scope.functions = shared
        @Suppress("UNCHECKED_CAST")
        val erased = shared as HashMap<MCFPPType?, Function>
        scope.operators["+"] = erased
        val failure = assertFailsWith<IllegalStateException> { scope.preparationRelations() }
        assertTrue(failure.message!!.contains("invalid operator key"))
    }

    @Test fun theSameListMustValidateFunctionAndOriginRolesSeparately() = isolated {
        val scope = scope()
        val shared = arrayListOf(Function("method", "domain.raw", null))
        scope.functions["method"] = shared
        @Suppress("UNCHECKED_CAST")
        val erased = shared as List<CompoundDataScope.FieldOrigin>
        scope.fieldOrigins["field"] = erased
        assertTrue(assertFailsWith<IllegalStateException> { scope.preparationRelations() }.message!!.contains("invalid ORIGINS"))
        shared.clear()
        val empty = scope.preparationRelations()
        assertTrue(rows(empty).any { (it.values[0] as RawPreparationFact.Scalar).value == "list.FUNCTIONS" })
        assertTrue(rows(empty).any { (it.values[0] as RawPreparationFact.Scalar).value == "list.ORIGINS" })
    }

    @Test fun unknownContainersAndErasedPayloadsAreRejectedBeforeHostCallbacks() = isolated {
        class HostMap : HashMap<String, ArrayList<Function>>() {
            var calls = 0
            override val entries: MutableSet<MutableMap.MutableEntry<String, ArrayList<Function>>>
                get() {
                    calls++
                    return super.entries
                }
        }
        val scope = scope()
        val hostMap = HostMap()
        scope.functions = hostMap
        assertFailsWith<IllegalStateException> { scope.preparationRelations() }
        assertEquals(0, hostMap.calls)
        class HostList : ArrayList<Function>() {
            var calls = 0
            override fun iterator(): MutableIterator<Function> {
                calls++
                return super.iterator()
            }
            override val size: Int get() {
                calls++
                return super.size
            }
        }
        val hostList = HostList()
        scope.functions = hashMapOf("host" to hostList)
        assertFailsWith<IllegalStateException> { scope.preparationRelations() }
        assertEquals(0, hostList.calls)
        class HostPayload {
            var calls = 0
            override fun hashCode(): Int {
                calls++
                return 1
            }
            override fun toString(): String {
                calls++
                return "host"
            }
        }
        val payload = HostPayload()
        scope.functions = hashMapOf()
        @Suppress("UNCHECKED_CAST")
        val table = scope.functions as MutableMap<Any?, Any?>
        table["wrong"] = payload
        assertFailsWith<IllegalStateException> { scope.preparationRelations() }
        assertEquals(0, payload.calls)
    }

    @Test fun invalidRegistrationKeysAreRejectedWithoutRehashingThem() = isolated {
        class HostKey {
            var calls = 0
            override fun hashCode(): Int {
                calls++
                return 7
            }
        }
        val scope = scope()
        val key = HostKey()
        @Suppress("UNCHECKED_CAST")
        val table = scope.functions as MutableMap<Any?, Any?>
        table[key] = arrayListOf(Function("f", "domain.raw", null))
        key.calls = 0
        assertFailsWith<IllegalStateException> { scope.preparationRelations() }
        assertEquals(0, key.calls)
    }

    @Test fun unknownOperatorTypesAreRejectedBeforeTheirIdentityGetter() = isolated {
        class HostType : MCFPPType() {
            var calls = 0
            override val typeId: TypeId get() {
                calls++
                return TypeId.Builtin("host")
            }
        }
        val scope = scope()
        val type = HostType()
        scope.operators["+"] = hashMapOf(type to Function("f", "domain.raw", null))
        type.calls = 0
        assertFailsWith<IllegalStateException> { scope.preparationRelations() }
        assertEquals(0, type.calls)
    }

    private class LookupScope(var target: MCFPPType, var available: Boolean = true) : IScopeWithType {
        override var parent = arrayListOf<IScope?>()
        var calls = 0
        var onContains: (() -> Unit)? = null
        override fun containType(id: String): Boolean {
            calls++
            onContains?.invoke()
            return available && id == "TrackedType"
        }
        override fun getType(key: String): MCFPPType? {
            calls++
            return target.takeIf { available && key == "TrackedType" }
        }
        override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean = error("Unexpected registration")
        override fun removeType(id: String): MCFPPType? = error("Unexpected removal")
        override fun forEachType(action: (MCFPPType) -> Any?) = error("Unexpected enumeration")
        override val allTypes: Collection<MCFPPType> get() = error("Unexpected enumeration")
    }

    @Test fun aliasFailureWithNoTargetIsDifferentFromAnUnresolvedAliasAndCanBeRestored() = isolated {
        val alias = alias()
        val scope = LookupScope(MCFPPBaseType.Int, false)
        val original = alias.rawPreparationFacts()
        val before = alias.checkpointPrivateState()
        assertNull(alias.resolve(scope))
        assertEquals(1, Project.errorCount)
        val failed = alias.rawPreparationFacts()
        val failedState = before.captureCurrent()
        assertNotEquals(original, failed)
        scope.calls = 0
        scope.available = true
        repeat(3) {
            failedState.restore()
            assertEquals(failed, alias.rawPreparationFacts())
            assertNull(alias.resolve(scope))
            assertEquals(0, scope.calls)
            before.restore()
            assertEquals(original, alias.rawPreparationFacts())
        }
        assertSame(MCFPPBaseType.Int, alias.resolve(scope))
        assertTrue(scope.calls > 0)
        assertNotEquals(original, alias.rawPreparationFacts())
    }

    @Test fun aliasRawFactsKeepAnActiveGuardAndNeverHashTheirOpaqueTarget() = isolated {
        class HostType : MCFPPType() {
            var calls = 0
            override val typeId: TypeId get() {
                calls++
                error("Unexpected hash/getter")
            }
        }
        val type = HostType()
        val alias = alias()
        val scope = LookupScope(type)
        val before = alias.checkpointPrivateState()
        val initial = alias.rawPreparationFacts()
        var active: MCFPPTypeAliasType.PrivateStateCheckpoint? = null
        var activeFacts: RawPreparationFact? = null
        scope.onContains = {
            active = alias.checkpointPrivateState()
            activeFacts = alias.rawPreparationFacts()
            scope.onContains = null
        }
        assertSame(type, alias.resolve(scope))
        val resolved = alias.rawPreparationFacts()
        val after = before.captureCurrent()
        scope.calls = 0
        assertNotEquals(initial, activeFacts)
        assertNotEquals(activeFacts, resolved)
        repeat(3) {
            requireNotNull(active).restore()
            assertEquals(activeFacts, alias.rawPreparationFacts())
            after.restore()
            assertEquals(resolved, alias.rawPreparationFacts())
            assertEquals(resolved.hashCode(), alias.rawPreparationFacts().hashCode())
            before.restore()
            assertEquals(initial, alias.rawPreparationFacts())
        }
        assertEquals(0, type.calls)
        assertEquals(0, scope.calls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun aliasTargetReplacementFactsKeepOldSnapshotsAndDetachedTargetsIndependent() = isolated {
        val alias = alias()
        val scope = LookupScope(MCFPPBaseType.Int)
        val unresolved = alias.checkpointPrivateState()
        assertSame(MCFPPBaseType.Int, alias.resolve(scope))
        val first = alias.rawPreparationFacts()
        val hash = first.hashCode()
        val before = unresolved.captureCurrent()
        unresolved.restore()
        scope.target = MCFPPBaseType.Bool
        assertSame(MCFPPBaseType.Bool, alias.resolve(scope))
        val second = alias.rawPreparationFacts()
        val after = before.captureCurrent()
        scope.calls = 0
        assertNotEquals(first, second)
        assertEquals(2, after.elements.size)
        assertSame(MCFPPBaseType.Int, after.elements[0])
        assertSame(MCFPPBaseType.Bool, after.elements[1])
        repeat(3) {
            before.restore()
            assertEquals(first, alias.rawPreparationFacts())
            assertEquals(hash, first.hashCode())
            after.restore()
            assertEquals(second, alias.rawPreparationFacts())
        }
        assertEquals(0, scope.calls)
        assertEquals(0, Project.errorCount)
    }

    private class ResolvingGeneric :
        GenericDataTemplate(
            mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}")))).templateBody(),
            "Tracked",
            "domain.raw"
        ) {
        var calls = 0
        lateinit var source: MCFPPGenericDataTemplateType
        var active: MCFPPDataTemplateType.PrivateStateCheckpoint? = null
        var activeFacts: RawPreparationFact? = null
        val result = CompiledGenericDataTemplate("Tracked_0", "domain.raw", this, emptyList(), emptyList())
        init {
            declarationFile = null
            result.declarationFile = null
        }
        override fun compile(readOnlyArgs: List<Var<*>>): CompiledGenericDataTemplate {
            calls++
            active = source.checkpointPrivateState()
            activeFacts = source.rawPreparationFacts()
            return result
        }
    }

    private fun generic(): ResolvingGeneric {
        val namespace = Namespace("domain.raw")
        GlobalScope.localNamespaces["domain.raw"] = namespace
        return ResolvingGeneric().also {
            namespace.scope.template["Tracked"] = it
            it.source = MCFPPGenericDataTemplateType(
                it,
                arrayListOf(),
                TypeId.Specialized(TypeId.Declaration("template", "domain.raw", "Tracked"), emptyList())
            )
        }
    }

    @Test fun knownGenericOperatorFactsDoNotResolveOrMaterializeTheirDeclaration() = isolated {
        val generic = generic()
        val scope = scope()
        val type = generic.source
        scope.operators["+"] = hashMapOf(type to Function("f", "domain.raw", null))
        val checkpoint = type.checkpointPrivateState()
        val raw = type.rawPreparationFacts()
        val facts = scope.preparationRelations()
        repeat(3) {
            assertEquals(facts, scope.preparationRelations())
            assertEquals(facts.hashCode(), scope.preparationRelations().hashCode())
            assertEquals(raw, type.rawPreparationFacts())
            checkpoint.restore()
        }
        assertEquals(0, generic.calls)
        assertEquals(0, Project.errorCount)
        assertSame(generic, type.checkpointPrivateState().elements.single())
        assertSame(generic.result, type.template)
        assertEquals(1, generic.calls)
    }

    @Test fun templateRawTargetsAndTheInProgressGuardFollowTheSamePrivateCheckpoint() = isolated {
        val generic = generic()
        val type = generic.source
        val before = type.checkpointPrivateState()
        val initial = type.rawPreparationFacts()
        assertSame(generic.result, type.template)
        val complete = type.rawPreparationFacts()
        val after = before.captureCurrent()
        val active = requireNotNull(generic.active)
        val activeFacts = requireNotNull(generic.activeFacts)
        assertNotEquals(initial, activeFacts)
        assertNotEquals(complete, activeFacts)
        repeat(3) {
            active.restore()
            assertEquals(activeFacts, type.rawPreparationFacts())
            assertSame(generic, type.template)
            after.restore()
            assertEquals(complete, type.rawPreparationFacts())
            assertSame(generic.result, type.template)
            before.restore()
            assertEquals(initial, type.rawPreparationFacts())
        }
        assertEquals(1, generic.calls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun opaqueTargetReplacementFactsRemainIndependentAndNeverCallHostMethods() = isolated {
        class HostTarget(name: String) : DataTemplate(name, "domain.raw") {
            var calls = 0
            override fun getType(): MCFPPDataTemplateType {
                calls++
                error("Unexpected getType")
            }
            override fun hashCode(): Int {
                calls++
                error("Unexpected host hash")
            }
        }
        val first = HostTarget("first")
        val second = HostTarget("second")
        val last = HostTarget("last")
        val type = MCFPPDataTemplateType(first, arrayListOf())
        val before = type.checkpointPrivateState()
        val one = type.rawPreparationFacts()
        val hash = one.hashCode()
        type.template = second
        val after = before.captureCurrent()
        val two = type.rawPreparationFacts()
        type.template = last
        val latest = after.captureCurrent()
        val three = type.rawPreparationFacts()
        assertNotEquals(one, two)
        assertNotEquals(two, three)
        repeat(3) {
            latest.restore()
            assertEquals(three, type.rawPreparationFacts())
            before.restore()
            assertEquals(one, type.rawPreparationFacts())
            assertEquals(hash, one.hashCode())
            after.restore()
            assertEquals(two, type.rawPreparationFacts())
        }
        assertEquals(3, latest.elements.size)
        assertSame(first, latest.elements[0])
        assertSame(second, latest.elements[1])
        assertSame(last, latest.elements[2])
        assertEquals(0, first.calls + second.calls + last.calls)
        assertFailsWith<UnsupportedOperationException> { ((one as RawPreparationFact.Sequence).values as MutableList<*>).clear() }
    }

    @Test fun unknownTemplateTypeReceiversFailBeforeAnyOverridableGetter() = isolated {
        class HostType : MCFPPDataTemplateType(model("Target"), arrayListOf()) {
            var calls = 0
            override val typeId: TypeId get() {
                calls++
                error("Unexpected getter")
            }
            override fun tryResolve() {
                calls++
                error("Unexpected resolver")
            }
        }
        val type = HostType()
        assertFailsWith<IllegalStateException> { type.rawPreparationFacts() }
        assertEquals(0, type.calls)
    }
}
