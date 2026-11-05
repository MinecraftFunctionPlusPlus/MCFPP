package top.mcfpp.test.library

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.LibraryFormatCode
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.mni.annotation.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import kotlin.test.*

class LibraryAnnotationTest {
    class Probe private constructor(restoring: Boolean, number: Int?, ratio: Float?) : Annotation("Probe", "fixture") {
        constructor(number: Int?, ratio: Float?) : this(false, number, ratio)
        init { if (!restoring) constructed++ }
        var counter = number ?: 0
        var shared: MutableList<Any?> = arrayListOf()
        var nested: MutableList<Any?> = arrayListOf()
        var map: MutableMap<Any?, Any?> = linkedMapOf()
        var set: MutableSet<Any?> = linkedSetOf()
        var paramsAlias: MutableList<String> = params
        var parentsAlias: MutableList<Annotation> = parents
        var link: Probe? = null
        var target: Any? = null
        var type: MCFPPType = MCFPPBaseType.Int
        val computed: Any get() = error("Computed state must not be read")
        override fun hashCode(): Int = error("Provider hashCode must not be read")
        override fun forFunction(function: Function) { actions++; error("Portable restoration must not apply annotations") }
        override fun forDataTemplate(data: DataTemplate) { actions++; error("Portable restoration must not apply annotations") }
        override fun forField(field: Var<*>) { actions++; error("Portable restoration must not apply annotations") }
        companion object {
            var constructed = 0
            var actions = 0
            fun restoreShell() = Probe(true, null, null)
        }
    }

    private object ProbeCodec : PortableAnnotationCodec<Probe> {
        override val annotationClass = Probe::class.java
        override val schema = "fixture.probe"
        override val version = 1L
        override val constructorParameters = listOf("java.lang.Integer", "java.lang.Float")
        override fun capture(value: Probe, writer: PortableAnnotationWriter) = writer.fields(
            "counter" to value.counter, "shared" to value.shared, "nested" to value.nested, "map" to value.map,
            "set" to value.set, "paramsAlias" to value.paramsAlias, "parentsAlias" to value.parentsAlias,
            "link" to value.link, "target" to (value.target?.let(writer::declaration) ?: writer.value(null)), "type" to writer.type(value.type))
        override fun allocateShell(reader: PortableAnnotationReader) = Probe.restoreShell()
        @Suppress("UNCHECKED_CAST")
        override fun restore(value: Probe, reader: PortableAnnotationReader) {
            value.counter = reader.integer("counter")
            value.shared = reader.list("shared"); value.nested = reader.list("nested")
            value.map = reader.map("map"); value.set = reader.set("set")
            value.paramsAlias = reader.list("paramsAlias") as MutableList<String>
            value.parentsAlias = reader.list("parentsAlias") as MutableList<Annotation>
            value.link = reader.value("link") as Probe?
            value.target = reader.value("target"); value.type = reader.value("type") as MCFPPType
        }
    }

    private class Capture(private val pending: Annotation? = null) : AnnotationCaptureBindings {
        val types = CompilerTypeTableCapture(object : CompilerTypeBindings {})
        val providers = linkedMapOf<AnnotationProviderBinding, DeclarationId>()
        val namespace = Namespace("fixture")
        override fun provider(binding: AnnotationProviderBinding) = DeclarationReference.Local(providers.getOrPut(binding) { DeclarationId(providers.size + 2L) })
        override fun constant(value: CompilerValue) = types.constant(value)
        override fun type(value: MCFPPType) = types.type(value)
        override fun declaration(value: Any): DeclarationReference {
            require(value === namespace)
            return DeclarationReference.Local(DeclarationId(1))
        }
        override fun phase(value: Annotation) = if (value === pending) AnnotationPhase.PENDING else AnnotationPhase.COMPLETED
        fun model(captured: CapturedAnnotations): LibraryModel {
            val environment = EnvironmentId(1)
            val owner = DeclarationId(1)
            val providerRecords = providers.map { (binding, _) ->
                val name = binding.ownerClass.substringAfterLast('.').replace('$', '_')
                AnnotationProviderDeclaration(DeclCommon(DeclarationKey("fixture", emptyList(), 13, name), null,
                    name, owner, environment, DeclarationAccess.PUBLIC, 0), binding)
            }
            val namespaceRecord = NamespaceDeclaration(DeclCommon(DeclarationKey("fixture", emptyList(), 1, "fixture"), null,
                "fixture", null, environment, DeclarationAccess.PUBLIC, 0), providers.values.toList())
            return LibraryModel(LibraryManifest(PackageKey("fixture:annotations", "1"), "main", LibraryTarget("1.21.4"),
                entryEnvironments = listOf(environment), rootDeclarations = listOf(owner)),
                LibraryDeclarations(listOf(namespaceRecord) + providerRecords, listOf(EnvironmentRecord("fixture", emptyList(), owner))),
                types.snapshot(), LibrarySyntax(emptyList(), emptyList(), emptyList()), captured.annotations, captured.stateCells)
        }
    }

    private class Restore(private val model: LibraryModel) : AnnotationRestoreBindings {
        val types = CompilerTypeTableRestore(model.tables, object : CompilerTypeBindings {})
        val namespace = Namespace("fixture")
        override fun provider(reference: DeclarationReference) =
            (model.declarations.declaration((reference as DeclarationReference.Local).target) as AnnotationProviderDeclaration).binding
        override fun constant(reference: ConstantRef) = types.constant(reference)
        override fun type(reference: TypeRef) = types.type(reference)
        override fun declaration(reference: DeclarationReference): Any { require(reference == DeclarationReference.Local(DeclarationId(1))); return namespace }
    }

    companion object {
        private val providerPackage = PackageKey("fixture:probe-codec", "1")
        private val managed = object : ManagedNativeBindings {
            override fun loader(provider: PackageKey?) = LibraryAnnotationTest::class.java.classLoader
            override fun provider(owner: Class<*>) = providerPackage
        }
        private fun registry() = PortableAnnotationCodecs.builtins().register(ProbeCodec, providerPackage, managed)
        private fun bytes(model: LibraryModel) = LibraryModelCodec(listOf(NativeCodec)).let { it.decode(it.encode(model)) }
    }

    @Test
    fun actualAhBytesPreserveSharedBaseContainersCyclesTypedArgumentsAndPhaseWithoutActions() {
        val parent = Probe(3, 1f)
        val source = Probe(7, -0.0f)
        source.parents += parent
        source.params += listOf("7", "-0.0")
        source.recordCompilerArguments(listOf(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)),
            CompilerValue.FloatBits((-0.0f).toRawBits())))
        val capture = Capture(source)
        source.target = capture.namespace
        source.shared.addAll(listOf("value", 42, CompilerValue.DoubleBits(0x7ff8_0000_0000_0001L), 'q'))
        source.shared.add(source.shared)
        source.shared.add(source.params)
        source.shared.add(source.parents)
        source.nested.addAll(listOf(source.shared, source.shared))
        source.map[7] = source.shared; source.map[8L] = source.nested; source.map['x'] = source.params
        source.set.addAll(listOf(1, 2L, 'z', false))
        source.link = source
        val beforeConstructed = Probe.constructed
        val beforeActions = Probe.actions
        registry().use { writer ->
            val captured = writer.capture(listOf(source, source), capture)
            val decoded = bytes(capture.model(captured))
            assertEquals(captured.annotations, decoded.annotations)
            assertEquals(captured.stateCells, decoded.stateCells)
            source.shared[0] = "mutated after capture"
            source.counter = 99
            val restore = Restore(decoded)
            registry().use { reader ->
                val arena = reader.restore(decoded.annotations, decoded.stateCells, restore)
                val value = arena.annotation(captured.roots[0]) as Probe
                assertSame(value, arena.annotation(captured.roots[1]))
                assertNotSame(source, value)
                assertEquals(7, value.counter)
                assertEquals("value", value.shared[0])
                assertEquals(42, value.shared[1])
                assertEquals(CompilerValue.DoubleBits(0x7ff8_0000_0000_0001L), value.shared[2])
                assertEquals('q', value.shared[3])
                assertSame(value.shared, value.shared[4])
                assertSame(value.params, value.shared[5]); assertSame(value.parents, value.shared[6])
                assertSame(value.shared, value.nested[0]); assertSame(value.shared, value.nested[1])
                assertSame(value.params, value.paramsAlias); assertSame(value.parents, value.parentsAlias)
                assertSame(value.shared, value.map[7]); assertSame(value.nested, value.map[8L]); assertSame(value.params, value.map['x'])
                assertTrue(1 in value.set && 2L in value.set && 'z' in value.set && false in value.set)
                assertSame(value, value.link); assertSame(restore.namespace, value.target)
                assertSame(MCFPPBaseType.Int, value.type)
                assertEquals(source.compilerArguments(), value.compilerArguments())
                assertEquals(AnnotationPhase.PENDING, arena.phase(value))
                assertEquals(AnnotationPhase.COMPLETED, arena.phase(value.parents.single()))
            }
        }
        assertEquals(beforeConstructed, Probe.constructed)
        assertEquals(beforeActions, Probe.actions)
    }

    @Test
    fun encodingMetadataReadOnDemandKeepsAliasesAndRestorationsIndependent() {
        val first = Probe(1, 1f)
        val second = Probe(2, 2f).apply { shared = first.shared }
        first.shared.add(first.shared)
        val capture = Capture()
        val metadata = arrayListOf<MutableList<Any?>>()
        val codec = object : PortableAnnotationCodec<Probe> by ProbeCodec {
            override fun restore(value: Probe, reader: PortableAnnotationReader) {
                ProbeCodec.restore(value, reader)
                // A provider can explicitly inspect the reserved directory through the public
                // reader. Its cell references must point at the same restored state containers.
                metadata.add(reader.list("@mcfpp.cell-hints"))
            }
        }
        PortableAnnotationCodecs.builtins().register(codec, providerPackage, managed).use { codecs ->
            val captured = codecs.capture(listOf(first, second), capture)
            val model = bytes(capture.model(captured))
            val restored = codecs.restore(model.annotations, model.stateCells, Restore(model))
            val a = restored.annotation(captured.roots[0]) as Probe
            val b = restored.annotation(captured.roots[1]) as Probe
            assertSame(a.shared, b.shared)
            assertSame(a.shared, a.shared.single())
            assertSame(metadata[0], metadata[1])
            assertTrue(metadata[0].any { (it as List<*>)[0] === a.shared })
            val next = codecs.restore(model.annotations, model.stateCells, Restore(model))
                .annotation(captured.roots[0]) as Probe
            a.shared.add("first compilation only")
            assertEquals(1, next.shared.size)
            assertSame(next.shared, next.shared.single())
            assertNotSame(a.params, next.params)
        }
    }

    @Test
    fun builtinsRetainProviderStateAndOriginalEntityParentsAcrossActualAhBytes() {
        val source = listOf<Annotation>(ConcreteOnly.restorePortable(), DataOnly(), Dynamic(), Load(), Tick(), MCFPPEntity(),
            NoInstance.restorePortable(), From("example.Source"), To("example.Target"), Name("display", "v2"), Name("without-version"))
        source[9].recordCompilerArguments(listOf(CompilerValue.Text("display"), CompilerValue.Text("v2")))
        val capture = Capture()
        PortableAnnotationCodecs.builtins().use { codecs ->
            assertEquals(10, codecs.bindings().size)
            assertTrue(codecs.bindings().none { it.ownerClass.endsWith("WritesReceiver") || it.ownerClass.endsWith("NoExternalWrites") })
            val captured = codecs.capture(source, capture)
            val model = bytes(capture.model(captured))
            val restored = codecs.restore(model.annotations, model.stateCells, Restore(model))
            source.indices.forEach { assertEquals(source[it].javaClass, restored.annotation(captured.roots[it]).javaClass) }
            val entity = restored.annotation(captured.roots[5]) as MCFPPEntity
            assertEquals(1, entity.parents.size)
            assertIs<DataOnly>(entity.parents.single())
            assertEquals("example.Source", (restored.annotation(captured.roots[7]) as From).className)
            assertEquals("example.Target", (restored.annotation(captured.roots[8]) as To).className)
            assertEquals("display", (restored.annotation(captured.roots[9]) as Name).name)
            assertEquals("v2", (restored.annotation(captured.roots[9]) as Name).version)
            assertNull((restored.annotation(captured.roots[10]) as Name).version)
            assertEquals(source[9].compilerArguments(), restored.annotation(captured.roots[9]).compilerArguments())
        }
    }

    @Test
    fun preparedArenaLoadsOnlyReachableAnnotationsAndDropsRequestCellsOnReset() {
        val first = Probe(1, 1f)
        val second = Probe(2, 2f).apply { shared = first.shared }
        val unused = Probe(3, 3f)
        first.link = first
        first.shared.add(first.shared)
        val capture = Capture()
        registry().use { codecs ->
            val captured = codecs.capture(listOf(first, second, unused), capture)
            val model = bytes(capture.model(captured))
            val plan = BuiltinImportPlan.build(model)
            val arena = codecs.restoreLazily(model.annotations, model.stateCells, Restore(model), plan.cellHints)
            assertEquals(0, arena.loadedCount())
            val reset = arena.checkpointForReuse()
            val a = arena.annotation(captured.roots[0]) as Probe
            assertEquals(1, arena.loadedCount())
            assertSame(a, a.link)
            assertSame(a.shared, a.shared.single())
            val b = arena.annotation(captured.roots[1]) as Probe
            assertEquals(2, arena.loadedCount())
            assertSame(a.shared, b.shared)
            a.shared.add("request only")
            reset()
            assertEquals(0, arena.loadedCount())
            val next = arena.annotation(captured.roots[0]) as Probe
            assertNotSame(a, next)
            assertEquals(1, next.shared.size)
            assertSame(next.shared, next.shared.single())
        }
    }

    @Test
    fun missingOrMismatchedCodecAndUnsupportedHostPayloadFailExplicitly() {
        val source = Probe(1, 1f)
        val capture = Capture()
        PortableAnnotationCodecs.builtins().use { codecs ->
            assertEquals("LIB_ANNOTATION_UNSUPPORTED", assertFailsWith<PortableAnnotationException> { codecs.capture(listOf(source), capture) }.code)
        }
        registry().use { codecs ->
            source.shared += Any()
            assertEquals("LIB_ANNOTATION_UNSUPPORTED", assertFailsWith<PortableAnnotationException> { codecs.capture(listOf(source), Capture()) }.code)
            source.shared.clear()
            val modelCapture = Capture()
            val good = codecs.capture(listOf(source), modelCapture)
            val model = modelCapture.model(good)
            val bad = object : AnnotationRestoreBindings by Restore(model) {
                override fun provider(reference: DeclarationReference) = Restore(model).provider(reference).copy(schemaVersion = 2)
            }
            assertFailsWith<PortableAnnotationException> { codecs.restore(good.annotations, good.stateCells, bad) }
            source.parents += source
            assertFailsWith<IllegalArgumentException> { codecs.capture(listOf(source), Capture(pending = source)) }
            val pendingCycle = modelCapture.model(good.copy(annotations = good.annotations.mapIndexed { index, record ->
                if (index == 0) record.copy(parents = listOf(AnnotationId(1)), phase = AnnotationPhase.PENDING) else record
            }))
            assertEquals(LibraryFormatCode.CORRUPT,
                assertFailsWith<LibraryFormatException> { LibraryModelCodec(listOf(NativeCodec)).encode(pendingCycle) }.code)
            val pendingRoot = Probe(2, 1f).apply { parents += source }
            assertFailsWith<IllegalArgumentException> { codecs.capture(listOf(pendingRoot), Capture(pending = pendingRoot)) }
        }
    }

    @Test
    fun fieldAnnotationCopiesKeepClosedArgumentsWithoutLiveCompilerObjects() {
        val source = Name("display")
        val arguments = listOf(
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)),
            CompilerValue.FloatBits((-0.0f).toRawBits()),
            CompilerValue.TypeValue(MCFPPBaseType.Int.typeId),
            CompilerValue.Record(mapOf("nested" to CompilerValue.Sequence(listOf(CompilerValue.Text("value")))))
        )
        source.recordCompilerArguments(arguments)
        val copied = top.mcfpp.model.property.Property.copyAnnotations(listOf(source)).single() as Name
        assertNotSame(source, copied)
        assertEquals("display", copied.name)
        assertEquals(arguments, copied.compilerArguments())
        copied.recordCompilerArguments(listOf(CompilerValue.Integral(9)))
        assertEquals(arguments, source.compilerArguments())
        assertEquals(listOf(CompilerValue.Integral(9)), copied.compilerArguments())
    }

    @Test
    fun closedRegistryRejectsFurtherCaptureAndRestore() {
        val codecs = registry()
        codecs.close(); codecs.close()
        assertFailsWith<IllegalStateException> { codecs.capture(emptyList(), Capture()) }
    }
}
