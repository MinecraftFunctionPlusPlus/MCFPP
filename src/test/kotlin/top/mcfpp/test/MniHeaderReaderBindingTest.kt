package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** These fixtures use actual declaration ASTs and H03; no standard-library loading is needed. */
class MniHeaderReaderBindingTest {
    private val source = SourceSpan("reader.mcfpp", 0, 8, 1, 1, 1, 9)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun expression(text: String): P.ExpressionContext = parser(text).let { parser ->
        parser.expression().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun syntax(text: String): P.TypeContext = parser(text).let { parser ->
        parser.type().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun fixture(): MCFPPFile {
        Project.init()
        val namespace = Namespace("reader.original")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun prototype(file: MCFPPFile, name: String = "Node", body: String = "{}") = GenericDataTemplate(parser(body).templateBody(), name, file.namespace.identifier).apply {
        declarationFile = file
        readOnlyParams.add(DataTemplateParam("Type", "T", MCFPPConcreteType.Type))
        // The real source declaration visitor registers generic heads in this template slot.
        file.namespace.scope.template[name] = this
    }
    private data class Work(
        val session: SignaturePreparationSession,
        val result: SignaturePreparationResult.NeedsCompletion
    ) {
        val owner get() = result.staged.headers.single()
        val lexical get() = result.requests.single().lexical
        fun context(fields: List<PreparedOwnedHeaderField> = emptyList()) = DeclarationPreparationContext.forHeader(lexical, owner, fields)
        fun binder(fields: List<PreparedOwnedHeaderField> = emptyList()) = DeclarationBinding(context(fields))
    }
    private fun work(file: MCFPPFile, name: String = "Node", argument: String = "int"): Work {
        val target = Function("target", file.namespace.identifier, null).apply {
            declarationFile = file
            normalParams.add(FunctionParam(UnresolvedType("$name<$argument>"), "input", this))
        }
        val session = SignaturePreparationSession()
        val result = assertIs<SignaturePreparationResult.NeedsCompletion>(
            session.prepare(
                SignaturePreparationRequest(listOf(target), emptyList(), source)
            )
        )
        return Work(session, result)
    }
    private fun field(owner: PreparedTemplateReservation, name: String, bound: DeclarationBinding.Bound) = PreparedOwnedHeaderField(
        owner,
        owner.prototype.ctx.templateMemberDeclaration()
            .mapNotNull { it.templateMember().templateFieldDeclaration() }
            .single { it.Identifier().text == name },
        bound
    )
    private fun integer(value: Long) = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(value))
    private fun failure(code: PreparationFactCode, action: () -> Unit): PreparationFactFailure = assertFailsWith<PreparationFactFailure>(block = action).also { assertEquals(code, it.code) }

    @Test fun realNeedsCompletionAllowsOnlyItsReaderToUseTheReservedSelf() {
        val file = fixture()
        val head = prototype(file)
        val work = work(file)
        assertEquals(HeaderPhase.RESERVED, work.owner.phase)
        assertSame(work.owner.type, work.binder().type(syntax("Node<T>")))
        assertSame(work.owner.type, work.binder().expression(expression("this")).type)
        val ordinary = DeclarationBinding(DeclarationPreparationContext(work.lexical))
        failure(PreparationFactCode.UNPREPARED_TYPE) { ordinary.preparedDeclaredType(work.owner.type, source) }
        work.result.staged.requireOpen()
        assertFalse(work.owner.isPublished)
        assertTrue(head.compiledTemplates.isEmpty())
        assertEquals(0, head.index)
        assertEquals(0, Project.errorCount)
    }

    @Test fun addingAHeaderPreservesThePendingBatchUntilExplicitRefresh() {
        val file = fixture()
        prototype(file)
        val base = prototype(file, "Base")
        val work = work(file)
        val old = work.result.staged
        val binder = work.binder()
        val child = binder.type(syntax("Base<T>"))
        assertSame(child, binder.type(syntax("Base<T>")))
        old.requireOpen()
        assertEquals(1, old.headers.size)
        assertSame(work.owner, old.headers.single())
        val refreshed = work.session.refreshHeaderReaderBatch(work.owner)
        assertTrue(old.discarded)
        assertFalse(refreshed.discarded)
        assertEquals(2, refreshed.headers.size)
        assertSame(work.owner, refreshed.headers[0])
        assertSame(base, refreshed.headers[1].prototype)
        assertSame(child, refreshed.headers[1].type)
        assertSame(old.requests.single(), refreshed.requests.single())
        assertEquals(old.declarations, refreshed.declarations)
        assertSame(work.owner.type, binder.type(syntax("Node<T>")))
        assertTrue(base.compiledTemplates.isEmpty())
        assertEquals(0, base.index)
    }

    @Test fun mutualReferencesReuseTheSameOwnedShellWithoutPublication() {
        val file = fixture()
        val node = prototype(file)
        val peer = prototype(file, "Peer")
        val work = work(file)
        val peerType = work.binder().type(syntax("Peer<T>"))
        val batch = work.session.refreshHeaderReaderBatch(work.owner)
        val peerOwner = batch.headers.single { it.prototype === peer }
        val peerReader = DeclarationBinding(DeclarationPreparationContext.forHeader(work.lexical, peerOwner))
        assertSame(work.owner.type, peerReader.type(syntax("Node<T>")))
        assertSame(peerType, peerReader.type(syntax("Peer<T>")))
        assertEquals(2, batch.headers.size)
        for (head in listOf(node, peer)) {
            assertTrue(head.compiledTemplates.isEmpty())
            assertEquals(0, head.index)
        }
        assertTrue(batch.headers.all { it.phase == HeaderPhase.RESERVED && !it.isPublished })
    }

    @Test fun anotherSessionCannotBorrowAReservationEvenWithTheSameTypedKey() {
        val file = fixture()
        prototype(file)
        val first = work(file)
        val second = work(file)
        assertEquals(first.owner.key, second.owner.key)
        assertNotSame(first.owner.model, second.owner.model)
        val error = failure(PreparationFactCode.UNPREPARED_TYPE) {
            DeclarationPreparationContext.forHeader(second.lexical, first.owner, source = source)
        }
        assertEquals(source, error.source)
        assertSame(second.owner.type, second.binder().type(syntax("Node<T>")))
        first.result.staged.requireOpen()
        second.result.staged.requireOpen()
    }

    @Test fun aDetachedReservationWithAnEqualKeyDoesNotGrantReaderAuthority() {
        val file = fixture()
        val head = prototype(file)
        val work = work(file)
        val detached = PreparedTemplateReservation.create(head, work.owner.key, work.owner.readonlyFacts, source)
        assertEquals(work.owner.key, detached.key)
        failure(PreparationFactCode.UNPREPARED_TYPE) {
            DeclarationPreparationContext.forHeader(work.lexical, detached)
        }
        assertSame(work.owner.type, work.binder().type(syntax("Node<T>")))
    }

    @Test fun bothSessionAndExplicitBatchExpirationRejectExistingReaderContextsAtTheUseSite() {
        val file = fixture()
        prototype(file)
        val first = work(file)
        val binder = first.binder()
        first.session.discard()
        val site = expression("1 + 2")
        assertEquals(preparationSpan(site), failure(PreparationFactCode.UNPREPARED_TYPE) { binder.expression(site) }.source)
        val second = work(file)
        val secondBinder = second.binder()
        second.result.staged.discard()
        val self = expression("this")
        assertEquals(preparationSpan(self), failure(PreparationFactCode.UNPREPARED_TYPE) { secondBinder.expression(self) }.source)
    }

    @Test fun bareAndThisFieldsShareOriginalFactsWhileOnlyConstFieldsAreClosed() {
        val file = fixture()
        prototype(file, body = "{ var value:int = 1; const fixed:int = 7; var next:Node<T>; }")
        val work = work(file)
        val fields = listOf(
            field(work.owner, "value", DeclarationBinding.Bound(MCFPPBaseType.Int, integer(1))),
            field(work.owner, "fixed", DeclarationBinding.Bound(MCFPPBaseType.Int, integer(7))),
            field(work.owner, "next", DeclarationBinding.Bound(work.owner.type))
        )
        val binder = work.binder(fields)
        for (text in listOf("value", "this.value")) {
            val value = binder.expression(expression(text))
            assertSame(MCFPPBaseType.Int, value.type)
            assertNull(value.constant)
            failure(PreparationFactCode.NON_CLOSED_VALUE) { binder.readonly(expression(text)) }
        }
        for (text in listOf("fixed", "this.fixed")) assertEquals(integer(7), binder.readonly(expression(text)).constant)
        assertSame(work.owner.type, binder.expression(expression("this.next")).type)
        failure(PreparationFactCode.UNSUPPORTED_EXPRESSION) { binder.expression(expression("next.value")) }
        assertTrue(work.owner.model.scope.allVars.isEmpty())
    }

    @Test fun onlyTheExactOwnedAstDeclarationCanSupplyAFieldFact() {
        val file = fixture()
        prototype(file, body = "{ var value:int; }")
        val other = prototype(file, "Other", "{ var value:int; }")
        val work = work(file)
        val foreignDeclaration = other.ctx.templateMemberDeclaration().single().templateMember().templateFieldDeclaration()
        failure(PreparationFactCode.UNPREPARED_TYPE) {
            work.context(
                listOf(
                    PreparedOwnedHeaderField(work.owner, foreignDeclaration, DeclarationBinding.Bound(MCFPPBaseType.Int))
                )
            )
        }
        val duplicate = field(work.owner, "value", DeclarationBinding.Bound(MCFPPBaseType.Int))
        failure(PreparationFactCode.UNPREPARED_TYPE) { work.context(listOf(duplicate, duplicate)) }
        val childType = work.binder().type(syntax("Other<T>"))
        val child = work.session.refreshHeaderReaderBatch(work.owner).headers.single { it.type === childType }
        failure(PreparationFactCode.UNPREPARED_TYPE) { DeclarationPreparationContext.forHeader(work.lexical, child, listOf(duplicate)) }
    }

    @Test fun originalReadonlyFormalsAndOwnedFieldsShadowCallerAliases() {
        val file = fixture()
        prototype(file, body = "{ var value:T; }")
        file.field.putResolvedAlias("value", MCFPPBaseType.Bool, forced = true)
        val work = work(file)
        file.field.putResolvedAlias("T", MCFPPBaseType.Bool, forced = true)
        val binder = work.binder(listOf(field(work.owner, "value", DeclarationBinding.Bound(MCFPPBaseType.Int))))
        assertSame(MCFPPBaseType.Int, binder.type(syntax("T")))
        assertSame(MCFPPBaseType.Int, binder.expression(expression("value")).type)
        assertEquals(
            CompilerValue.TypeValue(MCFPPBaseType.Int.typeId),
            assertIs<CompilerValue.Typed>(binder.readonly(expression("T")).constant).payload
        )
        assertSame(work.owner.type, binder.type(syntax("Node<T>")))
    }

    @Test fun anUnpreparedOwnedFieldNeverFallsThroughToAFreeDeclarationOfTheSameName() {
        val file = fixture()
        prototype(file, body = "{ var pending:int; }")
        file.field.putResolvedAlias("pending", MCFPPBaseType.Bool, forced = true)
        val work = work(file)
        val binder = work.binder()
        for (text in listOf("pending", "this.pending")) {
            val site = expression(text)
            val failure = failure(PreparationFactCode.UNPREPARED_TYPE) { binder.expression(site) }
            assertEquals(source.file, failure.source.file)
            assertTrue(failure.source.startOffset >= 0)
        }
        assertTrue(work.owner.model.scope.allVars.isEmpty())
    }

    @Test fun ownedFieldCollectionsAndReturnedTypesCannotMutateTheStoredFacts() {
        val file = fixture()
        prototype(file, body = "{ var values:list<int>; }")
        val work = work(file)
        val list = MCFPPListType(MCFPPBaseType.Int)
        val descriptors = linkedMapOf<TypeId, MCFPPType>(list.typeId to list)
        val dependencies = linkedSetOf("T")
        val binder = work.binder(
            listOf(
                field(
                    work.owner,
                    "values",
                    DeclarationBinding.Bound(
                        list,
                        dependencies = dependencies,
                        descriptors = descriptors
                    )
                )
            )
        )
        list.generic[0] = MCFPPBaseType.Bool
        descriptors.clear()
        dependencies.clear()
        val first = binder.expression(expression("values"))
        assertSame(MCFPPBaseType.Int, assertIs<MCFPPListType>(first.type).generic.single())
        assertEquals(setOf("T"), first.dependencies)
        assertTrue(first.descriptors.isNotEmpty())
        assertIs<MCFPPListType>(first.type).generic[0] = MCFPPBaseType.Float
        val second = binder.expression(expression("this.values"))
        assertSame(MCFPPBaseType.Int, assertIs<MCFPPListType>(second.type).generic.single())
        assertEquals(setOf("T"), second.dependencies)
    }

    @Test fun readonlyTypeLookupCannotMutateTheReservedCompositeArgumentOrCreateAnotherShell() {
        val file = fixture()
        val head = prototype(file)
        val work = work(file, argument = "list<int>")
        val binder = work.binder()
        val key = work.owner.key
        val exported = assertIs<MCFPPListType>(binder.type(syntax("T")))
        assertSame(MCFPPBaseType.Int, exported.generic.single())
        exported.generic[0] = MCFPPBaseType.Bool
        repeat(2) {
            assertSame(MCFPPBaseType.Int, assertIs<MCFPPListType>(binder.type(syntax("T"))).generic.single())
            assertSame(work.owner.type, binder.type(syntax("Node<T>")))
        }
        val refreshed = work.session.refreshHeaderReaderBatch(work.owner)
        assertSame(work.owner, refreshed.headers.single())
        assertEquals(key, work.owner.key)
        assertTrue(head.compiledTemplates.isEmpty())
        assertEquals(0, head.index)
    }

    @Test fun readonlyExpressionDescriptorsCannotMutateLaterTypeAndReadonlyBindings() {
        val file = fixture()
        val head = prototype(file)
        val work = work(file, argument = "list<int>")
        val binder = work.binder()
        val key = work.owner.key
        val originalType = MCFPPListType(MCFPPBaseType.Int).typeId
        val fact = binder.readonly(expression("T"))
        assertEquals(CompilerValue.TypeValue(originalType), assertIs<CompilerValue.Typed>(fact.constant).payload)
        assertIs<MCFPPListType>(fact.descriptors.getValue(originalType)).generic[0] = MCFPPBaseType.Bool
        repeat(2) {
            val rebound = binder.readonly(expression("T"))
            assertEquals(CompilerValue.TypeValue(originalType), assertIs<CompilerValue.Typed>(rebound.constant).payload)
            assertSame(MCFPPBaseType.Int, assertIs<MCFPPListType>(rebound.descriptors.getValue(originalType)).generic.single())
            assertSame(MCFPPBaseType.Int, assertIs<MCFPPListType>(binder.type(syntax("T"))).generic.single())
            assertSame(work.owner.type, binder.type(syntax("Node<T>")))
        }
        assertSame(work.owner, work.session.refreshHeaderReaderBatch(work.owner).headers.single())
        assertEquals(key, work.owner.key)
        assertTrue(head.compiledTemplates.isEmpty())
        assertEquals(0, head.index)
    }

    @Test fun unknownFieldShapesAndMalformedConstProofsFailBeforeOpenTypeGetters() {
        val file = fixture()
        prototype(file, body = "{ var value; const fixed:int = 7; }")
        val work = work(file)
        var calls = 0
        val opaque = object : MCFPPType() {
            override val typeId: TypeId get() {
                calls++
                error("Unexpected identity getter")
            }
            override val typeName: String get() {
                calls++
                error("Unexpected name getter")
            }
        }
        failure(PreparationFactCode.UNPREPARED_TYPE) {
            work.context(
                listOf(
                    field(work.owner, "value", DeclarationBinding.Bound(opaque))
                )
            )
        }
        assertEquals(0, calls)
        failure(PreparationFactCode.NON_CLOSED_VALUE) {
            work.context(
                listOf(
                    field(
                        work.owner,
                        "fixed",
                        DeclarationBinding.Bound(
                            MCFPPBaseType.Int,
                            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Text("wrong"))
                        )
                    )
                )
            )
        }
        assertTrue(work.owner.model.scope.allVars.isEmpty())
    }

    @Test fun readerBindingPreservesActualCompilerFramesCommandsResourcesFlowAndAllocationCounters() {
        val file = fixture()
        val head = prototype(file, body = "{ var value:int; }")
        val work = work(file)
        val caller = Function("caller", "reader.caller", null)
        caller.scope.vars["sentinel"] = MCInt(29, "sentinel")
        caller.commands.add(Command("say sentinel"))
        val fields = listOf(field(work.owner, "value", DeclarationBinding.Bound(MCFPPBaseType.Int)))
        caller.runInFunction {
            MCFPPFile.currFile = file
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
                repeat(2) {
                    val binder = work.binder(fields)
                    assertSame(work.owner.type, binder.type(syntax("Node<T>")))
                    assertSame(MCFPPBaseType.Int, binder.expression(expression("this.value + 1")).type)
                    failure(PreparationFactCode.UNSUPPORTED_EXPRESSION) { binder.expression(expression("this.value()")) }
                    failure(PreparationFactCode.UNSUPPORTED_EXPRESSION) { binder.expression(expression("host()")) }
                }
                assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            }
            assertSame(caller, Function.currFunction)
            assertSame(file, MCFPPFile.currFile)
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
        assertTrue(head.compiledTemplates.isEmpty())
        assertEquals(0, head.index)
        assertTrue(work.owner.model.scope.allVars.isEmpty())
        assertEquals(0, Project.errorCount)
    }
}
