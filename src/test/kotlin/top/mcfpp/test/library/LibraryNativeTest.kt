package top.mcfpp.test.library

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.backend.ReferenceHelperRecipe
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.core.lang.ref.ReferencePathShape
import top.mcfpp.core.lang.ref.ReferenceTargetShape
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.lib.EntitySelector
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.Const
import top.mcfpp.mni.api.InlineFn
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Ref
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.VoidT
import top.mcfpp.mni.expand.SourceCallbackParameter
import top.mcfpp.mni.expand.SourceCallbackSignature
import top.mcfpp.mni.expand.SourceCallbackType
import top.mcfpp.mni.expand.SourceTypeCatalog
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.type.*
import kotlin.test.*

class LibraryNativeTest {
    private class Bindings : NativeCaptureBindings {
        val packageKey = PackageKey("fixture:native", "1")
        val generic = MCFPPGenericParamType("T", arrayListOf())
        override val identities = object : CanonicalIdentityBindings {
            override fun localDeclaration(reference: DeclarationId) = "fixture.owner".toByteArray()
        }
        override val types = CompilerTypeTableCapture(object : CompilerTypeBindings {
            override fun parameter(id: top.mcfpp.type.TypeId.Opaque): LibraryType.Parameter? =
                if (id == generic.typeId) LibraryType.Parameter(DeclarationReference.Local(DeclarationId(1)), ParameterRole.READONLY, 0) else null
        })
        override fun sourceProvider(ownerClass: String) = packageKey
        override fun loader(provider: PackageKey?) = LibraryNativeTest::class.java.classLoader
        override fun provider(owner: Class<*>) = packageKey
        override fun sourceType(namespace: String, expression: String): MCFPPType = when (expression) {
            "int" -> MCFPPBaseType.Int; "void" -> MCFPPPrivateType.Void; else -> error("Missing prepared source type $expression")
        }
    }

    class Marker : Annotation("Marker", "fixture") {
        init { constructed++ }
        override fun forFunction(function: top.mcfpp.model.function.Function) { error("Linkage must not execute annotation actions") }
        override fun forDataTemplate(data: top.mcfpp.model.compound.DataTemplate) { error("Linkage must not execute annotation actions") }
        override fun forField(field: top.mcfpp.core.lang.Var<*>) { error("Linkage must not execute annotation actions") }
        companion object { var constructed = 0 }
    }

    companion object {
        @JvmStatic
        @MNIMacro(identifier = "mixed", normalParams = [
            "int",
            "int",
            "int",
            "(ref int, const int, inline (int)->void)->void"
        ])
        @Suppress("UNUSED_PARAMETER")
        fun mixed(context: MacroContext, input: Value<IntT>, target: Ref<IntT>, literal: Const<IntT>, body: InlineFn<VoidT>): Block =
            error("Metadata linkage must never execute the macro")

        @JvmStatic fun primitiveArray(value: Int, values: IntArray): Long = value.toLong() + values.size
        private fun common(name: String, kind: Int) = DeclCommon(DeclarationKey("fixture", emptyList(), kind, name), null,
            name, null, EnvironmentId(1), DeclarationAccess.PUBLIC, 0)
        private fun roundtrip(value: LibraryDeclaration): LibraryDeclaration {
            val strings = LibraryStrings.of(listOf(value.common.name) + NativeCodec.collectStrings(value))
            val writer = LibraryBinaryWriter()
            FunctionCodec.writeCommon(writer, value.common, strings)
            writer.tagged(value.tag) { NativeCodec.writePayload(this, value, strings) }
            val reader = LibraryBinaryReader(writer.toByteArray())
            val common = FunctionCodec.readCommon(reader, strings)
            return reader.tagged { NativeCodec.readPayload(this, it, common, strings) }.also { reader.requireEnd() }
        }
        private fun callbackCatalog(): SourceTypeCatalog {
            val int = SourceCallbackType.Declared(MCFPPBaseType.Int.typeId)
            val void = SourceCallbackType.Declared(MCFPPPrivateType.Void.typeId)
            val inner = SourceCallbackSignature(listOf(SourceCallbackParameter(int)), void)
            val outer = SourceCallbackSignature(listOf(SourceCallbackParameter(int, ParamMode.REF),
                SourceCallbackParameter(int, ParamMode.CONST), SourceCallbackParameter(SourceCallbackType.Callback(inner), ParamMode.INLINE)), void)
            return SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void), listOf(outer))
        }
    }

    fun instanceEntry(value: Int): Long = value.toLong()

    @Test
    fun realMacroDescriptorModesAndNestedCallbacksBindFromDtoIntoFreshCatalogs() {
        val bindings = Bindings()
        val method = LibraryNativeTest::class.java.getDeclaredMethod("mixed", MacroContext::class.java, Value::class.java,
            Ref::class.java, Const::class.java, InlineFn::class.java)
        val descriptor = assertNotNull(MacroFunctionInfo.from(method))
            .declaredAs("sourceAlias", listOf("input", "target", "literal", "body"))
        val captured = NativeDeclarations.captureMacro("fixture", common("sourceAlias", 11), descriptor, bindings)
        val decoded = assertIs<MacroDeclaration>(roundtrip(captured))
        assertEquals(captured, decoded)
        assertEquals(listOf(MacroParameterMode.VALUE, MacroParameterMode.REF, MacroParameterMode.CONST, MacroParameterMode.INLINE), decoded.binding.parameters.map { it.mode })
        val callback = assertIs<MacroType.Callback>(decoded.binding.parameters.last().type)
        assertEquals(listOf(MacroParameterMode.REF, MacroParameterMode.CONST, MacroParameterMode.INLINE), callback.parameters.map { it.mode })
        assertIs<MacroType.Callback>(callback.parameters.last().type)
        val restore = CompilerTypeTableRestore(bindings.types.snapshot(), object : CompilerTypeBindings {})
        val first = NativeDeclarations.restoreMacro("fixture", decoded, restore, callbackCatalog(), bindings)
        val second = NativeDeclarations.restoreMacro("fixture", decoded, restore, callbackCatalog(), bindings)
        assertEquals(method, first.javaMethod)
        assertEquals("sourceAlias", first.identifier)
        assertEquals(listOf("input", "target", "literal", "body"), first.parameters.map { it.name() })
        assertEquals(listOf(ParamMode.VALUE, ParamMode.REF, ParamMode.CONST, ParamMode.INLINE), first.parameters.map { it.mode() })
        assertEquals(3, first.parameters.last().inlineSignature()!!.parameters().size)
        assertNotEquals(first.reference.key.universe, second.reference.key.universe)
    }

    @Test
    fun helperKindsSelectorIncludingNearestEntityAndTypeClosureRestoreFreshTypeIds() {
        val bindings = Bindings()
        val list = MCFPPListType(MCFPPBaseType.Int)
        val source = ReferenceHelperRecipe("fixture", "access", ReferenceKind.NBT_ENTITY,
            ReferenceTargetShape.Selector(EntitySelector.Companion.SelectorType.NEAREST_ENTITY,
                listOf(ReferenceTargetShape.Filter("tag", true, MCFPPBaseType.String.typeId))), true,
            linkedMapOf(bindings.generic.typeId to bindings.generic, list.typeId to list),
            listOf(ReferencePathShape.Field("value"), ReferencePathShape.Index))
        val captured = NativeDeclarations.captureReference(common("access", 12), source, bindings)
        val decoded = assertIs<ReferenceHelperDeclaration>(roundtrip(captured))
        assertEquals(captured, decoded)
        assertEquals(NativeSelectorKind.NEAREST_ENTITY, assertIs<LibraryReferenceTarget.Selector>(decoded.recipe.target).kind)
        assertTrue(decoded.recipe.operandTypes.size > source.operandTypes.size, "Constructor, argument and filter descriptors belong to the closure")
        val fresh = MCFPPGenericParamType("T", arrayListOf())
        val types = CompilerTypeTableRestore(bindings.types.snapshot(), object : CompilerTypeBindings {
            override fun restoreParameter(value: LibraryType.Parameter) = fresh
        })
        val restored = NativeDeclarations.restoreReference(decoded, types)
        assertTrue(fresh.typeId in restored.operandTypes)
        assertFalse(bindings.generic.typeId in restored.operandTypes)
        assertTrue(restored.operandTypes.all { it.key == it.value.typeId })
        assertEquals(EntitySelector.Companion.SelectorType.NEAREST_ENTITY, assertIs<ReferenceTargetShape.Selector>(restored.shape).kind)
        assertEquals(source.pathShape, restored.pathShape)
        for (kind in ReferenceKind.entries) {
            val value = NativeDeclarations.captureReference(common("access", 12), source.copy(kind = kind, shape = null), bindings)
            assertEquals(kind, NativeDeclarations.restoreReference(assertIs<ReferenceHelperDeclaration>(roundtrip(value)), types).kind)
        }
        assertEquals("LIB_BODY_UNSUPPORTED", assertFailsWith<SyntaxCaptureException> {
            NativeDeclarations.captureReference(common("access", 12), source.copy(shape = ReferenceTargetShape.BoundExecutor), bindings)
        }.code)
    }

    @Test
    fun exactPublicStaticJvmDescriptorsCheckPrimitiveArraysProviderAndSharedApi() {
        val bindings = Bindings()
        val descriptor = NativeBinding(bindings.packageKey, LibraryNativeTest::class.java.name, "primitiveArray", listOf("int", "[I"), "long")
        assertEquals(listOf("int", "[I"), NativeDeclarations.resolveMethod(descriptor, bindings).parameterTypes.map { it.name })
        assertFailsWith<IllegalArgumentException> { NativeDeclarations.resolveMethod(descriptor.copy(jvmResult = "int"), bindings) }
        assertFails { NativeDeclarations.resolveMethod(descriptor.copy(jvmParameters = listOf("int", "int[]")), bindings) }
        assertFailsWith<IllegalArgumentException> { NativeDeclarations.resolveMethod(descriptor.copy(providerPackage = null), bindings) }
        assertFailsWith<IllegalArgumentException> {
            NativeDeclarations.resolveMethod(descriptor.copy(method = "instanceEntry", jvmParameters = listOf("int")), bindings)
        }
        var checkedApi = false
        val loader = object : ClassLoader(LibraryNativeTest::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == MacroContext::class.java.name) { checkedApi = true; return String::class.java }
                return super.loadClass(name, resolve)
            }
        }
        assertFails { NativeDeclarations.resolveMethod(descriptor, object : ManagedNativeBindings by bindings {
            override fun loader(provider: PackageKey?) = loader
        }) }
        assertTrue(checkedApi)
    }

    @Test
    fun annotationProviderLinkageRoundtripsWithoutConstructingOrRunningActions() {
        val bindings = Bindings()
        val before = Marker.constructed
        val source = NativeDeclarations.captureAnnotationProvider(common("Marker", 13), Marker::class.java, emptyList(), "fixture.marker", 2, bindings)
        val decoded = assertIs<AnnotationProviderDeclaration>(roundtrip(source))
        assertEquals(source, decoded)
        assertSame(Marker::class.java, NativeDeclarations.restoreAnnotationProvider(decoded.binding, bindings))
        assertEquals(before, Marker.constructed)
        assertFails { NativeDeclarations.restoreAnnotationProvider(decoded.binding.copy(constructorParameters = listOf("int")), bindings) }
    }
}
