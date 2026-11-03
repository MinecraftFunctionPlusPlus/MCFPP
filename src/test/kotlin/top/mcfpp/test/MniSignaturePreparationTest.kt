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
import top.mcfpp.io.info.DeclarationEnvironmentInfo
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
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

class MniSignaturePreparationTest {
    companion object {
        private var hostCalls = 0
        @JvmStatic fun nativeSentinel(context: top.mcfpp.mni.NativeCallContext) {
            hostCalls++
        }
    }
    private val site = SourceSpan("signatures.mcfpp", 4, 12, 1, 5, 1, 13)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, site.file))))
    private fun expression(text: String): P.ExpressionContext {
        val parser = parser(text)
        return parser.expression().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun value(text: String): P.ValueContext {
        val parser = parser(text)
        return parser.value().also { assertEquals(0, parser.numberOfSyntaxErrors, text) }
    }
    private fun fixture(): MCFPPFile {
        Project.init()
        val namespace = Namespace("signature.original")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun generic(file: MCFPPFile, name: String = "target") = GenericFunction(name, file.namespace.identifier, parser("{}").curlBlock()).apply { declarationFile = file }
    private fun readonly(
        function: GenericFunction,
        name: String,
        type: MCFPPType,
        syntax: String? = null,
        proof: CompilerValue? = null
    ) = FunctionParam(type, name, function, hasDefault = syntax != null || proof != null, isReadOnly = true).apply {
        defaultContext = syntax?.let(::value)
        defaultValue = proof
        function.readOnlyParams.add(this)
    }
    private fun normal(function: Function, name: String, type: MCFPPType, syntax: String? = null) = FunctionParam(type, name, function, hasDefault = syntax != null).apply {
        defaultContext = syntax?.let(::value)
        function.normalParams.add(this)
    }
    private fun fact(file: MCFPPFile, text: String) = PreparedExpressionFact.readonly(
        DeclarationLexicalSnapshot.capture(file, file.namespace.identifier),
        expression(text)
    )
    private fun ready(candidates: List<Function>, args: List<PreparedExpressionFact> = emptyList()) = assertIs<SignaturePreparationResult.Ready>(
        SignaturePreparationSession().prepare(
            SignaturePreparationRequest(candidates, args, site)
        )
    ).declarations
    private fun payload(fact: PreparedExpressionFact) = assertIs<CompilerValue.Typed>(fact.value).payload

    @Test fun preparesEveryNormalOverloadWithoutSelectingAWinner() {
        val file = fixture()
        val exact = Function("target", file.namespace.identifier, null).apply { declarationFile = file }
        val erased = Function("target", file.namespace.identifier, null).apply { declarationFile = file }
        normal(exact, "value", MCFPPBaseType.Int)
        normal(erased, "value", MCFPPBaseType.Object)
        val all = ready(listOf(exact, erased))
        assertEquals(listOf(exact, erased), all.signatures.map { it.function })
        assertEquals(listOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Object.typeId), all.signatures.map { it.normal.single().typeIdentity })
        assertEquals(0, Project.errorCount)
    }

    @Test fun concreteReadonlyTypesReplaceNestedNormalAndReturnTypesWithOriginalFormalIdentity() {
        val file = fixture()
        val function = generic(file)
        val parameter = readonly(function, "T", MCFPPConcreteType.Type)
        val item = MCFPPGenericParamType("T", arrayListOf())
        val input = normal(function, "values", MCFPPListType(MCFPPDictType(item)))
        function.returnType = UnresolvedType("T")
        val first = ready(listOf(function), listOf(fact(file, "int"))).signatures.single()
        val second = ready(listOf(function), listOf(fact(file, "float"))).signatures.single()
        assertSame(function, first.function)
        assertSame(parameter, first.readonly.single().identity)
        assertSame(input, first.normal.single().identity)
        assertEquals(MCFPPListType(MCFPPDictType(MCFPPBaseType.Int)).typeId, first.normal.single().typeIdentity)
        assertEquals(MCFPPBaseType.Int.typeId, first.returnIdentity)
        assertEquals(MCFPPBaseType.Float.typeId, second.returnIdentity)
        assertNotEquals(first.identity, second.identity)
        assertEquals(first.declaration, second.declaration)
        assertEquals("T", item.identifier)
        assertTrue(function.compiledFunctions.isEmpty())
    }

    @Test fun readonlyMismatchAndMissingRequiredValuesRemainInapplicableBesideLegalCandidates() {
        val file = fixture()
        val wrong = generic(file, "target")
        readonly(wrong, "switch", MCFPPBaseType.Bool)
        val valid = generic(file, "target")
        readonly(valid, "count", MCFPPBaseType.Int)
        val all = ready(listOf(wrong, valid), listOf(fact(file, "7")))
        assertIs<PreparedCandidate.Inapplicable>(all.candidates[0])
        assertSame(valid, all.signatures.single().function)
        assertIs<PreparedCandidate.Inapplicable>(ready(listOf(valid)).candidates.single())
        assertEquals(0, Project.errorCount)
    }

    @Test fun readonlyDefaultsUsePureFactsAndNormalDefaultsRetainTheirSourceAndIdentity() {
        val file = fixture()
        val function = generic(file)
        readonly(function, "amount", MCFPPBaseType.Float, "7")
        val parameter = normal(function, "value", MCFPPBaseType.Int, "9")
        val prepared = ready(listOf(function)).signatures.single()
        assertEquals(CompilerValue.FloatBits(7f.toRawBits()), payload(prepared.readonlyArguments.single()))
        val default = prepared.normal.single()
        assertSame(parameter, default.identity)
        assertSame(parameter.defaultContext, default.defaultSyntax)
        assertTrue(default.hasDefault)
        assertEquals("9", default.defaultSyntax!!.text)
        assertTrue(function.scope.vars.isEmpty())
    }

    @Test fun sourceCompositeDefaultDependsOnThePrecedingReadonlyFactAndNeverCallerNames() {
        val file = fixture()
        file.field.vars["amount"] = MCInt(99, "amount")
        val function = generic(file)
        readonly(function, "amount", MCFPPBaseType.Int)
        readonly(function, "items", MCFPPListType(MCFPPBaseType.Int), "[amount + 1]")
        val signature = ready(listOf(function), listOf(fact(file, "3"))).signatures.single()
        val sequence = assertIs<CompilerValue.Sequence>(payload(signature.readonlyArguments[1]))
        val element = sequence.elements.single().let { if (it is CompilerValue.Typed) it.payload else it }
        assertEquals(CompilerValue.Integral(4), element)
        assertEquals(setOf("amount"), signature.readonlyArguments[1].bound.dependencies)
    }

    @Test fun forwardAndRuntimeFormalDependenciesRejectWithTheOriginalSourceSpan() {
        val file = fixture()
        val function = generic(file)
        file.field.vars["later"] = MCInt(88, "later")
        readonly(function, "early", MCFPPListType(MCFPPBaseType.Int), "[later]")
        readonly(function, "later", MCFPPBaseType.Int, "1")
        val result = assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(function), emptyList(), site)
            )
        )
        assertEquals(PreparationFactCode.FORMAL_DEPENDENCY, result.diagnostics.single().code)
        assertEquals(site.file, result.diagnostics.single().source.file)
        assertTrue(result.diagnostics.single().source.endOffset > result.diagnostics.single().source.startOffset)
        assertEquals(0, Project.errorCount)
    }

    @Test fun originalLexicalImportsBindTypesRatherThanTheCurrentCallerNamespace() {
        val file = fixture()
        val imported = Namespace("signature.imported")
        GlobalScope.libNamespaces[imported.identifier] = imported
        imported.scope.putResolvedAlias("Element", MCFPPBaseType.Int, forced = true)
        val function = Function("target", file.namespace.identifier, null).apply {
            declarationEnvironment = DeclarationEnvironmentInfo(file.namespace.identifier, mapOf(imported.identifier to "*"))
        }
        normal(function, "item", UnresolvedType("Element"))
        val caller = Namespace("signature.caller")
        caller.scope.putResolvedAlias("Element", MCFPPBaseType.Bool, forced = true)
        GlobalScope.localNamespaces[caller.identifier] = caller
        val current = MCFPPFile(caller)
        MCFPPFile.currFile = current
        Project.currNamespace = caller.identifier
        val prepared = ready(listOf(function)).signatures.single()
        assertSame(MCFPPBaseType.Int, prepared.normal.single().type)
        assertNull(function.declarationFile)
        assertSame(current, MCFPPFile.currFile)
        assertEquals(mapOf(imported.identifier to "*"), prepared.stamp.imports)
    }

    @Test fun historicGenericBodiesAndRepeatedCandidatesDoNotAddOverloads() {
        val file = fixture()
        val function = generic(file)
        readonly(function, "count", MCFPPBaseType.Int)
        val historic = Function(function)
        val key = SpecializationKey(
            function.declarationId,
            listOf(
                SpecializationArgument.Constant(
                    CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1))
                )
            ),
            GenerationOptions("fixture", 1)
        )
        function.compiledFunctions[key] = historic
        val prepared = ready(listOf(function, historic, function), listOf(fact(file, "2")))
        assertEquals(1, prepared.candidates.size)
        assertSame(function, prepared.signatures.single().function)
        assertSame(historic, function.compiledFunctions[key])
    }

    @Test fun ownerAndTypedReadonlyMetadataBelongToTheRealDeclaration() {
        val file = fixture()
        val owner = DataTemplate("Owner", file.namespace.identifier)
        val function = generic(file).apply { this.owner = owner }
        readonly(function, "T", MCFPPConcreteType.Type)
        val signature = ready(listOf(function), listOf(fact(file, "list<int>"))).signatures.single()
        assertEquals(owner.inheritanceIdentity, signature.owner)
        val typeValue = assertIs<CompilerValue.TypeValue>(payload(signature.readonlyArguments.single()))
        assertEquals(MCFPPListType(MCFPPBaseType.Int).typeId, typeValue.id)
        assertTrue(signature.readonlyArguments.single().bound.descriptors.containsKey(MCFPPBaseType.Int.typeId))
    }

    @Test fun unpreparedApplicationsStageAnExplicitCompletionRequestWithoutCallingLegacy() {
        val file = fixture()
        val function = generic(file)
        readonly(function, "count", MCFPPBaseType.Int, "7")
        normal(function, "value", UnresolvedType("Missing"))
        // A declared but unprepared head is distinguishable from an unknown spelling.
        val template = DataTemplate("Missing", file.namespace.identifier)
        file.namespace.scope.template[template.identifier] = template
        val session = SignaturePreparationSession()
        val pending = assertIs<SignaturePreparationResult.NeedsCompletion>(
            session.prepare(
                SignaturePreparationRequest(listOf(function), emptyList(), site)
            )
        )
        assertEquals(CallableDeclarationId(function.declarationId), pending.requests.single().declaration)
        assertEquals(site, pending.requests.single().source)
        assertTrue(pending.requests.single().readonlyComplete)
        assertEquals(CompilerValue.Integral(7), payload(pending.requests.single().completedReadonly.single()))
        assertSame(function, pending.requests.single().function)
        pending.staged.requireOpen()
        assertTrue(template.scope.allVars.isEmpty())
        session.discard()
        assertTrue(pending.staged.discarded)
        assertFailsWith<IllegalStateException> { pending.staged.requireOpen() }
        assertFailsWith<IllegalStateException> { session.prepare(SignaturePreparationRequest(emptyList(), emptyList(), site)) }
        assertEquals(0, Project.errorCount)
    }

    @Test fun preparedCollectionsAndContainerTypesAreIndependentOfSubsequentDeclarationChanges() {
        val file = fixture()
        val function = Function("target", file.namespace.identifier, null).apply { declarationFile = file }
        val list = MCFPPListType(MCFPPBaseType.Int)
        val parameter = normal(function, "items", list, "[1]")
        val candidates = arrayListOf<Function>(function)
        val request = SignaturePreparationRequest(candidates, emptyList(), site)
        candidates.clear()
        val result = assertIs<SignaturePreparationResult.Ready>(SignaturePreparationSession().prepare(request)).declarations
        val signature = result.signatures.single()
        list.generic[0] = MCFPPBaseType.Bool
        parameter.identifier = "changed"
        function.normalParams.clear()
        assertEquals("items", signature.normal.single().name)
        assertEquals(MCFPPListType(MCFPPBaseType.Int).typeId, signature.normal.single().type.typeId)
        assertFailsWith<UnsupportedOperationException> { (result.candidates as MutableList<PreparedCandidate>).clear() }
        assertFailsWith<UnsupportedOperationException> { (signature.normal as MutableList<PreparedCompilerFormal>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.types as MutableMap<TypeId, MCFPPType>).clear() }
    }

    @Test fun nativeReadonlyAndMetadataDefaultsUseTheSamePurePolicy() {
        val file = fixture()
        hostCalls = 0
        val method = MniSignaturePreparationTest::class.java.getMethod("nativeSentinel", top.mcfpp.mni.NativeCallContext::class.java)
        val native = NativeFunction("target", file.namespace.identifier, method).apply { declarationFile = file }
        native.readOnlyParams.add(
            FunctionParam(MCFPPBaseType.Float, "count", native, hasDefault = true, isReadOnly = true).apply {
                defaultValue = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(3))
            }
        )
        val signature = ready(listOf(native)).signatures.single()
        assertSame(native, signature.function)
        assertEquals(CompilerValue.FloatBits(3f.toRawBits()), payload(signature.readonlyArguments.single()))
        assertEquals(0, hostCalls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun purePreparationPreservesCompilerCommandsResourcesFlowScopesAndCurrentContext() {
        val file = fixture()
        val function = generic(file)
        readonly(function, "T", MCFPPConcreteType.Type)
        normal(function, "value", UnresolvedType("T"))
        function.returnType = UnresolvedType("T")
        val caller = Function("caller", "signature.caller", null)
        caller.commands.add(Command("say sentinel"))
        caller.scope.vars["sentinel"] = MCInt(29, "sentinel")
        val args = listOf(fact(file, "int"))
        caller.runInFunction {
            MCFPPFile.currFile = file
            val commands = caller.commands.toList()
            val vars = caller.scope.vars.toMap()
            val scopeParents = function.scope.parent.toList()
            val parents = caller.parent.toList()
            val children = caller.child.toList()
            val flow = StorageAccess.flowSnapshot(caller.scope.vars.values.toList())
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val context = Project.ctx.toList()
            val namespace = Project.currNamespace
            val beforeEffect = caller.runtimeEffect
            EffectAnalysis.withActualEffects(caller) {
                val frameField = EffectAnalysis::class.java.getDeclaredField("actual").apply { isAccessible = true }
                val frame = frameField.get(EffectAnalysis)!!
                val effectField = frame.javaClass.getDeclaredField("effect").apply { isAccessible = true }
                val localsField = frame.javaClass.getDeclaredField("locals").apply { isAccessible = true }
                val externalField = frame.javaClass.getDeclaredField("external").apply { isAccessible = true }
                val effect = effectField.get(frame)
                val locals = (localsField.get(frame) as Set<*>).toSet()
                val external = (externalField.get(frame) as Set<*>).toSet()
                val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
                repeat(2) {
                    val signature = ready(listOf(function), args).signatures.single()
                    assertEquals(MCFPPBaseType.Int.typeId, signature.returnIdentity)
                }
                assertSame(frame, frameField.get(EffectAnalysis))
                assertSame(effect, effectField.get(frame))
                assertEquals(locals, localsField.get(frame))
                assertEquals(external, externalField.get(frame))
                assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            }
            assertSame(caller, Function.currFunction)
            assertSame(file, MCFPPFile.currFile)
            assertEquals(namespace, Project.currNamespace)
            assertEquals(context, Project.ctx.toList())
            assertEquals(commands, caller.commands.toList())
            assertEquals(vars, caller.scope.vars)
            assertEquals(scopeParents, function.scope.parent)
            assertEquals(parents, caller.parent)
            assertEquals(children, caller.child)
            assertEquals(flow, StorageAccess.flowSnapshot(caller.scope.vars.values.toList()))
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertSame(beforeEffect, caller.runtimeEffect)
            assertTrue(function.compiledFunctions.isEmpty())
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun malformedTypeAndCandidateBudgetRejectWithoutPublishingPartialResults() {
        val file = fixture()
        val valid = Function("valid", file.namespace.identifier, null).apply { declarationFile = file }
        val invalid = Function("invalid", file.namespace.identifier, null).apply { declarationFile = file }
        normal(invalid, "value", UnresolvedType("int trailing"))
        val rejected = assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(valid, invalid), emptyList(), site)
            )
        )
        assertEquals(site, rejected.diagnostics.single().source)
        assertEquals(PreparationFactCode.UNKNOWN_NAME, rejected.diagnostics.single().code)
        assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession(PreparationLimits(maxCandidates = 1)).prepare(
                SignaturePreparationRequest(listOf(valid, invalid), emptyList(), site)
            )
        )
        assertEquals(0, Project.errorCount)
    }

    @Test fun malformedTypedMetadataCannotBecomeAClosedReadonlySignature() {
        val file = fixture()
        val function = generic(file)
        readonly(
            function,
            "count",
            MCFPPBaseType.Int,
            proof = CompilerValue.Typed(
                MCFPPBaseType.Int.typeId,
                CompilerValue.Bool(true)
            )
        )
        val rejected = assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(function), emptyList(), site)
            )
        )
        assertEquals(PreparationFactCode.NON_CLOSED_VALUE, rejected.diagnostics.single().code)
        assertEquals(0, Project.errorCount)
    }

    @Test fun selectorNamesCannotChangePreparedTypesOrDomainKeysAfterReady() {
        val file = fixture()
        val names = arrayListOf("minecraft:pig")
        val selector = MCFPPEntityType(1, names, false)
        val function = Function("selector", file.namespace.identifier, null).apply {
            declarationFile = file
            returnType = selector
        }
        normal(function, "targets", MCFPPListType(selector))
        val prepared = ready(listOf(function))
        val signature = prepared.signatures.single()
        val normalId = signature.normal.single().typeIdentity
        val returnId = signature.returnIdentity
        val domain = prepared.types.keys.toSet()
        names[0] = "minecraft:cow"
        names.add("minecraft:sheep")
        assertEquals(normalId, signature.normal.single().type.typeId)
        assertEquals(returnId, signature.returnType.typeId)
        assertEquals(domain, prepared.types.keys)
        assertEquals(returnId, prepared.types.getValue(returnId).typeId)
        assertTrue(prepared.types.all { (key, type) -> key == type.typeId })
        val frozen = assertIs<MCFPPEntityType>(signature.returnType)
        assertNotSame(selector, frozen)
        assertEquals(listOf("minecraft:pig"), frozen.types)
        assertFailsWith<UnsupportedOperationException> { (frozen.types as MutableList<String>).clear() }
        assertEquals(0, Project.errorCount)
    }

    @Test fun duplicateFormalsAreStructuredDeclarationFailuresWithoutPartialResults() {
        val file = fixture()
        for (readonlyCount in 0..2) {
            val invalid = generic(file)
            repeat(readonlyCount) { readonly(invalid, "duplicate", MCFPPBaseType.Int, "1") }
            repeat(2 - readonlyCount) { normal(invalid, "duplicate", MCFPPBaseType.Int) }
            val valid = Function("valid", file.namespace.identifier, null).apply { declarationFile = file }
            val rejected = assertIs<SignaturePreparationResult.Rejected>(
                SignaturePreparationSession().prepare(
                    SignaturePreparationRequest(listOf(valid, invalid), emptyList(), site)
                )
            )
            assertEquals(PreparationFactCode.FORMAL_DEPENDENCY, rejected.diagnostics.single().code)
            assertEquals(site, rejected.diagnostics.single().source)
            assertTrue(rejected.diagnostics.single().message.contains("Duplicate declaration formal"))
            assertTrue(invalid.scope.vars.isEmpty())
            assertTrue(invalid.compiledFunctions.isEmpty())
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun privateDeclarationsRequireTheActualCallerAndRejectUnauthorizedRequests() {
        val file = fixture()
        val owner = DataTemplate("Owner", file.namespace.identifier)
        val hidden = Function("target", owner, null).apply {
            declarationFile = file
            accessModifier = Member.AccessModifier.PRIVATE
        }
        val visible = Function("target", owner, null).apply { declarationFile = file }
        val caller = Function("caller", owner, null).apply { declarationFile = file }
        val outsider = Function("outsider", file.namespace.identifier, null).apply { declarationFile = file }
        val commands = caller.commands.toList()
        val fields = owner.scope.allVars.toList()
        val current = Function.currFunction
        for (accessCaller in listOf(null, outsider)) {
            val rejected = assertIs<SignaturePreparationResult.Rejected>(
                SignaturePreparationSession().prepare(
                    SignaturePreparationRequest(listOf(hidden, visible), emptyList(), site, caller = accessCaller)
                )
            )
            assertEquals(site, rejected.diagnostics.single().source)
            assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, rejected.diagnostics.single().code)
            assertTrue(rejected.diagnostics.single().message.contains("Cannot access declaration method"))
            val publicOnly = assertIs<SignaturePreparationResult.Ready>(
                SignaturePreparationSession().prepare(
                    SignaturePreparationRequest(listOf(visible), emptyList(), site, caller = accessCaller)
                )
            ).declarations
            assertSame(visible, publicOnly.signatures.single().function)
        }
        val allowed = assertIs<SignaturePreparationResult.Ready>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(hidden, visible), emptyList(), site, caller = caller)
            )
        ).declarations
        assertEquals(listOf(hidden, visible), allowed.signatures.map { it.function })
        assertEquals(commands, caller.commands.toList())
        assertEquals(fields, owner.scope.allVars.toList())
        assertSame(current, Function.currFunction)
        assertEquals(0, Project.errorCount)
    }

    @Test fun protectedAccessUsesTheCommonCallerInheritanceRules() {
        val file = fixture()
        val base = DataTemplate("Base", file.namespace.identifier)
        val child = DataTemplate("Child", file.namespace.identifier).apply { extends(base) }
        val target = Function("target", base, null).apply {
            declarationFile = file
            accessModifier = Member.AccessModifier.PROTECTED
        }
        val caller = Function("caller", child, null).apply { declarationFile = file }
        val outsider = Function("outsider", file.namespace.identifier, null).apply { declarationFile = file }
        assertEquals(Member.AccessModifier.PROTECTED, caller.accessTo(base))
        assertEquals(Member.AccessModifier.PUBLIC, outsider.accessTo(base))
        val allowed = assertIs<SignaturePreparationResult.Ready>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(target), emptyList(), site, caller = caller)
            )
        ).declarations
        assertSame(target, allowed.signatures.single().function)
        val rejected = assertIs<SignaturePreparationResult.Rejected>(
            SignaturePreparationSession().prepare(
                SignaturePreparationRequest(listOf(target), emptyList(), site, caller = outsider)
            )
        )
        assertEquals(site, rejected.diagnostics.single().source)
        assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, rejected.diagnostics.single().code)
        assertTrue(rejected.diagnostics.single().message.contains("Cannot access declaration method"))
        assertTrue(target.compiledFunctions.isEmpty())
        assertEquals(0, Project.errorCount)
    }
}
