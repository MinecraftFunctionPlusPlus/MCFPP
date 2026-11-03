package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.property.Property
import top.mcfpp.test.DataInheritanceLibraryMetadataTest.GeneratingAnnotation
import top.mcfpp.test.DataInheritanceLibraryMetadataTest.MetadataMarker
import top.mcfpp.test.GenericTemplateAnnotationTest
import top.mcfpp.test.GenericTemplateAnnotationTest.BodyProbe
import top.mcfpp.test.GenericTemplateAnnotationTest.HeaderProbe
import top.mcfpp.test.GenericTemplateAnnotationTest.ParentProbe
import top.mcfpp.test.TemplateFieldInferenceTest.MutableFieldAnnotation
import top.mcfpp.test.TemplateFieldInferenceTest.NonSerializableFieldAnnotation
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import java.lang.reflect.Method
import java.util.IdentityHashMap
import kotlin.test.*

/** Explicit adapters for the real legacy fixture providers, without replacing their source actions. */
internal object AnnotationMetadataFixtureCodecs {
    private val providerPackage = PackageKey("fixture:metadata-codecs", "1")
    private val managed = object : ManagedNativeBindings {
        override fun loader(provider: PackageKey?) = AnnotationMetadataFixtureCodecs::class.java.classLoader
        override fun provider(owner: Class<*>) = providerPackage
    }
    private class Codec<A : Annotation>(
        override val annotationClass: Class<A>, override val constructorParameters: List<String> = emptyList(),
        private val capture: (A, PortableAnnotationWriter) -> List<StateField>,
        private val allocate: () -> A, private val restore: (A, PortableAnnotationReader) -> Unit
    ) : PortableAnnotationCodec<A> {
        override val schema = "fixture.metadata.${annotationClass.name}"
        override val version = 1L
        override fun capture(value: A, writer: PortableAnnotationWriter) = capture.invoke(value, writer)
        override fun allocateShell(reader: PortableAnnotationReader) = allocate()
        override fun restore(value: A, reader: PortableAnnotationReader) = restore.invoke(value, reader)
    }
    @Suppress("UNCHECKED_CAST")
    fun registry(): PortableAnnotationCodecs = PortableAnnotationCodecs.builtins().apply {
        register(Codec(MetadataMarker::class.java, capture = { value, writer -> writer.fields("values" to value.values) },
            allocate = ::MetadataMarker, restore = { value, reader -> value.restorePortableValues(reader.list("values") as ArrayList<String>) }), providerPackage, managed)
        register(Codec(GeneratingAnnotation::class.java, capture = { value, writer -> writer.fields("applications" to value.applications) },
            allocate = ::GeneratingAnnotation, restore = { value, reader -> value.applications = reader.integer("applications") }), providerPackage, managed)
        register(Codec(HeaderProbe::class.java, capture = { value, writer -> writer.fields("applications" to value.applications,
            "environments" to value.environments, "shared" to value.shared, "nested" to value.nested) }, allocate = { HeaderProbe.restoreShell() },
            restore = { value, reader -> value.restorePortableState(reader.integer("applications"), reader.list("environments") as ArrayList<String>,
                reader.list("shared") as ArrayList<String>, reader.list("nested") as ArrayList<ArrayList<String>>) }), providerPackage, managed)
        register(Codec(ParentProbe::class.java, listOf("java.util.ArrayList"),
            { value, writer -> writer.fields("applications" to value.applications, "shared" to value.shared) }, { ParentProbe.restoreShell() },
            { value, reader -> value.restorePortableState(reader.integer("applications"), reader.list("shared") as ArrayList<String>) }), providerPackage, managed)
        register(Codec(BodyProbe::class.java, capture = { value, writer -> writer.fields("applications" to value.applications) },
            allocate = ::BodyProbe, restore = { value, reader -> value.applications = reader.integer("applications") }), providerPackage, managed)
        register(Codec(MutableFieldAnnotation::class.java, capture = { value, writer -> writer.fields("values" to value.values) },
            allocate = ::MutableFieldAnnotation, restore = { value, reader -> value.restorePortableValues(reader.list("values") as ArrayList<String>) }), providerPackage, managed)
        register(Codec(NonSerializableFieldAnnotation::class.java, capture = { value, writer -> writer.fields("payload" to value.payload) },
            allocate = ::NonSerializableFieldAnnotation, restore = { _, _ -> error("An unsupported payload must fail before restoration") }), providerPackage, managed)
    }
}

class AnnotationMetadataFixturesTest {
    private class Capture(private val reserved: Int = 1, private val nominal: Map<top.mcfpp.type.TypeId, DeclarationId> = emptyMap()) : AnnotationCaptureBindings {
        val types = CompilerTypeTableCapture(object : CompilerTypeBindings {
            override fun declaration(id: top.mcfpp.type.TypeId) = nominal[id]?.let(DeclarationReference::Local)
        })
        val providers = linkedMapOf<AnnotationProviderBinding, DeclarationId>()
        val phases = IdentityHashMap<Annotation, AnnotationPhase>()
        override fun provider(binding: AnnotationProviderBinding) = DeclarationReference.Local(providers.getOrPut(binding) { DeclarationId(reserved + providers.size + 1L) })
        override fun constant(value: CompilerValue) = types.constant(value)
        override fun type(value: MCFPPType) = types.type(value)
        override fun declaration(value: Any): DeclarationReference = error("Fixture state has no declaration-valued fields")
        override fun phase(value: Annotation) = phases[value] ?: AnnotationPhase.COMPLETED
        fun model(captured: CapturedAnnotations, fixed: List<LibraryDeclaration> = emptyList(), syntax: LibrarySyntax = LibrarySyntax(emptyList(), emptyList(), emptyList())): LibraryModel {
            require(fixed.size + 1 == reserved)
            val namespace = NamespaceDeclaration(common("fixture.metadata", 1), fixed.indices.filter {
                fixed[it].common.owner == DeclarationId(1)
            }.map { DeclarationId(it + 2L) } + providers.values)
            val providerRecords = providers.map { (binding, _) ->
                val name = binding.ownerClass.substringAfterLast('.').replace('$', '_')
                AnnotationProviderDeclaration(common(name, 13, DeclarationId(1)), binding)
            }
            return LibraryModel(LibraryManifest(PackageKey("fixture:metadata", "1"), "main", LibraryTarget("1.21.4"),
                entryEnvironments = listOf(EnvironmentId(1)), rootDeclarations = listOf(DeclarationId(1))),
                LibraryDeclarations(listOf(namespace) + fixed + providerRecords, listOf(EnvironmentRecord("fixture.metadata", emptyList(), DeclarationId(1)))),
                types.snapshot(), syntax, captured.annotations, captured.stateCells)
        }
        fun expression(source: Parser.ExpressionContext, scope: ScopeId): Expression {
            val facts = mutableMapOf<ConstantRef, CompilerValue>()
            return ExpressionBridge(object : ExpressionCaptureBindings {
                override fun scope(site: ParserRuleContext) = scope
                override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse = error("Initializer is a closed scalar")
                override fun builtinType(key: String, site: ParserRuleContext) = types.type(LibraryBuiltinTypes.restore(key))
                override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef {
                    val fact = when (value) {
                        is ConstantValue.Integral -> CompilerValue.Integral(value.value)
                        is ConstantValue.Typed -> CompilerValue.Typed(LibraryBuiltinTypes.restore((types.snapshot().type(value.type) as LibraryType.Builtin).key).typeId, facts.getValue(value.value))
                        else -> error("Initializer is an integral literal")
                    }
                    return constant(fact).also { facts[it] = fact }
                }
            }).expression(source)
        }
    }

    private class Restore(val model: LibraryModel) : CompoundRestoreBindings, FunctionRestoreBindings {
        val shells = linkedMapOf<DeclarationId, CompoundShell>()
        private val types = CompilerTypeTableRestore(model.tables, object : CompilerTypeBindings {
            override fun restoreDeclaration(reference: DeclarationReference) = (shells.getValue((reference as DeclarationReference.Local).target).value as DataTemplate).getType()
        })
        val file = MCFPPFile(Namespace("fixture.metadata"))
        lateinit var annotations: RestoredAnnotations
        val initializers = linkedMapOf<DeclarationId, BodyId>()
        val annotationBindings = object : AnnotationRestoreBindings {
            override fun provider(reference: DeclarationReference) = (this@Restore.declaration((reference as DeclarationReference.Local).target) as AnnotationProviderDeclaration).binding
            override fun constant(reference: ConstantRef) = types.constant(reference)
            override fun type(reference: TypeRef) = types.type(reference)
            override fun declaration(reference: DeclarationReference): Any = this@Restore.reference(reference)
        }
        override fun constant(reference: ConstantRef) = RestoredDefault(types.constant(reference))
        override fun type(reference: TypeRef) = types.type(reference)
        override fun declaration(reference: DeclarationId) = model.declarations.declaration(reference)
        override fun shell(reference: DeclarationId) = shells.getValue(reference)
        override fun reference(reference: DeclarationReference): Any = requireNotNull(shell((reference as DeclarationReference.Local).target).value)
        override fun environment(reference: EnvironmentId) = file
        override fun annotation(reference: AnnotationId) = annotations.annotation(reference)
        override fun template(owner: DeclarationId): Parser.TemplateBodyContext = error("No generic template model is restored in this harness")
        override fun defaultValue(reference: BodyId): Parser.ValueContext? = null
        override fun nativeMethod(binding: NativeBinding): Method = error("No native function fixture")
        override fun owner(reference: DeclarationId) = shells[reference]?.value as? DataTemplate
        override fun allocated(shell: CompoundShell) { shells[shell.id] = shell }
        override fun initializer(owner: DeclarationId, field: DeclarationId, body: BodyId) { check(initializers.put(field, body) == null) }
        override fun bound(shell: CompoundShell) {
            val target = shell.value as? DataTemplate ?: return
            shell.declaration.common.annotations.filter { annotations.phase(it) == AnnotationPhase.PENDING }.forEach {
                target.pendingDeclarationAnnotations += annotations.annotation(it)
            }
        }
        fun allocate() { model.declarations.declarations.forEachIndexed { index, record -> CompoundDeclarations.allocateShell(DeclarationId(index + 1L), record, this) } }
        fun bind() {
            model.declarations.declarations.forEachIndexed { index, record ->
                val id = DeclarationId(index + 1L)
                when (record) {
                    is ValueDeclaration, is PropertyDeclaration -> CompoundDeclarations.bind(shell(id), this)
                    is FunctionDeclaration -> shell(id).value = FunctionDeclarations.restoreHead(id, record, { declaration(it) as ValueDeclaration }, this).function
                }
            }
            model.declarations.declarations.forEachIndexed { index, record -> if (record is CompoundDeclaration) CompoundDeclarations.bind(shell(DeclarationId(index + 1L)), this) }
        }
    }

    companion object {
        private fun common(name: String, kind: Int, owner: DeclarationId? = null, annotations: List<AnnotationId> = emptyList(), flags: Long = 0): DeclCommon {
            val ownerPath = when (owner?.value) {
                2L -> listOf(DeclarationPathPart(3, "Root")); 3L -> listOf(DeclarationPathPart(3, "Middle"))
                10L -> listOf(DeclarationPathPart(3, "Pending")); else -> emptyList()
            }
            return DeclCommon(DeclarationKey("fixture.metadata", ownerPath, kind, name,
                if (kind == 2) DeclarationSignature(emptyList(), emptyList()) else null), null, name, owner, EnvironmentId(1), DeclarationAccess.PUBLIC, flags, annotations)
        }
        private fun parser(source: String) = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        private fun bytes(model: LibraryModel) = LibraryModelCodec(listOf(CompoundCodec, NativeCodec)).let { it.decode(it.encode(model)) }
        private fun field(owner: DataTemplate, name: String = "value"): MCInt = MCInt(name).apply {
            declaredParentTemplate = owner; bindDeclaration()
            check(owner.addMember(this)); check(owner.addMember(Property.buildSimpleProperty(this).apply { declaredParentTemplate = owner }))
        }
        private fun integral(value: CompilerValue): Long = when (value) {
            is CompilerValue.Typed -> integral(value.payload); is CompilerValue.Integral -> value.value; else -> error("Expected integral")
        }
    }

    @Test
    fun realMetadataProvidersKeepCompletedGeneratedEffectsAndPendingActionsSeparateAcrossV1Bytes() {
        val root = DataTemplate("Root", "fixture.metadata")
        field(root); root.preInit["value"] = parser("1").expression()
        val middle = DataTemplate("Middle", "fixture.metadata").apply { extends(root); flatExtends() }
        val generator = GeneratingAnnotation()
        generator.forDataTemplate(middle)
        val marker = MetadataMarker().apply { values += "saved" }
        val pending = GeneratingAnnotation()
        val capture = Capture(10, mapOf(root.getType().typeId to DeclarationId(2)))
        capture.phases[pending] = AnnotationPhase.PENDING
        AnnotationMetadataFixtureCodecs.registry().use { codecs ->
            val captured = codecs.capture(listOf(generator, marker, pending), capture)
            val int = capture.type(MCFPPBaseType.Int); val void = capture.type(MCFPPPrivateType.Void); val parent = capture.type(root.getType())
            val sourceEffect = middle.completedInheritedFields().single()
            assertTrue((sourceEffect.field as MCInt).isDataOnly)
            val rootInit = capture.expression(root.preInit.getValue("value"), ScopeId(1))
            val generatedInit = capture.expression(middle.preInit.getValue("generated_1"), ScopeId(2))
            val syntax = SyntaxCodec.flatten(listOf(ScopeRecord(DeclarationId(4), null, emptyList()), ScopeRecord(DeclarationId(6), null, emptyList()),
                ScopeRecord(DeclarationId(9), null, emptyList())), listOf(
                SyntaxBodySource(DeclarationId(4), EnvironmentId(1), BodyRole.INITIALIZER, rootInit),
                SyntaxBodySource(DeclarationId(6), EnvironmentId(1), BodyRole.INITIALIZER, generatedInit),
                SyntaxBodySource(DeclarationId(9), EnvironmentId(1), BodyRole.FUNCTION, Statement.Block(ScopeId(3), emptyList()))))
            fun value(name: String, owner: Long, body: BodyId? = null, flags: Long = 0) = ValueDeclaration(common(name, 5, DeclarationId(owner), flags = flags), ValueRole.MEMBER,
                ValueStorage.VAR, int, null, body, null)
            fun property(name: String, owner: Long, backing: Long) = PropertyDeclaration(common(name, 6, DeclarationId(owner)), DeclarationReference.Local(DeclarationId(backing)), LibraryAccessor.Simple, LibraryAccessor.Simple)
            val records = listOf<LibraryDeclaration>(
                CompoundDeclaration(common("Root", 3, DeclarationId(1)), CompoundCategory.DATA, emptyList(), emptyList(), null,
                    listOf(DeclarationId(4), DeclarationId(5)), listOf(DeclarationId(4), DeclarationId(5)), null, emptyList()),
                CompoundDeclaration(common("Middle", 3, DeclarationId(1), listOf(captured.roots[0])), CompoundCategory.DATA, emptyList(), listOf(ParentRecord(parent, null)), null,
                    listOf(DeclarationId(6), DeclarationId(7)), listOf(DeclarationId(6), DeclarationId(7)), null,
                    listOf(InheritedEffect(parent, DeclarationReference.Local(DeclarationId(4)), DeclarationId(8), null, null, null))),
                value("value", 2, BodyId(1)), property("value", 2, 4), value("generated_1", 3, BodyId(2)), property("generated_1", 3, 6),
                value("value", 3, flags = DeclarationFlags.DATA_ONLY),
                FunctionDeclaration(common("tagged", 2, DeclarationId(1), listOf(captured.roots[1])), FunctionExecutionKind.ORDINARY, null, emptyList(), emptyList(), void, BodyId(3), null),
                CompoundDeclaration(common("Pending", 3, DeclarationId(1), listOf(captured.roots[2])), CompoundCategory.DATA, emptyList(), listOf(ParentRecord(parent, null)), null,
                    emptyList(), emptyList(), null, emptyList()))
            val model = bytes(capture.model(captured, records, syntax))
            generator.applications = 99; marker.values += "writer-only"; pending.applications = 99
            val restore = Restore(model)
            restore.allocate(); restore.annotations = codecs.restore(model.annotations, model.stateCells, restore.annotationBindings); restore.bind()
            val freshRoot = restore.shell(DeclarationId(2)).value as DataTemplate
            val freshMiddle = restore.shell(DeclarationId(3)).value as DataTemplate
            val freshPending = restore.shell(DeclarationId(10)).value as DataTemplate
            freshMiddle.flatExtends(); freshPending.flatExtends()
            val completed = freshMiddle.annotations.single() as GeneratingAnnotation
            assertEquals(1, completed.applications)
            assertNotNull(freshMiddle.scope.getVar("generated_1")); assertNotNull(freshMiddle.scope.getProperty("generated_1"))
            assertTrue((freshMiddle.scope.getVar("value") as MCInt).isDataOnly)
            assertSame(freshRoot, freshMiddle.scope.fieldOrigins.getValue("value").single().owner)
            assertFalse(DeclarationId(8) in (model.declarations.declaration(DeclarationId(3)) as CompoundDeclaration).members)
            val initializer = model.syntax.node(model.syntax.body(restore.initializers.getValue(DeclarationId(6))).root) as Expression.Literal
            assertEquals(17L, integral(restore.constant(initializer.constant).value))
            assertFalse(DeclarationId(8) in restore.initializers)
            val tagged = restore.shell(DeclarationId(9)).value as Function
            assertEquals(listOf("saved"), (tagged.annotations.single() as MetadataMarker).values)
            val freshGenerator = freshPending.annotations.single() as GeneratingAnnotation
            assertEquals(0, freshGenerator.applications)
            freshMiddle.applyDeclarationAnnotations(); freshPending.applyDeclarationAnnotations(); freshPending.applyDeclarationAnnotations()
            assertEquals(1, completed.applications); assertEquals(1, freshGenerator.applications)
            assertNull(freshMiddle.scope.getVar("generated_2")); assertNull(freshPending.scope.getVar("generated_2"))
            assertTrue(freshPending.pendingDeclarationAnnotations.isEmpty())
        }
    }

    @Test
    fun realHeaderParentAndBodyProvidersPreserveAliasesAndAuthorEnvironmentForEachFreshArena() {
        val header = HeaderProbe(); val body = BodyProbe()
        val capture = Capture()
        capture.phases[header] = AnnotationPhase.PENDING; capture.phases[header.parents.single()] = AnnotationPhase.PENDING; capture.phases[body] = AnnotationPhase.PENDING
        GenericTemplateAnnotationTest.clearPortableApplicationOrder()
        try {
            AnnotationMetadataFixtureCodecs.registry().use { codecs ->
                val captured = codecs.capture(listOf(header, body), capture)
                val model = bytes(capture.model(captured))
                assertTrue(model.declarations.declarations.filterIsInstance<AnnotationProviderDeclaration>().any { it.binding.ownerClass == ParentProbe::class.java.name })
                assertTrue(top.mcfpp.model.scope.GlobalScope.localNamespaces.values.none { it.scope.annotations.values.any { type -> type === ParentProbe::class.java } })
                header.shared += "writer-only"
                val readers = List(2) { codecs.restore(model.annotations, model.stateCells, Restore(model).annotationBindings) }
                val restored = readers.map { it.annotation(captured.roots[0]) as HeaderProbe }
                restored.forEachIndexed { index, value ->
                    val parent = value.parents.single() as ParentProbe
                    assertSame(value.shared, value.nested.single()); assertSame(value.shared, parent.shared)
                    assertEquals(listOf("seed"), value.shared)
                    val target = DataTemplate("Binding$index", value.namespace)
                    field(target)
                    target.declarationFile = MCFPPFile(Namespace(value.namespace))
                    target.annotations += value; target.pendingDeclarationAnnotations += value
                    val fieldAnnotation = readers[index].annotation(captured.roots[1]) as BodyProbe
                    target.pendingFieldAnnotations["value"] = arrayListOf(fieldAnnotation)
                    target.applyDeclarationAnnotations(); target.applyDeclarationAnnotations()
                    assertEquals(1, value.applications); assertEquals(1, parent.applications); assertEquals(1, fieldAnnotation.applications)
                    assertEquals(listOf(value.namespace, value.namespace), value.environments)
                    assertEquals(listOf("seed", "parent-applied"), value.shared)
                    assertTrue((target.scope.getVar("value") as MCInt).isDataOnly)
                    assertTrue(target.pendingDeclarationAnnotations.isEmpty() && target.pendingFieldAnnotations.isEmpty())
                }
                assertNotSame(restored[0].shared, restored[1].shared)
                restored[0].shared += "reader-only"
                assertEquals(listOf("seed", "parent-applied"), restored[1].shared)
                assertEquals(listOf("seed", "writer-only"), header.shared)
                assertEquals(0, header.applications)
                assertEquals(listOf("header", "body", "header", "body"), GenericTemplateAnnotationTest.portableApplicationOrder())
            }
        } finally { GenericTemplateAnnotationTest.clearPortableApplicationOrder() }
    }

    @Test
    fun realMutableFieldProviderSucceedsAndItsUnsupportedAnyPayloadKeepsTheExistingNegativeBoundary() {
        val source = MutableFieldAnnotation().apply { values += "original"; params += "saved" }
        val capture = Capture()
        AnnotationMetadataFixtureCodecs.registry().use { codecs ->
            val captured = codecs.capture(listOf(source, source), capture)
            val model = bytes(capture.model(captured))
            source.values += "writer-only"
            val first = codecs.restore(model.annotations, model.stateCells, Restore(model).annotationBindings)
            val second = codecs.restore(model.annotations, model.stateCells, Restore(model).annotationBindings)
            val left = first.annotation(captured.roots[0]) as MutableFieldAnnotation
            val right = second.annotation(captured.roots[0]) as MutableFieldAnnotation
            assertSame(left, first.annotation(captured.roots[1]))
            assertEquals(listOf("original"), left.values); assertEquals(listOf("saved"), left.params)
            left.values += "reader-only"
            assertEquals(listOf("original"), right.values)
            assertEquals(listOf("original", "writer-only"), source.values)
            val failure = assertFailsWith<PortableAnnotationException> { codecs.capture(listOf(NonSerializableFieldAnnotation()), Capture()) }
            assertEquals("LIB_ANNOTATION_UNSUPPORTED", failure.code)
        }
    }
}
