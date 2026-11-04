package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.CompletionTarget
import top.mcfpp.analysis.Effect
import top.mcfpp.analysis.EffectAnalysis
import top.mcfpp.analysis.SignaturePreparationRequest
import top.mcfpp.analysis.SignaturePreparationResult
import top.mcfpp.analysis.SignaturePreparationSession
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.MCFPPErrorListener
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.command.CommandList
import top.mcfpp.command.CommentLevel
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import top.mcfpp.util.LogLevel
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test
import org.apache.logging.log4j.core.Logger as CoreLogger
import org.apache.logging.log4j.core.config.Property as AppenderProperty
import top.mcfpp.antlr.mcfppParser as P

class MniCompilerStateJournalTest {
    companion object {
        private var calls = 0
        private var mutation: () -> Unit = {}
        @JvmStatic fun legacy() {
            calls++
            mutation()
        }
    }

    class Processor : TemplateProcessor {
        override fun expand(context: DeclarationContext, template: TemplateView): DeclarationEdits = error("Capturing a registry executed its processor")
    }

    private class Appender :
        AbstractAppender(
            "global-journal",
            null,
            PatternLayout.createDefaultLayout(),
            false,
            AppenderProperty.EMPTY_ARRAY
        ) {
        val events = arrayListOf<LogEvent>()
        override fun append(event: LogEvent) {
            events.add(event.toImmutable())
        }
    }

    private class Probe(val namespace: Namespace, val function: Function, val appender: Appender) {
        val events get() = appender.events
        val messages get() = events.map { it.message.formattedMessage }
        fun file(namespace: Namespace = this.namespace) = MCFPPFile(namespace)
        fun model(name: String) = DataTemplate(name, namespace.identifier).apply { declarationFile = null }
        fun variable(value: Int = 3): MCInt = MCInt(value, "stored").apply {
            bindDeclaration()
            function.scope.vars[identifier] = this
        }
        fun native(): NativeFunction = NativeFunction(
            "mutate",
            namespace.identifier,
            MniCompilerStateJournalTest::class.java.getMethod("legacy")
        ).also { namespace.scope.addFunction(it, true) }
    }

    private fun isolated(action: Probe.() -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val caller = Function.currFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val nullFunction = Function.nullFunction
        val extraFunction = Function.extraFunction
        val logger = LogProcessor.logger
        val logLevel = LogProcessor.level
        val debug = CompileSettings.isDebug
        val ignore = CompileSettings.ignoreStdLib
        val lib = CompileSettings.isLib
        val max = CompileSettings.maxWhileInline
        val print = CompileSettings.printAll
        val fold = CompileSettings.foldIRConstants
        val sink = LogManager.getLogger("compiler.state.journal.tests") as CoreLogger
        val level = sink.level
        val additive = sink.isAdditive
        val appender = Appender().apply { start() }
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            DataTemplate.currTemplate = null
            CompileSettings.isDebug = false
            Project.config.commentLevel = CommentLevel.DEBUG
            sink.level = Level.ALL
            sink.isAdditive = false
            sink.addAppender(appender)
            LogProcessor.logger = sink
            LogProcessor.level = LogLevel.DEBUG
            val namespace = Namespace("journal.fixture").also { GlobalScope.localNamespaces[it.identifier] = it }
            val function = Function("entry", namespace.identifier, null).apply { declarationFile = null }
            Function.currFunction = function
            Function.forcedField = null
            calls = 0
            mutation = {}
            Probe(namespace, function, appender).action()
        } finally {
            namespaces.restore()
            project.restore()
            Function.nullFunction = nullFunction
            Function.extraFunction = extraFunction
            Function.currFunction = caller
            Function.forcedField = forced
            DataTemplate.currTemplate = template
            LogProcessor.logger = logger
            LogProcessor.level = logLevel
            CompileSettings.isDebug = debug
            CompileSettings.ignoreStdLib = ignore
            CompileSettings.isLib = lib
            CompileSettings.maxWhileInline = max
            CompileSettings.printAll = print
            CompileSettings.foldIRConstants = fold
            mutation = {}
            sink.removeAppender(appender)
            appender.stop()
            sink.level = level
            sink.isAdditive = additive
        }
    }

    private fun parse(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun nodes(tree: ParseTree): List<ParseTree> = listOf(tree) +
        (0 until tree.childCount).flatMap { nodes(tree.getChild(it)) }

    private class DetachedCompletion(
        val source: MCFPPFile,
        val owner: DataTemplate,
        val call: Function,
        val work: SignaturePreparationResult.NeedsCompletion
    )

    private fun Probe.detachedCompletion(): DetachedCompletion {
        val detached = Namespace("completion.detached")
        val source = file(detached)
        val owner = DataTemplate("Record", detached.identifier).apply {
            declarationFile = source
            pendingDeclarationAnnotations.add(DataOnly())
        }
        // Already parsed compiler types are a real signature input, independently of lookup
        // through GlobalScope. The original declaration and file stay entirely unpublished.
        val call = Function("target", detached.identifier, null).apply {
            declarationFile = source
            normalParams.add(FunctionParam(MCFPPDataTemplateType(owner, arrayListOf()), "value", this))
        }
        val result = SignaturePreparationSession().prepare(
            SignaturePreparationRequest(
                listOf(call),
                emptyList(),
                SourceSpan.unknown()
            )
        )
        val work = assertIs<SignaturePreparationResult.NeedsCompletion>(
            result,
            (result as? SignaturePreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
        assertSame(owner, assertIs<CompletionTarget.Declaration>(work.requests.single().target).template)
        assertSame(call, work.requests.single().function)
        assertTrue(Project.journalState().sourceFiles.none { it === source })
        assertFalse(Project.trees.keys.any { it === source })
        assertTrue(
            listOf(
                GlobalScope.localNamespaces,
                GlobalScope.importedLibNamespaces,
                GlobalScope.libNamespaces,
                GlobalScope.stdNamespaces
            ).all { table -> table.values.none { it === detached } }
        )
        assertNull(MCFPPFile.currFile)
        assertSame(function, Function.currFunction)
        assertNull(DataTemplate.currTemplate)
        assertEquals(0, Project.errorCount)
        return DetachedCompletion(source, owner, call, work)
    }

    @Test fun realLegacyNativeRunsOnceAndRestoresCommandsAliasesNamesDeclarationsAndLogs() = isolated {
        val value = variable(5)
        val alias = MCInt(value)
        val command = Command("say before", "slot").also { function.commands.add(it) }
        val owner = model("Owner").also { namespace.scope.addTemplate(it.identifier, it, true) }
        val native = native()
        val count = TempPool.varCount
        fun assertProducedValue() {
            val executor = ScoreCommandExecutor(
                listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                    function.commands.map { it.analyze() }
            )
            assertEquals(17, executor.read(value))
            assertEquals(17, executor.read(alias))
            assertTrue(executor.failedScoreOperations.isEmpty())
        }
        mutation = {
            command.replace("slot" to "say prepared")
            StorageAccess.write(alias, MCInt(17))
            owner.isFinal = true
            Project.macroFunction["resource"] = "prepared"
            TempPool.varCount += 7
            LogProcessor.warn("native warning")
        }
        val before = CompilerStateJournal.checkpoint()
        val prepared = before.prepare {
            assertFalse(native.invoke(emptyList(), emptyList(), null).isError)
            assertEquals(0, Project.errorCount)
            assertEquals(1, Project.warningCount)
            assertProducedValue()
            assertTrue(events.isEmpty())
        }
        assertEquals(1, calls)
        assertSame(function, Function.currFunction)
        assertEquals("say before", command.toString())
        assertFalse(owner.isFinal)
        assertEquals(5, assertIs<MCInt>(StorageAccess.read(alias)).value)
        assertEquals(count, TempPool.varCount)
        assertNull(Project.macroFunction["resource"])
        assertEquals(0, Project.warningCount)
        assertTrue(events.isEmpty())
        prepared.apply()
        assertEquals("say prepared", command.toString())
        assertTrue(owner.isFinal)
        assertProducedValue()
        assertEquals("prepared", Project.macroFunction["resource"])
        assertEquals(1, Project.warningCount)
        assertEquals(1, events.size)
        StorageAccess.materialize(value)
        assertProducedValue()
        repeat(2) {
            prepared.rollback()
            prepared.apply()
            assertProducedValue()
        }
        assertEquals(1, calls)
        assertEquals(1, events.size)
        prepared.rollback()
        assertEquals(5, assertIs<MCInt>(StorageAccess.read(alias)).value)
        assertSame(command, function.commands.single())
        assertEquals("say before", command.toString())
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun failedRealNativePreparationRestoresTheOriginalExceptionAndAmbientPointers() = isolated {
        val value = variable(4)
        val owner = model("Owner")
        DataTemplate.currTemplate = owner
        Function.forcedField = function.scope
        val native = native()
        val failure = IllegalArgumentException("abort native preparation")
        mutation = {
            StorageAccess.write(value, MCInt(99))
            owner.isFinal = true
            LogProcessor.error("discarded native error")
        }
        val before = CompilerStateJournal.checkpoint()
        val caught = assertFailsWith<IllegalArgumentException> {
            before.prepare {
                native.invoke(emptyList(), emptyList(), null)
                assertEquals(1, Project.errorCount)
                Function.currFunction = Function("stray", "other", null)
                Function.forcedField = null
                DataTemplate.currTemplate = null
                throw failure
            }
        }
        assertSame(failure, caught)
        assertEquals(1, calls)
        assertSame(function, Function.currFunction)
        assertSame(function.scope, Function.forcedField)
        assertSame(owner, DataTemplate.currTemplate)
        assertFalse(owner.isFinal)
        assertEquals(4, assertIs<MCInt>(StorageAccess.read(value)).value)
        assertTrue(events.isEmpty())
        assertTrue(function.commands.isEmpty())
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun cumulativePreparationRestoresNativeStateAndPublishesOnceAfterRehashing() = isolated {
        val value = variable(5)
        val leaf = model("Leaf")
        val leafType = MCFPPDataTemplateType(leaf, arrayListOf())
        val inner = MCFPPListType(leafType)
        val arguments = arrayListOf<MCFPPType>(inner)
        val generic = GenericDataTemplate(parse("{}").templateBody(), "Box", namespace.identifier).apply { declarationFile = null }
        namespace.scope.genericTemplate["Box" to arguments] = generic
        val native = native()
        mutation = {
            StorageAccess.write(value, MCInt(17))
            inner.generic[0] = MCFPPBaseType.Bool
            Project.macroFunction["cumulative"] = "prepared"
            LogProcessor.warn("cumulative warning")
        }
        lateinit var retained: CompilerEnclosingPreparation
        val commit = CompilerStateJournal.checkpoint().prepareCumulative { enclosing ->
            retained = enclosing
            enclosing.generate { assertFalse(native.invoke(emptyList(), emptyList(), null).isError) }
            assertEquals(1, calls)
            assertTrue(events.isEmpty())
        }
        assertSame(leafType, inner.generic.single())
        assertEquals(5, assertIs<MCInt>(StorageAccess.read(value)).value)
        assertNull(Project.macroFunction["cumulative"])
        assertTrue(events.isEmpty())
        repeat(2) {
            commit.apply()
            assertSame(MCFPPBaseType.Bool, inner.generic.single())
            assertSame(generic, namespace.scope.genericTemplate["Box" to listOf(inner)])
            assertEquals("prepared", Project.macroFunction["cumulative"])
            assertEquals(1, events.size)
            commit.rollback()
            assertSame(leafType, inner.generic.single())
            assertEquals(5, assertIs<MCInt>(StorageAccess.read(value)).value)
        }
        assertEquals(1, calls)
        var executedAfterClose = false
        assertFailsWith<IllegalStateException> { retained.generate { executedAfterClose = true } }
        assertFalse(executedAfterClose)
    }

    @Test fun caughtCumulativeGenerationFailureStillRevokesTheOuterPreparation() = isolated {
        val value = variable(5)
        val failure = IllegalArgumentException("partial cumulative generation")
        val caught = assertFailsWith<IllegalArgumentException> {
            CompilerStateJournal.checkpoint().prepareCumulative { enclosing ->
                assertSame(failure, assertFailsWith<IllegalArgumentException> {
                    enclosing.generate {
                        StorageAccess.write(value, MCInt(91))
                        Project.macroFunction["partial"] = "discarded"
                        LogProcessor.warn("discarded cumulative warning")
                        throw failure
                    }
                })
            }
        }
        assertSame(failure, caught)
        assertEquals(5, assertIs<MCInt>(StorageAccess.read(value)).value)
        assertNull(Project.macroFunction["partial"])
        assertEquals(0, Project.warningCount)
        assertTrue(events.isEmpty())
    }

    @Test fun nestedSuccessfulPreparationPublishesAtApplyPositionExactlyOnce() = isolated {
        val value = variable()
        val owner = model("Owner").also { namespace.scope.addTemplate(it.identifier, it, true) }
        val outer = CompilerStateJournal.checkpoint().prepare {
            LogProcessor.info("outer before")
            val inner = CompilerStateJournal.checkpoint().prepare {
                StorageAccess.write(value, MCInt(7))
                owner.isFinal = true
                LogProcessor.warn("inner warning")
            }
            assertEquals(3, assertIs<MCInt>(StorageAccess.read(value)).value)
            assertFalse(owner.isFinal)
            assertEquals(0, Project.warningCount)
            LogProcessor.info("inner prepared")
            inner.apply()
            inner.apply()
            assertEquals(7, assertIs<MCInt>(StorageAccess.read(value)).value)
            assertTrue(owner.isFinal)
            assertEquals(1, Project.warningCount)
            LogProcessor.info("outer after")
            assertTrue(events.isEmpty())
        }
        assertEquals(3, assertIs<MCInt>(StorageAccess.read(value)).value)
        assertEquals(0, Project.warningCount)
        assertFalse(owner.isFinal)
        assertTrue(events.isEmpty())
        outer.apply()
        outer.apply()
        assertEquals(4, events.size)
        assertEquals("outer before", messages[0])
        assertEquals("inner prepared", messages[1])
        assertTrue(messages[2].contains("inner warning"))
        assertEquals("outer after", messages[3])
        assertTrue(owner.isFinal)
        assertEquals(1, Project.warningCount)
        outer.rollback()
        assertFalse(owner.isFinal)
        assertEquals(0, Project.warningCount)
    }

    @Test fun caughtNestedFailureDoesNotEraseOuterMutationsOrPublishItsDiagnostics() = isolated {
        val value = variable()
        val owner = model("Owner").also { namespace.scope.addTemplate(it.identifier, it, true) }
        val outer = CompilerStateJournal.checkpoint().prepare {
            StorageAccess.write(value, MCInt(5))
            owner.isFinal = true
            LogProcessor.warn("outer survives")
            val caught = assertFailsWith<IllegalStateException> {
                CompilerStateJournal.checkpoint().prepare {
                    StorageAccess.write(value, MCInt(19))
                    owner.isFinal = false
                    LogProcessor.error("inner discarded")
                    error("inner failure")
                }
            }
            assertEquals("inner failure", caught.message)
            assertEquals(5, assertIs<MCInt>(StorageAccess.read(value)).value)
            assertTrue(owner.isFinal)
            assertEquals(0, Project.errorCount)
            assertEquals(1, Project.warningCount)
            assertTrue(events.isEmpty())
        }
        assertEquals(3, assertIs<MCInt>(StorageAccess.read(value)).value)
        outer.apply()
        assertEquals(5, assertIs<MCInt>(StorageAccess.read(value)).value)
        assertEquals(1, events.size)
        assertTrue(messages.single().contains("outer survives"))
        assertEquals(0, Project.errorCount)
        outer.rollback()
    }

    @Test fun outerFailureRevokesAnAppliedInnerStateAndAllNestedLogs() = isolated {
        val value = variable()
        val owner = model("Owner").also { namespace.scope.addTemplate(it.identifier, it, true) }
        val original = function.commands
        assertFailsWith<IllegalArgumentException> {
            CompilerStateJournal.checkpoint().prepare {
                LogProcessor.warn("outer warning")
                val inner = CompilerStateJournal.checkpoint().prepare {
                    StorageAccess.write(value, MCInt(11))
                    owner.isFinal = true
                    function.commands.add(Command("say inner"))
                    LogProcessor.error("inner error")
                }
                inner.apply()
                assertEquals(1, Project.errorCount)
                assertEquals(1, Project.warningCount)
                assertEquals(11, assertIs<MCInt>(StorageAccess.read(value)).value)
                throw IllegalArgumentException("outer failure")
            }
        }
        assertEquals(3, assertIs<MCInt>(StorageAccess.read(value)).value)
        assertFalse(owner.isFinal)
        assertSame(original, function.commands)
        assertTrue(original.isEmpty())
        assertTrue(events.isEmpty())
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
    }

    @Test fun nestedGenericKeysAreRehashedAfterTheirDeepTypeAndDeclarationState() = isolated {
        val leaf = model("Leaf")
        val leafType = MCFPPDataTemplateType(leaf, arrayListOf())
        val inner = MCFPPListType(leafType)
        val outer = MCFPPListType(inner)
        val arguments = arrayListOf<MCFPPType>(outer)
        val generic = GenericDataTemplate(parse("{}").templateBody(), "Box", namespace.identifier).apply { declarationFile = null }
        namespace.scope.genericTemplate["Box" to arguments] = generic
        val owner = model("Operators").also { namespace.scope.addTemplate(it.identifier, it, true) }
        val operator = Function("plus", namespace.identifier, null)
        owner.scope.operators["+"] = hashMapOf(outer to operator)
        val before = CompilerStateJournal.checkpoint()
        val identity = outer.typeId
        leaf.identifier = "Changed"
        inner.generic[0] = MCFPPBaseType.Bool
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            assertEquals(identity, outer.typeId)
            assertSame(leafType, inner.generic.single())
            assertSame(operator, owner.scope.operators.getValue("+")[outer])
            assertSame(generic, namespace.scope.genericTemplate["Box" to listOf(MCFPPListType(MCFPPListType(leafType)))])
            assertSame(arguments, namespace.scope.genericTemplate.keys.single().second)
            after.restore()
            assertSame(MCFPPBaseType.Bool, inner.generic.single())
            assertSame(generic, namespace.scope.genericTemplate["Box" to listOf(outer)])
            assertSame(operator, owner.scope.operators.getValue("+")[outer])
        }
        before.restore()
    }

    @Test fun projectFilesKeepTheirInternalStateAndDetachedFilesAcrossInitAndThreeGenerations() = isolated {
        val first = file()
        val originalImports = first.unsolvedImports
        originalImports["before"] = "Type"
        Project.journalState().sourceFiles.add(first)
        first.topFunction.commands.add(Command("say before"))
        val before = CompilerStateJournal.checkpoint()
        Project.init()
        val secondNamespace = Namespace("after").also { GlobalScope.localNamespaces[it.identifier] = it }
        first.namespace = secondNamespace
        first.syntaxError = true
        originalImports.clear()
        originalImports["after"] = "*"
        first.topFunction.commands.clear()
        first.topFunction.commands.add(Command("say after"))
        val second = MCFPPFile(secondNamespace)
        Project.journalState().sourceFiles.add(second)
        val after = before.captureCurrent()
        Project.journalState().sourceFiles.clear()
        second.syntaxError = true
        second.unsolvedImports["third"] = "Member"
        first.unsolvedImports.clear()
        first.topFunction.identifier = "detached_third"
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(first, Project.journalState().sourceFiles.single())
            assertSame(namespace, first.namespace)
            assertFalse(first.syntaxError)
            assertSame(originalImports, first.unsolvedImports)
            assertEquals(mapOf("before" to "Type"), originalImports)
            assertEquals("say before", first.topFunction.commands.single().toString())
            after.restore()
            assertSame(second, Project.journalState().sourceFiles.single())
            assertSame(secondNamespace, first.namespace)
            assertTrue(first.syntaxError)
            assertEquals(mapOf("after" to "*"), first.unsolvedImports)
            assertEquals("say after", first.topFunction.commands.single().toString())
            assertFalse(second.syntaxError)
            latest.restore()
            assertTrue(Project.journalState().sourceFiles.isEmpty())
            assertTrue(second.syntaxError)
            assertEquals(mapOf("third" to "Member"), second.unsolvedImports)
            assertTrue(first.unsolvedImports.isEmpty())
            assertEquals("detached_third", first.topFunction.identifier)
        }
        before.restore()
    }

    @Test fun declarationFilesAreDiscoveredEvenWhenTheyWereNeverProjectFilesOrCurrentFiles() = isolated {
        val detachedNamespace = Namespace("detached")
        val source = file(detachedNamespace)
        val replacement = file(Namespace("replacement"))
        val owner = model("Owner").also {
            it.declarationFile = source
            namespace.scope.addTemplate(it.identifier, it, true)
        }
        val method = Function("method", namespace.identifier, null).apply { declarationFile = source }
        owner.scope.addFunction(method, true)
        val before = CompilerStateJournal.checkpoint()
        owner.declarationFile = replacement
        method.declarationFile = replacement
        source.syntaxError = true
        source.unsolvedImports["lost"] = "Type"
        source.topFunction.identifier = "lost_top"
        detachedNamespace.scope.vars["added"] = MCInt(6, "added")
        val after = before.captureCurrent()
        source.unsolvedImports["third"] = "Other"
        replacement.syntaxError = true
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(source, owner.declarationFile)
            assertSame(source, method.declarationFile)
            assertFalse(source.syntaxError)
            assertTrue(source.unsolvedImports.isEmpty())
            assertFalse(detachedNamespace.scope.vars.containsKey("added"))
            after.restore()
            assertSame(replacement, owner.declarationFile)
            assertSame(replacement, method.declarationFile)
            assertTrue(source.syntaxError)
            assertEquals(mapOf("lost" to "Type"), source.unsolvedImports)
            assertEquals("lost_top", source.topFunction.identifier)
            assertFalse(replacement.syntaxError)
            assertEquals(6, assertIs<MCInt>(detachedNamespace.scope.vars["added"]).value)
            latest.restore()
            assertTrue(replacement.syntaxError)
            assertEquals("Other", source.unsolvedImports["third"])
        }
        before.restore()
    }

    @Test fun processorSourceFilesRemainEnrolledAfterRequestsAndNamespacesAreRemoved() = isolated {
        val emptyRegistry = namespace.templateProcessors.checkpoint()
        assertTrue(namespace.registerTemplateProcessor("journal.fixture:Mark", Processor::class.java))
        val parser = parse("namespace journal.fixture\n@Mark\ndata Marked{}")
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val source = file()
        val target = model("Marked").apply { declarationFile = null }
        val declaration = nodes(tree).filterIsInstance<P.TemplateDeclarationContext>().single()
        val annotation = nodes(tree).filterIsInstance<P.AnnotationContext>().single()
        val registration = assertNotNull(namespace.templateProcessors.find("journal.fixture:Mark"))
        namespace.templateProcessors.record(
            registration,
            target,
            source,
            declaration,
            annotation,
            listOf(CompilerValue.Typed(TypeId.Builtin("int"), CompilerValue.Integral(1))),
            TemplateProcessorSourceOrder(0, declaration.start.tokenIndex, 0)
        )
        val before = CompilerStateJournal.checkpoint()
        emptyRegistry.restore()
        GlobalScope.localNamespaces.clear()
        source.syntaxError = true
        source.unsolvedImports["later"] = "*"
        source.topFunction.commands.add(Command("say detached"))
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertFalse(source.syntaxError)
            assertTrue(source.unsolvedImports.isEmpty())
            assertTrue(source.topFunction.commands.isEmpty())
            assertEquals(1, namespace.templateProcessors.requests().size)
            after.restore()
            assertTrue(source.syntaxError)
            assertEquals("*", source.unsolvedImports["later"])
            assertEquals("say detached", source.topFunction.commands.single().toString())
            assertTrue(namespace.templateProcessors.requests().isEmpty())
            assertTrue(GlobalScope.localNamespaces.isEmpty())
        }
        before.restore()
        assertEquals(0, Project.errorCount)
    }

    @Test fun fileNamespaceSlotsCanReturnToTheirUninitializedState() = isolated {
        val source = MCFPPFile()
        Project.trees[source] = ParserRuleContext()
        assertFailsWith<UninitializedPropertyAccessException> { source.field.namespaceField }
        val before = CompilerStateJournal.checkpoint()
        source.field.namespaceField = namespace.scope
        source.field.importedNamespaceField["self"] = namespace.scope
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertFailsWith<UninitializedPropertyAccessException> { source.field.namespaceField }
            assertTrue(source.field.importedNamespaceField.isEmpty())
            after.restore()
            assertSame(namespace.scope, source.field.namespaceField)
            assertSame(namespace.scope, source.field.importedNamespaceField["self"])
        }
        before.restore()
    }

    @Test fun parserSyntaxDiagnosticsAreCapturedWithTheirAfterFileStateAndPublishedOnce() = isolated {
        val source = file()
        MCFPPFile.currFile = source
        val before = CompilerStateJournal.checkpoint()
        val prepared = before.prepare {
            val parser = parse("data Broken {")
            parser.removeErrorListeners()
            parser.addErrorListener(MCFPPErrorListener())
            parser.compilationUnit()
            assertEquals(1, parser.numberOfSyntaxErrors)
            assertTrue(source.syntaxError)
            assertEquals(1, Project.errorCount)
            assertTrue(events.isEmpty())
        }
        assertFalse(source.syntaxError)
        assertEquals(0, Project.errorCount)
        assertTrue(events.isEmpty())
        prepared.apply()
        prepared.apply()
        assertTrue(source.syntaxError)
        assertEquals(1, Project.errorCount)
        assertEquals(1, events.size)
        assertEquals(Level.ERROR, events.single().level)
        assertTrue(messages.single().contains("Syntax Error in"))
        assertTrue(messages.single().contains(source.absolutePath))
        prepared.rollback()
        assertFalse(source.syntaxError)
        assertEquals(0, Project.errorCount)
    }

    @Test fun thrownPreparationWithAnActualParserErrorLeavesNoSyntaxFlagCountOrLog() = isolated {
        val source = file()
        MCFPPFile.currFile = source
        val failure = IllegalStateException("syntax preparation rejected")
        val caught = assertFailsWith<IllegalStateException> {
            CompilerStateJournal.checkpoint().prepare {
                val parser = parse("data Broken {")
                parser.removeErrorListeners()
                parser.addErrorListener(MCFPPErrorListener())
                parser.compilationUnit()
                assertEquals(1, Project.errorCount)
                assertTrue(source.syntaxError)
                throw failure
            }
        }
        assertSame(failure, caught)
        assertFalse(source.syntaxError)
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
        assertTrue(events.isEmpty())
        assertSame(function, Function.currFunction)
    }

    @Test fun templateAndCompilerSettingsAreRestoredOnSuccessFailureAndApply() = isolated {
        val owner = model("Ambient")
        DataTemplate.currTemplate = owner
        val settings = listOf(
            CompileSettings.isDebug,
            CompileSettings.ignoreStdLib,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        val loop = CompileSettings.maxWhileInline
        val replacement = model("After")
        val nullFunction = Function.nullFunction
        val extraFunction = Function.extraFunction
        val afterNull = Function("new_null", namespace.identifier, null)
        val afterExtra = Function("new_extra", namespace.identifier, null)
        val before = CompilerStateJournal.checkpoint()
        val prepared = before.prepare {
            DataTemplate.currTemplate = replacement
            owner.isFinal = true
            Function.nullFunction = afterNull
            Function.extraFunction = afterExtra
            CompileSettings.isDebug = !settings[0]
            CompileSettings.ignoreStdLib = !settings[1]
            CompileSettings.isLib = !settings[2]
            CompileSettings.printAll = !settings[3]
            CompileSettings.foldIRConstants = !settings[4]
            CompileSettings.maxWhileInline = loop + 3
        }
        assertSame(owner, DataTemplate.currTemplate)
        assertFalse(owner.isFinal)
        assertSame(nullFunction, Function.nullFunction)
        assertSame(extraFunction, Function.extraFunction)
        assertEquals(loop, CompileSettings.maxWhileInline)
        prepared.apply()
        assertSame(replacement, DataTemplate.currTemplate)
        assertTrue(owner.isFinal)
        assertSame(afterNull, Function.nullFunction)
        assertSame(afterExtra, Function.extraFunction)
        assertEquals(
            settings.map { !it },
            listOf(
                CompileSettings.isDebug,
                CompileSettings.ignoreStdLib,
                CompileSettings.isLib,
                CompileSettings.printAll,
                CompileSettings.foldIRConstants
            )
        )
        prepared.rollback()
        assertFailsWith<IllegalStateException> {
            before.prepare {
                DataTemplate.currTemplate = replacement
                owner.isFinal = true
                CompileSettings.maxWhileInline = 1
                error("abort")
            }
        }
        assertSame(owner, DataTemplate.currTemplate)
        assertFalse(owner.isFinal)
        assertSame(nullFunction, Function.nullFunction)
        assertSame(extraFunction, Function.extraFunction)
        assertEquals(
            settings,
            listOf(
                CompileSettings.isDebug,
                CompileSettings.ignoreStdLib,
                CompileSettings.isLib,
                CompileSettings.printAll,
                CompileSettings.foldIRConstants
            )
        )
        assertEquals(loop, CompileSettings.maxWhileInline)
    }

    @Test fun activeEffectsAreRestoredAlongWithARealExternalWrite() = isolated {
        val value = variable()
        // The value is external to the active body, so a write must enter its actual effect frame.
        function.scope.vars.clear()
        val parent = top.mcfpp.model.scope.FunctionScope(null).apply { vars["external"] = value }
        function.scope = top.mcfpp.model.scope.FunctionScope(parent)
        EffectAnalysis.withActualEffects(function) {
            val before = CompilerStateJournal.checkpoint()
            assertFailsWith<IllegalStateException> {
                before.prepare {
                    StorageAccess.write(value, MCInt(9))
                    error("abort effect")
                }
            }
            assertEquals(3, assertIs<MCInt>(StorageAccess.read(value)).value)
            function.bodyCompiled = true
        }
        assertSame(Effect.Pure, function.runtimeEffect)
    }

    @Test fun unknownAdaptersAddedToDetachedCommandsAreRejectedBeforeTheirTypeGetter() = isolated {
        var getters = 0
        val unknown = object : MCInt("unknown") {
            override var type: MCFPPType
                get() {
                    getters++
                    error("Unknown adapter type getter")
                }
                set(value) {}
        }
        val original = function.commands
        val before = CompilerStateJournal.checkpoint()
        function.commands = CommandList()
        original.add(Command("say").buildMacro(unknown))
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, getters)
        before.restore()
        assertSame(original, function.commands)
        assertTrue(original.isEmpty())
    }

    @Test fun erasedImportKeysAreRejectedWithoutHashingOrFormattingTheHostObject() = isolated {
        var callbacks = 0
        val unknown = object {
            override fun hashCode(): Int {
                callbacks++
                return 17
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
        val source = file()
        Project.trees[source] = ParserRuleContext()
        @Suppress("UNCHECKED_CAST")
        val imports = source.unsolvedImports as MutableMap<Any, Any>
        imports[unknown] = "Type"
        callbacks = 0
        try {
            val rejected = assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint() }
            assertTrue(rejected.message!!.contains("string import names"))
            assertEquals(0, callbacks)
        } finally {
            imports.clear()
        }
    }

    @Test fun actualDetachedTypedRequestEnrollsItsFileAndPrivateNamespaceInputsOnFailure() = isolated {
        val detached = detachedCompletion()
        val source = detached.source
        val originalNamespace = source.namespace
        val originalImports = source.unsolvedImports
        source.unsolvedImports["original.import"] = "Member"
        val originalMacros = originalNamespace.journalMacroDeclarations
        val marker = MacroFunctionInfo(Processor::class.java.name, "expand", emptyList(), "void", "marker", emptyList(), "void")
        val before = CompilerStateJournal.checkpoint(listOf(detached.call, detached.owner))
        val failure = IllegalStateException("abort detached completion")
        val caught = assertFailsWith<IllegalStateException> {
            before.prepare {
                source.syntaxError = true
                source.namespace = Namespace("changed.detached")
                source.unsolvedImports.clear()
                source.unsolvedImports["changed.import"] = "*"
                source.topFunction.commands.add(Command("say detached"))
                originalMacros.add(marker)
                assertTrue(originalNamespace.registerTemplateProcessor("completion.detached:Mark", Processor::class.java))
                originalNamespace.scope.vars["added"] = MCInt(8, "added")
                detached.owner.isFinal = true
                detached.call.identifier = "changed"
                LogProcessor.error("discard detached completion")
                throw failure
            }
        }
        assertSame(failure, caught)
        assertSame(originalNamespace, source.namespace)
        assertSame(originalImports, source.unsolvedImports)
        assertEquals(mapOf("original.import" to "Member"), source.unsolvedImports)
        assertFalse(source.syntaxError)
        assertTrue(source.topFunction.commands.isEmpty())
        assertSame(originalMacros, originalNamespace.journalMacroDeclarations)
        assertTrue(originalMacros.isEmpty())
        assertTrue(originalNamespace.templateProcessors.registrations().isEmpty())
        assertFalse(originalNamespace.scope.vars.containsKey("added"))
        assertFalse(detached.owner.isFinal)
        assertEquals("target", detached.call.identifier)
        assertSame(source, detached.call.declarationFile)
        assertEquals(0, Project.errorCount)
        assertTrue(events.isEmpty())
        assertSame(function, Function.currFunction)
    }

    @Test fun extraRootsCaptureNewDeclarationFilesAndKeepEveryDetachedGeneration() = isolated {
        val detached = detachedCompletion()
        val first = detached.source
        val originalNamespace = first.namespace
        val secondNamespace = Namespace("completion.second")
        val second = file(secondNamespace)
        val roots = arrayListOf<Any>(detached.call, detached.owner)
        val before = CompilerStateJournal.checkpoint(roots)
        roots.clear() // The caller's list is not the journal's enrollment authority.
        detached.call.declarationFile = second
        detached.owner.declarationFile = second
        first.namespace = secondNamespace
        first.syntaxError = true
        first.unsolvedImports["first.after"] = "Member"
        first.topFunction.identifier = "first_after"
        originalNamespace.scope.vars["lost"] = MCInt(7, "lost")
        assertTrue(originalNamespace.registerTemplateProcessor("completion.detached:Mark", Processor::class.java))
        second.topFunction.commands.add(Command("say second"))
        val after = before.captureCurrent()
        detached.call.declarationFile = null
        detached.owner.declarationFile = null
        first.unsolvedImports.clear()
        first.topFunction.identifier = "first_third"
        second.syntaxError = true
        second.unsolvedImports["second.third"] = "*"
        second.topFunction.commands.clear()
        originalNamespace.scope.vars.clear()
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(first, detached.call.declarationFile)
            assertSame(first, detached.owner.declarationFile)
            assertSame(originalNamespace, first.namespace)
            assertFalse(first.syntaxError)
            assertTrue(first.unsolvedImports.isEmpty())
            assertFalse(originalNamespace.scope.vars.containsKey("lost"))
            assertTrue(originalNamespace.templateProcessors.registrations().isEmpty())
            after.restore()
            assertSame(second, detached.call.declarationFile)
            assertSame(second, detached.owner.declarationFile)
            assertSame(secondNamespace, first.namespace)
            assertTrue(first.syntaxError)
            assertEquals("Member", first.unsolvedImports["first.after"])
            assertEquals("first_after", first.topFunction.identifier)
            assertEquals(7, assertIs<MCInt>(originalNamespace.scope.vars["lost"]).value)
            assertEquals(1, originalNamespace.templateProcessors.registrations().size)
            assertFalse(second.syntaxError)
            assertTrue(second.unsolvedImports.isEmpty())
            assertEquals("say second", second.topFunction.commands.single().toString())
            latest.restore()
            assertNull(detached.call.declarationFile)
            assertNull(detached.owner.declarationFile)
            assertEquals("first_third", first.topFunction.identifier)
            assertTrue(first.unsolvedImports.isEmpty())
            assertTrue(second.syntaxError)
            assertEquals("*", second.unsolvedImports["second.third"])
            assertTrue(second.topFunction.commands.isEmpty())
            assertTrue(originalNamespace.scope.vars.isEmpty())
        }
        before.restore()
        assertEquals(0, Project.errorCount)
    }

    @Test fun successfulDetachedPreparationRestoresAndAppliesActualFileAndDeepStateOnce() = isolated {
        val detached = detachedCompletion()
        val source = detached.source
        val before = CompilerStateJournal.checkpoint(listOf(detached.call))
        var executions = 0
        val prepared = before.prepare {
            executions++
            source.syntaxError = true
            source.unsolvedImports["after.import"] = "*"
            source.topFunction.commands.add(Command("say after"))
            source.namespace.scope.vars["owned"] = MCInt(19, "owned")
            detached.owner.isAbstract = true
            LogProcessor.warn("detached after warning")
        }
        assertEquals(1, executions)
        assertFalse(source.syntaxError)
        assertTrue(source.unsolvedImports.isEmpty())
        assertTrue(source.topFunction.commands.isEmpty())
        assertFalse(detached.owner.isAbstract)
        assertFalse(source.namespace.scope.vars.containsKey("owned"))
        assertEquals(0, Project.warningCount)
        repeat(2) {
            prepared.apply()
            assertTrue(source.syntaxError)
            assertEquals("*", source.unsolvedImports["after.import"])
            assertEquals("say after", source.topFunction.commands.single().toString())
            assertTrue(detached.owner.isAbstract)
            assertEquals(19, assertIs<MCInt>(source.namespace.scope.vars["owned"]).value)
            assertEquals(1, Project.warningCount)
            assertEquals(1, events.size)
            assertEquals(1, executions)
            prepared.rollback()
            assertFalse(source.syntaxError)
            assertTrue(source.unsolvedImports.isEmpty())
            assertTrue(source.topFunction.commands.isEmpty())
            assertFalse(detached.owner.isAbstract)
        }
    }

    @Test fun unknownAdaptersInExtraDeclarationsAreRejectedBeforeTheirTypeGetter() = isolated {
        var getters = 0
        val detached = Function("unknown_input", "detached", null).apply { declarationFile = null }
        detached.scope.vars["unknown"] = object : MCInt("unknown") {
            override var type: MCFPPType
                get() {
                    getters++
                    error("extra-root getter")
                }
                set(value) {}
        }
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint(listOf(detached)) }
        assertEquals(0, getters)
        assertEquals(0, Project.errorCount)
        assertTrue(events.isEmpty())
    }

    @Test fun extraRootContainersAndErasedRolesAreCheckedBeforeHostMethods() = isolated {
        var callbacks = 0
        val unknownList = object : ArrayList<Any>() {
            override val size: Int get() {
                callbacks++
                return super.size
            }
            override fun iterator(): MutableIterator<Any> {
                callbacks++
                return super.iterator()
            }
        }
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint(unknownList) }
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
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint(listOf(host)) }
        assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint(listOf(MCInt(3))) }
        assertEquals(0, callbacks)
        assertEquals(0, Project.errorCount)
    }

    @Test fun detachedExtraFilesReuseImportRolePreflightWithoutCallingUnknownKeys() = isolated {
        val detached = detachedCompletion()
        var callbacks = 0
        val host = object {
            override fun hashCode(): Int {
                callbacks++
                return 1
            }
            override fun toString(): String {
                callbacks++
                return "host"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val imports = detached.source.unsolvedImports as MutableMap<Any, Any>
        imports[host] = "Member"
        callbacks = 0
        try {
            val failure = assertFailsWith<IllegalStateException> { CompilerStateJournal.checkpoint(listOf(detached.call)) }
            assertTrue(failure.message.orEmpty().contains("string import names"))
            assertEquals(0, callbacks)
        } finally {
            imports.clear()
        }
    }
}
