package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** Actual H03 producers and actual publication; no declaration completion/body is executed here. */
class MniDeclarationCompletionRequestTest {
    companion object {
        private var hostCalls = 0
        @JvmStatic fun neverInvoke(context: top.mcfpp.mni.NativeCallContext) {
            hostCalls++
        }
    }
    private val source = SourceSpan("completion-requests.mcfpp", 8, 24, 2, 1, 2, 17)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun expression(text: String) = parser(text).let { p ->
        p.expression().also {
            assertEquals(0, p.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, p.currentToken.type, text)
        }
    }
    private fun fixture(): MCFPPFile {
        Project.init()
        val namespace = Namespace("completion.original")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun ordinary(file: MCFPPFile, name: String = "Record") = DataTemplate(name, file.namespace.identifier).apply {
        declarationFile = file
        file.namespace.scope.template[name] = this
    }
    private fun generic(file: MCFPPFile, name: String = "Box") = GenericDataTemplate(parser("{}").templateBody(), name, file.namespace.identifier).apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam("type", "T", MCFPPConcreteType.Type))
        file.namespace.scope.template[name] = this
    }
    private fun target(file: MCFPPFile, spelling: String, name: String = "target") = Function(name, file.namespace.identifier, null).apply {
        declarationFile = file
        normalParams.add(FunctionParam(UnresolvedType(spelling), "input", this))
    }
    private fun fact(file: MCFPPFile, text: String) = PreparedExpressionFact.readonly(
        DeclarationLexicalSnapshot.capture(file, file.namespace.identifier),
        expression(text)
    )
    private fun pending(function: Function, caller: Function? = null): SignaturePreparationResult.NeedsCompletion {
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source, caller = caller))
        val message = (result as? SignaturePreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        return assertIs<SignaturePreparationResult.NeedsCompletion>(result, message)
    }
    private fun stale(batch: PreparedHeaderBatch) = assertFailsWith<PreparationFactFailure> { batch.adoptForCompletion() }.also {
        assertEquals(source, it.source)
        assertFalse(batch.consumed)
    }

    @Test fun genericApplicationProducesTypedMaterializationTargetWithTheOriginalClosedBinding() {
        val file = fixture()
        val head = generic(file)
        val function = target(file, "Box<list<int>>")
        val work = pending(function)
        val request = work.requests.single()
        assertEquals(DeclarationCompletionKind.MATERIALIZE_HEADER, request.kind)
        val actual = assertIs<CompletionTarget.Header>(request.target).reservation
        assertSame(head, actual.prototype)
        assertSame(actual.model, request.target.identity)
        assertSame(actual, work.staged.headers.single())
        assertEquals(source, request.source)
        assertEquals(CallableDeclarationId(function.declarationId), request.declaration)
        val proof = assertIs<CompilerValue.Typed>(request.targetReadonly.single().value)
        assertEquals(MCFPPConcreteType.Type.typeId, proof.type)
        assertEquals(MCFPPListType(MCFPPBaseType.Int).typeId, assertIs<CompilerValue.TypeValue>(proof.payload).id)
        request.stamp.domain!!.requireCurrent()
        assertEquals(0, head.index)
        assertTrue(head.compiledTemplates.isEmpty())
        assertFalse(actual.isPublished)
    }

    @Test fun pendingDeclarationAnnotationsHaveAnEffectsTargetWithoutCallingTheAnnotation() {
        val file = fixture()
        val model = ordinary(file)
        val annotation = DataOnly()
        model.pendingDeclarationAnnotations.add(annotation)
        val work = pending(target(file, "Record"))
        val request = work.requests.single()
        assertEquals(DeclarationCompletionKind.PENDING_DECLARATION_EFFECTS, request.kind)
        assertSame(model, assertIs<CompletionTarget.Declaration>(request.target).template)
        assertSame(annotation, model.pendingDeclarationAnnotations.single())
        assertTrue(model.scope.allVars.isEmpty())
        assertFalse(model.hasCompletedDeclaration)
        assertTrue(request.targetReadonly.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun anUnresolvedAliasRetainsItsActualDeclarationInsteadOfParsingTheReason() {
        val file = fixture()
        val alias = MCFPPTypeAliasType(parser("int").type())
        file.field.putResolvedAlias("Tracked", alias, true)
        val work = pending(target(file, "Tracked"))
        val request = work.requests.single()
        assertEquals(DeclarationCompletionKind.UNRESOLVED_ALIAS, request.kind)
        assertSame(alias, assertIs<CompletionTarget.Alias>(request.target).alias)
        assertSame(alias, request.target.identity)
        assertNull(alias.cachedTarget)
        assertEquals(source, request.source)
        assertEquals(0, Project.errorCount)
    }

    @Test fun anIncompleteOrdinaryDeclarationRequiresLayoutWithItsOriginalIdentity() {
        val file = fixture()
        val model = ordinary(file)
        val request = pending(target(file, "Record")).requests.single()
        assertEquals(DeclarationCompletionKind.REQUIRES_DECLARATION_LAYOUT, request.kind)
        assertSame(model, request.target.identity)
        assertEquals(source, request.source)
        assertFalse(model.hasCompletedDeclaration)
        assertTrue(model.constructors.isEmpty())
    }

    @Test fun unknownNamesAndUnknownRuntimeTypeShapesAreNotGuessedToBeCompletable() {
        val file = fixture()
        val missing = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(target(file, "Missing")), emptyList(), source))
        assertEquals(source, assertIs<SignaturePreparationResult.Rejected>(missing).diagnostics.single().source)
        var getterCalls = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                getterCalls++
                error("computed identity")
            }
        }
        // Avoid FunctionParam's eager legacy typeName construction: replace the raw type afterwards.
        val function = target(file, "int")
        function.normalParams.single().type = unknown
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        assertIs<SignaturePreparationResult.Rejected>(result)
        assertEquals(0, getterCalls)
    }

    @Test fun sameOwnerAndTypeIdentityCannotHideAFieldVisibilityChange() {
        val file = fixture()
        val head = generic(file)
        val model = ordinary(file)
        val field = MCInt(4, "health")
        model.scope.putVar("health", field)
        val identity = model.inheritanceIdentity
        val work = pending(target(file, "Box<int>"))
        field.accessModifier = Member.AccessModifier.PRIVATE
        assertEquals(identity, model.inheritanceIdentity)
        stale(work.staged)
        assertEquals(0, head.index)
        assertNull(head.preparedHeader(work.staged.headers.single().key))
    }

    @Test fun sameTypeIdentityCannotHidePropertyFlagsOrItsAccessorShape() {
        val file = fixture()
        generic(file)
        val model = ordinary(file)
        val property = Property("health", null, null)
        model.scope.putProperty("health", property)
        val work = pending(target(file, "Box<int>"))
        property.isFinal = true
        stale(work.staged)
    }

    @Test fun aDefaultProofChangeInvalidatesTheWholeCandidateBatch() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        val parameter = FunctionParam(MCFPPBaseType.Int, "amount", function, hasDefault = true).apply {
            defaultValue = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1))
        }
        function.normalParams.add(parameter)
        val work = pending(function)
        parameter.defaultValue = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(2))
        stale(work.staged)
    }

    @Test fun rawMemberTypeRegistrationKeysArePartOfTheStampEvenWithIdenticalValues() {
        val file = fixture()
        generic(file)
        val model = ordinary(file)
        model.scope.putType("AliasA", MCFPPBaseType.Int)
        val work = pending(target(file, "Box<int>"))
        model.scope.removeType("AliasA")
        model.scope.putType("AliasB", MCFPPBaseType.Int)
        stale(work.staged)
    }

    @Test fun aliasTargetsAndNestedMutableTypeRelationsAreBothChecked() {
        val file = fixture()
        generic(file)
        val list = MCFPPListType(MCFPPBaseType.Int)
        file.field.putResolvedAlias("Item", list, true)
        val work = pending(target(file, "Box<Item>"))
        list.generic[0] = MCFPPBaseType.Bool
        stale(work.staged)
        assertEquals(
            MCFPPListType(MCFPPBaseType.Int).typeId,
            assertIs<CompilerValue.TypeValue>(assertIs<CompilerValue.Typed>(work.requests.single().targetReadonly.single().value).payload).id
        )
    }

    @Test fun replacingAnImportTargetUnderTheSameNamespaceKeyInvalidatesTheBatch() {
        val file = fixture()
        generic(file)
        val first = Namespace("import.same")
        file.field.importField["import.same"] = first.scope
        val work = pending(target(file, "Box<int>"))
        file.field.importField["import.same"] = Namespace("import.same").scope
        stale(work.staged)
    }

    @Test fun callerOwnerAndParentRelationsArePartOfTheAccessDomain() {
        val file = fixture()
        generic(file)
        val model = ordinary(file)
        val caller = Function("caller", file.namespace.identifier, null).apply {
            declarationFile = file
            owner = model
        }
        val work = pending(target(file, "Box<int>"), caller)
        model.parent.add(DataTemplate("NewParent", file.namespace.identifier).apply { declarationFile = file })
        stale(work.staged)
    }

    @Test fun restoredDeclarationStateCanBeAdoptedWithoutReplacingItsCanonicalShell() {
        val file = fixture()
        val head = generic(file)
        val model = ordinary(file)
        val field = MCInt(4, "health")
        model.scope.putVar("health", field)
        val work = pending(target(file, "Box<int>"))
        val shell = work.staged.headers.single()
        val before = DeclarationStateJournal.checkpoint(listOf(model, head))
        field.nullable = true
        stale(work.staged)
        val after = before.captureCurrent()
        before.restore()
        val adopted = work.staged.adoptForCompletion()
        assertSame(shell, adopted.headers.single())
        assertSame(shell, head.preparedHeader(shell.key))
        assertEquals(1, head.index)
        assertFalse(field.nullable)
        after.restore()
        assertTrue(field.nullable)
    }

    @Test fun aCompetingBatchCannotPublishTheEarlierEntriesBeforeDiscoveringItsLaterConflict() {
        val file = fixture()
        val first = generic(file, "First")
        val later = generic(file, "Later")
        val actual = fact(file, "int")
        val session = SignaturePreparationSession()
        val firstShell = session.stageHeader(first, listOf(actual), source)
        val losingShell = session.stageHeader(later, listOf(actual), source)
        val losingBatch = session.stagedHeaderBatch()
        val other = SignaturePreparationSession()
        val winner = other.stageHeader(later, listOf(actual), source)
        other.stagedHeaderBatch().adoptForCompletion()
        stale(losingBatch)
        assertEquals(0, first.index)
        assertNull(first.preparedHeader(firstShell.key))
        assertFalse(firstShell.isPublished)
        assertEquals(1, later.index)
        assertSame(winner, later.preparedHeader(winner.key))
        assertNotSame(winner, losingShell)
    }

    @Test fun adoptionIsSingleUseAndASecondCurrentBatchReusesThePublishedCanonicalShell() {
        val file = fixture()
        val head = generic(file)
        val first = pending(target(file, "Box<int>"))
        val shell = first.staged.headers.single()
        val adopted = first.staged.adoptForCompletion()
        assertSame(shell.model, adopted.headers.single().model)
        assertTrue(first.staged.consumed)
        assertFailsWith<IllegalStateException> { first.staged.adoptForCompletion() }
        val second = pending(target(file, "Box<int>"))
        assertSame(shell, second.staged.headers.single())
        second.staged.adoptForCompletion()
        assertEquals(1, head.index)
        assertTrue(head.compiledTemplates.isEmpty())
        assertFalse(shell.model.hasCompletedDeclaration)
    }

    @Test fun discardingAnUnconsumedBatchHasNoCommandsCountersNamesOrCompletionEffects() {
        val file = fixture()
        val head = generic(file)
        head.pendingDeclarationAnnotations.add(DataOnly())
        val caller = Function("sentinel", file.namespace.identifier, null).apply { commands.add(Command("say sentinel")) }
        val function = target(file, "Box<int>")
        caller.runInFunction {
            val commands = caller.commands.toList()
            val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
            val work = pending(function, caller)
            val shell = work.staged.headers.single()
            work.staged.discard()
            assertFailsWith<IllegalStateException> { work.staged.adoptForCompletion() }
            assertEquals(commands, caller.commands.toList())
            assertSame(caller, Function.currFunction)
            assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            assertEquals(0, head.index)
            assertFalse(shell.isPublished)
            assertEquals(1, head.pendingDeclarationAnnotations.size)
            assertTrue(head.compiledTemplates.isEmpty())
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun anUnknownSyntaxReceiverIsRejectedBeforeItsTextCallbackCanRun() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        var textCalls = 0
        function.normalParams.single().defaultContext = object : P.ValueContext(null, -1) {
            override fun getText(): String {
                textCalls++
                error("host syntax callback")
            }
        }
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        assertEquals(source, assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single().source)
        assertEquals(0, textCalls)
    }

    @Test fun unknownScopeContainersAreRejectedBeforeTheirEntriesCallbackCanRun() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        var entriesCalls = 0
        file.namespace.scope.functions = object : HashMap<String, ArrayList<Function>>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, ArrayList<Function>>>
                get() {
                    entriesCalls++
                    error("host collection callback")
                }
        }
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        assertEquals(source, assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single().source)
        assertEquals(0, entriesCalls)
    }

    @Test fun aBorrowedUnmodifiableViewCannotHideAnUnknownDefaultDescriptorContainer() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        var entriesCalls = 0
        val unknown = object : HashMap<TypeId, MCFPPType>() {
            override val entries: MutableSet<MutableMap.MutableEntry<TypeId, MCFPPType>>
                get() {
                    entriesCalls++
                    error("wrapped host callback")
                }
        }
        function.normalParams.single().defaultTypes = java.util.Collections.unmodifiableMap(unknown)
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        assertEquals(source, assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single().source)
        assertEquals(0, entriesCalls)
    }

    @Test fun mutatingTheOriginalEffectSetInvalidatesTheRequestWithNoNewTypeIdentity() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        val writes = linkedSetOf(Place(top.mcfpp.analysis.SymbolId.fresh()))
        function.runtimeEffect = Effect.Writes(writes)
        val work = pending(function)
        writes.clear()
        stale(work.staged)
    }

    class RegisteredEffect : top.mcfpp.model.annotation.Annotation("RegisteredEffect", "completion.original") {
        var applications = 0
        var payload: Any = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7))
        override fun forDataTemplate(data: DataTemplate) {
            applications++
            data.scope.putVar("generated", MCInt(7, "generated"))
        }
        override fun forFunction(function: Function) = Unit
        override fun forField(field: top.mcfpp.core.lang.Var<*>) = Unit
    }

    @Test fun aReallyRegisteredExtensionUsesTheSharedPayloadFactsWithoutCallingItsAction() {
        val file = fixture()
        val model = ordinary(file)
        val effect = RegisteredEffect()
        file.namespace.scope.annotations["RegisteredEffect"] = RegisteredEffect::class.java
        model.pendingDeclarationAnnotations.add(effect)
        val old = pending(target(file, "Record"))
        effect.payload = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(8))
        stale(old.staged)
        val current = pending(target(file, "Record"))
        val adopted = current.staged.adoptForCompletion()
        assertEquals(DeclarationCompletionKind.PENDING_DECLARATION_EFFECTS, adopted.requests.single().kind)
        assertSame(model, adopted.requests.single().target.identity)
        assertEquals(0, effect.applications)
        assertTrue(model.scope.allVars.isEmpty())
        assertSame(effect, model.pendingDeclarationAnnotations.single())
    }

    @Test fun registeredUnsupportedAnnotationPayloadsBecomeSourceDiagnosticsBeforeAnyHostCallback() {
        val file = fixture()
        val model = ordinary(file)
        val effect = RegisteredEffect()
        file.namespace.scope.annotations["RegisteredEffect"] = RegisteredEffect::class.java
        var textCalls = 0
        effect.payload = object {
            override fun toString(): String {
                textCalls++
                error("host payload")
            }
        }
        model.pendingDeclarationAnnotations.add(effect)
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(target(file, "Record")), emptyList(), source))
        val diagnostic = assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single()
        assertEquals(source, diagnostic.source)
        assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, diagnostic.code)
        assertEquals(0, textCalls)
        assertEquals(0, effect.applications)
    }

    @Test fun aLegacyUnregisteredAnnotationCanBeReadButCannotGrantACompletionCapability() {
        val file = fixture()
        val head = generic(file)
        val effect = RegisteredEffect()
        head.annotations.add(effect)
        val work = pending(target(file, "Box<int>"))
        assertIs<CompletionTarget.Header>(work.requests.single().target)
        val failure = stale(work.staged)
        assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, failure.code)
        assertEquals(0, effect.applications)
        assertEquals(0, head.index)
        assertFalse(work.staged.headers.single().isPublished)
    }

    private fun defaultTarget(file: MCFPPFile): Pair<Function, org.antlr.v4.runtime.tree.TerminalNodeImpl> {
        val function = target(file, "Box<int>")
        val syntax = parser("1").value()
        function.normalParams.add(FunctionParam(MCFPPBaseType.Int, "amount", function, hasDefault = true).apply { defaultContext = syntax })
        fun terminal(tree: org.antlr.v4.runtime.tree.ParseTree): org.antlr.v4.runtime.tree.TerminalNodeImpl = if (tree is org.antlr.v4.runtime.tree.TerminalNodeImpl) tree else terminal(tree.getChild(0))
        return function to terminal(syntax)
    }

    @Test fun replacingATokenAtTheSameCoordinatesStillInvalidatesTheDefaultSyntax() {
        val file = fixture()
        generic(file)
        val (function, terminal) = defaultTarget(file)
        val work = pending(function)
        val original = terminal.symbol as org.antlr.v4.runtime.CommonToken
        terminal.symbol = org.antlr.v4.runtime.CommonToken(original)
        assertEquals(original.startIndex, terminal.symbol.startIndex)
        assertEquals(original.stopIndex, terminal.symbol.stopIndex)
        stale(work.staged)
    }

    @Test fun changingTheOriginalSourceBytesIsDetectedEvenWhenRawTokenTextIsNull() {
        val file = fixture()
        generic(file)
        val (function, terminal) = defaultTarget(file)
        val token = terminal.symbol as org.antlr.v4.runtime.CommonToken
        val textSlot = org.antlr.v4.runtime.CommonToken::class.java.getDeclaredField("text").apply { isAccessible = true }
        assertNull(textSlot.get(token))
        val work = pending(function)
        val stream = token.inputStream
        val bytes = stream.javaClass.getDeclaredField("byteArray").apply { isAccessible = true }.get(stream) as ByteArray
        bytes[0] = '2'.code.toByte()
        stale(work.staged)
    }

    @Test fun anUnknownCharStreamIsRejectedWithoutReadingItsText() {
        val file = fixture()
        generic(file)
        val (function, terminal) = defaultTarget(file)
        val token = terminal.symbol as org.antlr.v4.runtime.CommonToken
        var textCalls = 0
        val original = token.inputStream
        val unknown = object : org.antlr.v4.runtime.CharStream by original {
            override fun getText(interval: org.antlr.v4.runtime.misc.Interval): String {
                textCalls++
                error("host source callback")
            }
        }
        terminal.symbol = org.antlr.v4.runtime.CommonToken(
            org.antlr.v4.runtime.misc.Pair(token.tokenSource, unknown),
            token.type,
            token.channel,
            token.startIndex,
            token.stopIndex
        )
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        assertEquals(source, assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single().source)
        assertEquals(0, textCalls)
    }

    @Test fun anUnknownTokenSourceIsRejectedBeforeReadingItsSourceNameForASpan() {
        val file = fixture()
        generic(file)
        val (function, terminal) = defaultTarget(file)
        val token = terminal.symbol as org.antlr.v4.runtime.CommonToken
        var sourceCalls = 0
        val unknown = object : org.antlr.v4.runtime.TokenSource by token.tokenSource {
            override fun getSourceName(): String {
                sourceCalls++
                error("host token source")
            }
        }
        terminal.symbol = org.antlr.v4.runtime.CommonToken(
            org.antlr.v4.runtime.misc.Pair(unknown, token.inputStream),
            token.type,
            token.channel,
            token.startIndex,
            token.stopIndex
        )
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        assertEquals(source, assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single().source)
        assertEquals(0, sourceCalls)
    }

    @Test fun movingAnAnnotationRegistrationKeepsSupportButInvalidatesItsOriginalDomainKey() {
        val file = fixture()
        val model = ordinary(file)
        val effect = RegisteredEffect()
        file.namespace.scope.annotations["OriginalKey"] = RegisteredEffect::class.java
        model.pendingDeclarationAnnotations.add(effect)
        val work = pending(target(file, "Record"))
        file.namespace.scope.annotations.remove("OriginalKey")
        file.namespace.scope.annotations["NewKey"] = RegisteredEffect::class.java
        assertTrue(effect.hasDeclarationStateSupport())
        stale(work.staged)
        assertEquals(0, effect.applications)
    }

    @Test fun productionBatchesExpireWhenAnySpecializationGenerationOptionChanges() {
        val file = fixture()
        val head = generic(file)
        val work = pending(target(file, "Box<int>"))
        val version = Project.config.version
        val limit = CompileSettings.maxWhileInline
        val fold = CompileSettings.foldIRConstants
        try {
            Project.config.version = version + ".changed"
            stale(work.staged)
            Project.config.version = version
            CompileSettings.maxWhileInline = limit + 1
            stale(work.staged)
            CompileSettings.maxWhileInline = limit
            CompileSettings.foldIRConstants = !fold
            stale(work.staged)
            CompileSettings.foldIRConstants = fold
            work.staged.adoptForCompletion()
            assertEquals(1, head.index)
        } finally {
            Project.config.version = version
            CompileSettings.maxWhileInline = limit
            CompileSettings.foldIRConstants = fold
        }
    }

    private fun rejectedShape(function: Function): PreparationDiagnostic {
        val result = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        return assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single().also {
            assertEquals(source, it.source)
            assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, it.code)
            assertNotNull(it.cause)
            assertEquals(0, Project.errorCount)
        }
    }

    private fun rejectReadonlyBoundary(replaceStart: Boolean) {
        val file = fixture()
        val head = generic(file)
        val function = top.mcfpp.model.function.GenericFunction("target", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
            normalParams.add(FunctionParam(UnresolvedType("Box<int>"), "input", this))
        }
        val syntax = parser("1").value()
        function.readOnlyParams.add(
            FunctionParam(MCFPPBaseType.Int, "amount", function, hasDefault = true, isReadOnly = true).apply {
                defaultContext = syntax
            }
        )
        val original = syntax.start
        var sourceCalls = 0
        val unknown = object : org.antlr.v4.runtime.TokenSource by original.tokenSource {
            override fun getSourceName(): String {
                sourceCalls++
                error("boundary source callback")
            }
        }
        val replacement = org.antlr.v4.runtime.CommonToken(
            org.antlr.v4.runtime.misc.Pair(unknown, original.inputStream),
            original.type,
            original.channel,
            original.startIndex,
            original.stopIndex
        )
        if (replaceStart) syntax.start = replacement else syntax.stop = replacement
        // Terminals still refer to the admitted original token: only the context edge is hostile.
        fun terminal(tree: org.antlr.v4.runtime.tree.ParseTree): org.antlr.v4.runtime.tree.TerminalNode = if (tree is org.antlr.v4.runtime.tree.TerminalNode) tree else terminal(tree.getChild(0))
        assertSame(original, terminal(syntax).symbol)
        assertTrue(rejectedShape(function).message.contains("token source"))
        assertEquals(0, sourceCalls)
        assertEquals(0, head.index)
        assertTrue(head.compiledTemplates.isEmpty())
    }

    @Test fun readonlyDefaultStartTokensUseTheSameAdmissionAsTerminals() = rejectReadonlyBoundary(true)

    @Test fun readonlyDefaultStopTokensUseTheSameAdmissionAsTerminals() = rejectReadonlyBoundary(false)

    @Test fun aLexersDifferentRawInputCannotBypassTheTokenSourcePairGate() {
        val file = fixture()
        generic(file)
        val (function, terminal) = defaultTarget(file)
        val token = terminal.symbol
        val original = token.inputStream
        var callbacks = 0
        val unknown = object : org.antlr.v4.runtime.CharStream by original {
            override fun getSourceName(): String {
                callbacks++
                error("lexer source callback")
            }
            override fun getText(interval: org.antlr.v4.runtime.misc.Interval): String {
                callbacks++
                error("lexer text callback")
            }
        }
        val input = org.antlr.v4.runtime.Lexer::class.java.getDeclaredField("_input").apply { isAccessible = true }
        input.set(token.tokenSource, unknown)
        assertSame(original, token.inputStream)
        assertTrue(rejectedShape(function).message.contains("lexer input"))
        assertEquals(0, callbacks)
    }

    @Test fun anErasedNormalFormalListCannotSmuggleASupportedFunctionIntoLexicalCapture() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        @Suppress("UNCHECKED_CAST")
        (function.normalParams as MutableList<Any?>).add(function)
        assertTrue(rejectedShape(function).message.contains("normalParams"))
    }

    @Test fun erasedGenericAndNativeReadonlyListsAreCheckedBeforeAnyFormalOrHostAccess() {
        val file = fixture()
        generic(file)
        val generic = top.mcfpp.model.function.GenericFunction("generic", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
        }
        val native = top.mcfpp.model.function.NativeFunction(
            "native",
            file.namespace.identifier,
            MniDeclarationCompletionRequestTest::class.java.getMethod("neverInvoke", top.mcfpp.mni.NativeCallContext::class.java)
        ).apply {
            declarationFile = file
        }
        hostCalls = 0
        for ((function, parameters) in listOf(generic to generic.readOnlyParams, native to native.readOnlyParams)) {
            @Suppress("UNCHECKED_CAST")
            (parameters as MutableList<Any?>).add(function)
            assertTrue(rejectedShape(function).message.contains("readOnlyParams"))
        }
        assertEquals(0, hostCalls)
    }

    @Test fun defaultDescriptorMapsRequireTypeIdentityKeysAndCompilerTypeValuesBeforeCasting() {
        val file = fixture()
        generic(file)
        val function = target(file, "Box<int>")
        val parameter = function.normalParams.single()
        for ((key, value) in listOf<Pair<Any, Any>>(function to MCFPPBaseType.Int, MCFPPBaseType.Int.typeId to function)) {
            val erased = java.util.IdentityHashMap<Any, Any>().apply { put(key, value) }
            @Suppress("UNCHECKED_CAST")
            parameter.defaultTypes = erased as Map<TypeId, MCFPPType>
            assertTrue(rejectedShape(function).message.contains("defaultTypes"))
        }
    }

    private fun readonlyDefault(file: MCFPPFile): Pair<top.mcfpp.model.function.GenericFunction, P.ValueContext> {
        val function = top.mcfpp.model.function.GenericFunction("target", file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = file
        }
        val syntax = parser("1").value()
        function.readOnlyParams.add(
            FunctionParam(MCFPPBaseType.Int, "amount", function, hasDefault = true, isReadOnly = true).apply {
                defaultContext = syntax
            }
        )
        return function to syntax
    }

    private fun <T> withoutPreparationEffects(action: () -> T): T {
        val current = Function.currFunction
        val commands = current.commands.toList()
        val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
        return action().also {
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
        }
    }

    @Test fun aMissingReadonlyDefaultStartIsRejectedAtPreflightWithTheOriginalSourceAndCause() {
        val file = fixture()
        val (function, syntax) = readonlyDefault(file)
        val original = syntax.start
        syntax.start = null
        assertEquals("1", syntax.text) // The admitted terminals remain intact.
        assertTrue(withoutPreparationEffects { rejectedShape(function) }.message.contains("required declaration syntax token"))
        assertSame(original, syntax.stop)
        assertTrue(function.compiledFunctions.isEmpty())
    }

    @Test fun aMissingReadonlyDefaultTerminalSymbolIsRejectedBeforeBindingOrSpanCreation() {
        val file = fixture()
        val (function, syntax) = readonlyDefault(file)
        fun terminal(tree: org.antlr.v4.runtime.tree.ParseTree): org.antlr.v4.runtime.tree.TerminalNodeImpl = if (tree is org.antlr.v4.runtime.tree.TerminalNodeImpl) tree else terminal(tree.getChild(0))
        val node = terminal(syntax)
        val original = syntax.start
        node.symbol = null
        assertTrue(withoutPreparationEffects { rejectedShape(function) }.message.contains("required declaration syntax token"))
        assertSame(original, syntax.start)
        assertSame(original, syntax.stop)
        assertTrue(function.compiledFunctions.isEmpty())
    }

    @Test fun aMissingReadonlyDefaultStopUsesTheValidatedStartAndStillProducesItsClosedProof() {
        val file = fixture()
        val (function, syntax) = readonlyDefault(file)
        val expectedSource = preparationSpan(syntax)
        syntax.stop = null
        val result = withoutPreparationEffects {
            SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        }
        val prepared = assertIs<SignaturePreparationResult.Ready>(result).declarations.signatures.single()
        val actual = prepared.readonlyArguments.single()
        assertEquals(expectedSource, actual.source)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1)), actual.value)
        assertSame(function.readOnlyParams.single(), prepared.readonly.single().identity)
        assertNull(syntax.stop)
        assertEquals(0, Project.errorCount)
        assertTrue(function.compiledFunctions.isEmpty())
    }

    private fun parsedWrapperRequest(nested: Boolean, effects: Boolean) {
        val file = fixture()
        // Parsed parameter descriptors are a real declaration root even without a namespace registration.
        val model = DataTemplate("Detached", file.namespace.identifier).apply { declarationFile = file }
        if (effects) model.pendingDeclarationAnnotations.add(DataOnly())
        val wrapper = MCFPPDataTemplateType(model, arrayListOf())
        val descriptor = if (nested) MCFPPListType(MCFPPDictType(wrapper)) else wrapper
        val (function, _) = readonlyDefault(file)
        val parameter = FunctionParam(MCFPPBaseType.Int, "input", function).apply { type = descriptor }
        function.normalParams.add(parameter)
        val work = withoutPreparationEffects { pending(function) }
        val request = work.requests.single()
        assertEquals(
            if (effects) DeclarationCompletionKind.PENDING_DECLARATION_EFFECTS
            else DeclarationCompletionKind.REQUIRES_DECLARATION_LAYOUT,
            request.kind
        )
        assertSame(model, assertIs<CompletionTarget.Declaration>(request.target).template)
        assertSame(model, request.target.identity)
        assertSame(function, request.function)
        assertEquals(CallableDeclarationId(function.declarationId), request.declaration)
        assertEquals(source, request.source)
        assertTrue(request.readonlyComplete)
        assertTrue(request.explicitReadonly.isEmpty())
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1)), request.completedReadonly.single().value)
        assertSame(descriptor, parameter.type)
        request.stamp.domain!!.requireCurrent()
        assertTrue(work.staged.headers.isEmpty())
        assertFalse(work.staged.consumed)
        assertTrue(file.namespace.scope.template.isEmpty())
        assertFalse(model.hasCompletedDeclaration)
        assertTrue(model.scope.allVars.isEmpty())
        assertEquals(if (effects) 1 else 0, model.pendingDeclarationAnnotations.size)
        assertTrue(function.compiledFunctions.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun aParsedDetachedTemplateWrapperPreservesItsTypedEffectsCompletionRequest() = parsedWrapperRequest(nested = false, effects = true)

    @Test fun aParsedNestedContainerPreservesItsOriginalLayoutTargetAndClosedReadonlyFacts() = parsedWrapperRequest(nested = true, effects = false)

    @Test fun parsedContainersWithUnknownLeafTypesRemainRejectedBeforeAnyTypeGetter() {
        val file = fixture()
        var getterCalls = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                getterCalls++
                error("unknown parsed leaf")
            }
        }
        val function = target(file, "int")
        function.normalParams.single().type = MCFPPListType(MCFPPDictType(unknown))
        val result = withoutPreparationEffects {
            SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(function), emptyList(), source))
        }
        val diagnostic = assertIs<SignaturePreparationResult.Rejected>(result).diagnostics.single()
        assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, diagnostic.code)
        assertEquals(source, diagnostic.source)
        assertEquals(0, getterCalls)
        assertTrue(function.compiledFunctions.isEmpty())
        assertEquals(0, Project.errorCount)
    }
}
