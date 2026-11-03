package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Member
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test

class MniDeclarationDomainFactsTest {
    private val site = SourceSpan("domain-facts.mcfpp", 1, 9, 1, 2, 1, 10)
    private fun parser(text: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun model(name: String) = DataTemplate(name, "facts.test").apply { declarationFile = null }
    private fun prototype() = GenericDataTemplate(parser("{}").templateBody(), "Box", "facts.test").apply {
        declarationFile = null
        readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
    }
    private fun fact(text: String) = PreparedExpressionFact.readonly(
        DeclarationLexicalSnapshot.capture(null, "facts.test"),
        parser(text).expression()
    )
    private fun field(owner: DataTemplate): MCInt = MCInt("value").also {
        it.declaredParentTemplate = owner
        owner.scope.putVar("value", it)
        owner.scope.putProperty("value", Property.buildSimpleProperty(it))
    }
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val base = DataTemplate.baseDataTemplate.checkpointDeclarationState()
        val caller = Function.currFunction
        val template = DataTemplate.currTemplate
        try {
            Project.init()
            action()
        } finally {
            base.restore()
            namespaces.restore()
            project.restore()
            Function.currFunction = caller
            DataTemplate.currTemplate = template
        }
    }
    private fun scalar(facts: DeclarationPreparationFacts, name: String) = assertIs<RawPreparationFact.Scalar>(facts.values.getValue(name)).value
    private fun identities(value: RawPreparationFact): List<Any?> = when (value) {
        is RawPreparationFact.Identity -> listOf(value.value)
        is RawPreparationFact.Scalar -> emptyList()
        is RawPreparationFact.Sequence -> value.values.flatMap(::identities)
        is RawPreparationFact.Unordered -> value.values.flatMap(::identities)
    }

    @Test fun unchangedCapturesCompareByFactsWithoutCheckpointWrapperIdentities() = isolated {
        val target = model("Target")
        val first = target.preparationFacts()
        val second = target.preparationFacts()
        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertSame(target, first.owner.value)
    }

    @Test fun delegatedTemplateTypeChangesInvalidateTheDomainAndJournalRestoresBothVersions() = isolated {
        val target = TypeDataTemplate(MCFPPBaseType.Int, "Time", "facts.test").apply { declarationFile = null }
        assertSame(target, target.preparationFacts().owner.value)
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        val stamp = DeclarationDomainStamp.captureRoots(listOf(target), site)
        stamp.requireCurrent()
        target.typeAs = MCFPPBaseType.Bool
        val after = before.captureCurrent()
        assertEquals(PreparationFactCode.UNPREPARED_TYPE,
            assertFailsWith<PreparationFactFailure> { stamp.requireCurrent() }.code)
        before.restore()
        assertSame(MCFPPBaseType.Int, target.typeAs)
        stamp.requireCurrent()
        after.restore()
        assertSame(MCFPPBaseType.Bool, target.typeAs)
        assertFailsWith<PreparationFactFailure> { stamp.requireCurrent() }
    }

    @Test fun twoRealCompletionRequestsWithTheSameOwnerAndTypeIdentityAreDifferentVersions() = isolated {
        val target = model("Target")
        val identity = target.inheritanceIdentity
        Project.collectingTemplateSignatures {
            Project.registerTemplateContract(target)
            val first = target.preparationFacts()
            Project.registerTemplateContract(target)
            val second = target.preparationFacts()
            assertEquals(identity, target.inheritanceIdentity)
            assertEquals("PENDING", scalar(first, "completion.phase"))
            assertEquals("PENDING", scalar(second, "completion.phase"))
            assertNotEquals(first, second)
            assertNotEquals(scalar(first, "completion.generation"), scalar(second, "completion.generation"))
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun anActualFailedStageKeepsItsPoisonFactWhenCountersAreClearedAndCanBeRolledBack() = isolated {
        val target = model("Target")
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        val clean = target.preparationFacts()
        target.declarationCompletionStage { LogProcessor.error("intentional domain-facts failure") }
        val failed = target.preparationFacts()
        assertEquals("FAILED", scalar(failed, "completion.phase"))
        assertEquals(true, scalar(failed, "completion.poisoned"))
        Project.errorCount = 0
        target.requestDeclarationCompletion()
        assertEquals(true, scalar(target.preparationFacts(), "completion.poisoned"))
        before.restore()
        assertEquals(clean, target.preparationFacts())
        target.declarationCompletionStage { }
        assertFalse(target.hasDeclarationCompletionFailure)
        assertNotEquals(failed, target.preparationFacts())
    }

    private class Marker : Annotation("Marker", "facts.test") {
        var applications = 0
        override fun forFunction(function: Function) {
            applications++
        }
        override fun forField(field: Var<*>) {
            applications++
        }
        override fun forDataTemplate(data: DataTemplate) {
            applications++
        }
        override fun equals(other: Any?): Boolean = error("Annotation equals must not run")
        override fun hashCode(): Int = error("Annotation hashCode must not run")
    }

    @Test fun capturingComparingAndHashingPendingEffectsNeverInvokesTheAnnotation() = isolated {
        val target = model("Target")
        val marker = Marker()
        target.pendingDeclarationAnnotations.add(marker)
        val before = target.preparationFacts()
        val oldHash = before.hashCode()
        assertEquals(before, target.preparationFacts())
        assertEquals(0, marker.applications)
        Project.registerTemplateContract(target)
        assertEquals(0, marker.applications)
        Project.completeTemplateDeclarations()
        assertEquals(1, marker.applications)
        assertTrue(target.pendingDeclarationAnnotations.isEmpty())
        assertNotEquals(before, target.preparationFacts())
        assertEquals(oldHash, before.hashCode())
        Project.completeTemplateDeclarations()
        assertEquals(1, marker.applications)
        assertTrue(target.pendingDeclarationAnnotations.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun actualFlatteningChangesPrivateRequirementsAndLayoutUnderTheSameIdentity() = isolated {
        val parent = model("Parent")
        field(parent)
        val child = model("Child").also { it.extends(parent) }
        val identity = child.inheritanceIdentity
        val before = child.preparationFacts()
        child.flatExtends()
        val after = child.preparationFacts()
        assertEquals(identity, child.inheritanceIdentity)
        assertNotEquals(before.values.getValue("relations"), after.values.getValue("relations"))
        assertSame(parent, child.scope.fieldOrigins.getValue("value").single().owner)
        assertNotNull(child.scope.getVar("value"))
        child.flatExtends()
        assertEquals(after, child.preparationFacts())
    }

    private fun restoredTarget(): DataTemplate {
        val parent = model("Parent")
        field(parent)
        val source = model("Source").also {
            it.extends(parent)
            it.flatExtends()
        }
        (source.scope.getVar("value") as MCInt).isDataOnly = true
        return model("Target").also {
            it.extends(parent)
            it.restoreInheritedFields(source.completedInheritedFields())
        }
    }

    @Test fun restoredEffectsAndAppliedMarkersFollowRealDeepCheckpointRestoration() = isolated {
        val target = restoredTarget()
        val beforeFacts = target.preparationFacts()
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        target.flatExtends()
        assertTrue((target.scope.getVar("value") as MCInt).isDataOnly)
        val inherited = target.scope.getVar("value")
        val afterFacts = target.preparationFacts()
        val after = before.captureCurrent()
        assertNotEquals(beforeFacts, afterFacts)
        repeat(2) {
            before.restore()
            assertEquals(beforeFacts, target.preparationFacts())
            assertNull(target.scope.getVar("value"))
            after.restore()
            assertEquals(afterFacts, target.preparationFacts())
            assertSame(inherited, target.scope.getVar("value"))
            target.flatExtends()
            assertEquals(afterFacts, target.preparationFacts())
        }
    }

    @Test fun privateEffectPropertyMetadataChangesWithoutReplacingTheOwnerOrAnyRoot() = isolated {
        val target = restoredTarget()
        val before = target.preparationFacts()
        val property = identities(before.values.getValue("relations")).filterIsInstance<Property>().single()
        val oldHash = before.hashCode()
        property.accessModifier = Member.AccessModifier.PRIVATE
        val after = target.preparationFacts()
        assertNotEquals(before, after)
        assertEquals(oldHash, before.hashCode())
        assertSame(property, identities(after.values.getValue("relations")).filterIsInstance<Property>().single())
    }

    @Test fun readonlyMetadataChangesAreVisibleWithTheSameOwnerAndArity() = isolated {
        val target = prototype()
        val initial = target.preparationFacts()
        val parameter = target.readOnlyParams.single()
        parameter.identifier = "U"
        val renamed = target.preparationFacts()
        parameter.typeIdentifier = "int"
        val respelled = target.preparationFacts()
        parameter.type = MCFPPBaseType.Int
        val retyped = target.preparationFacts()
        assertNotEquals(initial, renamed)
        assertNotEquals(renamed, respelled)
        assertNotEquals(respelled, retyped)
        assertEquals(1, target.readOnlyParams.size)
        assertSame(target, retyped.owner.value)
    }

    @Test fun realGenericHeaderPreparationFlagsRestoreWithoutRunningAnnotationsOrBodies() = isolated {
        val target = prototype().apply {
            readOnlyParams.clear()
            readOnlyParams.add(DataTemplateParam("int", "N", MCFPPBaseType.Int))
        }
        val beforeFacts = target.preparationFacts()
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        target.prepareHeader()
        val afterFacts = target.preparationFacts()
        assertEquals(false, scalar(beforeFacts, "generic.headerPrepared"))
        assertEquals(false, scalar(beforeFacts, "generic.varianceChecked"))
        assertEquals(true, scalar(afterFacts, "generic.headerPrepared"))
        assertEquals(true, scalar(afterFacts, "generic.varianceChecked"))
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertEquals(beforeFacts, target.preparationFacts())
            after.restore()
            assertEquals(afterFacts, target.preparationFacts())
        }
        assertTrue(target.compiledTemplates.isEmpty())
        assertEquals(0, target.index)
        assertEquals(0, Project.errorCount)
    }

    @Test fun typedBindingsWithEqualCacheSizesRemainDifferentFactsAndOldSnapshotsStayIndependent() = isolated {
        val target = prototype()
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        val intSession = SignaturePreparationSession()
        val intHeader = intSession.stageHeader(target, listOf(fact("int")), site)
        intSession.stagedHeaderBatch().publishHeaders()
        val intFacts = target.preparationFacts()
        before.restore()
        val boolSession = SignaturePreparationSession()
        val boolHeader = boolSession.stageHeader(target, listOf(fact("bool")), site)
        boolSession.stagedHeaderBatch().publishHeaders()
        val boolFacts = target.preparationFacts()
        assertEquals(1, target.index)
        assertTrue(target.compiledTemplates.isEmpty())
        assertNotEquals(intHeader.identity, boolHeader.identity)
        assertNotEquals(intFacts.values.getValue("generic.headers"), boolFacts.values.getValue("generic.headers"))
        assertNotEquals(intFacts, boolFacts)
        intSession.discard()
        boolSession.discard()
    }

    @Test fun multipleDeepGenerationsRetainDetachedCanonicalShellsAndRestoreTheirPrivateFacts() = isolated {
        val target = prototype()
        val emptyFacts = target.preparationFacts()
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        val session = SignaturePreparationSession()
        val header = session.stageHeader(target, listOf(fact("int")), site)
        session.stagedHeaderBatch().publishHeaders()
        val field = field(header.model)
        val publishedFacts = target.preparationFacts()
        val published = before.captureCurrent()
        before.restore()
        val detached = published.captureCurrent()
        header.model.requestDeclarationCompletion()
        field.isDataOnly = true
        val changed = detached.captureCurrent()
        repeat(2) {
            published.restore()
            assertEquals(publishedFacts, target.preparationFacts())
            assertSame(header, target.preparedHeader(header.key))
            assertSame(field, header.model.scope.getVar("value"))
            assertFalse(field.isDataOnly)
            detached.restore()
            assertEquals(emptyFacts, target.preparationFacts())
            assertNull(target.preparedHeader(header.key))
            assertFalse(field.isDataOnly)
            changed.restore()
            assertEquals(emptyFacts, target.preparationFacts())
            assertNull(target.preparedHeader(header.key))
            assertTrue(field.isDataOnly)
            assertEquals("PENDING", scalar(header.model.preparationFacts(), "completion.phase"))
        }
        session.discard()
    }

    @Test fun rawHeaderPhaseStaysCompletingWhenTheRealBarrierDerivesCompleteAndRestoresBothStates() = isolated {
        val settings = listOf(
            CompileSettings.ignoreStdLib,
            CompileSettings.isDebug,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        val maxWhile = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig(version = "26.3")
            CompileSettings.ignoreStdLib = false
            MCFPPStringTest.readFromString("func main(){}", version = "26.3")
            assertEquals(0, Project.errorCount)
            val target = prototype()
            GlobalScope.getOrCreateNamespace(target.namespace).scope.template[target.identifier] = target
            val session = SignaturePreparationSession()
            val header = session.stageHeader(target, listOf(fact("int")), site)
            session.stagedHeaderBatch().publishHeaders()
            lateinit var pending: DeclarationStateJournal
            lateinit var pendingRaw: RawPreparationFact
            lateinit var pendingHeaderFacts: RawPreparationFact
            fun rawPhase(value: RawPreparationFact) = assertIs<RawPreparationFact.Scalar>(
                assertIs<RawPreparationFact.Sequence>(value).values.first()
            ).value
            Project.collectingTemplateSignatures {
                assertSame(header.model, target.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int, "T"))))
                assertFalse(header.model.hasCompletedDeclaration)
                assertEquals(HeaderPhase.COMPLETING, header.phase)
                pendingRaw = header.rawPreparationFacts()
                assertEquals("COMPLETING", rawPhase(pendingRaw))
                pendingHeaderFacts = target.preparationFacts().values.getValue("generic.headers")
                pending = DeclarationStateJournal.checkpoint(listOf(target))
            }
            assertEquals(0, Project.errorCount)
            assertTrue(header.model.hasCompletedDeclaration)
            assertEquals(HeaderPhase.COMPLETE, header.phase)
            assertEquals(pendingRaw, header.rawPreparationFacts())
            assertEquals(pendingHeaderFacts, target.preparationFacts().values.getValue("generic.headers"))
            assertSame(header.model, target.compiledTemplates[header.key])
            val complete = pending.captureCurrent()
            repeat(2) {
                pending.restore()
                assertEquals(HeaderPhase.COMPLETING, header.phase)
                assertFalse(header.model.hasCompletedDeclaration)
                assertTrue(target.compiledTemplates.isEmpty())
                assertEquals(pendingRaw, header.rawPreparationFacts())
                complete.restore()
                assertEquals(HeaderPhase.COMPLETE, header.phase)
                assertTrue(header.model.hasCompletedDeclaration)
                assertSame(header.model, target.compiledTemplates[header.key])
                assertEquals(pendingRaw, header.rawPreparationFacts())
            }
            session.discard()
        } finally {
            CompileSettings.ignoreStdLib = settings[0]
            CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxWhile
        }
    }

    @Test fun orderedPendingParentPayloadsAreFrozenIncludingAllReadonlyValues() = isolated {
        val target = model("Child")
        val constructor = TypeId.Declaration("template", "facts.test", "Parent")
        fun binding(value: Long) = TypeId.Specialized(
            constructor,
            listOf(
                CompilerValue.Typed(
                    MCFPPBaseType.Int.typeId,
                    CompilerValue.Record(mapOf("key" to CompilerValue.Sequence(listOf(CompilerValue.Integral(value)))))
                )
            )
        )
        target.deferParentTypes(listOf(binding(1), binding(2)))
        val first = target.preparationFacts()
        val hash = first.hashCode()
        target.deferParentTypes(listOf(binding(2), binding(1)))
        val second = target.preparationFacts()
        assertNotEquals(first.values.getValue("relations"), second.values.getValue("relations"))
        assertEquals(hash, first.hashCode())
        assertEquals(2, target.pendingParentTypeIds.size)
    }

    @Test fun everyReturnedCollectionIsUnmodifiableAndOldContentsKeepTheirValue() = isolated {
        val target = model("Target")
        val first = target.preparationFacts()
        val oldHash = first.hashCode()
        assertFailsWith<UnsupportedOperationException> { (first.values as MutableMap<String, RawPreparationFact>).clear() }
        val relation = assertIs<RawPreparationFact.Unordered>(first.values.getValue("relations"))
        assertFailsWith<UnsupportedOperationException> { (relation.values as MutableList<RawPreparationFact>).clear() }
        val row = assertIs<RawPreparationFact.Sequence>(relation.values.first())
        assertFailsWith<UnsupportedOperationException> { (row.values as MutableList<RawPreparationFact>).clear() }
        target.pendingDeclarationAnnotations.add(Marker())
        assertNotEquals(first, target.preparationFacts())
        assertEquals(oldHash, first.hashCode())
    }

    private class UnknownTemplate : DataTemplate("Unknown", "facts.test") {
        var calls = 0
        override fun initialize() {
            calls++
            error("Unexpected initialize")
        }
        override fun getType(): MCFPPDataTemplateType {
            calls++
            error("Unexpected type getter")
        }
        internal override fun checkpointDeclarationState(): DeclarationStateCheckpoint {
            calls++
            error("Unexpected virtual checkpoint")
        }
    }
    @Test fun unknownReceiversAreRejectedBeforeAnyComputedOrVirtualEntry() = isolated {
        val target = UnknownTemplate()
        val failure = assertFailsWith<IllegalStateException> { target.preparationFacts() }
        assertTrue(failure.message.orEmpty().contains("template shape"))
        assertEquals(0, target.calls)
    }

    private class UnknownList : ArrayList<Annotation>() {
        var calls = 0
        override fun iterator(): MutableIterator<Annotation> {
            calls++
            error("Unexpected iterator")
        }
    }
    @Test fun unknownPrivateContainersAreRejectedBeforeTheirIteratorCanExecute() = isolated {
        val target = model("Target")
        val unknown = UnknownList()
        target.pendingFieldAnnotations["value"] = unknown
        val failure = assertFailsWith<IllegalStateException> { target.preparationFacts() }
        assertTrue(failure.message.orEmpty().contains("cannot capture list"))
        assertEquals(0, unknown.calls)
    }

    @Test fun erasedReadonlyPayloadIsRejectedByTheExistingRoleCheckBeforeConsumption() = isolated {
        val target = prototype()
        @Suppress("UNCHECKED_CAST")
        val erased = target.readOnlyParams as MutableList<Any?>
        erased.add(object {
            override fun toString(): String = error("Unexpected rendering")
        })
        val failure = assertFailsWith<IllegalStateException> { target.preparationFacts() }
        assertTrue(failure.message.orEmpty().contains("READONLY_PARAMETERS"))
        assertTrue(target.compiledTemplates.isEmpty())
        assertEquals(0, target.index)
    }
}
