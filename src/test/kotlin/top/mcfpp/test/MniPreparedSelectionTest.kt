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
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.StringHelper.splitNamespaceID
import top.mcfpp.util.TempPool
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniPreparedSelectionTest {
    companion object {
        private var hostCalls = 0
        @JvmStatic fun nativeSentinel(context: NativeCallContext) {
            hostCalls++
        }
    }

    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "prepared.mcfpp"))))
    private fun expression(text: String): P.ExpressionContext {
        val parser = parser(text)
        return parser.expression().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun fixture(): MCFPPFile {
        Project.init()
        val namespace = Namespace("prepared.original")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }
    private fun ordinary(file: MCFPPFile, name: String, result: MCFPPType, vararg parameters: MCFPPType) = Function(name, file.namespace.identifier, parser("{}").curlBlock()).apply {
        declarationFile = file
        returnType = result
        parameters.forEachIndexed { index, type -> normalParams.add(FunctionParam(type, "p$index", this)) }
        file.field.addFunction(this, force = true)
    }
    private fun generic(
        file: MCFPPFile,
        name: String,
        readonlyType: MCFPPType,
        result: MCFPPType,
        default: String? = null
    ) = GenericFunction(name, file.namespace.identifier, parser("{}").curlBlock()).apply {
        declarationFile = file
        returnType = result
        readOnlyParams.add(
            FunctionParam(readonlyType, "T", this, hasDefault = default != null, isReadOnly = true).apply {
                defaultContext = default?.let { parser(it).value() }
            }
        )
        file.field.addFunction(this, force = true)
    }
    private fun lexical(file: MCFPPFile) = DeclarationLexicalSnapshot.capture(file, file.namespace.identifier)
    private fun fact(file: MCFPPFile, text: String) = PreparedExpressionFact.readonly(lexical(file), expression(text))
    private fun prepared(functions: List<Function>, readonly: List<PreparedExpressionFact> = emptyList()): ParameterMatcher.PreparedCallFacts {
        val request = SignaturePreparationRequest(functions, readonly, preparationSpan(expression("0")))
        val ready = assertIs<SignaturePreparationResult.Ready>(SignaturePreparationSession().prepare(request))
        return ParameterMatcher.PreparedCallFacts(request, ready.declarations)
    }
    private fun calls(tree: ParseTree): List<P.FunctionCallContext> = (if (tree is P.FunctionCallContext) listOf(tree) else emptyList()) +
        (0 until tree.childCount).flatMap { calls(tree.getChild(it)) }
    private fun binder(file: MCFPPFile, tree: ParseTree): DeclarationBinding {
        val lexical = lexical(file)
        val batches = calls(tree).associateWith { call ->
            val (namespace, name) = call.namespaceID().text.splitNamespaceID()
            val readonly = call.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map {
                PreparedExpressionFact.readonly(lexical, it)
            }
            prepared(lexical.functions(namespace, name), readonly)
        }
        return DeclarationBinding(DeclarationPreparationContext(lexical), batches)
    }
    private fun selected(
        facts: ParameterMatcher.PreparedCallFacts,
        name: String,
        readonly: List<PreparedExpressionFact> = emptyList(),
        normal: List<MCFPPType> = emptyList()
    ) = assertIs<ParameterMatcher.PreparedTypeSelection.Selected>(ParameterMatcher.selectPreparedTypes(facts, name, readonly, normal)).signature

    @Test fun preparedAndLegacySelectorsPreferExactOverConversionWithoutPreparingAgain() {
        val file = fixture()
        val converted = ordinary(file, "pick", MCFPPBaseType.Float, MCFPPBaseType.Float)
        val exact = ordinary(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int)
        val functions = listOf(converted, exact)
        val facts = prepared(functions)
        assertSame(exact, assertIs<ParameterMatcher.TypeSelection.Selected>(ParameterMatcher.selectTypes(functions, "pick", listOf(MCFPPBaseType.Int))).function)
        // A subsequent declaration mutation would break a second resolution of this signature.
        exact.normalParams.single().type = UnresolvedType("Missing<int>")
        exact.returnType = UnresolvedType("Missing<int>")
        assertSame(exact, selected(facts, "pick", normal = listOf(MCFPPBaseType.Int)).function)
        assertEquals(MCFPPBaseType.Int.typeId, selected(facts, "pick", normal = listOf(MCFPPBaseType.Int)).returnIdentity)
        assertTrue(exact.compiledFunctions.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun normalAndReadonlyDefaultsShareTheExistingDefaultScore() {
        val file = fixture()
        val noDefault = ordinary(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int)
        val normalDefault = ordinary(file, "pick", MCFPPBaseType.String, MCFPPBaseType.Int, MCFPPBaseType.Int)
        normalDefault.normalParams.last().hasDefault = true
        normalDefault.normalParams.last().defaultContext = parser("1").value()
        val readonlyDefault = generic(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Bool, "1")
        readonlyDefault.normalParams.add(FunctionParam(MCFPPBaseType.Int, "p", readonlyDefault))
        val all = listOf(normalDefault, readonlyDefault, noDefault)
        val facts = prepared(all)
        assertEquals(3, facts.declarations.signatures.size)
        assertSame(noDefault, selected(facts, "pick", normal = listOf(MCFPPBaseType.Int)).function)
        assertSame(noDefault, assertIs<ParameterMatcher.TypeSelection.Selected>(ParameterMatcher.selectTypes(all, "pick", listOf(MCFPPBaseType.Int))).function)
    }

    @Test fun readonlyConversionRanksUseOriginalExplicitTypesAndClosedValues() {
        val file = fixture()
        val float = generic(file, "pick", MCFPPBaseType.Float, MCFPPBaseType.String)
        val int = generic(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Bool)
        val args = listOf(fact(file, "2"))
        val facts = prepared(listOf(float, int), args)
        assertEquals(2, facts.declarations.signatures.size)
        assertSame(int, selected(facts, "pick", args).function)
        assertSame(
            int,
            assertIs<ParameterMatcher.TypeSelection.Selected>(
                ParameterMatcher.selectDeclaredTypes(
                    listOf(float, int),
                    "pick",
                    args.map { it.bound },
                    emptyList()
                )
            ).function
        )
    }

    @Test fun equallyRankedPreparedCandidatesRemainAmbiguous() {
        val file = fixture()
        val first = ordinary(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int)
        val second = ordinary(file, "pick", MCFPPBaseType.Bool, MCFPPBaseType.Int)
        val facts = prepared(listOf(first, second))
        val result = assertIs<ParameterMatcher.PreparedTypeSelection.Ambiguous>(
            ParameterMatcher.selectPreparedTypes(
                facts,
                "pick",
                emptyList(),
                listOf(MCFPPBaseType.Int)
            )
        )
        assertEquals(listOf(first, second), result.signatures.map { it.function })
        assertFailsWith<UnsupportedOperationException> { (result.signatures as MutableList<*>).clear() }
        val syntax = expression("pick(1)")
        val error = assertFailsWith<PreparationFactFailure> { binder(file, syntax).expression(syntax) }
        assertEquals(preparationSpan(calls(syntax).single()), error.source)
        assertTrue(error.message.orEmpty().contains("Ambiguous overload"))
    }

    @Test fun pureNestedCallsSelectOnlyPreparedSignaturesAndNeverInventConstants() {
        val file = fixture()
        ordinary(file, "inner", MCFPPBaseType.Int)
        ordinary(file, "outer", MCFPPBaseType.String, MCFPPBaseType.Object)
        ordinary(file, "outer", MCFPPBaseType.Bool, MCFPPBaseType.Int)
        val syntax = expression("outer(inner())")
        val value = binder(file, syntax).expression(syntax)
        assertEquals(MCFPPBaseType.Bool.typeId, value.type.typeId)
        assertNull(value.constant)
        assertEquals(0, Project.errorCount)
    }

    @Test fun readonlyTypesAreSubstitutedOnceBeforePureChildSelection() {
        val file = fixture()
        val target = generic(file, "pick", MCFPPConcreteType.Type, UnresolvedType("T"))
        target.normalParams.add(FunctionParam(UnresolvedType("T"), "value", target))
        val syntax = expression("pick<int>(1)")
        val value = binder(file, syntax).expression(syntax)
        assertEquals(MCFPPBaseType.Int.typeId, value.type.typeId)
        assertNull(value.constant)
        assertTrue(target.compiledFunctions.isEmpty())
    }

    @Test fun inapplicableReadonlyCandidatesStillCountAsPreparedOriginalDeclarations() {
        val file = fixture()
        generic(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.String)
        val target = generic(file, "pick", MCFPPBaseType.Bool, MCFPPBaseType.Int)
        val syntax = expression("pick<true>()")
        assertEquals(MCFPPBaseType.Int.typeId, binder(file, syntax).expression(syntax).type.typeId)
        assertTrue(target.compiledFunctions.isEmpty())
    }

    @Test fun historicSpecializationsAreNotExtraCandidatesOrMissingPreparation() {
        val file = fixture()
        val target = generic(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int)
        val historic = Function(target)
        val key = SpecializationKey(
            target.declarationId,
            listOf(
                SpecializationArgument.Constant(
                    CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(1))
                )
            ),
            GenerationOptions("fixture", 1)
        )
        target.compiledFunctions[key] = historic
        file.field.functions.getValue("pick").add(historic)
        val syntax = expression("pick<2>()")
        assertEquals(MCFPPBaseType.Int.typeId, binder(file, syntax).expression(syntax).type.typeId)
        assertSame(historic, target.compiledFunctions[key])
        assertEquals(1, target.compiledFunctions.size)
    }

    @Test fun preparedSelectionRejectsTooManyAndMissingRequiredNormalArguments() {
        val file = fixture()
        val target = ordinary(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int)
        val facts = prepared(listOf(target))
        for (types in listOf(emptyList(), listOf(MCFPPBaseType.Int, MCFPPBaseType.Int))) {
            assertSame(
                ParameterMatcher.PreparedTypeSelection.Missing,
                ParameterMatcher.selectPreparedTypes(facts, "pick", emptyList(), types)
            )
        }
        for (text in listOf("pick()", "pick(1, 2)")) {
            val syntax = expression(text)
            val error = assertFailsWith<PreparationFactFailure> { binder(file, syntax).expression(syntax) }
            assertEquals(preparationSpan(calls(syntax).single()), error.source)
        }
    }

    @Test fun qualifiedCallsUseTheFrozenDeclarationImportView() {
        val file = fixture()
        val namespace = Namespace("prepared.library")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val library = MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
        ordinary(library, "read", MCFPPBaseType.Int)
        ordinary(file, "read", MCFPPBaseType.Bool)
        file.unsolvedImports[namespace.identifier] = "*"
        val syntax = expression("prepared.library:read()")
        val binding = binder(file, syntax)
        file.unsolvedImports.clear()
        val current = Project.currNamespace
        try {
            Project.currNamespace = "caller.other"
            assertEquals(MCFPPBaseType.Int.typeId, binding.expression(syntax).type.typeId)
            assertNull(binding.expression(syntax).constant)
        } finally {
            Project.currNamespace = current
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun missingPreparedBatchAndOmittedCandidateFailWithTheActualCallSpan() {
        val file = fixture()
        val exact = ordinary(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int)
        ordinary(file, "pick", MCFPPBaseType.Float, MCFPPBaseType.Object)
        val syntax = expression("pick(1)")
        val call = calls(syntax).single()
        for (map in listOf(emptyMap(), mapOf(call to prepared(listOf(exact))))) {
            val error = assertFailsWith<PreparationFactFailure> {
                DeclarationBinding(DeclarationPreparationContext(lexical(file)), map).expression(syntax)
            }
            assertEquals(PreparationFactCode.UNSUPPORTED_EXPRESSION, error.code)
            assertEquals(preparationSpan(call), error.source)
            assertTrue(error.message.orEmpty().contains("prepared"))
        }
    }

    @Test fun explicitReadonlyFactsCannotBeReusedForAnotherValueOrAnOmittedDefault() {
        val file = fixture()
        val target = generic(file, "pick", MCFPPBaseType.Int, MCFPPBaseType.Int, "3")
        val facts = prepared(listOf(target), listOf(fact(file, "7")))
        for (text in listOf("pick<8>()", "pick()")) {
            val syntax = expression(text)
            val call = calls(syntax).single()
            val error = assertFailsWith<PreparationFactFailure> {
                DeclarationBinding(DeclarationPreparationContext(lexical(file)), mapOf(call to facts)).expression(syntax)
            }
            assertEquals(preparationSpan(call), error.source)
            assertTrue(error.message.orEmpty().contains("No prepared signature"))
        }
        val default = expression("pick()")
        assertEquals(MCFPPBaseType.Int.typeId, binder(file, default).expression(default).type.typeId)
    }

    @Test fun runtimeCallsAreNeverClosedReadonlyProofs() {
        val file = fixture()
        ordinary(file, "value", MCFPPBaseType.Int)
        val syntax = expression("value()")
        val binding = binder(file, syntax)
        assertNull(binding.expression(syntax).constant)
        val error = assertFailsWith<PreparationFactFailure> { binding.readonly(syntax) }
        assertEquals(PreparationFactCode.NON_CLOSED_VALUE, error.code)
        assertEquals(preparationSpan(syntax), error.source)
    }

    @Test fun callsiteFactMapIsCopiedAndOneTreeKeepsSeparateReadonlyValues() {
        val file = fixture()
        val target = generic(file, "value", MCFPPBaseType.Int, MCFPPBaseType.Int)
        val syntax = expression("(value<1>()) + (value<2>())")
        val calls = calls(syntax)
        assertEquals(2, calls.size)
        assertEquals(listOf("1", "2"), calls.map { it.arguments().readOnlyArgs().expressionList().expression().single().text })
        val map = calls.associateWith { call ->
            prepared(
                listOf(target),
                call.arguments().readOnlyArgs()
                    .expressionList().expression().map { PreparedExpressionFact.readonly(lexical(file), it) }
            )
        }.toMutableMap()
        val binding = DeclarationBinding(DeclarationPreparationContext(lexical(file)), map)
        map.clear()
        assertEquals(MCFPPBaseType.Int.typeId, binding.expression(syntax).type.typeId)
        assertNull(binding.expression(syntax).constant)
    }

    @Test fun nativeAndOrdinaryQueriesPreserveRealCompilerStateAndExecuteNoHost() {
        val file = fixture()
        hostCalls = 0
        val method = MniPreparedSelectionTest::class.java.getMethod("nativeSentinel", NativeCallContext::class.java)
        val native = NativeFunction("nativeValue", file.namespace.identifier, method).apply {
            declarationFile = file
            returnType = MCFPPBaseType.Int
        }
        file.field.addFunction(native, force = true)
        val ordinary = ordinary(file, "ordinaryValue", MCFPPBaseType.Int)
        val syntax = expression("nativeValue() + ordinaryValue()")
        val binding = binder(file, syntax)
        val caller = Function("caller", file.namespace.identifier, null)
        val sentinel = MCInt(29, "sentinel")
        caller.scope.vars["sentinel"] = sentinel
        caller.commands.add(Command("say sentinel"))
        caller.runInFunction {
            MCFPPFile.currFile = file
            val commands = caller.commands.toList()
            val vars = caller.scope.vars.toMap()
            val scopeParents = caller.scope.parent.toList()
            val parents = caller.parent.toList()
            val children = caller.child.toList()
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val context = Project.ctx.toList()
            val namespace = Project.currNamespace
            val flow = StorageAccess.flowSnapshot(listOf(sentinel))
            EffectAnalysis.withActualEffects(caller) {
                val counters = listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount)
                repeat(2) {
                    val value = binding.expression(syntax)
                    assertEquals(MCFPPBaseType.Int.typeId, value.type.typeId)
                    assertNull(value.constant)
                }
                assertEquals(counters, listOf(TempPool.varCount, TempPool.functionCount, TempPool.functionParam, TempPool.anonymousTemplateCount))
            }
            assertEquals(commands, caller.commands.toList())
            assertEquals(vars, caller.scope.vars)
            assertEquals(scopeParents, caller.scope.parent)
            assertEquals(parents, caller.parent)
            assertEquals(children, caller.child)
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertEquals(context, Project.ctx.toList())
            assertEquals(namespace, Project.currNamespace)
            assertSame(caller, Function.currFunction)
            assertSame(file, MCFPPFile.currFile)
            assertEquals(flow, StorageAccess.flowSnapshot(listOf(sentinel)))
            assertEquals(0, hostCalls)
            assertTrue(native.commands.isEmpty())
            assertTrue(ordinary.commands.isEmpty())
            assertTrue(ordinary.compiledFunctions.isEmpty())
            assertTrue(native.compiledFunctions.isEmpty())
            assertEquals(0, Project.errorCount)
        }
    }
}
