@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniMacroRegistrationTest {
    private val types = RegistrationTypes()
    private val diagnostics = ArrayList<MacroRegistrationDiagnostic>()
    private fun registry() = MacroRegistry("fixture.macros", 91, types) { diagnostics.add(it) }

    @Test fun acceptsAllModesAndUsesSourceTypesWithoutInvokingHostMethods() {
        val registry = registry()
        val entries = registry.register(ValidMacroEntries::class.java)
        assertTrue(diagnostics.isEmpty())
        assertEquals(setOf("all", "expression", "empty"), entries.map { it.identifier }.toSet())
        val all = registry.find("all").single()
        assertEquals(ParamMode.entries, all.parameters.map { it.mode() })
        assertEquals(FunctionKind.MACRO, all.reference.kind)
        assertEquals("fixture.macros:all", all.reference.name())
        assertTrue(all.returnsVoid)
        val expression = registry.find("expression").single()
        assertTrue(expression.returnType.sameType(types.int))
        assertFalse(expression.returnsVoid)
        // The fixture deliberately declares Expr<BoolT>: JVM generics do not prove the MCFPP result.
        assertEquals(Expr::class.java, expression.javaMethod.returnType)
        assertEquals(0, MacroHostProbe.calls)
    }

    @Test fun rejectsExactJvmProtocolViolationsWithOneDiagnosticPerRealMethod() {
        val registry = registry()
        assertTrue(registry.register(InvalidMacroJvm::class.java).isEmpty())
        assertEquals(12, diagnostics.size)
        assertTrue(diagnostics.all { it.code == MacroRegistrationCode.INVALID_JVM })
        assertTrue(diagnostics.all { it.message.contains("InvalidMacroJvm.") })
        assertTrue(registry.all().isEmpty())
    }

    @Test fun rejectsMalformedAndUnresolvedMetadataWithoutPublishingEntries() {
        val registry = registry()
        assertTrue(registry.register(InvalidMacroMetadata::class.java).isEmpty())
        assertEquals(13, diagnostics.size)
        assertEquals(3, diagnostics.count { it.code == MacroRegistrationCode.UNRESOLVED_TYPE })
        assertEquals(10, diagnostics.count { it.code == MacroRegistrationCode.INVALID_METADATA })
        assertTrue(diagnostics.any { it.reason.contains("duplicate parameter") })
        assertTrue(diagnostics.any { it.reason.contains("INLINE callbacks cannot accept INLINE") })
    }

    @Test fun modesAliasesAndReturnTypesCannotDistinguishOverloads() {
        val registry = registry()
        val entries = registry.register(DuplicateMacroEntries::class.java)
        assertEquals(2, entries.size)
        assertEquals(3, diagnostics.size)
        assertTrue(diagnostics.all { it.code == MacroRegistrationCode.DUPLICATE })
        assertEquals("aValue", registry.find("duplicate").single().javaMethod.name)
        assertEquals("dInline", registry.find("callbackMode").single().javaMethod.name)
        assertTrue(diagnostics.all { it.previous != null && it.message.contains("previous:") })
    }

    @Test fun equalDisplayNamesWithDifferentCanonicalTypeIdentityRemainDistinct() {
        val registry = registry()
        val overloads = registry.register(DistinctMacroTypes::class.java)
        assertTrue(diagnostics.isEmpty())
        assertEquals(2, overloads.size)
        val first = overloads[0].parameters.single().type()
        val second = overloads[1].parameters.single().type()
        assertEquals(first.name(), second.name())
        assertFalse(first.sameType(second))
        assertFalse(overloads[0].reference.key == overloads[1].reference.key)
        val snapshot = registry.all()
        assertFailsWith<UnsupportedOperationException> { (snapshot as MutableList).clear() }
    }

    @Test fun parsesInlineModesAndGenericTypeCommasIntoImmutableDescriptors() {
        val entry = registry().register(InlineMacroEntries::class.java).single()
        assertTrue(diagnostics.isEmpty())
        val parameter = entry.parameters.single()
        val signature = assertNotNull(parameter.inlineSignature())
        assertEquals(listOf(ParamMode.VALUE, ParamMode.REF, ParamMode.CONST), signature.parameters().map { it.mode() })
        assertTrue(signature.parameters()[1].type().sameType(types.map))
        assertTrue(signature.returnType().sameType(types.int))
        assertTrue(parameter.type().inlineSignature()!!.returnType().sameType(types.int))
        assertFailsWith<UnsupportedOperationException> { (signature.parameters() as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (entry.parameters as MutableList).clear() }
    }

    @Test fun acceptsKotlinFacadeObjectAndCompanionButSkipsSyntheticDefaultHelpers() {
        val registry = registry()
        registry.register(Class.forName("top.mcfpp.test.MniMacroRegistrationTestKt"))
        registry.register(ObjectMacroEntries::class.java)
        registry.register(DefaultMacroEntries::class.java)
        assertTrue(diagnostics.isEmpty())
        assertEquals(setOf("facadeMacro", "objectMacro", "withDefault"), registry.all().map { it.identifier }.toSet())
        assertTrue(DefaultMacroEntries.Companion::class.java.declaredMethods.any { it.isSynthetic })
        // The host default is not exposed as an MCFPP default argument.
        assertEquals(1, registry.find("withDefault").single().parameters.size)
    }

    @Test fun skipsBridgeAndRejectsNonPublicProvidersWithoutInvokingThem() {
        val registry = registry()
        assertEquals(1, BridgeMacroEntries::class.java.declaredMethods.count { it.isBridge })
        registry.register(BridgeMacroEntries::class.java)
        registry.register(HiddenMacroEntries::class.java)
        assertTrue(registry.all().isEmpty())
        assertEquals(2, diagnostics.size)
        assertTrue(diagnostics.all { it.code == MacroRegistrationCode.INVALID_JVM })
    }

    @Test fun invalidEntryDoesNotReserveSignatureAndDuplicateKeepsOriginalDescriptor() {
        val registry = registry()
        registry.register(InvalidBeforeValidMacro::class.java)
        val original = registry.find("same").single()
        assertEquals("zValid", original.javaMethod.name)
        assertEquals(MacroRegistrationCode.INVALID_JVM, diagnostics.single().code)
        registry.register(original.javaMethod)
        assertSame(original, registry.find("same").single())
        assertEquals(MacroRegistrationCode.DUPLICATE, diagnostics.last().code)
    }

    @Test fun rejectsMissingCanonicalInlineTypeInsteadOfPublishingPartialMetadata() {
        val resolver = object : MacroTypeResolver {
            override fun resolve(typeName: String): TypeRef<*>? = types.resolve(typeName)
            override fun inlineType(signature: InlineSignature<*>): TypeRef<*>? = null
        }
        val registry = MacroRegistry("fixture.macros", 91, resolver) { diagnostics.add(it) }
        assertTrue(registry.register(InlineMacroEntries::class.java).isEmpty())
        assertEquals(MacroRegistrationCode.UNRESOLVED_TYPE, diagnostics.single().code)
        assertTrue(registry.all().isEmpty())
    }

    @Test fun preparationKeepsSourceExpressionsAndSourcesWithoutResolvingTypesOrInvokingProviders() {
        val source = SourceSpan("macro-provider.java", 10, 40, 2, 1, 2, 31)
        val method = ValidMacroEntries::class.java.declaredMethods.single { it.name == "all" }
        val prepared = assertNotNull(MacroSignatureParser.prepare(method, source))
        assertSame(method, prepared.javaMethod)
        assertEquals("all", prepared.identifier)
        assertEquals(ParamMode.entries, prepared.parameters.map { it.mode })
        assertEquals(source, prepared.source)
        prepared.parameters.forEach {
            assertEquals(source, it.source)
            assertEquals(source, it.type.source)
        }
        assertEquals("int", assertIs<PreparedMacroType.Source>(prepared.parameters[0].type).expression)
        val callback = assertIs<PreparedMacroType.Callback>(prepared.parameters[3].type).signature
        assertEquals("int", assertIs<PreparedMacroType.Source>(callback.parameters.single().type).expression)
        assertEquals("void", assertIs<PreparedMacroType.Source>(callback.returnType).expression)
        assertEquals(source, callback.source)
        assertEquals("void", assertIs<PreparedMacroType.Source>(prepared.returnType).expression)
        assertFailsWith<UnsupportedOperationException> { (prepared.parameters as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (callback.parameters as MutableList).clear() }

        val unresolved = InvalidMacroMetadata::class.java.declaredMethods.single { it.name == "unknownInlineType" }
        val unresolvedTree = assertNotNull(MacroSignatureParser.prepare(unresolved, source))
        val unresolvedCallback = assertIs<PreparedMacroType.Callback>(unresolvedTree.parameters.single().type).signature
        assertEquals("missing", assertIs<PreparedMacroType.Source>(unresolvedCallback.parameters.single().type).expression)
        val literalMethod = NestedMacroEntries::class.java.declaredMethods.single { it.name == "literalType" }
        val literalTree = assertNotNull(MacroSignatureParser.prepare(literalMethod, source))
        val literalCallback = assertIs<PreparedMacroType.Callback>(literalTree.parameters.single().type).signature
        assertEquals("Box<\"x)>[,]\">", assertIs<PreparedMacroType.Source>(literalCallback.parameters.single().type).expression)
        assertEquals(0, types.resolveCalls)
        assertEquals(0, types.inlineCalls)
        assertEquals(0, MacroHostProbe.calls)
    }

    @Test fun nestedCallbacksPrepareAsOneImmutableTreeAndRegistrationConsumesThatExactTree() {
        val method = NestedMacroEntries::class.java.declaredMethods.single { it.name == "nested" }
        val prepared = assertNotNull(MacroSignatureParser.prepare(method))
        val outer = assertIs<PreparedMacroType.Callback>(prepared.parameters.single().type).signature
        assertEquals(listOf(ParamMode.INLINE, ParamMode.CONST), outer.parameters.map { it.mode })
        assertEquals("Map<int,bool>", assertIs<PreparedMacroType.Source>(outer.parameters[1].type).expression)
        val inner = assertIs<PreparedMacroType.Callback>(outer.parameters[0].type).signature
        assertEquals(ParamMode.REF, inner.parameters.single().mode)
        assertEquals("int", assertIs<PreparedMacroType.Source>(inner.returnType).expression)
        assertFailsWith<UnsupportedOperationException> { (inner.parameters as MutableList).clear() }
        assertEquals(0, types.inlineCalls)

        val registry = registry()
        val entry = assertNotNull(registry.register(prepared))
        assertSame(prepared, entry.prepared)
        assertEquals(2, types.inlineCalls)
        val actual = assertNotNull(entry.parameters.single().inlineSignature())
        val nested = assertNotNull(actual.parameters()[0].inlineSignature())
        assertEquals(ParamMode.REF, nested.parameters().single().mode())
        assertTrue(nested.returnType().sameType(types.int))
        assertTrue(diagnostics.isEmpty())
        assertEquals(0, MacroHostProbe.calls)
    }

    @Test fun invalidPreparationKeepsJvmAndMetadataDiagnosticCategoriesBeforeTypeBinding() {
        val registry = registry()
        for ((provider, name, expected) in listOf(
            Triple(InvalidMacroJvm::class.java, "wrongContext", MacroRegistrationCode.INVALID_JVM),
            Triple(InvalidMacroMetadata::class.java, "higherOrder", MacroRegistrationCode.INVALID_METADATA),
            Triple(InvalidMacroMetadata::class.java, "emptyInlineType", MacroRegistrationCode.INVALID_METADATA)
        )) {
            val method = provider.declaredMethods.single { it.name == name }
            val failure = assertFailsWith<MacroSignatureException> { MacroSignatureParser.prepare(method) }
            assertEquals(expected, failure.code)
            assertEquals(null, registry.register(method))
            assertEquals(failure.code, diagnostics.last().code)
            assertEquals(failure.message, diagnostics.last().reason)
        }
        assertEquals(0, types.resolveCalls)
        assertEquals(0, types.inlineCalls)
        assertTrue(registry.all().isEmpty())
        assertEquals(0, MacroHostProbe.calls)
    }
}

private class RegistrationTypes : MacroTypeResolver {
    val int = ImmutableTypeRef<IntT>(TypeKey(91, 1), "int", kind = TypeKind.INT)
    private val bool = ImmutableTypeRef<BoolT>(TypeKey(91, 2), "bool", kind = TypeKind.BOOL)
    private val void = ImmutableTypeRef<VoidT>(TypeKey(91, 3), "void", kind = TypeKind.VOID)
    private val otherInt = ImmutableTypeRef<IntT>(TypeKey(91, 4, 1), "int", kind = TypeKind.INT)
    val map = ImmutableTypeRef<Any>(TypeKey(91, 5), "Map<int,bool>")
    private val callbacks = LinkedHashMap<String, TypeRef<*>>()
    var resolveCalls = 0
        private set
    var inlineCalls = 0
        private set
    override fun resolve(typeName: String): TypeRef<*>? {
        resolveCalls++
        return when (typeName) {
            "int", "integer" -> int
            "bool" -> bool
            "void" -> void
            "otherInt" -> otherInt
            "Map<int,bool>" -> map
            "callbackInt" -> inlineType(FunctionSignature.of(listOf(ParameterView.of("x", int, ParamMode.VALUE)), void))
            else -> null
        }
    }
    override fun inlineType(signature: InlineSignature<*>): TypeRef<*> {
        inlineCalls++
        val key = signature.parameters().joinToString { "${it.mode()}:${(it.type() as ImmutableTypeRef<*>).key}" } +
            "->${(signature.returnType() as ImmutableTypeRef<*>).key}"
        return callbacks.getOrPut(key) {
            ImmutableTypeRef<Any>(TypeKey(91, 100L + callbacks.size), "callback", inlineSignature = signature)
        }
    }
}

object MacroHostProbe {
    var calls = 0
}
private fun registrationMustNotExecute(): Nothing {
    MacroHostProbe.calls++
    error("Registration invoked a macro")
}

class ValidMacroEntries {
    companion object {
        @JvmStatic
        @MNIMacro(
            params = [
                MacroParam(name = "value", type = "int"),
                MacroParam(name = "reference", type = "int", mode = ParamMode.REF),
                MacroParam(name = "constant", type = "int", mode = ParamMode.CONST),
                MacroParam(name = "body", type = "(int)->void", mode = ParamMode.INLINE)
            ]
        )
        fun all(context: MacroContext, value: Value<IntT>, ref: Ref<IntT>, constant: Const<IntT>, body: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(returnType = "int")
        fun expression(context: MacroContext): Expr<BoolT> = registrationMustNotExecute()
        @JvmStatic @MNIMacro
        fun empty(context: MacroContext): Block = registrationMustNotExecute()
    }
}

class InvalidMacroJvm {
    @MNIMacro fun instance(context: MacroContext): Block = registrationMustNotExecute()
    companion object {
        @JvmStatic @MNIMacro
        private fun hidden(context: MacroContext): Block = registrationMustNotExecute()
        @JvmStatic @MNIMacro
        fun wrongContext(context: Any): Block = registrationMustNotExecute()
        @JvmStatic @MNIMacro
        fun extra(context: MacroContext, extra: Int): Block = registrationMustNotExecute()
        @JvmStatic @MNIMacro
        fun missing(): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "int")])
        fun value(context: MacroContext, x: Ref<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "int", mode = ParamMode.REF)])
        fun ref(context: MacroContext, x: Local<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "int", mode = ParamMode.CONST)])
        fun constant(context: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "(int)->void", mode = ParamMode.INLINE)])
        fun inline(context: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic @MNIMacro
        fun voidExpr(context: MacroContext): Expr<IntT> = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(returnType = "int")
        fun resultBlock(context: MacroContext): Block = registrationMustNotExecute()
        @JvmStatic @MNIMacro
        fun jvmVoid(context: MacroContext) {
            registrationMustNotExecute()
        }
    }
}

class InvalidMacroMetadata {
    companion object {
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "int"), MacroParam(name = "x", type = "int")])
        fun duplicateNames(c: MacroContext, x: Value<IntT>, y: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = " ", type = "int")])
        fun blankName(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "void")])
        fun voidParam(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "missing")])
        fun unknownParam(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(returnType = "missing")
        fun unknownResult(c: MacroContext): Expr<IntT> = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "int", mode = ParamMode.INLINE)])
        fun notSignature(c: MacroContext, x: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "(inline int)->void", mode = ParamMode.INLINE)])
        fun higherOrder(c: MacroContext, x: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "(int,)->void", mode = ParamMode.INLINE)])
        fun emptyInlineType(c: MacroContext, x: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "(missing)->void", mode = ParamMode.INLINE)])
        fun unknownInlineType(c: MacroContext, x: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "has space")
        fun badIdentifier(c: MacroContext): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "ref")
        fun keyword(c: MacroContext): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "has space", type = "int")])
        fun badParameter(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "1number", type = "int")])
        fun numericParameter(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
    }
}

class DuplicateMacroEntries {
    companion object {
        @JvmStatic
        @MNIMacro(identifier = "duplicate", params = [MacroParam(name = "x", type = "int")])
        fun aValue(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "duplicate", params = [MacroParam(name = "x", type = "integer", mode = ParamMode.REF)])
        fun bRef(c: MacroContext, x: Ref<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "duplicate", returnType = "int", params = [MacroParam(name = "x", type = "int", mode = ParamMode.CONST)])
        fun cConst(c: MacroContext, x: Const<IntT>): Expr<IntT> = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "callbackMode", params = [MacroParam(name = "x", type = "(int)->void", mode = ParamMode.INLINE)])
        fun dInline(c: MacroContext, x: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "callbackMode", params = [MacroParam(name = "x", type = "callbackInt")])
        fun eCallbackValue(c: MacroContext, x: Value<Any>): Block = registrationMustNotExecute()
    }
}

class DistinctMacroTypes {
    companion object {
        @JvmStatic
        @MNIMacro(identifier = "choose", params = [MacroParam(name = "x", type = "int")])
        fun first(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "choose", params = [MacroParam(name = "x", type = "otherInt")])
        fun second(c: MacroContext, x: Value<IntT>): Block = registrationMustNotExecute()
    }
}

class InlineMacroEntries {
    companion object {
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "body", type = "(int, ref Map<int,bool>, const int)->int", mode = ParamMode.INLINE)])
        fun callback(c: MacroContext, body: InlineFn<*>): Block = registrationMustNotExecute()
    }
}

class NestedMacroEntries {
    companion object {
        @JvmStatic
        @MNIMacro(
            params = [
                MacroParam(
                    name = "body",
                    type = "(inline (ref int)->int, const Map<int,bool>)->void",
                    mode = ParamMode.INLINE
                )
            ]
        )
        fun nested(context: MacroContext, body: InlineFn<*>): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "body", type = "(Box<\"x)>[,]\">)->void", mode = ParamMode.INLINE)])
        fun literalType(context: MacroContext, body: InlineFn<*>): Block = registrationMustNotExecute()
    }
}

@MNIMacro fun facadeMacro(context: MacroContext): Block = registrationMustNotExecute()
object ObjectMacroEntries {
    @JvmStatic @MNIMacro
    fun objectMacro(context: MacroContext): Block = registrationMustNotExecute()
}
class DefaultMacroEntries {
    companion object {
        @JvmStatic
        @MNIMacro(params = [MacroParam(name = "x", type = "int")])
        fun withDefault(context: MacroContext, x: Value<IntT>? = null): Block = registrationMustNotExecute()
    }
}
interface GenericMacroProvider<T> {
    fun bridge(context: MacroContext): T
}
class BridgeMacroEntries : GenericMacroProvider<Block> {
    @MNIMacro override fun bridge(context: MacroContext): Block = registrationMustNotExecute()
}
private class HiddenMacroEntries {
    companion object {
        @JvmStatic @MNIMacro
        fun hiddenProvider(context: MacroContext): Block = registrationMustNotExecute()
    }
}
class InvalidBeforeValidMacro {
    companion object {
        @JvmStatic
        @MNIMacro(identifier = "same")
        fun aBad(context: Any): Block = registrationMustNotExecute()
        @JvmStatic
        @MNIMacro(identifier = "same")
        fun zValid(context: MacroContext): Block = registrationMustNotExecute()
    }
}
