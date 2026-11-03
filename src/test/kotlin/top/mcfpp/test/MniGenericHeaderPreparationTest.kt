package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
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

/** Real declaration ASTs and real H03/H04 staging; no bundled library or eager visitor is used. */
class MniGenericHeaderPreparationTest {
    companion object {
        private var calls = 0
        @JvmStatic fun nativeSentinel(context: NativeCallContext) {
            calls++
        }
    }
    private val source = SourceSpan("header-queue.mcfpp", 0, 7, 1, 1, 1, 8)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun file(): MCFPPFile {
        Project.init()
        val namespace = Namespace("header.queue")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun prototype(file: MCFPPFile, text: String): GenericDataTemplate {
        val parser = parser(text)
        val declaration = parser.templateDeclaration()
        assertEquals(0, parser.numberOfSyntaxErrors, text)
        assertEquals(Token.EOF, parser.currentToken.type, text)
        val header = declaration.compoundDeclaration()
        val name = header.declarationName().classWithoutNamespace().text
        return GenericDataTemplate(declaration.templateBody(), name, file.namespace.identifier).apply {
            declarationFile = file
            header.declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { index, parameter ->
                readOnlyParams.add(
                    DataTemplateParam(
                        ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                        ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                        if (ReadonlyParamsDeclarationSyntax.isBareTypeParameter(parameter)) MCFPPConcreteType.Type else null
                    )
                )
            }
            parentID.addAll(header.extendName().map { it.text })
            file.namespace.scope.template[name] = this
        }
    }
    private data class Work(val session: SignaturePreparationSession, val result: SignaturePreparationResult.NeedsCompletion) {
        val root get() = result.staged.headers.single()
        val lexical get() = result.requests.single().lexical
        fun read(limits: HeaderReadLimits = HeaderReadLimits()) = session.readHeaders(result.staged, lexical, limits)
    }
    private fun work(file: MCFPPFile, type: String = "Node<int>"): Work {
        val target = Function("target", file.namespace.identifier, null).apply {
            declarationFile = file
            normalParams.add(FunctionParam(UnresolvedType(type), "input", this))
        }
        val session = SignaturePreparationSession()
        val result = session.prepare(SignaturePreparationRequest(listOf(target), emptyList(), source))
        val reason = (result as? SignaturePreparationResult.Rejected)?.diagnostics?.joinToString { "${it.code}: ${it.message}" }
        return Work(session, assertIs<SignaturePreparationResult.NeedsCompletion>(result, reason))
    }
    private fun copy(file: MCFPPFile, result: String = "Node<U>") = GenericFunction(
        "copy",
        file.namespace.identifier,
        parser("{ return value }").curlBlock()
    ).apply {
        declarationFile = file
        readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "U", this, isReadOnly = true))
        normalParams.add(FunctionParam(UnresolvedType("Node<U>"), "value", this))
        returnType = UnresolvedType(result)
        file.field.addFunction(this, force = true)
    }
    private fun local(result: HeaderReadResult, prototype: GenericDataTemplate): PreparedTemplateHeader = assertIs<HeaderReadResult.LocalHeaders>(result).headers.single { it.owner.prototype === prototype }
    private fun unchanged(vararg prototypes: GenericDataTemplate) {
        for (prototype in prototypes) {
            assertEquals(0, prototype.index)
            assertTrue(prototype.compiledTemplates.isEmpty())
            assertTrue(prototype.scope.allVars.isEmpty())
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun firstApplicationReadsOriginalFieldsParentAndMemberTypesWithoutMaterializingAnything() {
        val file = file()
        val base = prototype(file, "data Base<T>{var base:T;}")
        val node = prototype(
            file,
            """
            data Node<T>:Base<T>{
                private var value:T;
                var items:list<T>?;
                func identity(value:T):T{return value}
                constructor(value:T){}
            }
            """.trimIndent()
        )
        val work = work(file)
        val result = assertIs<HeaderReadResult.LocalHeaders>(work.read())
        val header = local(result, node)
        assertSame(work.root, header.owner)
        assertEquals(listOf("value", "items"), header.fields.map { it.name })
        assertSame(MCFPPBaseType.Int, header.fields[0].type)
        assertEquals(AccessModifier.PRIVATE, header.fields[0].access)
        assertFalse(header.fields[0].isStatic)
        assertSame(MCFPPBaseType.Int, assertIs<MCFPPListType>(header.fields[1].type).generic.single())
        assertTrue(header.fields[1].nullable)
        assertSame(local(result, base).owner.type, header.parents.single().type)
        val method = header.members.first()
        assertEquals("identity", method.name)
        assertFalse(method.isStatic)
        assertSame(MCFPPBaseType.Int, method.normal.single().type.type)
        assertSame(MCFPPBaseType.Int, method.result.type)
        assertSame(
            node.ctx.templateMemberDeclaration()[2].templateMember().templateFunctionDeclaration()
                .functionDeclarationPart().functionParams().normalParams().parameterList().parameter().single(),
            method.normal.single().declaration
        )
        assertEquals(PreparedHeaderMemberKind.CONSTRUCTOR, header.members.last().kind)
        assertEquals(source.file, header.fields.first().source.file)
        assertTrue(header.hasCompleteInheritedView)
        assertEquals(listOf("base", "value", "items"), header.visibleFields.map { it.name })
        assertEquals(
            listOf(local(result, base).owner.identity, work.root.identity, work.root.identity),
            header.visibleFields.map { it.origin }
        )
        assertSame(MCFPPBaseType.Int, header.visibleFields[0].bound.type)
        assertSame(MCFPPBaseType.Int, header.visibleFields[1].bound.type)
        val visibleItems = assertIs<MCFPPListType>(header.visibleFields[2].bound.type)
        assertSame(MCFPPBaseType.Int, visibleItems.generic.single())
        assertTrue(header.visibleFields[2].contract.nullable)
        assertEquals(HeaderPhase.HEADER_READY, work.root.phase)
        assertFalse(work.root.model.hasCompletedDeclaration)
        assertTrue(work.root.model.scope.allVars.isEmpty())
        assertTrue(work.result.staged.discarded)
        unchanged(node, base)
    }

    @Test fun localFieldInferenceUsesEarlierConstantsAndTheExistingPreparedChildCallSelector() {
        val file = file()
        val node = prototype(file, "data Node<T>{const seed=7;var total=seed+2;var copied=copy<T>(this);}")
        val function = copy(file)
        val work = work(file)
        val header = local(work.read(), node)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), header.fields[0].bound.constant)
        assertSame(MCFPPBaseType.Int, header.fields[1].type)
        assertNull(header.fields[1].bound.constant)
        assertSame(work.root.type, header.fields[2].type)
        assertNull(header.fields[2].bound.constant)
        assertTrue(function.compiledFunctions.isEmpty())
        unchanged(node)
    }

    @Test fun forwardOwnedFactsConvergeWithoutFallingBackToFreeNamesOrRuntimeAdapters() {
        val file = file()
        val node = prototype(file, "data Node<T>{var first=last+1;const last=7;var fromThis=this.last;}")
        file.field.putResolvedAlias("last", MCFPPBaseType.Float, forced = true)
        val header = local(work(file).read(), node)
        assertEquals(listOf("first", "last", "fromThis"), header.fields.map { it.name })
        header.fields.forEach { assertSame(MCFPPBaseType.Int, it.type) }
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), header.fields[1].bound.constant)
        unchanged(node)
    }

    @Test fun readerUsesTheFrozenOriginalAliasAndKeepsMethodFormalTemplatesWithTheirAstIdentity() {
        val file = file()
        file.field.putResolvedAlias("Chosen", MCFPPBaseType.Int, forced = true)
        val node = prototype(file, "data Node<T>{var value:Chosen;func convert<U>(value:list<U>):U{return value};}")
        val work = work(file)
        file.field.putResolvedAlias("Chosen", MCFPPBaseType.Float, forced = true)
        val header = local(work.read(), node)
        assertSame(MCFPPBaseType.Int, header.fields.single().type)
        val method = header.members.single()
        assertSame(MCFPPConcreteType.Type, method.readonly.single().type.type)
        assertNull(method.normal.single().type.type)
        assertEquals(setOf("U"), method.normal.single().type.dependencies)
        assertEquals(setOf("U"), method.result.dependencies)
        assertEquals("list<U>", method.normal.single().type.syntax!!.text)
        unchanged(node)
    }

    @Test fun memberDefaultsUseSharedFloatConversionAndRetainTheirOriginalValueSyntax() {
        val file = file()
        val node = prototype(file, "data Node<T>{func scale(value:float=3):float{return value};}")
        val formal = local(work(file).read(), node).members.single().normal.single()
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Float.typeId, CompilerValue.FloatBits(3f.toRawBits())), formal.defaultValue)
        assertEquals("3", formal.defaultSyntax!!.text)
        assertSame(MCFPPBaseType.Float, formal.defaultTypes[MCFPPBaseType.Float.typeId])
        assertFalse(formal.defaultNeedsBinding)
        unchanged(node)
    }

    @Test fun recursiveNodeFieldsUseTheSameReservedShellAndDoNotCreateAnotherSpecialization() {
        val file = file()
        val node = prototype(file, "data Node<T>{var next:Node<T>?;}")
        val work = work(file)
        val result = assertIs<HeaderReadResult.LocalHeaders>(work.read())
        assertEquals(1, result.headers.size)
        assertSame(work.root.type, result.headers.single().fields.single().type)
        assertEquals(1, result.staged.headers.size)
        unchanged(node)
    }

    @Test fun mutualFieldsCloseThroughCanonicalHeadersWhileDifferentBindingsStayIndependent() {
        val file = file()
        val node = prototype(file, "data Node<T>{var peer:Peer<T>;var other:Peer<bool>;}")
        val peer = prototype(file, "data Peer<T>{var node:Node<T>;}")
        val work = work(file)
        val result = assertIs<HeaderReadResult.LocalHeaders>(work.read())
        assertEquals(4, result.headers.size)
        val root = result.headers.single { it.owner === work.root }
        val first = root.fields[0].type
        val second = root.fields[1].type
        assertNotSame(first, second)
        assertNotEquals(first.typeId, second.typeId)
        val intPeer = result.headers.single { it.owner.type === first }
        assertSame(work.root.type, intPeer.fields.single().type)
        assertEquals(2, result.headers.count { it.owner.prototype === node })
        assertEquals(2, result.headers.count { it.owner.prototype === peer })
        unchanged(node, peer)
    }

    @Test fun returnTypeHeadersDiscoveredByPreparedCallsAreReadInTheirOwnOriginalDomain() {
        val file = file()
        val node = prototype(file, "data Node<T>{var copied=copy<T>(this);}")
        val peer = prototype(file, "data Peer<T>{var content:T;}")
        copy(file, "Peer<U>")
        val result = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        val nodeHeader = local(result, node)
        val peerHeader = local(result, peer)
        assertSame(peerHeader.owner.type, nodeHeader.fields.single().type)
        assertSame(MCFPPBaseType.Int, peerHeader.fields.single().type)
        unchanged(node, peer)
    }

    @Test fun constantOwnedReadonlyValuesCreateExactTypedKeysButMutableFieldsCannotSupplyProofs() {
        val file = file()
        val box = prototype(file, "data Box<N:int>{var content:int;}")
        val node = prototype(file, "data Node<T>{const count=7;var sized:Box<count>;}")
        val result = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        val boxHeader = local(result, box)
        assertEquals(listOf(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7))), boxHeader.owner.identity.arguments)
        assertSame(boxHeader.owner.type, local(result, node).fields[1].type)
        unchanged(node, box)
        val badFile = file()
        val badBox = prototype(badFile, "data Box<N:int>{}")
        val badNode = prototype(badFile, "data Node<T>{var count=7;var sized:Box<count>;}")
        val badWork = work(badFile)
        val bad = assertIs<HeaderReadResult.Rejected>(badWork.read())
        assertEquals(PreparationFactCode.NON_CLOSED_VALUE, bad.diagnostics.single().code)
        assertNull(badWork.root.localHeader)
        unchanged(badNode, badBox)
    }

    @Test fun fieldInferenceCyclesHaveAStableSpanAndNeverPublishPartialRecords() {
        val file = file()
        val node = prototype(file, "data Node<T>{var first=second;var second=first;}")
        val work = work(file)
        val result = assertIs<HeaderReadResult.Rejected>(work.read())
        val diagnostic = result.diagnostics.single()
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, diagnostic.code)
        assertEquals(source.file, diagnostic.source.file)
        assertTrue(diagnostic.message.contains("no progress"))
        assertTrue(diagnostic.message.contains("first"))
        assertTrue(diagnostic.message.contains("second"))
        assertTrue(diagnostic.message.contains("header path"))
        assertNull(work.root.localHeader)
        assertEquals(HeaderPhase.RESERVED, work.root.phase)
        work.result.staged.requireOpen()
        unchanged(node)
    }

    private fun growth(limits: HeaderReadLimits, message: String) {
        val file = file()
        val node = prototype(file, "data Node<T>{var next:Node<list<T>>;}")
        val work = work(file)
        val result = assertIs<HeaderReadResult.Rejected>(work.read(limits))
        val diagnostic = result.diagnostics.single()
        assertTrue(diagnostic.message.contains(message), diagnostic.message)
        assertTrue(diagnostic.message.contains("header.queue:Node"), diagnostic.message)
        assertEquals(source.file, diagnostic.source.file)
        assertTrue(diagnostic.source.endOffset > diagnostic.source.startOffset)
        assertEquals(listOf(work.root), work.session.readerReservations(work.root))
        assertEquals(HeaderPhase.RESERVED, work.root.phase)
        assertNull(work.root.localHeader)
        work.result.staged.requireOpen()
        unchanged(node)
    }
    @Test fun growthStopsAtTheSpecializationBudgetWithoutConsumingNames() = growth(HeaderReadLimits(maxSpecializations = 3), "specialization budget")
    @Test fun growthStopsAtThePathDepthBudgetWithoutLosingTheOriginalReservation() = growth(HeaderReadLimits(maxDepth = 2), "depth budget")
    @Test fun growthStopsAtTheQueueRoundBudgetAndReportsItsTypedRequestPath() = growth(HeaderReadLimits(maxRounds = 2), "round budget")
    @Test fun syntaxNodeBudgetRejectsBeforeAnyHeaderOrNameIsPublished() = growth(HeaderReadLimits(maxNodes = 2), "node budget")

    @Test fun anInferenceRoundBudgetIsDistinctFromARecursiveTypeReference() {
        val file = file()
        val node = prototype(file, "data Node<T>{var a=b;var b=c;const c=7;}")
        val work = work(file)
        val result = assertIs<HeaderReadResult.Rejected>(work.read(HeaderReadLimits(maxRounds = 1)))
        assertTrue(result.diagnostics.single().message.contains("inference round budget"))
        assertNull(work.root.localHeader)
        unchanged(node)
    }

    @Test fun foreignAndDiscardedBatchesCannotBeReadOrReviveAReservation() {
        val file = file()
        val node = prototype(file, "data Node<T>{var next:Node<T>;}")
        val first = work(file)
        val foreign = SignaturePreparationSession()
        assertIs<HeaderReadResult.Rejected>(foreign.readHeaders(first.result.staged, first.lexical))
        first.session.discard()
        assertIs<HeaderReadResult.Rejected>(first.read())
        assertNull(first.root.localHeader)
        unchanged(node)
    }

    @Test fun originalAnnotationsPropertiesAndOperatorHeadsAreRecordedWithoutExecutingBodies() {
        val file = file()
        val node = prototype(
            file,
            """
            data Node<T>{
                @Observed
                protected var slot:T { get { return slot }; set { slot=value } };
                operator +(other:T):T{return other}
                func ignored(){return}
            }
            """.trimIndent()
        )
        val result = local(work(file).read(), node)
        val field = result.fields.single()
        assertEquals(AccessModifier.PROTECTED, field.access)
        assertEquals(PreparedHeaderProperty.GET_SET, field.property)
        assertSame(node.ctx.templateMemberDeclaration().first().templateMember().annotation(), field.annotations.single())
        assertEquals(PreparedHeaderMemberKind.OPERATOR, result.members.first().kind)
        assertSame(MCFPPBaseType.Int, result.members.first().result.type)
        unchanged(node)
    }

    @Test fun privateHeaderCheckpointAndDeepJournalRetainRecordsAfterTheirCacheRelationshipIsLost() {
        val file = file()
        val node = prototype(file, "data Node<T>{var values:list<T>;var next:Node<T>;}")
        val work = work(file)
        val before = DeclarationStateJournal.checkpoint(listOf(node))
        val result = assertIs<HeaderReadResult.LocalHeaders>(work.read())
        val record = result.headers.single()
        result.staged.publishHeaders()
        val after = before.captureCurrent()
        val originalType = assertIs<MCFPPListType>(record.fields.first().type)
        originalType.generic[0] = MCFPPBaseType.Bool
        val mutated = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertNull(node.preparedHeader(work.root.key))
            assertEquals(0, node.index)
            after.restore()
            assertSame(work.root, node.preparedHeader(work.root.key))
            assertSame(record, work.root.localHeader)
            assertSame(MCFPPBaseType.Int, originalType.generic.single())
            assertEquals(HeaderPhase.HEADER_READY, work.root.phase)
            assertEquals(1, node.index)
            mutated.restore()
            assertSame(MCFPPBaseType.Bool, originalType.generic.single())
        }
        before.restore()
        unchanged(node)
    }

    @Test fun aHeaderRecordStillCannotTurnAnOrdinarySignatureRequestIntoReady() {
        val file = file()
        val node = prototype(file, "data Node<T>{var value:T;}")
        val work = work(file)
        val result = assertIs<HeaderReadResult.LocalHeaders>(work.read())
        result.staged.publishHeaders()
        val target = work.result.requests.single().function
        val prepared = SignaturePreparationSession().prepare(SignaturePreparationRequest(listOf(target), emptyList(), source))
        assertIs<SignaturePreparationResult.NeedsCompletion>(prepared)
        assertFalse(work.root.model.hasCompletedDeclaration)
        assertTrue(node.compiledTemplates.isEmpty())
    }

    @Test fun anotherSessionReusesPublishedFactsWithoutReplacingTheCanonicalRecord() {
        val file = file()
        file.field.putResolvedAlias("Chosen", MCFPPBaseType.Int, forced = true)
        val node = prototype(file, "data Node<T>{var value:Chosen;}")
        val first = work(file)
        val original = assertIs<HeaderReadResult.LocalHeaders>(first.read())
        val record = original.headers.single()
        original.staged.publishHeaders()
        val second = work(file)
        assertSame(first.root, second.root)
        val reused = assertIs<HeaderReadResult.LocalHeaders>(second.read())
        assertSame(record, reused.headers.single())
        assertSame(record, first.root.localHeader)
        assertSame(MCFPPBaseType.Int, record.fields.single().type)
        assertEquals(1, node.index)
        reused.staged.publishHeaders()
        assertSame(record, first.root.localHeader)
        assertEquals(1, node.index)
        assertTrue(node.compiledTemplates.isEmpty())
    }

    @Test fun changedAliasesRejectNewReadersButPreservePublishedAndPreviouslyFrozenFacts() {
        val file = file()
        file.field.putResolvedAlias("Chosen", MCFPPBaseType.Int, forced = true)
        val node = prototype(file, "data Node<T>{var value:Chosen;}")
        val first = work(file)
        val original = assertIs<HeaderReadResult.LocalHeaders>(first.read())
        val record = original.headers.single()
        original.staged.publishHeaders()
        val frozen = work(file)
        file.field.putResolvedAlias("Chosen", MCFPPBaseType.Float, forced = true)
        val changed = work(file)
        assertSame(first.root, changed.root)
        val failure = assertIs<HeaderReadResult.Rejected>(changed.read()).diagnostics.single()
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, failure.code)
        assertEquals(changed.root.source, failure.source)
        assertTrue(failure.message.contains("Published header facts changed"))
        changed.result.staged.requireOpen()
        assertSame(record, first.root.localHeader)
        assertSame(MCFPPBaseType.Int, record.fields.single().type)
        assertEquals(1, node.index)
        val preserved = assertIs<HeaderReadResult.LocalHeaders>(frozen.read())
        assertSame(record, preserved.headers.single())
        preserved.staged.publishHeaders()
        assertSame(record, first.root.localHeader)
        assertEquals(1, node.index)
        assertTrue(node.compiledTemplates.isEmpty())
    }

    @Test fun aPublishedShellReceivesItsFirstLocalRecordOnlyAtExplicitPublication() {
        val file = file()
        file.field.putResolvedAlias("Chosen", MCFPPBaseType.Int, forced = true)
        val node = prototype(file, "data Node<T>{var value:Chosen;var peer:Peer<T>;}")
        val peer = prototype(file, "data Peer<T>{}")
        val first = work(file)
        first.result.staged.publishHeaders()
        assertNull(first.root.localHeader)
        val next = work(file)
        assertSame(first.root, next.root)
        val rejected = assertIs<HeaderReadResult.Rejected>(next.read(HeaderReadLimits(maxSpecializations = 1)))
        assertTrue(rejected.diagnostics.single().message.contains("specialization budget"))
        assertEquals(1, next.session.readerReservations(next.root).size)
        next.result.staged.publishHeaders()
        assertNull(first.root.localHeader)
        assertEquals(0, peer.index)
        val prepared = assertIs<HeaderReadResult.LocalHeaders>(next.read())
        val record = local(prepared, node)
        assertSame(MCFPPBaseType.Int, record.fields.first().type)
        assertNull(first.root.localHeader)
        assertEquals(HeaderPhase.RESERVED, first.root.phase)
        assertEquals(1, node.index)
        prepared.staged.publishHeaders()
        assertSame(record, first.root.localHeader)
        assertEquals(HeaderPhase.HEADER_READY, first.root.phase)
        assertEquals(1, node.index)
        assertTrue(node.compiledTemplates.isEmpty())
        assertEquals(1, peer.index)
    }

    @Test fun bodyGetterNativeAndAnnotationExecutionAndActualCompilerStateRemainUntouched() {
        val file = file()
        val node = prototype(file, "data Node<T>{var read=nativeValue();var copied=copy<T>(this);func body():int{return 99};}")
        copy(file)
        val host = NativeFunction(
            "nativeValue",
            file.namespace.identifier,
            MniGenericHeaderPreparationTest::class.java.getMethod("nativeSentinel", NativeCallContext::class.java)
        ).apply {
            declarationFile = file
            returnType = MCFPPBaseType.Int
            file.field.addFunction(this, force = true)
        }
        val annotation = Probe()
        node.annotations.add(annotation)
        val current = Function("current", file.namespace.identifier, null)
        current.commands.add(Command("say sentinel"))
        current.scope.vars["sentinel"] = MCInt(29, "sentinel")
        calls = 0
        val work = work(file)
        current.runInFunction {
            MCFPPFile.currFile = file
            val commands = current.commands.toList()
            val variables = current.scope.vars.toMap()
            val children = current.child.toList()
            val parents = current.parent.toList()
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val flow = StorageAccess.flowSnapshot(current.scope.vars.values.toList())
            val contexts = Project.ctx.toList()
            val namespace = Project.currNamespace
            EffectAnalysis.withActualEffects(current) {
                val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
                val record = local(work.read(), node)
                assertSame(MCFPPBaseType.Int, record.fields.first().type)
                assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            }
            assertEquals(commands, current.commands.toList())
            assertEquals(variables, current.scope.vars)
            assertEquals(children, current.child)
            assertEquals(parents, current.parent)
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertEquals(flow, StorageAccess.flowSnapshot(current.scope.vars.values.toList()))
            assertEquals(contexts, Project.ctx.toList())
            assertEquals(namespace, Project.currNamespace)
            assertSame(current, Function.currFunction)
            assertSame(file, MCFPPFile.currFile)
        }
        assertEquals(0, calls)
        assertEquals(0, annotation.applications)
        assertTrue(host.compiledFunctions.isEmpty())
        unchanged(node)
    }
    private class Probe : Annotation("Probe", "header.queue") {
        var applications = 0
        override fun forDataTemplate(template: DataTemplate) {
            applications++
        }
        override fun forFunction(function: Function) {
            applications++
        }
        override fun forField(field: Var<*>) {
            applications++
        }
    }
}
