package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

/** Extension payloads have an explicit tag registry; no JVM class or reflective fallback is encoded. */
interface DeclarationPayloadHandler {
    val kinds: Set<Int>
    fun collectStrings(declaration: LibraryDeclaration): Set<String>
    fun writePayload(writer: LibraryBinaryWriter, declaration: LibraryDeclaration, strings: LibraryStrings)
    fun readPayload(reader: LibraryBinaryReader, tag: Int, common: DeclCommon, strings: LibraryStrings): LibraryDeclaration
    fun validate(declaration: LibraryDeclaration, id: DeclarationId, context: LibraryModelValidation)
}

/** Local protocol domains only. A dependency reference is range-checked, never falsely language-bound. */
class LibraryModelValidation(val model: LibraryModel) {
    private val pendingNominals by lazy { PendingNominalUses(model.tables) }
    fun declaration(reference: DeclarationId): LibraryDeclaration = model.declarations.declaration(reference)
    fun declarationReference(reference: DeclarationReference): LibraryDeclaration? = when (reference) {
        is DeclarationReference.Local -> declaration(reference.target)
        is DeclarationReference.Dependency -> {
            require(reference.dependencyIndex <= model.manifest.dependencies.size.toLong()) { "Dependency index is out of bounds" }
            null
        }
    }
    fun environment(reference: EnvironmentId): EnvironmentRecord = model.declarations.environment(reference)
    fun type(reference: TypeRef): LibraryType {
        require(!pendingNominals.containsType(reference)) { "Unresolved nominal identity is only valid in a pending parent, not an executable type" }
        return model.tables.type(reference)
    }
    fun constant(reference: ConstantRef): ConstantValue {
        require(!pendingNominals.containsConstant(reference)) { "Unresolved nominal identity is only valid in pending parent constants" }
        return model.tables.constant(reference)
    }
    fun parentType(reference: TypeRef, expression: NodeRef?): LibraryType {
        if (pendingNominals.containsType(reference)) {
            require(expression == null) { "Unresolved nominal pending parent cannot carry an executable type expression" }
            pendingNominals.allowParent(reference)
        }
        return model.tables.type(reference)
    }
    internal fun validatePendingNominals() = pendingNominals.validate()
    fun node(reference: NodeRef): SemanticNode = model.syntax.node(reference)
    fun body(reference: BodyId): BodyRecord = model.syntax.body(reference)
    fun annotation(reference: AnnotationId): AnnotationRecord {
        require(reference.value <= model.annotations.size.toLong()) { "A reference is out of bounds" }
        return model.annotations[(reference.value - 1).toInt()]
    }
    fun cell(reference: StateCellRef): StateCell {
        require(reference.value <= model.stateCells.size.toLong()) { "H reference is out of bounds" }
        return model.stateCells[(reference.value - 1).toInt()]
    }
    fun runtime(reference: RuntimeRef): RuntimeRecord {
        require(reference.value <= model.runtime.size.toLong()) { "R reference is out of bounds" }
        return model.runtime[(reference.value - 1).toInt()]
    }
}

/** T11 can be carried through closed T/C identity data, but only from explicit pending parent roots. */
private class PendingNominalUses(private val tables: LibraryTables) {
    private val typeMemo = hashMapOf<TypeRef, Boolean>()
    private val constantMemo = hashMapOf<ConstantRef, Boolean>()
    private val allowedTypes = hashSetOf<TypeRef>()
    private val allowedConstants = hashSetOf<ConstantRef>()
    private fun typeChildren(value: LibraryType): List<TypeRef> = when (value) {
        is LibraryType.Applied -> listOf(value.constructor) + value.arguments
        is LibraryType.Specialized -> listOf(value.constructor)
        is LibraryType.Union -> value.alternatives
        is LibraryType.Intersection -> value.components
        is LibraryType.Function -> value.parameters + value.result
        is LibraryType.NbtRef -> listOf(value.value)
        else -> emptyList()
    }
    private fun typeConstants(value: LibraryType): List<ConstantRef> = (value as? LibraryType.Specialized)?.arguments.orEmpty()
    private fun constantTypes(value: ConstantValue): List<TypeRef> = when (value) {
        is ConstantValue.TypeValue -> listOf(value.value)
        is ConstantValue.Typed -> listOf(value.type)
        is ConstantValue.FunctionValue -> listOf(value.type)
        else -> emptyList()
    }
    private fun constantChildren(value: ConstantValue): List<ConstantRef> = when (value) {
        is ConstantValue.Typed -> listOf(value.value)
        is ConstantValue.Sequence -> value.elements
        is ConstantValue.Record -> value.fields.map { it.value }
        is ConstantValue.FunctionValue -> value.captures.map { it.value }
        else -> emptyList()
    }
    // TypeTableCodec validates all bounds and structural T/C cycles before these usage queries.
    fun containsType(reference: TypeRef): Boolean = typeMemo.getOrPut(reference) {
        val value = tables.type(reference)
        value is LibraryType.UnresolvedNominal || typeChildren(value).any(::containsType) || typeConstants(value).any(::containsConstant)
    }
    fun containsConstant(reference: ConstantRef): Boolean = constantMemo.getOrPut(reference) {
        val value = tables.constant(reference)
        constantTypes(value).any(::containsType) || constantChildren(value).any(::containsConstant)
    }
    fun allowParent(reference: TypeRef) {
        if (!allowedTypes.add(reference)) return
        val value = tables.type(reference)
        typeChildren(value).forEach(::allowParent)
        typeConstants(value).forEach(::allowConstant)
    }
    private fun allowConstant(reference: ConstantRef) {
        if (!allowedConstants.add(reference)) return
        val value = tables.constant(reference)
        constantTypes(value).forEach(::allowParent)
        constantChildren(value).forEach(::allowConstant)
    }
    fun validate() {
        tables.types.indices.forEach { index ->
            val reference = TypeRef(index + 1L)
            require(!containsType(reference) || reference in allowedTypes) { "Unresolved nominal type must be reachable from an explicit pending parent" }
        }
        tables.constants.indices.forEach { index ->
            val reference = ConstantRef(index + 1L)
            require(!containsConstant(reference) || reference in allowedConstants) { "Unresolved nominal constant must be reachable from an explicit pending parent" }
        }
    }
}

class LibraryModelCodec(handlers: List<DeclarationPayloadHandler> = emptyList()) {
    private val handlers: Map<Int, DeclarationPayloadHandler> = buildMap {
        handlers.forEach { handler -> handler.kinds.forEach { kind ->
            require(kind in 3..13 && kind != 5 && put(kind, handler) == null) { "Duplicate/reserved D payload handler $kind" }
        } }
    }

    fun encode(model: LibraryModel, compatibility: LibraryCompatibility = LibraryCompatibility()): ByteArray {
        validate(model, compatibility)
        val strings = LibraryStrings.of(collectStrings(model))
        val syntax = SyntaxCodec.encode(model.syntax, strings, syntaxValidator(LibraryModelValidation(model)))
        val sections = listOf(
            LibrarySection(1, true, ManifestCodec.encode(model.manifest)),
            LibrarySection(2, true, TypeTableCodec.encodeStrings(strings)),
            LibrarySection(3, true, FunctionCodec.encodeEnvironments(model.declarations.environments, strings)),
            LibrarySection(4, true, TypeTableCodec.encodeTypes(model.tables.types, strings)),
            LibrarySection(5, true, TypeTableCodec.encodeConstants(model.tables.constants, strings)),
            LibrarySection(6, true, encodeDeclarations(model.declarations.declarations, strings)),
            LibrarySection(7, true, syntax.scopes), LibrarySection(8, true, syntax.nodes), LibrarySection(9, true, syntax.bodies),
            LibrarySection(10, true, encodeAnnotations(model, strings)),
            LibrarySection(11, true, encodeRuntime(model.runtime, strings)),
            LibrarySection(12, true, encodeResources(model.resources, strings)),
        ) + listOfNotNull(model.debug?.let { LibrarySection(LibrarySectionId.DEBUG, false, encodeDebug(it, strings)) })
        return LibraryWire.encode(sections)
    }

    /** Decode to an isolated protocol document. Dependency symbol resolution still belongs to the import session. */
    fun decode(bytes: ByteArray, compatibility: LibraryCompatibility = LibraryCompatibility()): LibraryModel {
        val container = LibraryWire.decode(bytes)
        fun section(id: Int) = requireNotNull(container.section(id)).payload
        val manifest = ManifestCodec.decode(section(1))
        ManifestCodec.validate(manifest, compatibility)
        val strings = TypeTableCodec.decodeStrings(section(2))
        val declarations = LibraryDeclarations(decodeDeclarations(section(6), strings), FunctionCodec.decodeEnvironments(section(3), strings))
        val tables = TypeTableCodec.decode(section(4), section(5), strings)
        val syntax = SyntaxCodec.decodeUnbound(EncodedSyntax(section(8), section(7), section(9)), strings)
        val (annotations, cells) = decodeAnnotations(section(10), strings)
        val model = LibraryModel(manifest, declarations, tables, syntax, annotations, cells,
            decodeRuntime(section(11), strings), decodeResources(section(12), strings),
            container.section(LibrarySectionId.DEBUG)?.let { decodeDebug(it.payload, strings) })
        validate(model, compatibility)
        return model
    }

    fun collectStrings(model: LibraryModel): Set<String> = buildSet {
        addAll(FunctionCodec.collectStrings(model.declarations)); addAll(TypeTableCodec.collectStrings(model.tables))
        addAll(SyntaxCodec.collectStrings(model.syntax))
        model.declarations.declarations.filter { it.tag !in setOf(1, 2, 5) }.forEach { addAll(handler(it.tag).collectStrings(it)) }
        model.annotations.forEach { it.state?.let { state -> add(state.schema); state.fields.forEach { field -> add(field.name) } } }
        model.runtime.forEach { when (it) {
            is RuntimeRecord.FunctionBinding -> add(it.resourcePath)
            is RuntimeRecord.TagContribution -> { add(it.tag.namespace); add(it.tag.name) }
            is RuntimeRecord.Scoreboard -> { add(it.name); add(it.criterion) }
            is RuntimeRecord.Initialization -> Unit
        } }
        model.resources.forEach { add(it.path); add(it.module); it.activation?.let(::add) }
        model.debug?.documents?.forEach { add(it.text) }
    }

    fun encodeDeclarations(declarations: List<LibraryDeclaration>, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(declarations) { value -> record {
            FunctionCodec.writeCommon(this, value.common, strings)
            tagged(value.tag) { when (value) {
                is NamespaceDeclaration -> list(value.members) { u32(it.value) }
                is FunctionDeclaration -> FunctionCodec.writeFunctionPayload(this, value, strings)
                is ValueDeclaration -> FunctionCodec.writeValuePayload(this, value)
                else -> handler(value.tag).writePayload(this, value, strings)
            } }
        } }
    }.toByteArray()

    fun decodeDeclarations(bytes: ByteArray, strings: LibraryStrings): List<LibraryDeclaration> = table(bytes, 6) {
        val common = FunctionCodec.readCommon(this, strings)
        tagged { tag -> when (tag) {
            1 -> NamespaceDeclaration(common, list { DeclarationId(u32()) })
            2 -> FunctionCodec.readFunctionPayload(this, common, strings)
            5 -> FunctionCodec.readValuePayload(this, common)
            else -> handler(tag).readPayload(this, tag, common, strings).also {
                require(it.tag == tag && it.common == common) { "Handler changed D tag/common" }
            }
        } }
    }

    private fun handler(kind: Int): DeclarationPayloadHandler = handlers[kind] ?: throw LibraryFormatException(
        LibraryFormatCode.UNSUPPORTED, "No explicit payload handler for required declaration kind $kind", 0, LibrarySectionId.DECLARATIONS)

    fun validate(model: LibraryModel, compatibility: LibraryCompatibility = LibraryCompatibility()) {
        model.localReferencesValidated = false
        val context = LibraryModelValidation(model)
        check(1) {
            ManifestCodec.validate(model.manifest, compatibility)
            model.manifest.entryEnvironments.forEach(context::environment)
            model.manifest.rootDeclarations.forEach(context::declaration)
        }
        check(4) { TypeTableCodec.validate(model.tables) }
        check(6) {
            FunctionCodec.validate(model.declarations, object : FunctionReferenceValidator {
                override fun type(reference: TypeRef) { context.type(reference) }
                override fun node(reference: NodeRef) { context.node(reference) }
                override fun body(reference: BodyId) { context.body(reference) }
                override fun constant(reference: ConstantRef) { context.constant(reference) }
                override fun annotation(reference: AnnotationId) { context.annotation(reference) }
                override fun runtime(reference: RuntimeRef) { context.runtime(reference) }
            })
            val owners = IntArray(model.declarations.declarations.size)
            fun owner(id: DeclarationId) {
                val index = (id.value - 1).toInt()
                context.declaration(id)
                require(owners[index] != 1) { "Cycle in D owner graph" }
                if (owners[index] == 2) return
                owners[index] = 1; context.declaration(id).common.owner?.let(::owner); owners[index] = 2
            }
            model.declarations.declarations.forEachIndexed { index, declaration ->
                val id = DeclarationId(index + 1L)
                owner(id)
                require(declaration.common.key == null || declaration.common.key?.kind == declaration.tag) { "D key kind differs from payload" }
                when (declaration) {
                    is NamespaceDeclaration -> {
                        require(declaration.common.flags == 0L) { "Namespace flags must be zero" }
                        require(declaration.members.distinct().size == declaration.members.size)
                        declaration.members.forEach { member ->
                            val target = context.declaration(member)
                            require(target.common.owner == null || target.common.owner == id) { "Namespace member has another owner" }
                        }
                    }
                    is FunctionDeclaration -> {
                        declaration.body?.let { reference ->
                            val body = context.body(reference)
                            require(body.owner == id && body.environment == declaration.common.environment) { "Function B owner/environment differs" }
                            require(body.role == BodyRole.FUNCTION || (body.role == BodyRole.LAMBDA && declaration.common.key == null)) { "Function B role differs" }
                        }
                        declaration.linkedRuntime.forEach { reference ->
                            val binding = context.runtime(reference) as? RuntimeRecord.FunctionBinding
                            require(binding?.declaration == DeclarationReference.Local(id)) { "Linked runtime R owner differs" }
                        }
                    }
                    is ValueDeclaration -> {
                        declaration.declaredType?.let { require(context.node(it) is TypeSyntax) { "Declared type N is not type syntax" } }
                        declaration.defaultBody?.let { reference ->
                            val body = context.body(reference)
                            require(body.owner == id && body.environment == declaration.common.environment) { "Default/initializer B owner differs" }
                            require(body.role == BodyRole.DEFAULT || body.role == BodyRole.INITIALIZER)
                        }
                    }
                    else -> handler(declaration.tag).validate(declaration, id, context)
                }
            }
        }
        check(3) {
            val packages = model.manifest.dependencies.map { it.library }.toSet() + model.manifest.library
            model.declarations.environments.forEach { env -> env.imports.forEach { item ->
                require(item.resolvedPackages.all { it in packages }) { "Import refers to a package outside manifest dependencies" }
            } }
        }
        check(4) { model.tables.types.forEach { when (it) {
            is LibraryType.Declaration -> require(context.declarationReference(it.target)?.tag.let { tag -> tag == null || tag in setOf(3, 8, 10) }) {
                "Nominal T does not reference a type declaration"
            }
            is LibraryType.Parameter -> {
                val owner = context.declarationReference(it.owner)
                if (it.role == ParameterRole.NATIVE_TYPE) {
                    require(if (owner == null) (it.owner as DeclarationReference.Dependency).key.kind == 2
                        else owner is FunctionDeclaration && owner.execution == FunctionExecutionKind.NATIVE && owner.native != null) {
                        "Native type parameter requires a native function owner"
                    }
                } else {
                    require(owner == null || owner.tag in setOf(2, 3, 4, 11)) { "T parameter owner is not parameterized" }
                    if (owner is FunctionDeclaration) require(it.index < owner.readonly.size.toLong()) { "T parameter index is outside its owner" }
                }
            }
            is LibraryType.Intersection -> it.declaration?.let(context::declaration)
            is LibraryType.Dependent -> {
                require(context.node(it.syntax) is TypeSyntax) { "Dependent N must be type syntax" }
                context.environment(it.environment); context.declaration(it.owner)
            }
            else -> Unit
        } } }
        check(5) { model.tables.constants.filterIsInstance<ConstantValue.FunctionValue>().forEach {
            require(context.declarationReference(it.target)?.tag.let { tag -> tag == null || tag == 2 })
            it.captures.forEach { capture -> context.declarationReference(capture.declaration) }
        } }
        check(8) {
            SyntaxCodec.validateAll(model.syntax, syntaxValidator(context))
            model.syntax.nodes.filterIsInstance<TemplateMember.AnnotationConstruction>().forEach { construction ->
                val provider = context.declarationReference(construction.provider)
                val kind = provider?.tag ?: (construction.provider as DeclarationReference.Dependency).key.kind
                require(kind == 13) {
                    "Annotation construction provider is not D13"
                }
            }
            model.syntax.nodes.filterIsInstance<Expression.Member>().forEach { member ->
                member.candidates?.forEach { reference ->
                    val declaration = context.declarationReference(reference)
                    val kind = declaration?.tag ?: (reference as DeclarationReference.Dependency).key.kind
                    require(kind == 2) { "Frozen member candidate is not a function declaration" }
                }
            }
        }
        check(7) { model.syntax.scopes.forEach { scope ->
            scope.bindings.forEach { binding ->
                val declaration = context.declaration(binding)
                require(declaration is ValueDeclaration && declaration.common.owner == scope.owner) { "Q binding owner differs" }
            }
        } }
        check(9) { model.syntax.bodies.forEachIndexed { index, body ->
            val reference = BodyId(index + 1L)
            when (body.role) {
                BodyRole.FUNCTION, BodyRole.LAMBDA -> require((context.declaration(body.owner) as? FunctionDeclaration)?.body == reference) { "B is not owned by its function D" }
                BodyRole.DEFAULT, BodyRole.INITIALIZER -> require((context.declaration(body.owner) as? ValueDeclaration)?.defaultBody == reference) { "B is not owned by its value D" }
                BodyRole.CONSTRUCTOR -> require(context.declaration(body.owner).tag == 4) { "Constructor B requires D4" }
                BodyRole.TOP_LEVEL -> require(context.declaration(body.owner).tag in setOf(1, 2)) { "Top-level B requires namespace/function D" }
            }
        } }
        check(10) { validateAnnotations(model, context) }
        check(11) { model.runtime.forEach { when (it) {
            is RuntimeRecord.FunctionBinding -> {
                require(context.declarationReference(it.declaration)?.tag.let { tag -> tag == null || tag == 2 })
                context.constant(it.generationOptions); path(it.resourcePath)
                require(it.target.edition == 1 && it.target.minecraftVersion.isNotEmpty())
            }
            is RuntimeRecord.TagContribution -> it.members.forEach { ref -> require(context.declarationReference(ref)?.tag.let { tag -> tag == null || tag == 2 }) }
            is RuntimeRecord.Scoreboard -> it.display?.let(context::constant)
            is RuntimeRecord.Initialization -> { context.declarationReference(it.declaration); require(it.order in 0..0xffff_ffffL) }
        } } }
        check(12) {
            if (model.resources.map { it.path }.distinct().size != model.resources.size)
                throw LibraryFormatException("LIB_RESOURCE_CONFLICT", "Duplicate resource path", 0, 12)
            model.resources.forEach {
                path(it.path); require(it.module.isNotEmpty() && '/' !in it.module && '\\' !in it.module)
                require(it.path.startsWith("datapack/${it.module}/data/")) { "Resource does not use its canonical module root" }
                require(it.sha256.size == 32) { "Resource SHA-256 must be 32 bytes" }
                it.owner?.let(context::declarationReference)
            }
            model.runtime.filterIsInstance<RuntimeRecord.FunctionBinding>().forEach { binding ->
                require(model.resources.any { it.path == binding.resourcePath }) { "Runtime function resource is absent from the resource table" }
            }
        }
        model.debug?.let { debug -> check(LibrarySectionId.DEBUG) {
            debug.files.forEach(::path)
            debug.locations.forEach { location ->
                context.node(location.node); require(location.fileIndex in 0 until debug.files.size.toLong())
                require(location.start in 0..0xffff_ffffL && location.end in location.start..0xffff_ffffL)
                require(location.line in 1..0xffff_ffffL && location.column in 1..0xffff_ffffL)
            }
            debug.documents.forEach { context.declaration(it.declaration) }
        } }
        check(LibrarySectionId.TYPES) { context.validatePendingNominals() }
        model.localReferencesValidated = true
    }

    private fun syntaxValidator(context: LibraryModelValidation) = object : SyntaxReferenceValidator {
        override fun declaration(reference: DeclarationReference) { context.declarationReference(reference) }
        override fun environment(reference: EnvironmentId) { context.environment(reference) }
        override fun type(reference: TypeRef) { context.type(reference) }
        override fun constant(reference: ConstantRef) { context.constant(reference) }
        override fun annotation(reference: AnnotationId) { context.annotation(reference) }
    }

    private fun validateAnnotations(model: LibraryModel, context: LibraryModelValidation) {
        fun value(value: PortableValue) { when (value) {
            is PortableValue.Constant -> context.constant(value.value)
            is PortableValue.Declaration -> context.declarationReference(value.value)
            is PortableValue.Type -> context.type(value.value)
            is PortableValue.Annotation -> context.annotation(value.value)
            is PortableValue.Cell -> context.cell(value.value)
        } }
        model.annotations.forEach { record ->
            require(context.declarationReference(record.provider)?.tag.let { it == null || it == 13 }) { "A provider is not D13" }
            record.arguments.forEach(context::constant); record.parents.forEach(context::annotation)
            record.state?.let { portable ->
                require(portable.schema.isNotEmpty() && portable.version in 0..0xffff_ffffL)
                require(portable.fields.zipWithNext().all { (a, b) -> compareUtf8(a.name, b.name) < 0 }) { "Portable fields must be sorted and unique" }
                portable.fields.forEach { value(it.value) }
            }
        }
        // Every record is validated above, including completed cycles retained only as state.
        val state = IntArray(model.annotations.size)
        fun pendingParents(reference: AnnotationId) {
            val record = context.annotation(reference); val index = (reference.value - 1).toInt()
            require(state[index] != 1) { "Cycle reachable from pending annotation parents" }
            if (state[index] == 2) return
            state[index] = 1
            record.parents.forEach(::pendingParents)
            state[index] = 2
        }
        model.annotations.forEachIndexed { index, record ->
            if (record.phase == AnnotationPhase.PENDING) pendingParents(AnnotationId(index + 1L))
        }
        fun scalar(reference: ConstantRef): ByteArray = LibraryBinaryWriter().apply { when (val constant = context.constant(reference)) {
            ConstantValue.Null -> u8(1)
            is ConstantValue.Integral -> { u8(2); i64(constant.value) }
            is ConstantValue.FloatBits -> { u8(3); u32(constant.bits.toLong() and 0xffff_ffffL) }
            is ConstantValue.DoubleBits -> { u8(4); u64(constant.bits.toULong()) }
            is ConstantValue.Bool -> { u8(5); bool(constant.value) }
            is ConstantValue.Text -> { u8(6); string(constant.value) }
            else -> throw IllegalArgumentException("H map/set keys require closed primitive/text/null constants")
        } }.toByteArray()
        model.stateCells.forEach { cell -> when (cell) {
            is StateCell.Sequence -> cell.values.forEach(::value)
            is StateCell.Map -> {
                val keys = cell.entries.map { scalar(it.key) }
                require(keys.zipWithNext().all { (a, b) -> compareBytes(a, b) < 0 }) { "H map keys must be sorted and unique" }
                cell.entries.forEach { value(it.value) }
            }
            is StateCell.Set -> {
                val keys = cell.values.map(::scalar)
                require(keys.zipWithNext().all { (a, b) -> compareBytes(a, b) < 0 }) { "H set items must be sorted and unique" }
            }
        } }
    }

    private fun path(value: String) {
        require(value.isNotEmpty() && '\\' !in value && ':' !in value && value.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "Invalid relative library path: $value"
        }
    }
    private fun check(section: Int, operation: () -> Unit) {
        try { operation() } catch (failure: IllegalArgumentException) {
            throw LibraryFormatException(LibraryFormatCode.CORRUPT, failure.message ?: "Invalid library model", 0, section, cause = failure)
        }
    }
    private fun <T> table(bytes: ByteArray, section: Int, read: LibraryBinaryReader.() -> T): List<T> {
        val reader = LibraryBinaryReader(bytes, sectionId = section)
        var index = 0L
        return reader.list { record(++index) { checked(this, read) } }.also { reader.requireEnd() }
    }
    private fun <T> checked(reader: LibraryBinaryReader, action: LibraryBinaryReader.() -> T): T = try { action(reader) }
    catch (failure: IllegalArgumentException) { reader.fail(failure.message ?: "Invalid protocol record") }

    private fun writePortable(writer: LibraryBinaryWriter, value: PortableValue) = with(writer) { when (value) {
        is PortableValue.Constant -> tagged(1) { u32(value.value.value) }
        is PortableValue.Declaration -> tagged(2) { TypeTableCodec.writeDeclarationReference(this, value.value) }
        is PortableValue.Type -> tagged(3) { u32(value.value.value) }
        is PortableValue.Annotation -> tagged(4) { u32(value.value.value) }
        is PortableValue.Cell -> tagged(5) { u32(value.value.value) }
    } }
    private fun readPortable(reader: LibraryBinaryReader): PortableValue = reader.tagged { tag -> when (tag) {
        1 -> PortableValue.Constant(ConstantRef(u32()))
        2 -> PortableValue.Declaration(TypeTableCodec.readDeclarationReference(this))
        3 -> PortableValue.Type(TypeRef(u32()))
        4 -> PortableValue.Annotation(AnnotationId(u32()))
        5 -> PortableValue.Cell(StateCellRef(u32()))
        else -> fail("Unknown portable state tag $tag", LibraryFormatCode.UNSUPPORTED)
    } }

    private fun encodeAnnotations(model: LibraryModel, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(model.annotations) { annotation -> record {
            TypeTableCodec.writeDeclarationReference(this, annotation.provider)
            list(annotation.arguments) { u32(it.value) }; list(annotation.parents) { u32(it.value) }; u8(annotation.phase.wireValue)
            optional(annotation.state) { state ->
                u32(strings.reference(state.schema)); u32(state.version)
                list(state.fields) { u32(strings.reference(it.name)); writePortable(this, it.value) }
            }
        } }
        list(model.stateCells) { cell -> record { when (cell) {
            is StateCell.Sequence -> tagged(1) { list(cell.values) { writePortable(this, it) } }
            is StateCell.Map -> tagged(2) { list(cell.entries) { u32(it.key.value); writePortable(this, it.value) } }
            is StateCell.Set -> tagged(3) { list(cell.values) { u32(it.value) } }
        } } }
    }.toByteArray()

    private fun decodeAnnotations(bytes: ByteArray, strings: LibraryStrings): Pair<List<AnnotationRecord>, List<StateCell>> {
        val reader = LibraryBinaryReader(bytes, sectionId = 10)
        val annotations = reader.list { record { checked(this) {
            val provider = TypeTableCodec.readDeclarationReference(this); val arguments = list { ConstantRef(u32()) }
            val parents = list { AnnotationId(u32()) }; val phase = enum(u8(), AnnotationPhase.entries, { it.wireValue })
            AnnotationRecord(provider, arguments, parents, phase, optional {
                PortableState(strings.value(u32()), u32(), list { StateField(strings.value(u32()), readPortable(this)) })
            })
        } } }
        val cells = reader.list { record { checked(this) { tagged { tag -> when (tag) {
            1 -> StateCell.Sequence(list { readPortable(this) })
            2 -> StateCell.Map(list { StateMapEntry(ConstantRef(u32()), readPortable(this)) })
            3 -> StateCell.Set(list { ConstantRef(u32()) })
            else -> fail("Unknown H tag $tag", LibraryFormatCode.UNSUPPORTED)
        } } } } }
        reader.requireEnd()
        return annotations to cells
    }

    private fun encodeRuntime(records: List<RuntimeRecord>, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(records) { value -> record { when (value) {
            is RuntimeRecord.FunctionBinding -> tagged(1) {
                TypeTableCodec.writeDeclarationReference(this, value.declaration); u32(strings.reference(value.resourcePath))
                ManifestCodec.writeTarget(this, value.target); u32(value.generationOptions.value); u8(value.policy.wireValue)
            }
            is RuntimeRecord.TagContribution -> tagged(2) {
                u32(strings.reference(value.tag.namespace)); u32(strings.reference(value.tag.name))
                list(value.members) { TypeTableCodec.writeDeclarationReference(this, it) }; bool(value.replace)
            }
            is RuntimeRecord.Scoreboard -> tagged(3) {
                u32(strings.reference(value.name)); u32(strings.reference(value.criterion)); optional(value.display) { u32(it.value) }
            }
            is RuntimeRecord.Initialization -> tagged(4) { TypeTableCodec.writeDeclarationReference(this, value.declaration); u32(value.order) }
        } } }
    }.toByteArray()

    private fun decodeRuntime(bytes: ByteArray, strings: LibraryStrings): List<RuntimeRecord> = table(bytes, 11) { tagged { tag -> when (tag) {
        1 -> RuntimeRecord.FunctionBinding(TypeTableCodec.readDeclarationReference(this), strings.value(u32()),
            ManifestCodec.readTarget(this), ConstantRef(u32()), enum(u8(), RuntimePolicy.entries, { it.wireValue }))
        2 -> RuntimeRecord.TagContribution(TagKey(strings.value(u32()), strings.value(u32())),
            list { TypeTableCodec.readDeclarationReference(this) }, bool())
        3 -> RuntimeRecord.Scoreboard(strings.value(u32()), strings.value(u32()), optional { ConstantRef(u32()) })
        4 -> RuntimeRecord.Initialization(TypeTableCodec.readDeclarationReference(this), u32())
        else -> fail("Unknown R tag $tag", LibraryFormatCode.UNSUPPORTED)
    } } }

    private fun encodeResources(records: List<LibraryResource>, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply {
        list(records) { value -> record {
            u32(strings.reference(value.path)); u64(value.byteLength); bytes(value.sha256.toByteArray())
            optional(value.owner) { TypeTableCodec.writeDeclarationReference(this, it) }
            u32(strings.reference(value.module)); optional(value.activation) { u32(strings.reference(it)) }; u8(value.origin.wireValue)
        } }
    }.toByteArray()
    private fun decodeResources(bytes: ByteArray, strings: LibraryStrings): List<LibraryResource> = table(bytes, 12) {
        LibraryResource(strings.value(u32()), u64(), bytes().toList(), optional { TypeTableCodec.readDeclarationReference(this) },
            strings.value(u32()), optional { strings.value(u32()) }, enum(u8(), ResourceOrigin.entries, { it.wireValue }))
    }

    private fun encodeDebug(debug: LibraryDebug, strings: LibraryStrings): ByteArray = LibraryBinaryWriter().apply { record {
        list(debug.files) { string(it) }
        list(debug.locations) { u32(it.node.value); u32(it.fileIndex); u32(it.start); u32(it.end); u32(it.line); u32(it.column) }
        list(debug.documents) { u32(it.declaration.value); u32(strings.reference(it.text)) }; optional(debug.producer) { string(it) }
    } }.toByteArray()
    private fun decodeDebug(bytes: ByteArray, strings: LibraryStrings): LibraryDebug {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.DEBUG)
        return reader.record { checked(this) {
            LibraryDebug(list { string() }, list { NodeLocation(NodeRef(u32()), u32(), u32(), u32(), u32(), u32()) },
                list { DeclarationDocument(DeclarationId(u32()), strings.value(u32())) }, optional { string() })
        } }.also { reader.requireEnd() }
    }
    private fun <T> LibraryBinaryReader.enum(value: Int, entries: List<T>, wire: (T) -> Int): T =
        entries.firstOrNull { wire(it) == value } ?: fail("Unknown enum value $value", LibraryFormatCode.UNSUPPORTED)
}
