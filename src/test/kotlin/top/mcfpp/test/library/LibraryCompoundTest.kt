package top.mcfpp.test.library

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.MCNumber
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum as CompilerEnum
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.SimpleScopeWithType
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import java.lang.reflect.Method
import kotlin.test.*

class LibraryCompoundTest {
    private class Arena : CompoundCaptureBindings, CompilerTypeBindings {
        private val ids = java.util.IdentityHashMap<Any, DeclarationId>()
        private val operatorIds = mutableMapOf<CompoundDeclarations.OperatorSlot, DeclarationId>()
        private var sequence = 0L
        private var scopeSequence = 1L
        private var scopeId = ScopeId(1)
        private var ownerId = DeclarationId(1)
        private val visible = mutableMapOf<String, DeclarationId>()
        val types = CompilerTypeTableCapture(this)
        val external = mutableMapOf<top.mcfpp.type.TypeId, MCFPPType>()
        val bodies = mutableListOf<SyntaxBodySource>()
        val nodes = mutableListOf<TypeSyntax>()
        val templates = mutableMapOf<DeclarationId, TemplateBody>()
        val origins = java.util.IdentityHashMap<Any, AnonymousOrigin>()
        val contexts = java.util.IdentityHashMap<DataTemplate, ParserRuleContext>()
        override fun id(value: Any, owner: DeclarationId?): DeclarationId = if (value is CompoundDeclarations.OperatorSlot)
            operatorIds.getOrPut(value) { DeclarationId(++sequence) } else ids.getOrPut(value) { DeclarationId(++sequence) }
        override fun reference(value: Any): DeclarationReference {
            if (value is top.mcfpp.core.lang.Var<*>) {
                val parent = value.declaredParentTemplate
                if (parent != null && parent.getType().typeId in external) return DeclarationReference.Dependency(1,
                    DeclarationKey(parent.namespace, listOf(DeclarationPathPart(3, parent.identifier)), 5, value.identifier))
            }
            return DeclarationReference.Local(id(value, null))
        }
        override fun declaration(id: top.mcfpp.type.TypeId): DeclarationReference? = external[id]?.let {
            DeclarationReference.Dependency(1, DeclarationKey("fixture", emptyList(), 3, it.typeName))
        }
        override fun compoundKey(value: Any): DeclarationKey? = if (value in origins) null else when (value) {
            is DataTemplate -> DeclarationKey(value.namespace, emptyList(), 3, value.identifier)
            is CompilerEnum -> DeclarationKey(value.namespace, emptyList(), 8, value.identifier)
            else -> null
        }
        override fun compoundOrigin(value: Any) = origins[value]
        override fun compoundEnvironment(value: Any) = EnvironmentId(1)
        override fun environment(function: Function) = EnvironmentId(1)
        override fun key(function: Function): DeclarationKey? = null
        override fun declarationOwner(function: Function) = function.owner?.let { id(it, null) }
        override fun reserveParameter(owner: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int) = id(parameter, owner)
        override fun <T> inFunction(function: Function, owner: DeclarationId, environment: EnvironmentId, action: () -> T): T {
            val previous = ownerId; ownerId = owner
            return try { action() } finally { ownerId = previous }
        }
        override fun publishParameter(id: DeclarationId, parameter: FunctionParam, readonly: Boolean, index: Int) { visible[parameter.identifier] = id }
        override fun type(value: MCFPPType, owner: DeclarationId, declaredSyntax: NodeRef?) = types.type(value)
        override fun identity(value: top.mcfpp.type.TypeId) = types.identity(value)
        override fun dependentType(owner: DeclarationId, syntax: NodeRef): TypeRef = error("Fixture has prepared types")
        override fun constant(value: CompilerValue, types: Map<top.mcfpp.type.TypeId, MCFPPType>) = this.types.constant(value)
        override fun storeNode(owner: DeclarationId, node: TypeSyntax) = NodeRef(nodes.size + 1L).also { nodes += node }
        override fun storeBody(body: SyntaxBodySource) = BodyId(bodies.size + 1L).also { bodies += body }
        override fun <T> inCompound(value: DataTemplate, owner: DeclarationId, action: () -> T): T {
            val previous = ownerId; ownerId = owner
            return try { action() } finally { ownerId = previous }
        }
        override fun declarationContext(value: DataTemplate) = contexts[value]
        override fun storeTemplate(owner: DeclarationId, node: TemplateBody) { check(templates.put(owner, node) == null) }
        override fun scope(site: ParserRuleContext) = scopeId
        override fun <T> withScope(site: ParserRuleContext, owner: DeclarationId?, action: (ScopeId) -> T): T {
            val previous = scopeId; scopeId = ScopeId(++scopeSequence)
            return try { action(scopeId) } finally { scopeId = previous }
        }
        override fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse =
            visible[name]?.let { NameUse.Local(it) } ?: NameUse.Declaration(DeclarationReference.Dependency(1, DeclarationKey(namespace ?: "builtin", emptyList(), 3, name)))
        override fun builtinType(key: String, site: ParserRuleContext) = types.type(LibraryBuiltinTypes.restore(key))
        override fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef = when (value) {
            is ConstantValue.Integral -> types.constant(CompilerValue.Integral(value.value))
            is ConstantValue.Typed -> {
                val payload = CompilerTypeTableRestore(types.snapshot(), this).constant(value.value)
                val type = (types.snapshot().type(value.type) as LibraryType.Builtin).key
                types.constant(CompilerValue.Typed(LibraryBuiltinTypes.restore(type).typeId, payload))
            }
            else -> error("No other source literals in this fixture")
        }
        override fun reserveLocal(site: Parser.FieldDeclarationContext) = id(site, ownerId)
        override fun completeLocal(declaration: DeclarationId, facts: LocalDeclarationFacts) {}
        override fun publishLocal(declaration: DeclarationId, name: String, site: ParserRuleContext) { visible[name] = declaration }
        override fun reserveIterationVariable(site: Parser.ForeachStatementContext, iterable: Expression) = id(site, ownerId)
        override fun destination(name: String, site: Parser.TryStoreStatementContext) = this.name(name, null, NameRole.VALUE, site)
        override fun executePathPart(site: Parser.VarContext, capture: StatementBridge): Expression = error("No execute fixture")
        override fun reserveLambda(site: Parser.InlineLambdaContext): LambdaAllocation = error("No lambda fixture")
        override fun completeLambdaParameters(allocation: LambdaAllocation, parameters: List<LambdaParameterSyntax>, explicit: Boolean) {}
        override fun completeLambda(allocation: LambdaAllocation, body: LambdaBodyCapture) {}
        override fun receiverCapture(owner: DeclarationReference): DeclarationReference? = null
        override fun templateMember(site: Parser.TemplateMemberDeclarationContext, capture: StatementBridge): TemplateMember =
            error("Empty generic fixture needs no members")
    }

    private class Restore(val records: Map<DeclarationId, LibraryDeclaration>, tables: LibraryTables,
                          externalTypes: Map<String, DataTemplate> = emptyMap(),
                          val templateRoots: Map<DeclarationId, TemplateBody> = emptyMap(),
                          val externalDeclarations: Map<DeclarationKey, Any> = emptyMap()) : CompoundRestoreBindings, FunctionRestoreBindings {
        val shells = linkedMapOf<DeclarationId, CompoundShell>()
        val file = MCFPPFile(Namespace("fixture"))
        val initializers = mutableListOf<Triple<DeclarationId, DeclarationId, BodyId>>()
        val pendingParents = mutableMapOf<DeclarationId, List<ParentRecord>>()
        val types = CompilerTypeTableRestore(tables, object : CompilerTypeBindings {
            override fun restoreDeclaration(reference: DeclarationReference) = externalTypes.getValue((reference as DeclarationReference.Dependency).key.name).getType()
        })
        override fun declaration(reference: DeclarationId) = records.getValue(reference)
        override fun shell(reference: DeclarationId) = shells.getValue(reference)
        override fun reference(reference: DeclarationReference): Any = when (reference) {
            is DeclarationReference.Local -> shell(reference.target).value!!
            is DeclarationReference.Dependency -> externalDeclarations.getValue(reference.key)
        }
        override fun type(reference: TypeRef) = types.type(reference)
        override fun constant(reference: ConstantRef) = RestoredDefault(types.constant(reference))
        override fun environment(reference: EnvironmentId) = file
        override fun annotation(reference: AnnotationId): top.mcfpp.model.annotation.Annotation = error("No annotations")
        override fun template(owner: DeclarationId) = StatementProjection(object : ProjectionBindings {
            override fun name(use: NameUse, role: NameRole): ProjectedName = error("Empty member stream")
            override fun constant(reference: ConstantRef): ConstantValue = error("Empty member stream")
        }, object : StatementProjectionBindings {
            override fun body(reference: BodyId): SemanticNode = error("Empty member stream")
            override fun name(declaration: DeclarationId): String = error("Empty member stream")
            override fun local(declaration: DeclarationId): LocalDeclarationFacts = error("Empty member stream")
            override fun lambdaParameter(declaration: DeclarationId): LambdaParameterSyntax = error("Empty member stream")
        }).template(templateRoots.getValue(owner))
        override fun defaultValue(reference: BodyId): Parser.ValueContext? = null
        override fun nativeMethod(binding: NativeBinding): Method = error("No native fixture")
        override fun owner(reference: DeclarationId): DataTemplate? {
            var current: DeclarationId? = reference
            while (current != null) {
                (shells[current]?.value as? DataTemplate)?.let { return it }
                current = records[current]?.common?.owner
            }
            return null
        }
        override fun allocated(shell: CompoundShell) { shells[shell.id] = shell }
        override fun initializer(owner: DeclarationId, field: DeclarationId, body: BodyId) { initializers += Triple(owner, field, body) }
        override fun deferredParents(owner: DeclarationId, parents: List<ParentRecord>) { pendingParents[owner] = parents }
        fun restore(root: DeclarationId): Any {
            records.forEach { (id, record) -> CompoundDeclarations.allocateShell(id, record, this) }
            records.filterValues { it is FunctionDeclaration }.forEach { (id, record) ->
                shells.getValue(id).value = FunctionDeclarations.restoreHead(id, record as FunctionDeclaration,
                    { declaration(it) as ValueDeclaration }, this).function
            }
            records.filterValues { it is ValueDeclaration && it.role == ValueRole.MEMBER }.keys.forEach { CompoundDeclarations.bind(shell(it), this) }
            records.filterValues { it is ConstructorDeclaration || it is PropertyDeclaration || it is OperatorDeclaration || it is EnumMemberDeclaration }.keys
                .forEach { CompoundDeclarations.bind(shell(it), this) }
            return CompoundDeclarations.bind(shell(root), this)
        }
    }

    companion object {
        private fun parser(source: String) = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        private fun function(owner: DataTemplate, name: String, literal: Int) = Function(name, owner, parser("{ return $literal; }").curlBlock()).apply {
            returnType = MCFPPBaseType.Int
        }
        private fun roundtrip(records: Map<DeclarationId, LibraryDeclaration>): Map<DeclarationId, LibraryDeclaration> {
            val strings = LibraryStrings.of(records.values.flatMap { listOf(it.common.name) + CompoundCodec.collectStrings(it) })
            return records.mapValues { (_, record) ->
                val writer = LibraryBinaryWriter()
                FunctionCodec.writeCommon(writer, record.common, strings)
                writer.tagged(record.tag) {
                    when (record) {
                        is FunctionDeclaration -> FunctionCodec.writeFunctionPayload(this, record, strings)
                        is ValueDeclaration -> FunctionCodec.writeValuePayload(this, record)
                        else -> CompoundCodec.writePayload(this, record, strings)
                    }
                }
                val reader = LibraryBinaryReader(writer.toByteArray())
                val common = FunctionCodec.readCommon(reader, strings)
                reader.tagged { tag -> when (tag) {
                    2 -> FunctionCodec.readFunctionPayload(this, common, strings)
                    5 -> FunctionCodec.readValuePayload(this, common)
                    else -> CompoundCodec.readPayload(this, tag, common, strings)
                } }.also { reader.requireEnd() }
            }
        }
    }

    @Test
    fun actualFieldsConstructorsAndSameSignatureAccessorTargetsRestoreWithoutMergingOrDoubleInitialization() {
        val arena = Arena()
        val source = DataTemplate("Owner", "fixture")
        val id = arena.id(source, null)
        val field = MCFPPBaseType.Int.buildUnConcrete("value").apply { declaredParentTemplate = source; isFinal = true; nullable = true }
        source.scope.putVar("value", field, true)
        source.preInit["value"] = parser("7").expression()
        val ordinary = function(source, "value", 1)
        source.scope.addFunction(ordinary, false)
        val getter = function(source, "value", 2)
        val property = Property("value", FunctionAccessor(getter), SimpleMutator()).apply { declaredParentTemplate = source; isStatic = false }
        source.scope.putProperty("value", property, true)
        val operator = function(source, "plus", 3)
        source.scope.addOperator("+", MCFPPBaseType.Int, operator, true)
        source.constructors += DataTemplateConstructor(source, parser("{}").curlBlock())
        val captured = CompoundDeclarations.capture(source, id, arena)
        val records = roundtrip(captured.declarations)
        assertEquals(captured.declarations, records)
        val restored = Restore(records, arena.types.snapshot())
        val model = restored.restore(id) as DataTemplate
        val restoredProperty = model.scope.getProperty("value")!!
        val method = model.scope.getFunctionCandidates("value").single()
        val accessor = assertIs<FunctionAccessor>(restoredProperty.accessor).function
        assertNotSame(method, accessor)
        assertSame(model, accessor.owner)
        assertEquals(1, model.constructors.size)
        assertEquals(1, restored.initializers.size)
        assertTrue(model.scope.getVar("value")!!.isFinal && model.scope.getVar("value")!!.nullable)
        assertEquals(1, model.scope.operators.getValue("+").size)
        assertSame(model, CompoundDeclarations.bind(restored.shell(id), restored))
        assertEquals(1, restored.initializers.size)
        assertEquals(1, model.constructors.size)
        assertTrue(model.preInit.isEmpty(), "No expression projection provider was installed; initializer B remains registered")
        assertEquals(BodyRole.INITIALIZER, arena.bodies[(restored.initializers.single().third.value - 1).toInt()].role)
    }

    @Test
    fun enumDataAliasAndTypeAsIntrinsicKeepTheirActualCompilerModels() {
        val arena = Arena()
        val enumeration = CompilerEnum("Mode", "fixture").apply { addMember(EnumMember("Second", 9, StringTag("payload"))); addMember(EnumMember("First", 2)) }
        try {
            val id = arena.id(enumeration, null)
            val records = roundtrip(CompoundDeclarations.captureEnum(enumeration, id, arena))
            val restore = Restore(records, arena.types.snapshot())
            val model = restore.restore(id) as CompilerEnum
            assertEquals(9, model.members.getValue("Second").value)
            assertEquals("payload", model.members.getValue("Second").dataAsString())
            assertSame(enumeration.sbObject, GlobalScope.scoreboards[enumeration.namespaceID], "Shell allocation must not publish its scoreboard")

            val aliases = Arena()
            val alias = MCFPPTypeAliasType(parser("int").type())
            assertNotNull(alias.resolve(SimpleScopeWithType.getTypeScope()))
            val aliasId = aliases.id(alias, null)
            val record = CompoundDeclarations.captureAlias(alias, "Count", aliasId, EnvironmentId(1), aliases)
            val decoded = roundtrip(mapOf(aliasId to record))
            assertEquals(MCFPPBaseType.Int, Restore(decoded, aliases.types.snapshot()).restore(aliasId))

            val wrappers = Arena()
            val wrapper = TypeDataTemplate(MCFPPBaseType.Int, "CountWrapper", "fixture")
            wrapper.constructors += TypeDataTemplateConstructor(wrapper)
            val wrapperId = wrappers.id(wrapper, null)
            val captured = CompoundDeclarations.capture(wrapper, wrapperId, wrappers)
            val restored = Restore(roundtrip(captured.declarations), wrappers.types.snapshot()).restore(wrapperId) as TypeDataTemplate
            assertEquals(MCFPPBaseType.Int, restored.typeAs)
            assertIs<TypeDataTemplateConstructor>(restored.constructors.single())
            assertNull(captured.declarations.values.filterIsInstance<ConstructorDeclaration>().single().body)
            val objects = Arena()
            val state = ObjectDataTemplate("State", "fixture")
            val stateId = objects.id(state, null)
            val stateRecords = CompoundDeclarations.capture(state, stateId, objects)
            assertIs<ObjectDataTemplate>(Restore(roundtrip(stateRecords.declarations), objects.types.snapshot()).restore(stateId))
        } finally { GlobalScope.scoreboards.remove(enumeration.namespaceID) }
    }

    @Test
    fun genericAndAnonymousHeadsKeepStructuralParentsAndTheOrderedTemplateRoot() {
        val arena = Arena()
        val declaration = parser("data Box<T> : Parent<T> {} ").templateDeclaration()
        val source = GenericDataTemplate(declaration.templateBody(), "Box", "fixture")
        source.readOnlyParams += DataTemplateParam("Type", "T", MCFPPConcreteType.Type, top.mcfpp.model.compound.DeclarationVariance.OUT)
        arena.contexts[source] = declaration
        val id = arena.id(source, null)
        val captured = CompoundDeclarations.capture(source, id, arena)
        assertEquals(1, captured.declaration.readonly.size)
        assertEquals(Variance.OUT, (captured.declarations.getValue(captured.declaration.readonly.single()) as ValueDeclaration).variance)
        val parent = captured.declaration.parents.single()
        assertNull(parent.type)
        val parentTree = assertIs<TypeSyntax.Apply>(arena.nodes[(assertNotNull(parent.expression).value - 1).toInt()])
        assertEquals(NameUse.Local(captured.declaration.readonly.single()), assertIs<Expression.Name>(parentTree.readonly.single()).use)
        assertTrue(arena.templates.getValue(id).members.isEmpty())
        val restored = Restore(roundtrip(captured.declarations), arena.types.snapshot(), templateRoots = arena.templates)
        assertIs<GenericDataTemplate>(restored.restore(id))
        assertEquals(listOf(parent), restored.pendingParents.getValue(id))
        val anonymousArena = Arena()
        val anonymous = DataTemplate("anonymous_display", "fixture")
        anonymousArena.origins[anonymous] = AnonymousOrigin("unit.mcfpp", 0, listOf(1))
        val anonymousId = anonymousArena.id(anonymous, null)
        val anonymousCapture = CompoundDeclarations.capture(anonymous, anonymousId, anonymousArena)
        val record = anonymousCapture.declaration
        assertEquals(CompoundCategory.ANONYMOUS, record.category)
        assertEquals(AnonymousOrigin("unit.mcfpp", 0, listOf(1)), record.common.origin)
        assertNotSame(anonymous, Restore(roundtrip(anonymousCapture.declarations), anonymousArena.types.snapshot()).restore(anonymousId))
    }

    @Test
    fun intersectionAndCompletedInheritedEffectRecordsDoNotBecomeOrdinaryLocalFields() {
        val left = DataTemplate("Left", "fixture")
        val right = DataTemplate("Right", "fixture")
        val intersection = UnionDataTemplate(listOf(left, right))
        val arena = Arena()
        arena.external[left.getType().typeId] = left.getType(); arena.external[right.getType().typeId] = right.getType()
        val id = arena.id(intersection, null)
        val captured = CompoundDeclarations.capture(intersection, id, arena)
        assertEquals(CompoundCategory.INTERSECTION, captured.declaration.category)
        assertEquals(2, captured.declaration.parents.size)
        val freshLeft = DataTemplate("Left", "fixture")
        val freshRight = DataTemplate("Right", "fixture")
        val restored = Restore(roundtrip(captured.declarations), arena.types.snapshot(),
            mapOf(left.getType().typeName to freshLeft, right.getType().typeName to freshRight)).restore(id)
        val intersectionModel = assertIs<UnionDataTemplate>(restored)
        assertEquals<List<CompoundData>>(listOf(freshLeft, freshRight), intersectionModel.parent)
        assertTrue(freshLeft.scope in intersectionModel.scope.parent && freshRight.scope in intersectionModel.scope.parent)

        val parent = DataTemplate("Parent", "fixture")
        val field = MCFPPBaseType.Int.buildUnConcrete("field").apply { declaredParentTemplate = parent; bindDeclaration() }
        parent.scope.putVar("field", field, true)
        val child = DataTemplate("Child", "fixture").apply { extends(parent) }
        child.flatExtends()
        MCFPPFieldVisitor().completeTemplateFields(child)
        (child.scope.getVar("field") as MCNumber<*>).isDataOnly = true
        val effects = Arena()
        effects.external[parent.getType().typeId] = parent.getType()
        val childId = effects.id(child, null)
        val result = CompoundDeclarations.capture(child, childId, effects)
        val effect = result.declaration.inherited.single()
        assertTrue(effect.field !in result.declaration.members)
        assertNull((result.declarations.getValue(effect.field) as ValueDeclaration).defaultBody)
        assertEquals(result.declaration, roundtrip(result.declarations).getValue(childId))
        val freshParent = DataTemplate("Parent", "fixture")
        val freshField = MCFPPBaseType.Int.buildUnConcrete("field").apply { declaredParentTemplate = freshParent; bindDeclaration() }
        freshParent.scope.putVar("field", freshField, true)
        val imported = Restore(roundtrip(result.declarations), effects.types.snapshot(),
            mapOf(parent.getType().typeName to freshParent), externalDeclarations = mapOf((effect.slot as DeclarationReference.Dependency).key to freshField))
        val freshChild = imported.restore(childId) as DataTemplate
        freshChild.flatExtends()
        assertTrue((freshChild.scope.getVar("field") as MCNumber<*>).isDataOnly)
        assertSame(freshParent, freshChild.scope.getVar("field")!!.declaredParentTemplate)
        assertTrue(imported.initializers.isEmpty())
    }
}
