package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniHeaderDeclarationReaderCallTest {
    companion object {
        private var nativeCalls = 0
        @JvmStatic fun nativeSentinel(context: NativeCallContext) {
            nativeCalls++
        }
    }
    private val source = SourceSpan("header-call.mcfpp", 0, 5, 1, 1, 1, 6)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun expression(text: String): P.ExpressionContext = parser(text).let { parser ->
        parser.expression().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun calls(tree: ParseTree): List<P.FunctionCallContext> = (if (tree is P.FunctionCallContext) listOf(tree) else emptyList()) +
        (0 until tree.childCount).flatMap { calls(tree.getChild(it)) }
    private fun file(name: String): MCFPPFile {
        val namespace = Namespace(name)
        GlobalScope.localNamespaces[name] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun prototype(file: MCFPPFile, name: String = "Node", body: String = "{}") = GenericDataTemplate(parser(body).templateBody(), name, file.namespace.identifier).apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam("Type", "T", MCFPPConcreteType.Type))
        file.namespace.scope.template[name] = this
    }
    private fun generic(file: MCFPPFile, name: String = "copy", result: String = "Node<U>") = GenericFunction(name, file.namespace.identifier, parser("{ return value }").curlBlock()).apply {
        declarationFile = file
        returnType = UnresolvedType(result)
        readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "U", this, isReadOnly = true))
        normalParams.add(FunctionParam(UnresolvedType("Node<U>"), "value", this))
        file.field.addFunction(this, force = true)
    }
    private fun maker(file: MCFPPFile) = GenericFunction("make", file.namespace.identifier, parser("{}").curlBlock()).apply {
        declarationFile = file
        returnType = UnresolvedType("Node<U>")
        readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "U", this, isReadOnly = true))
        file.field.addFunction(this, force = true)
    }
    private data class Fixture(val file: MCFPPFile, val head: GenericDataTemplate, val function: GenericFunction)
    private fun fixture(body: String = "{}"): Fixture {
        Project.init()
        val file = file("reader.call")
        return Fixture(file, prototype(file, body = body), generic(file))
    }
    private class Work(val session: SignaturePreparationSession, val result: SignaturePreparationResult.NeedsCompletion) {
        val owner get() = result.staged.headers.single()
        val lexical get() = result.requests.single().lexical
        val context get() = DeclarationPreparationContext.forHeader(lexical, owner)
        fun request(call: P.FunctionCallContext): SignaturePreparationRequest {
            val binder = DeclarationBinding(context)
            val readonly = call.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map {
                PreparedExpressionFact.fromBound(binder.readonly(it), preparationSpan(it), binder)
            }
            val name = call.namespaceID().text
            return SignaturePreparationRequest(lexical.functions(null, name), readonly, preparationSpan(call))
        }
        fun prepare(request: SignaturePreparationRequest) = session.prepareReaderCall(owner, lexical, request)
    }
    private fun work(file: MCFPPFile): Work {
        val target = Function("target", file.namespace.identifier, null).apply {
            declarationFile = file
            normalParams.add(FunctionParam(UnresolvedType("Node<int>"), "input", this))
        }
        val session = SignaturePreparationSession()
        return Work(
            session,
            assertIs<SignaturePreparationResult.NeedsCompletion>(
                session.prepare(
                    SignaturePreparationRequest(listOf(target), emptyList(), source)
                )
            )
        )
    }
    private fun prepared(work: Work, request: SignaturePreparationRequest) = assertIs<HeaderCallPreparationResult.Prepared>(work.prepare(request)).facts

    @Test fun aRealCopyCallBindsItsOwnReadonlyFormalToTheReservedReceiverWithoutCompletingIt() {
        val fixture = fixture()
        val work = work(fixture.file)
        val tree = expression("copy<T>(this)")
        val call = calls(tree).single()
        val request = work.request(call)
        val facts = prepared(work, request)
        val signature = facts.declarations.signatures.single()
        assertSame(fixture.function, signature.function)
        assertSame(fixture.function.readOnlyParams.single(), signature.readonly.single().identity)
        assertEquals("U", signature.readonly.single().name)
        assertSame(work.owner.type, signature.normal.single().type)
        assertSame(work.owner.type, signature.returnType)
        assertEquals(
            CompilerValue.TypeValue(MCFPPBaseType.Int.typeId),
            assertIs<CompilerValue.Typed>(signature.readonlyArguments.single().value).payload
        )
        val bound = DeclarationBinding(work.context, mapOf(call to facts)).expression(tree)
        assertSame(work.owner.type, bound.type)
        assertNull(bound.constant)
        work.result.staged.requireOpen()
        assertEquals(HeaderPhase.RESERVED, work.owner.phase)
        assertFalse(work.owner.isPublished)
        assertEquals(0, fixture.head.index)
        assertTrue(fixture.head.compiledTemplates.isEmpty())
        assertTrue(work.owner.model.scope.allVars.isEmpty())
    }

    @Test fun originalCalleeImportsAndAliasesWinOverHeaderCallerNames() {
        Project.init()
        val caller = file("reader.caller")
        val head = prototype(caller)
        caller.field.putResolvedAlias("Chosen", MCFPPBaseType.Float, forced = true)
        val callee = file("reader.callee")
        callee.field.importedNamespaceField[caller.namespace.identifier] = caller.namespace.scope
        callee.field.putResolvedAlias("Chosen", MCFPPBaseType.Int, forced = true)
        val copy = generic(callee).apply { normalParams.add(FunctionParam(UnresolvedType("Chosen"), "extra", this)) }
        caller.field.importedNamespaceField[callee.namespace.identifier] = callee.namespace.scope
        val work = work(caller)
        val tree = expression("copy<T>(this, 7)")
        val call = calls(tree).single()
        val facts = prepared(work, work.request(call))
        val signature = facts.declarations.signatures.single()
        assertSame(copy, signature.function)
        assertEquals(callee.namespace.identifier, signature.lexical.namespace)
        assertSame(MCFPPBaseType.Int, signature.normal[1].type)
        assertSame(work.owner.type, signature.normal[0].type)
        assertSame(work.owner.type, DeclarationBinding(work.context, mapOf(call to facts)).expression(tree).type)
        assertTrue(head.compiledTemplates.isEmpty())
        assertEquals(0, head.index)
    }

    @Test fun calleeContextsCannotBorrowThisOrOwnedFieldsAndRespectTheirUnavailableFormals() {
        val fixture = fixture("{ const secret:int = 29; }")
        val work = work(fixture.file)
        val lexical = DeclarationLexicalSnapshot.capture(fixture.function, work.session)
        val context = DeclarationPreparationContext.forHeaderDeclaration(lexical, work.owner, work.lexical)
        val binder = DeclarationBinding(context)
        val declaration = fixture.head.ctx.templateMemberDeclaration().single().templateMember().templateFieldDeclaration()
        val callerContext = DeclarationPreparationContext.forHeader(
            work.lexical,
            work.owner,
            listOf(
                PreparedOwnedHeaderField(
                    work.owner,
                    declaration,
                    DeclarationBinding.Bound(
                        MCFPPBaseType.Int,
                        CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(29))
                    )
                )
            )
        )
        assertEquals(
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(29)),
            DeclarationBinding(callerContext).readonly(expression("secret")).constant
        )
        val self = expression("this")
        val receiverFailure = assertFailsWith<PreparationFactFailure> { binder.expression(self) }
        assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, receiverFailure.code)
        assertEquals(preparationSpan(self), receiverFailure.source)
        val formal = expression("U")
        assertEquals(
            PreparationFactCode.FORMAL_DEPENDENCY,
            assertFailsWith<PreparationFactFailure> { binder.readonly(formal) }.code
        )
        val unknown = expression("secret")
        assertEquals(
            PreparationFactCode.UNKNOWN_NAME,
            assertFailsWith<PreparationFactFailure> { binder.expression(unknown) }.code
        )
    }

    @Test fun normalPreparationStillRequiresCompletionAndDoesNotReceiveReaderPrivileges() {
        val fixture = fixture()
        val work = work(fixture.file)
        val call = calls(expression("copy<T>(this)")).single()
        val request = work.request(call)
        val ordinary = SignaturePreparationSession()
        val result = assertIs<SignaturePreparationResult.NeedsCompletion>(ordinary.prepare(request))
        assertEquals(HeaderPhase.RESERVED, result.staged.headers.single().phase)
        assertNotSame(work.owner.model, result.staged.headers.single().model)
        assertSame(work.owner.type, prepared(work, request).declarations.signatures.single().returnType)
        work.result.staged.requireOpen()
    }

    @Test fun newReturnHeadersExtendTheCanonicalTableWithoutChangingThePendingSnapshot() {
        val fixture = fixture()
        val peer = prototype(fixture.file, "Peer")
        fixture.function.returnType = UnresolvedType("Peer<U>")
        val work = work(fixture.file)
        val call = calls(expression("copy<T>(this)")).single()
        val facts = prepared(work, work.request(call))
        val childType = facts.declarations.signatures.single().returnType
        work.result.staged.requireOpen()
        assertEquals(1, work.result.staged.headers.size)
        assertSame(work.owner, work.result.staged.headers.single())
        val refreshed = work.session.refreshHeaderReaderBatch(work.owner)
        assertTrue(work.result.staged.discarded)
        assertEquals(2, refreshed.headers.size)
        assertSame(work.owner, refreshed.headers[0])
        assertSame(peer, refreshed.headers[1].prototype)
        assertSame(childType, refreshed.headers[1].type)
        assertSame(childType, prepared(work, work.request(call)).declarations.signatures.single().returnType)
        assertTrue(peer.compiledTemplates.isEmpty())
        assertEquals(0, peer.index)
    }

    @Test fun aRejectedCandidateRestoresOnlyNewReaderEntriesAndKeepsTheOriginalBatchOpen() {
        val fixture = fixture()
        val peer = prototype(fixture.file, "Peer")
        fixture.function.returnType = UnresolvedType("Peer<U>")
        val denied = generic(fixture.file, "denied").apply { accessModifier = AccessModifier.PRIVATE }
        val work = work(fixture.file)
        val call = calls(expression("copy<T>(this)")).single()
        val original = work.request(call)
        val request = SignaturePreparationRequest(listOf(fixture.function, denied), original.explicitReadonly, original.source)
        val failure = assertIs<HeaderCallPreparationResult.Rejected>(work.prepare(request))
        assertEquals(original.source, failure.diagnostics.single().source)
        assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, failure.diagnostics.single().code)
        work.result.staged.requireOpen()
        val refreshed = work.session.refreshHeaderReaderBatch(work.owner)
        assertSame(work.owner, refreshed.headers.single())
        assertTrue(peer.compiledTemplates.isEmpty())
        assertEquals(0, peer.index)
        prepared(work, original)
        assertEquals(2, work.session.refreshHeaderReaderBatch(work.owner).headers.size)
    }

    @Test fun unresolvedCalleeFactsReportNeedsCompletionWithoutFakingPreparedOrDiscardingTheOwner() {
        val fixture = fixture()
        fixture.function.returnType = UnresolvedType("UnresolvedResult")
        DataTemplate("UnresolvedResult", fixture.file.namespace.identifier).apply {
            declarationFile = fixture.file
            fixture.file.namespace.scope.template[identifier] = this
        }
        val work = work(fixture.file)
        val call = calls(expression("copy<T>(this)")).single()
        val request = work.request(call)
        val result = assertIs<HeaderCallPreparationResult.NeedsCompletion>(work.prepare(request))
        assertSame(fixture.function, result.requests.single().function)
        assertEquals(request.source, result.requests.single().source)
        work.result.staged.requireOpen()
        assertSame(work.owner, work.session.refreshHeaderReaderBatch(work.owner).headers.single())
    }

    @Test fun foreignOwnerOrLexicalDomainsAreRejectedBeforeCandidatePreparation() {
        val fixture = fixture()
        val first = work(fixture.file)
        val second = work(fixture.file)
        val request = first.request(calls(expression("copy<T>(this)")).single())
        for (result in listOf(
            second.session.prepareReaderCall(first.owner, first.lexical, request),
            first.session.prepareReaderCall(first.owner, second.lexical, request)
        )) {
            val diagnostic = assertIs<HeaderCallPreparationResult.Rejected>(result).diagnostics.single()
            assertEquals(PreparationFactCode.UNPREPARED_TYPE, diagnostic.code)
            assertEquals(request.source, diagnostic.source)
        }
        first.result.staged.requireOpen()
        second.result.staged.requireOpen()
        assertSame(first.owner.type, prepared(first, request).declarations.signatures.single().returnType)
    }

    @Test fun expiredOrChangedOwnersCannotKeepUsingPreviouslyGrantedReaderAuthority() {
        val fixture = fixture()
        val first = work(fixture.file)
        val request = first.request(calls(expression("copy<T>(this)")).single())
        first.result.staged.discard()
        assertIs<HeaderCallPreparationResult.Rejected>(first.prepare(request))
        val second = work(fixture.file)
        val secondRequest = second.request(calls(expression("copy<T>(this)")).single())
        second.session.discard()
        assertIs<HeaderCallPreparationResult.Rejected>(second.prepare(secondRequest))
        val third = work(fixture.file)
        val thirdRequest = third.request(calls(expression("copy<T>(this)")).single())
        fixture.head.isFinal = true
        val stale = assertIs<HeaderCallPreparationResult.Rejected>(third.prepare(thirdRequest)).diagnostics.single()
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, stale.code)
        assertTrue(fixture.head.compiledTemplates.isEmpty())
        assertEquals(0, fixture.head.index)
    }

    @Test fun ordinaryBindingCannotConsumeLocalFactsEvenWhenArgumentsNeedNoReaderCapability() {
        val fixture = fixture()
        maker(fixture.file)
        val work = work(fixture.file)
        val tree = expression("make<int>()")
        val call = calls(tree).single()
        val facts = prepared(work, work.request(call))
        assertSame(work.owner.type, DeclarationBinding(work.context, mapOf(call to facts)).expression(tree).type)
        val ordinary = DeclarationBinding(DeclarationPreparationContext(work.lexical), mapOf(call to facts))
        val failure = assertFailsWith<PreparationFactFailure> { ordinary.expression(tree) }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, failure.code)
        assertEquals(preparationSpan(call), failure.source)
        assertTrue(failure.message.orEmpty().contains("Prepared header call"))
        work.result.staged.requireOpen()
    }

    @Test fun foreignReaderConsumersCannotBorrowFactsFromAnotherOwnerOrSession() {
        val fixture = fixture()
        val peer = prototype(fixture.file, "Peer")
        maker(fixture.file)
        val first = work(fixture.file)
        val second = work(fixture.file)
        val tree = expression("make<int>()")
        val call = calls(tree).single()
        val facts = prepared(first, first.request(call))
        val peerSyntax = parser("Peer<T>").type()
        val peerType = DeclarationBinding(first.context).type(peerSyntax)
        val peerOwner = first.session.refreshHeaderReaderBatch(first.owner).headers.single { it.type === peerType }
        assertSame(peer, peerOwner.prototype)
        val otherOwner = DeclarationPreparationContext.forHeader(first.lexical, peerOwner)
        for (context in listOf(second.context, otherOwner)) {
            val failure = assertFailsWith<PreparationFactFailure> {
                DeclarationBinding(context, mapOf(call to facts)).expression(tree)
            }
            assertEquals(PreparationFactCode.UNPREPARED_TYPE, failure.code)
            assertEquals(preparationSpan(call), failure.source)
            assertTrue(failure.message.orEmpty().contains("Prepared header call"))
        }
        assertSame(first.owner.type, DeclarationBinding(first.context, mapOf(call to facts)).expression(tree).type)
    }

    @Test fun alreadyPreparedFactsCannotBeConsumedAfterTheirReaderBatchExpires() {
        val fixture = fixture()
        maker(fixture.file)
        val work = work(fixture.file)
        val tree = expression("make<int>()")
        val call = calls(tree).single()
        val facts = prepared(work, work.request(call))
        val binder = DeclarationBinding(work.context, mapOf(call to facts))
        assertSame(work.owner.type, binder.expression(tree).type)
        work.result.staged.discard()
        val failure = assertFailsWith<PreparationFactFailure> { binder.expression(tree) }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, failure.code)
        assertEquals(preparationSpan(tree), failure.source)
        assertTrue(fixture.head.compiledTemplates.isEmpty())
        assertEquals(0, fixture.head.index)
    }

    @Test fun allCandidatesRemainPreparedAndH04AloneChoosesTheExactNormalOverload() {
        val fixture = fixture()
        val converted = Function("choose", fixture.file.namespace.identifier, null).apply {
            declarationFile = fixture.file
            returnType = MCFPPBaseType.Float
            normalParams.add(FunctionParam(MCFPPBaseType.Float, "value", this))
            fixture.file.field.addFunction(this, force = true)
        }
        val exact = Function("choose", fixture.file.namespace.identifier, null).apply {
            declarationFile = fixture.file
            returnType = MCFPPBaseType.Int
            normalParams.add(FunctionParam(MCFPPBaseType.Int, "value", this))
            fixture.file.field.addFunction(this, force = true)
        }
        val work = work(fixture.file)
        val tree = expression("choose(7)")
        val call = calls(tree).single()
        val facts = prepared(work, work.request(call))
        assertEquals(2, facts.declarations.signatures.size)
        assertTrue(facts.declarations.signatures.any { it.function === converted })
        assertTrue(facts.declarations.signatures.any { it.function === exact })
        assertSame(MCFPPBaseType.Int, DeclarationBinding(work.context, mapOf(call to facts)).expression(tree).type)
        work.result.staged.requireOpen()
    }

    @Test fun readonlyInapplicabilityKeepsTheOriginalCandidateWithoutBlockingAnApplicableSibling() {
        val fixture = fixture()
        val incompatible = GenericFunction("copy", fixture.file.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = fixture.file
            returnType = MCFPPBaseType.Float
            readOnlyParams.add(FunctionParam(MCFPPBaseType.Float, "scale", this, isReadOnly = true))
            normalParams.add(FunctionParam(MCFPPBaseType.Int, "value", this))
            fixture.file.field.addFunction(this, force = true)
        }
        val work = work(fixture.file)
        val tree = expression("copy<T>(this)")
        val call = calls(tree).single()
        val facts = prepared(work, work.request(call))
        assertEquals(2, facts.declarations.candidates.size)
        assertEquals(
            CallableDeclarationId(incompatible.declarationId),
            assertIs<PreparedCandidate.Inapplicable>(
                facts.declarations.candidates.single {
                    it.declaration == CallableDeclarationId(incompatible.declarationId)
                }
            ).declaration
        )
        assertSame(fixture.function, facts.declarations.signatures.single().function)
        assertSame(work.owner.type, DeclarationBinding(work.context, mapOf(call to facts)).expression(tree).type)
    }

    @Test fun readonlyDefaultsRetainTheConvertedTypedValueAndH04DefaultBoundary() {
        val fixture = fixture()
        fixture.function.readOnlyParams.add(
            FunctionParam(
                MCFPPBaseType.Float,
                "scale",
                fixture.function,
                hasDefault = true,
                isReadOnly = true
            ).apply {
                defaultValue = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(3))
            }
        )
        val work = work(fixture.file)
        val tree = expression("copy<T>(this)")
        val call = calls(tree).single()
        val facts = prepared(work, work.request(call))
        val signature = facts.declarations.signatures.single()
        assertEquals(1, facts.request.explicitReadonly.size)
        assertEquals(2, signature.readonlyArguments.size)
        assertEquals(
            CompilerValue.Typed(MCFPPBaseType.Float.typeId, CompilerValue.FloatBits(3f.toRawBits())),
            signature.readonlyArguments[1].value
        )
        assertTrue(signature.readonly[1].hasDefault)
        assertSame(work.owner.type, DeclarationBinding(work.context, mapOf(call to facts)).expression(tree).type)
        work.result.staged.requireOpen()
    }

    @Test fun nativeAndOrdinarySignaturesDoNotExecuteOrChangeActualCompilerState() {
        val fixture = fixture()
        nativeCalls = 0
        val native = NativeFunction(
            "nativeCopy",
            fixture.file.namespace.identifier,
            MniHeaderDeclarationReaderCallTest::class.java.getMethod("nativeSentinel", NativeCallContext::class.java)
        ).apply {
            declarationFile = fixture.file
            returnType = UnresolvedType("Node<U>")
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "U", this, isReadOnly = true))
            normalParams.add(FunctionParam(UnresolvedType("Node<U>"), "value", this))
            fixture.file.field.addFunction(this, force = true)
        }
        val work = work(fixture.file)
        val trees = listOf(expression("copy<T>(this)"), expression("nativeCopy<T>(this)"))
        val requests = trees.map { work.request(calls(it).single()) }
        val caller = Function("caller", "reader.actual", null).apply {
            commands.add(Command("say sentinel"))
            scope.vars["sentinel"] = MCInt(29, "sentinel")
        }
        caller.runInFunction {
            MCFPPFile.currFile = fixture.file
            val commands = caller.commands.toList()
            val vars = caller.scope.vars.toMap()
            val parents = caller.parent.toList()
            val children = caller.child.toList()
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val flow = StorageAccess.flowSnapshot(caller.scope.vars.values.toList())
            val context = Project.ctx.toList()
            val namespace = Project.currNamespace
            EffectAnalysis.withActualEffects(caller) {
                val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
                for ((index, tree) in trees.withIndex()) {
                    val facts = prepared(work, requests[index])
                    val call = calls(tree).single()
                    assertSame(work.owner.type, DeclarationBinding(work.context, mapOf(call to facts)).expression(tree).type)
                }
                assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            }
            assertSame(caller, Function.currFunction)
            assertSame(fixture.file, MCFPPFile.currFile)
            assertEquals(commands, caller.commands.toList())
            assertEquals(vars, caller.scope.vars)
            assertEquals(parents, caller.parent)
            assertEquals(children, caller.child)
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertEquals(flow, StorageAccess.flowSnapshot(caller.scope.vars.values.toList()))
            assertEquals(context, Project.ctx.toList())
            assertEquals(namespace, Project.currNamespace)
        }
        assertEquals(0, nativeCalls)
        assertTrue(native.commands.isEmpty())
        assertTrue(fixture.function.commands.isEmpty())
        assertTrue(fixture.head.compiledTemplates.isEmpty())
        assertEquals(0, fixture.head.index)
        assertEquals(0, Project.errorCount)
    }
}
