package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniCompletionDomainJournalTest {
    private val source = SourceSpan("completion-domain.mcfpp", 0, 1, 1, 1, 1, 2)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun body() = parser("{}").let { p -> p.curlBlock().also { assertEquals(0, p.numberOfSyntaxErrors) } }

    private class Fixture(
        val pendingFile: MCFPPFile,
        val readyFile: MCFPPFile,
        val callerFile: MCFPPFile,
        val owner: DataTemplate,
        val pending: Function,
        val ready: Function,
        val caller: Function,
        val receiver: MCFPPListType,
        val imported: NamespaceScope,
        val work: SignaturePreparationResult.NeedsCompletion,
        val explicit: PreparedExpressionFact?
    ) {
        fun capabilities() = work.staged.domainStamps().map { it.forCompletion() }
    }

    private fun fixture(explicitType: Boolean = false, configure: (NamespaceScope) -> Unit = {}): Fixture {
        val pendingFile = MCFPPFile(Namespace("completion.pending"))
        val readyFile = MCFPPFile(Namespace("completion.ready"))
        val callerFile = MCFPPFile(Namespace("completion.caller"))
        val imported = NamespaceScope("completion.imported").also(configure)
        readyFile.field.importField["private"] = imported
        val owner = DataTemplate("Record", pendingFile.namespace.identifier).apply {
            declarationFile = pendingFile
            pendingDeclarationAnnotations.add(DataOnly())
        }
        fun call(name: String, file: MCFPPFile): Function {
            val result = if (explicitType) GenericFunction(name, file.namespace.identifier, body())
            else Function(name, file.namespace.identifier, null)
            result.declarationFile = file
            if (result is GenericFunction) result.readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", result))
            return result
        }
        val pending = call("pending", pendingFile).apply {
            normalParams.add(FunctionParam(MCFPPDataTemplateType(owner, arrayListOf()), "record", this))
        }
        val ready = call("ready", readyFile).apply { normalParams.add(FunctionParam(MCFPPBaseType.Int, "n", this)) }
        val caller = Function("caller", callerFile.namespace.identifier, null).apply { declarationFile = callerFile }
        val receiver = MCFPPListType(MCFPPDictType(MCFPPBaseType.Int))
        val explicit = if (explicitType) {
            val p = parser("list<int>")
            val expression = p.expression()
            assertEquals(0, p.numberOfSyntaxErrors)
            assertEquals(Token.EOF, p.currentToken.type)
            PreparedExpressionFact.readonly(DeclarationLexicalSnapshot.capture(readyFile, readyFile.namespace.identifier), expression)
        } else null
        val supplied = listOfNotNull(explicit)
        assertIs<SignaturePreparationResult.Ready>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(ready), supplied, source)
            )
        )
        val result = SignaturePreparationSession().prepare(
            SignaturePreparationRequest(
                listOf(pending, ready),
                supplied,
                source,
                mapOf("IndependentReceiver" to receiver),
                caller
            )
        )
        val work = assertIs<SignaturePreparationResult.NeedsCompletion>(
            result,
            (result as? SignaturePreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
        assertEquals(2, work.staged.declarations.size)
        assertSame(pending, work.requests.single().function)
        assertSame(owner, assertIs<CompletionTarget.Declaration>(work.requests.single().target).template)
        assertTrue(work.staged.headers.isEmpty())
        assertFalse(work.staged.consumed)
        listOf(pendingFile, readyFile, callerFile).forEach { file ->
            assertTrue(Project.journalState().sourceFiles.none { it === file })
            assertTrue(Project.trees.keys.none { it === file })
            assertTrue(
                listOf(
                    GlobalScope.localNamespaces,
                    GlobalScope.importedLibNamespaces,
                    GlobalScope.libNamespaces,
                    GlobalScope.stdNamespaces
                ).all { table -> table.values.none { it === file.namespace } }
            )
        }
        assertEquals(0, Project.errorCount)
        return Fixture(pendingFile, readyFile, callerFile, owner, pending, ready, caller, receiver, imported, work, explicit)
    }

    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val function = Function.currFunction
        val nullFunction = Function.nullFunction
        val extraFunction = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            DataTemplate.currTemplate = null
            Function.currFunction = Function("ambient", "completion.ambient", null).apply { declarationFile = null }
            Function.forcedField = null
            action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.nullFunction = nullFunction
            Function.extraFunction = extraFunction
            Function.currFunction = function
            Function.forcedField = forced
            DataTemplate.currTemplate = template
        }
    }

    private fun Fixture.innerReceiver() = assertIs<MCFPPDictType>(receiver.generic.single())
    private fun stale(stamp: DeclarationDomainStamp) = assertFailsWith<PreparationFactFailure> { stamp.requireCurrent() }

    @Test fun mixedRealPreparationEnrollsTheReadyCandidateCallerIndependentTypeAndTheirFiles() = isolated {
        val f = fixture()
        val before = CompilerStateJournal.checkpointDomains(f.capabilities())
        val originalReadyNamespace = f.readyFile.namespace
        val failure = IllegalArgumentException("abort whole domain")
        assertSame(
            failure,
            assertFailsWith<IllegalArgumentException> {
                before.prepare {
                    f.ready.identifier = "changed_ready"
                    f.caller.identifier = "changed_caller"
                    f.owner.isFinal = true
                    f.innerReceiver().generic[0] = MCFPPBaseType.Bool
                    f.readyFile.namespace = Namespace("completion.replaced")
                    f.readyFile.syntaxError = true
                    f.readyFile.unsolvedImports["changed"] = "*"
                    f.callerFile.topFunction.commands.add(Command("say caller"))
                    originalReadyNamespace.scope.vars["added"] = MCInt(7, "added")
                    f.imported.vars["added"] = MCInt(9, "added")
                    LogProcessor.error("whole domain failure")
                    throw failure
                }
            }
        )
        assertEquals("ready", f.ready.identifier)
        assertEquals("caller", f.caller.identifier)
        assertFalse(f.owner.isFinal)
        assertSame(MCFPPBaseType.Int, f.innerReceiver().generic.single())
        assertSame(originalReadyNamespace, f.readyFile.namespace)
        assertFalse(f.readyFile.syntaxError)
        assertTrue(f.readyFile.unsolvedImports.isEmpty())
        assertTrue(f.callerFile.topFunction.commands.isEmpty())
        assertTrue(originalReadyNamespace.scope.vars.isEmpty())
        assertTrue(f.imported.vars.isEmpty())
        assertEquals(0, Project.errorCount)
        f.work.staged.domainStamps().forEach { it.requireCurrent() }
    }

    @Test fun refreshedStampChecksTheSameFullOriginalDomainAndKeepsTheOldVersionIndependent() = isolated {
        val f = fixture()
        val before = CompilerStateJournal.checkpointDomains(f.capabilities())
        val original = f.work.staged.domainStamps().first()
        val capability = original.forCompletion()
        f.ready.identifier = "ready_after"
        f.innerReceiver().generic[0] = MCFPPBaseType.Bool
        val refreshed = capability.refresh()
        stale(original)
        refreshed.requireCurrent()
        f.caller.identifier = "caller_after"
        stale(refreshed)
        val latest = capability.refresh()
        latest.requireCurrent()
        before.restore()
        original.requireCurrent()
        stale(refreshed)
        stale(latest)
        assertEquals("ready", f.ready.identifier)
        assertEquals("caller", f.caller.identifier)
    }

    @Test fun successfulPrepareAppliesAllDomainStateOnceAndReturnsAUsableFullRevision() = isolated {
        val f = fixture()
        val capabilities = f.capabilities()
        val before = CompilerStateJournal.checkpointDomains(capabilities)
        var runs = 0
        lateinit var revision: List<DeclarationDomainStamp>
        fun requireRevision(phase: String) {
            try {
                revision.forEach { it.requireCurrent() }
            } catch (failure: PreparationFactFailure) {
                throw AssertionError("$phase: ${failure.message}", failure)
            }
        }
        val commit = before.prepare {
            runs++
            f.ready.identifier = "published"
            f.innerReceiver().generic[0] = MCFPPBaseType.Bool
            f.readyFile.syntaxError = true
            f.readyFile.unsolvedImports["complete"] = "*"
            f.readyFile.topFunction.commands.add(Command("say complete"))
            revision = capabilities.map { it.refresh() }
            requireRevision("immediately after refresh")
        }
        assertEquals(1, runs)
        assertEquals("ready", f.ready.identifier)
        assertFalse(f.readyFile.syntaxError)
        revision.forEach(::stale)
        repeat(2) {
            commit.apply()
            assertEquals(1, runs)
            assertEquals("published", f.ready.identifier)
            requireRevision("immediately after apply")
            assertTrue(f.readyFile.syntaxError)
            assertEquals("*", f.readyFile.unsolvedImports["complete"])
            assertEquals("say complete", f.readyFile.topFunction.commands.single().toString())
            requireRevision("after observing restored commands")
            f.work.staged.domainStamps().forEach(::stale)
            commit.rollback()
            assertEquals("ready", f.ready.identifier)
            assertFalse(f.readyFile.syntaxError)
            assertTrue(f.readyFile.topFunction.commands.isEmpty())
            f.work.staged.domainStamps().forEach { it.requireCurrent() }
        }
    }

    @Test fun captureCurrentRetainsChangedAndThenDetachedReadyFilesAcrossThreeGenerations() = isolated {
        val f = fixture()
        val original = f.readyFile
        val originalNamespace = original.namespace
        val later = MCFPPFile(Namespace("completion.later"))
        val before = CompilerStateJournal.checkpointDomains(f.capabilities())
        f.ready.declarationFile = later
        original.syntaxError = true
        original.unsolvedImports["old.after"] = "*"
        originalNamespace.scope.vars["detached"] = MCInt(3, "detached")
        later.topFunction.commands.add(Command("say later"))
        val after = before.captureCurrent()
        f.ready.declarationFile = null
        original.syntaxError = false
        original.unsolvedImports.clear()
        originalNamespace.scope.vars.clear()
        original.topFunction.identifier = "old_final"
        later.syntaxError = true
        later.topFunction.commands.clear()
        later.unsolvedImports["last"] = "Member"
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(original, f.ready.declarationFile)
            assertFalse(original.syntaxError)
            assertTrue(original.unsolvedImports.isEmpty())
            assertTrue(originalNamespace.scope.vars.isEmpty())
            after.restore()
            assertSame(later, f.ready.declarationFile)
            assertTrue(original.syntaxError)
            assertEquals("*", original.unsolvedImports["old.after"])
            assertTrue(originalNamespace.scope.vars.containsKey("detached"))
            assertEquals("say later", later.topFunction.commands.single().toString())
            assertFalse(later.syntaxError)
            latest.restore()
            assertNull(f.ready.declarationFile)
            assertEquals("old_final", original.topFunction.identifier)
            assertTrue(original.unsolvedImports.isEmpty())
            assertTrue(originalNamespace.scope.vars.isEmpty())
            assertTrue(later.syntaxError)
            assertTrue(later.topFunction.commands.isEmpty())
            assertEquals("Member", later.unsolvedImports["last"])
        }
        before.restore()
    }

    @Test fun explicitReadonlyDescriptorRootsAreEnrolledWithoutDependingOnTheIncompleteRequest() = isolated {
        val f = fixture(explicitType = true)
        val actual = assertNotNull(f.explicit).bound.descriptors.values.filterIsInstance<MCFPPListType>().single()
        assertTrue(f.ready.normalParams.none { it.type === actual })
        val capabilities = f.capabilities()
        val before = CompilerStateJournal.checkpointDomains(capabilities)
        actual.generic[0] = MCFPPBaseType.Bool
        val refreshed = capabilities.map { it.refresh() }
        // The batch also includes narrower request stamps. The complete aggregate must reject
        // this independent input drift, even when a request-only component is still current.
        assertFailsWith<PreparationFactFailure> { f.work.staged.adoptForCompletion() }
        assertFalse(f.work.staged.consumed)
        assertFailsWith<PreparationFactFailure> { CompilerStateJournal.checkpointDomains(capabilities) }
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(MCFPPBaseType.Int, actual.generic.single())
            f.work.staged.domainStamps().forEach { it.requireCurrent() }
            after.restore()
            assertSame(MCFPPBaseType.Bool, actual.generic.single())
            refreshed.forEach { it.requireCurrent() }
            actual.generic[0] = MCFPPBaseType.Float
            try {
                assertFailsWith<PreparationFactFailure> { refreshed.forEach { it.requireCurrent() } }
            } finally {
                actual.generic[0] = MCFPPBaseType.Bool
            }
            refreshed.forEach { it.requireCurrent() }
        }
        before.restore()
    }

    @Test fun scopeMapsRehashOnlyAfterTheirNestedGenericTypesHaveBeenRestored() = isolated {
        val keyType = MCFPPListType(MCFPPDictType(MCFPPBaseType.Int))
        val key = "Box" to listOf<MCFPPType>(keyType)
        val prototype = GenericDataTemplate(parser("{}").templateBody(), "Box", "completion.imported").apply {
            declarationFile = null
            readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        }
        val f = fixture(configure = { it.genericTemplate[key] = prototype })
        val before = CompilerStateJournal.checkpointDomains(f.capabilities())
        val inner = assertIs<MCFPPDictType>(keyType.generic.single())
        inner.generic[0] = MCFPPBaseType.Bool
        val after = before.captureCurrent()
        fun lookup(type: MCFPPType) = f.imported.genericTemplate["Box" to listOf<MCFPPType>(MCFPPListType(MCFPPDictType(type)))]
        repeat(2) {
            before.restore()
            assertSame(prototype, lookup(MCFPPBaseType.Int))
            assertNull(lookup(MCFPPBaseType.Bool))
            after.restore()
            assertSame(prototype, lookup(MCFPPBaseType.Bool))
            assertNull(lookup(MCFPPBaseType.Int))
        }
        before.restore()
        assertSame(keyType, key.second.single())
    }

    @Test fun additionalUnpublishedCanonicalModelsJoinTheSameWholeDomainSavepoint() = isolated {
        val f = fixture()
        val prototype = GenericDataTemplate(parser("{}").templateBody(), "Unpublished", "completion.staged").apply {
            declarationFile = null
            readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        }
        val p = parser("int")
        val expr = p.expression()
        assertEquals(0, p.numberOfSyntaxErrors)
        val fact = PreparedExpressionFact.readonly(DeclarationLexicalSnapshot.capture(f.readyFile, f.readyFile.namespace.identifier), expr)
        val reservation = SignaturePreparationSession().stageHeader(prototype, listOf(fact), source)
        val model = reservation.model
        val before = CompilerStateJournal.checkpointDomains(f.capabilities(), listOf(prototype, model))
        val commit = before.prepare {
            model.isFinal = true
            model.scope.putVar("generated", MCInt(6, "generated"), true)
            model.declarationFile = f.readyFile
            prototype.index += 4
            f.ready.identifier = "promoted"
        }
        assertFalse(model.isFinal)
        assertTrue(model.scope.allVars.isEmpty())
        assertEquals(0, prototype.index)
        repeat(2) {
            commit.apply()
            assertTrue(model.isFinal)
            assertEquals(6, assertIs<MCInt>(model.scope.getVar("generated")).value)
            assertSame(f.readyFile, model.declarationFile)
            assertEquals(4, prototype.index)
            assertEquals("promoted", f.ready.identifier)
            commit.rollback()
            assertFalse(model.isFinal)
            assertTrue(model.scope.allVars.isEmpty())
            assertEquals(0, prototype.index)
            assertEquals("ready", f.ready.identifier)
        }
        assertFalse(reservation.isPublished)
    }

    @Test fun oldAdditionalRootRoleGateIsStillEnforcedWhenDomainsAreSupplied() = isolated {
        val f = fixture()
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpointDomains(f.capabilities(), listOf(MCInt(2))) }
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint(listOf(MCInt(2))) }
        assertEquals(0, Project.errorCount)
    }

    @Test fun domainListsAndErasedDomainRolesAreRejectedBeforeHostCallbacks() = isolated {
        var callbacks = 0
        val unknown = object : ArrayList<DeclarationDomainStamp.CompletionCapability>() {
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override fun iterator(): MutableIterator<DeclarationDomainStamp.CompletionCapability> {
                callbacks++
                return super.iterator()
            }
        }
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpointDomains(unknown) }
        val host = object {
            override fun hashCode(): Int {
                callbacks++
                return 0
            }
            override fun toString(): String {
                callbacks++
                return "host"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val erased = arrayListOf(host) as List<DeclarationDomainStamp.CompletionCapability>
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpointDomains(erased) }
        assertEquals(0, callbacks)
    }

    @Test fun aChangedIndependentTypeRejectsEnrollmentBeforeAnyUnknownGetter() = isolated {
        val f = fixture()
        val capabilities = f.capabilities()
        var callbacks = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                callbacks++
                error("host type identity")
            }
        }
        val inner = f.innerReceiver()
        val original = inner.generic.single()
        inner.generic[0] = unknown
        try {
            assertFailsWith<PreparationFactFailure> { CompilerStateJournal.checkpointDomains(capabilities) }
            assertEquals(0, callbacks)
            assertEquals(0, Project.errorCount)
        } finally {
            inner.generic[0] = original
        }
    }

    @Test fun sameRootRefreshAlsoPreflightsNewUnknownPayloadsWithoutCallbacks() = isolated {
        val f = fixture()
        val capability = f.capabilities().first()
        var callbacks = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                callbacks++
                error("host type identity")
            }
        }
        val inner = f.innerReceiver()
        val original = inner.generic.single()
        inner.generic[0] = unknown
        try {
            assertFailsWith<PreparationFactFailure> { capability.refresh() }
            assertEquals(0, callbacks)
        } finally {
            inner.generic[0] = original
        }
        capability.requireCurrent()
    }

    @Test fun alreadyEnrolledLostFilesPreflightNewUnknownCommandsBeforeAdapterGetters() = isolated {
        val f = fixture()
        val before = CompilerStateJournal.checkpointDomains(f.capabilities())
        var callbacks = 0
        val unknown = object : MCInt("host") {
            override var type: MCFPPType
                get() {
                    callbacks++
                    error("host adapter getter")
                }
                set(value) {}
        }
        f.ready.declarationFile = null
        f.readyFile.topFunction.commands.add(Command("say").buildMacro(unknown))
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, callbacks)
        before.restore()
        assertSame(f.readyFile, f.ready.declarationFile)
        assertTrue(f.readyFile.topFunction.commands.isEmpty())
    }

    @Test fun allOriginalCapabilitiesAreCheckedBeforeTakingASavepointOrExecutingAnAction() = isolated {
        val f = fixture()
        val capabilities = f.capabilities()
        f.ready.identifier = "stale"
        var runs = 0
        assertFailsWith<PreparationFactFailure> { CompilerStateJournal.checkpointDomains(capabilities).prepare { runs++ } }
        assertEquals(0, runs)
        assertEquals("stale", f.ready.identifier)
        assertEquals(0, Project.errorCount)
    }

    @Test fun originalFormalAndTypeRootsRemainInTheRevisionAfterBeingRemovedFromTheFunction() = isolated {
        val f = fixture()
        val capabilities = f.capabilities()
        val before = CompilerStateJournal.checkpointDomains(capabilities)
        val formal = f.ready.normalParams.single()
        f.ready.normalParams.clear()
        // This is the Ready declaration's own lexical stamp, which also keeps its original
        // formal as an explicit root after the mutable normalParams relationship changes.
        val refreshed = capabilities[1].refresh()
        refreshed.requireCurrent()
        formal.identifier = "lost_formal"
        stale(refreshed)
        before.restore()
        assertSame(formal, f.ready.normalParams.single())
        assertEquals("n", formal.identifier)
        capabilities.forEach { it.requireCurrent() }
    }
}
