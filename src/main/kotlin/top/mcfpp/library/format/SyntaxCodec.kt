package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import java.util.IdentityHashMap

data class EncodedSyntax(val nodes: ByteArray, val scopes: ByteArray, val bodies: ByteArray)

/** v1 N/Q/B codec. No parser, source buffer, compiler model, or runtime value is serialized. */
object SyntaxCodec {
    fun flatten(
        scopes: List<ScopeRecord>,
        bodies: List<SyntaxBodySource>,
        roots: List<SyntaxRoot> = emptyList(),
    ): LibrarySyntax {
        val nodes = ArrayList<SemanticNode>()
        val seen = IdentityHashMap<SemanticNode, NodeRef>()
        val active = IdentityHashMap<SemanticNode, Boolean>()
        fun visit(node: SemanticNode) {
            require(!active.containsKey(node)) { "Cycle in semantic node graph" }
            if (seen.containsKey(node)) return
            seen[node] = NodeRef(nodes.size + 1L)
            nodes.add(node)
            active[node] = true
            children(node).forEach(::visit)
            active.remove(node)
        }
        data class Root(val owner: Long, val role: Int, val path: List<Long>, val node: SemanticNode)
        val ordered = bodies.map { Root(it.owner.value, it.role.wireValue, emptyList(), it.root) } +
            roots.map { Root(it.owner.value, 0, it.path, it.node) }
        require(ordered.map { Triple(it.owner, it.role, it.path) }.distinct().size == ordered.size) {
            "Independent roots need distinct owner/role/structural paths"
        }
        ordered.sortedWith { a, b ->
            val owner = a.owner.compareTo(b.owner)
            val role = a.role.compareTo(b.role)
            when { owner != 0 -> owner; role != 0 -> role; else -> comparePaths(a.path, b.path) }
        }.forEach { visit(it.node) }
        return LibrarySyntax(nodes, scopes, bodies.map {
            BodyRecord(it.owner, it.environment, it.role, seen.getValue(it.root), it.captures)
        }).also(::validateDomain)
    }

    private fun comparePaths(a: List<Long>, b: List<Long>): Int {
        for (index in 0 until minOf(a.size, b.size)) {
            val comparison = a[index].compareTo(b[index])
            if (comparison != 0) return comparison
        }
        return a.size.compareTo(b.size)
    }

    fun collectStrings(syntax: LibrarySyntax): Set<String> = buildSet {
        fun name(use: NameUse) { if (use is NameUse.Lookup) { add(use.name); use.namespace?.let(::add) } }
        fun text(parts: List<TextPart>) { parts.filterIsInstance<TextPart.Text>().forEach { add(it.value) } }
        syntax.nodes.forEach { node -> when (node) {
            is Expression.Name -> name(node.use)
            is Expression.Unary -> add(node.operator)
            is Expression.Binary -> add(node.operator)
            is Expression.Member -> add(node.name)
            is Expression.PropertyFilter -> node.entries.forEach { add(it.name) }
            is Expression.Compound -> node.entries.forEach { add(it.key) }
            is Expression.InterpolatedString -> text(node.parts)
            is TypeSyntax.Name -> name(node.use)
            is TypeSyntax.Entity -> addAll(node.names)
            is Statement.Assign -> add(node.operator)
            is Statement.TryStore -> name(node.destination)
            is Statement.Command -> text(node.parts)
            else -> Unit
        } }
    }

    fun encode(syntax: LibrarySyntax, strings: LibraryStrings, references: SyntaxReferenceValidator): EncodedSyntax {
        validateAll(syntax, references)
        return EncodedSyntax(encodeNodes(syntax, strings), encodeScopes(syntax.scopes), encodeBodies(syntax.bodies))
    }

    /** Staged section encoder; the complete import/export session must also run validateAll. */
    fun encodeNodes(syntax: LibrarySyntax, strings: LibraryStrings): ByteArray {
        validateDomain(syntax)
        return LibraryBinaryWriter().apply {
            list(syntax.nodes) { node -> record {
                u16(node.tag); u32(node.scope.value)
                record { writePayload(this, node, syntax, strings) }
            } }
        }.toByteArray()
    }

    fun encodeScopes(scopes: List<ScopeRecord>): ByteArray = LibraryBinaryWriter().apply {
        list(scopes) { scope -> record {
            u32(scope.owner.value); optional(scope.parent) { u32(it.value) }; list(scope.bindings) { u32(it.value) }
        } }
    }.toByteArray()

    fun encodeBodies(bodies: List<BodyRecord>): ByteArray = LibraryBinaryWriter().apply {
        list(bodies) { body -> record {
            u32(body.owner.value); u32(body.environment.value); u8(body.role.wireValue); u32(body.root.value)
            list(body.captures) { TypeTableCodec.writeDeclarationReference(this, it) }
        } }
    }.toByteArray()

    fun decode(encoded: EncodedSyntax, strings: LibraryStrings, references: SyntaxReferenceValidator): LibrarySyntax =
        decodeUnbound(encoded, strings).also { validateAll(it, references) }

    /** Isolated staging only. Returned syntax is explicitly unbound until validateAll succeeds. */
    fun decodeUnbound(encoded: EncodedSyntax, strings: LibraryStrings): LibrarySyntax {
        val scopesReader = LibraryBinaryReader(encoded.scopes, sectionId = LibrarySectionId.SCOPES)
        val scopes = checked(scopesReader) {
            var index = 0L
            list { record(++index) { ScopeRecord(DeclarationId(u32()), optional { ScopeId(u32()) }, list { DeclarationId(u32()) }) } }
                .also { requireEnd() }
        }
        val bodiesReader = LibraryBinaryReader(encoded.bodies, sectionId = LibrarySectionId.BODIES)
        val bodies = checked(bodiesReader) {
            var index = 0L
            list { record(++index) {
                val owner = DeclarationId(u32())
                val environment = EnvironmentId(u32())
                val roleCode = u8()
                val role = BodyRole.values().firstOrNull { it.wireValue == roleCode }
                    ?: fail("Unknown B role $roleCode", LibraryFormatCode.UNSUPPORTED)
                BodyRecord(owner, environment, role, NodeRef(u32()), list { TypeTableCodec.readDeclarationReference(this) })
            } }.also { requireEnd() }
        }
        val nodeReader = LibraryBinaryReader(encoded.nodes, sectionId = LibrarySectionId.NODES)
        val raw = checked(nodeReader) {
            var index = 0L
            list { record(++index) {
                val offset = byteOffset
                val tag = u16()
                if (!SyntaxTags.isKnown(tag)) fail("Unknown N tag 0x${tag.toString(16)}", LibraryFormatCode.UNSUPPORTED)
                val scope = ScopeId(u32())
                record { readRaw(this, strings, tag, scope, offset, index) }
            } }.also { requireEnd() }
        }
        val resolver = NodeResolver(raw)
        return LibrarySyntax(raw.indices.map { resolver.node(NodeRef(it + 1L)) }, scopes, bodies).also(::validateDomain)
    }

    fun validateAll(syntax: LibrarySyntax, references: SyntaxReferenceValidator) {
        syntax.externalReferencesValidated = false
        validateDomain(syntax)
        fun d(id: DeclarationId) = references.declaration(DeclarationReference.Local(id))
        fun name(use: NameUse) { when (use) {
            is NameUse.Local -> d(use.target)
            is NameUse.Declaration -> references.declaration(use.target)
            is NameUse.Lookup -> { references.environment(use.environment); use.candidates.forEach(references::declaration) }
        } }
        syntax.scopes.forEach { d(it.owner); it.bindings.forEach(::d) }
        syntax.bodies.forEach { d(it.owner); references.environment(it.environment); it.captures.forEach(references::declaration) }
        syntax.nodes.forEach { node -> when (node) {
            is Expression.Literal -> references.constant(node.constant)
            is Expression.Name -> name(node.use)
            is Expression.Member -> node.candidates?.forEach(references::declaration)
            is Expression.This -> references.declaration(node.owner)
            is Expression.Super -> references.declaration(node.owner)
            is Expression.Lambda -> { d(node.declaration); node.parameters.forEach(::d) }
            is Expression.Coordinate -> node.dimensions.forEach { it.value?.let(references::constant) }
            is Expression.NbtArray -> node.values.forEach(references::constant)
            is TypeSyntax.Name -> { name(node.use); node.resolved?.let(references::type) }
            is TypeSyntax.Apply -> node.resolved?.let(references::type)
            is TypeSyntax.Entity -> node.limit?.let(references::constant)
            is TypeSyntax.AnonymousData -> d(node.declaration)
            is Statement.Local -> d(node.declaration)
            is Statement.Foreach -> d(node.variable)
            is Statement.TryStore -> name(node.destination)
            is TemplateMember.Declaration -> d(node.declaration)
            is TemplateMember.AnnotationApplication -> { references.annotation(node.annotation); node.targets.forEach(::d) }
            is TemplateMember.AnnotationConstruction -> { references.declaration(node.provider); node.targets.forEach(::d) }
            else -> Unit
        } }
        syntax.externalReferencesValidated = true
    }

    fun validateDomain(syntax: LibrarySyntax) {
        val state = IdentityHashMap<SemanticNode, Int>()
        fun visit(node: SemanticNode) {
            when (state[node]) { 1 -> invalid("Cycle in N graph"); 2 -> return }
            require(SyntaxTags.isKnown(node.tag)) { "Unknown semantic node tag" }
            require(node is Expression || node is TypeSyntax || node is Statement || node is TemplateBody || node is TemplateMember) {
                "Unsupported semantic node implementation"
            }
            syntax.reference(node)
            syntax.scope(node.scope)
            state[node] = 1
            children(node).forEach { child -> syntax.reference(child); visit(child) }
            state[node] = 2
        }
        syntax.nodes.forEachIndexed { index, node -> domain(LibrarySectionId.NODES, index + 1L) { visit(node) } }
        val scopeState = IntArray(syntax.scopes.size)
        fun scope(reference: ScopeId) {
            val record = syntax.scope(reference)
            val index = (reference.value - 1).toInt()
            when (scopeState[index]) { 1 -> invalid("Cycle in Q parent graph"); 2 -> return }
            scopeState[index] = 1
            require(record.bindings.distinct().size == record.bindings.size) { "Duplicate D binding in Q" }
            record.parent?.let(::scope)
            scopeState[index] = 2
        }
        syntax.scopes.indices.forEach { index -> domain(LibrarySectionId.SCOPES, index + 1L) { scope(ScopeId(index + 1L)) } }
        syntax.bodies.forEachIndexed { index, body -> domain(LibrarySectionId.BODIES, index + 1L) {
            val root = syntax.node(body.root)
            when (body.role) {
                BodyRole.DEFAULT, BodyRole.INITIALIZER -> require(root is Expression) { "Default/initializer B requires an expression root" }
                else -> require(root is Statement.Block) { "Executable B requires a Block root" }
            }
        } }
        syntax.nodes.forEachIndexed { index, node -> domain(LibrarySectionId.NODES, index + 1L) {
            if (node is Expression.Lambda) {
                val body = syntax.body(node.body)
                require(body.owner == node.declaration && body.role == BodyRole.LAMBDA) { "Lambda D/B ownership or role differs" }
                node.tail?.let { tail ->
                    val seen = IdentityHashMap<SemanticNode, Boolean>()
                    fun contains(current: SemanticNode): Boolean {
                        if (current === tail) return true
                        if (seen.put(current, true) != null) return false
                        return children(current).any(::contains)
                    }
                    require(!contains(syntax.node(body.root))) { "Lambda tail also occurs in its statement body" }
                }
            }
        } }
        // B links are structural lambda children; ordinary recursive calls stop at D references.
        val compositions = IdentityHashMap<SemanticNode, Int>()
        fun composition(node: SemanticNode) {
            when (compositions[node]) { 1 -> invalid("Cycle through lambda B roots"); 2 -> return }
            compositions[node] = 1
            children(node).forEach(::composition)
            if (node is Expression.Lambda) composition(syntax.node(syntax.body(node.body).root))
            compositions[node] = 2
        }
        syntax.nodes.forEachIndexed { index, node -> domain(LibrarySectionId.NODES, index + 1L) { composition(node) } }
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
    private fun domain(section: Int, record: Long, operation: () -> Unit) {
        try { operation() } catch (failure: IllegalArgumentException) {
            throw LibraryFormatException(LibraryFormatCode.CORRUPT, failure.message ?: "Invalid N/Q/B graph", 0, section, record, failure)
        }
    }
    private fun <T> checked(reader: LibraryBinaryReader, operation: LibraryBinaryReader.() -> T): T = try {
        operation(reader)
    } catch (failure: IllegalArgumentException) { reader.fail(failure.message ?: "Invalid syntax value") }

    private fun writeName(writer: LibraryBinaryWriter, use: NameUse, strings: LibraryStrings) = with(writer) {
        when (use) {
            is NameUse.Local -> tagged(1) { u32(use.target.value) }
            is NameUse.Declaration -> tagged(2) { TypeTableCodec.writeDeclarationReference(this, use.target) }
            is NameUse.Lookup -> tagged(3) {
                u32(strings.reference(use.name)); optional(use.namespace) { u32(strings.reference(it)) }; u32(use.environment.value)
                list(use.candidates) { TypeTableCodec.writeDeclarationReference(this, it) }
            }
        }
    }

    private fun readName(reader: LibraryBinaryReader, strings: LibraryStrings): NameUse = reader.tagged { tag -> when (tag) {
        1 -> NameUse.Local(DeclarationId(u32()))
        2 -> NameUse.Declaration(TypeTableCodec.readDeclarationReference(this))
        3 -> NameUse.Lookup(strings.value(u32()), optional { strings.value(u32()) }, EnvironmentId(u32()),
            list { TypeTableCodec.readDeclarationReference(this) })
        else -> fail("Unknown NameUse tag $tag", LibraryFormatCode.UNSUPPORTED)
    } }

    private fun writePayload(writer: LibraryBinaryWriter, node: SemanticNode, syntax: LibrarySyntax, strings: LibraryStrings) {
        with(writer) {
            fun LibraryBinaryWriter.n(value: SemanticNode) = u32(syntax.reference(value).value)
            fun LibraryBinaryWriter.ns(values: List<SemanticNode>) = list(values) { n(it) }
            fun LibraryBinaryWriter.maybe(value: SemanticNode?) = optional(value) { n(it) }
            fun LibraryBinaryWriter.text(parts: List<TextPart>) = list(parts) { when (it) {
                is TextPart.Text -> tagged(1) { u32(strings.reference(it.value)) }
                is TextPart.Interpolation -> tagged(2) { n(it.expression) }
            } }
            when (node) {
                is Expression.Literal -> u32(node.constant.value)
                is Expression.Name -> writeName(this, node.use, strings)
                is Expression.This -> TypeTableCodec.writeDeclarationReference(this, node.owner)
                is Expression.Super -> TypeTableCodec.writeDeclarationReference(this, node.owner)
                is Expression.TypeValue -> n(node.type)
                is Expression.Group -> maybe(node.value)
                is Expression.Unary -> { u32(strings.reference(node.operator)); n(node.operand) }
                is Expression.Binary -> { u32(strings.reference(node.operator)); n(node.left); n(node.right) }
                is Expression.Cast -> { n(node.value); n(node.target) }
                is Expression.Member -> {
                    n(node.receiver); u32(strings.reference(node.name)); u8(node.keyword.wireValue)
                    optional(node.candidates) { values -> list(values) { TypeTableCodec.writeDeclarationReference(this, it) } }
                }
                is Expression.Index -> { n(node.receiver); maybe(node.index) }
                is Expression.PropertyFilter -> { n(node.receiver); list(node.entries) { u32(strings.reference(it.name)); n(it.value) } }
                is Expression.Call -> {
                    n(node.callee); ns(node.readonly); list(node.normal) { u8(it.kind); n(it.expression) }; maybe(node.trailingLambda)
                }
                is Expression.Lambda -> {
                    u32(node.declaration.value); list(node.parameters) { u32(it.value) }; bool(node.explicitParameters)
                    u32(node.body.value); maybe(node.tail)
                }
                is Expression.Range -> { maybe(node.lower); maybe(node.upper) }
                is Expression.Coordinate -> list(node.dimensions) { u8(it.mode.wireValue); optional(it.value) { u32(it.value) } }
                is Expression.Selector -> u8(node.kind.wireValue)
                is Expression.ListValue -> ns(node.elements)
                is Expression.Compound -> list(node.entries) { u32(strings.reference(it.key)); n(it.value) }
                is Expression.NbtArray -> { u8(node.element.wireValue); list(node.values) { u32(it.value) } }
                is Expression.InterpolatedString -> text(node.parts)
                is TypeSyntax.Name -> { writeName(this, node.use, strings); optional(node.resolved) { u32(it.value) } }
                is TypeSyntax.Apply -> { n(node.base); ns(node.readonly); optional(node.resolved) { u32(it.value) } }
                is TypeSyntax.Container -> { u8(node.kind.wireValue); n(node.argument) }
                is TypeSyntax.Vector -> u32(node.dimensions)
                is TypeSyntax.Entity -> { optional(node.limit) { u32(it.value) }; list(node.names) { u32(strings.reference(it)) } }
                is TypeSyntax.Union -> ns(node.alternatives)
                is TypeSyntax.Intersection -> ns(node.components)
                is TypeSyntax.AnonymousData -> u32(node.declaration.value)
                is TypeSyntax.Function -> { ns(node.parameters); n(node.result); u8(node.execution.wireValue) }
                is TypeSyntax.Void -> Unit
                is TypeSyntax.Inferred -> n(node.expression)
                is Statement.Block -> ns(node.statements)
                is Statement.Local -> { u32(node.declaration.value); maybe(node.initializer) }
                is Statement.Evaluate -> n(node.value)
                is Statement.Assign -> { n(node.target); u32(strings.reference(node.operator)); n(node.value) }
                is Statement.If -> { list(node.branches) { n(it.condition); n(it.body) }; maybe(node.elseBody) }
                is Statement.While -> { n(node.condition); n(node.body) }
                is Statement.DoWhile -> { n(node.body); n(node.condition) }
                is Statement.Foreach -> { u32(node.variable.value); n(node.iterable); n(node.body) }
                is Statement.TryStore -> { n(node.body); writeName(this, node.destination, strings) }
                is Statement.Execute -> {
                    list(node.contexts) { context ->
                        when (val path = context.path) {
                            ExecutePath.As -> tagged(1) { }
                            is ExecutePath.Members -> tagged(2) { ns(path.parts) }
                        }
                        n(context.value)
                    }
                    n(node.body)
                }
                is Statement.Command -> text(node.parts)
                is Statement.Break, is Statement.Continue, is Statement.Empty -> Unit
                is Statement.Return -> maybe(node.value)
                is TemplateBody -> ns(node.members)
                is TemplateMember.Declaration -> u32(node.declaration.value)
                is TemplateMember.AnnotationApplication -> { u32(node.annotation.value); list(node.targets) { u32(it.value) } }
                is TemplateMember.AnnotationConstruction -> {
                    TypeTableCodec.writeDeclarationReference(this, node.provider); ns(node.arguments); list(node.targets) { u32(it.value) }
                }
                else -> error("Unsupported semantic node implementation")
            }
        }
    }

    private fun children(node: SemanticNode): List<SemanticNode> = when (node) {
        is Expression.TypeValue -> listOf(node.type)
        is Expression.Group -> listOfNotNull(node.value)
        is Expression.Unary -> listOf(node.operand)
        is Expression.Binary -> listOf(node.left, node.right)
        is Expression.Cast -> listOf(node.value, node.target)
        is Expression.Member -> listOf(node.receiver)
        is Expression.Index -> listOfNotNull(node.receiver, node.index)
        is Expression.PropertyFilter -> listOf(node.receiver) + node.entries.map { it.value }
        is Expression.Call -> listOf(node.callee) + node.readonly + node.normal.map { it.expression } + listOfNotNull(node.trailingLambda)
        is Expression.Lambda -> listOfNotNull(node.tail)
        is Expression.Range -> listOfNotNull(node.lower, node.upper)
        is Expression.ListValue -> node.elements
        is Expression.Compound -> node.entries.map { it.value }
        is Expression.InterpolatedString -> node.parts.filterIsInstance<TextPart.Interpolation>().map { it.expression }
        is TypeSyntax.Apply -> listOf(node.base) + node.readonly
        is TypeSyntax.Container -> listOf(node.argument)
        is TypeSyntax.Union -> node.alternatives
        is TypeSyntax.Intersection -> node.components
        is TypeSyntax.Function -> node.parameters + node.result
        is TypeSyntax.Inferred -> listOf(node.expression)
        is Statement.Block -> node.statements
        is Statement.Local -> listOfNotNull(node.initializer)
        is Statement.Evaluate -> listOf(node.value)
        is Statement.Assign -> listOf(node.target, node.value)
        is Statement.If -> node.branches.flatMap { listOf(it.condition, it.body) } + listOfNotNull(node.elseBody)
        is Statement.While -> listOf(node.condition, node.body)
        is Statement.DoWhile -> listOf(node.body, node.condition)
        is Statement.Foreach -> listOf(node.iterable, node.body)
        is Statement.TryStore -> listOf(node.body)
        is Statement.Execute -> node.contexts.flatMap { context ->
            ((context.path as? ExecutePath.Members)?.parts ?: emptyList()) + context.value
        } + node.body
        is Statement.Command -> node.parts.filterIsInstance<TextPart.Interpolation>().map { it.expression }
        is Statement.Return -> listOfNotNull(node.value)
        is TemplateBody -> node.members
        is TemplateMember.AnnotationConstruction -> node.arguments
        else -> emptyList()
    }

    // Decode first into file-local references, then restore a shared, acyclic nested DTO graph.
    private data class RawNode(val offset: Long, val index: Long, val create: (NodeResolver) -> SemanticNode)
    private class NodeResolver(private val raw: List<RawNode>) {
        private val nodes = arrayOfNulls<SemanticNode>(raw.size)
        private val active = BooleanArray(raw.size)
        fun node(reference: NodeRef): SemanticNode {
            require(reference.value <= raw.size.toLong()) { "N reference ${reference.value} is out of bounds" }
            val index = (reference.value - 1).toInt()
            nodes[index]?.let { return it }
            val record = raw[index]
            if (active[index]) throw LibraryFormatException(LibraryFormatCode.CORRUPT, "Cycle in N graph",
                record.offset, LibrarySectionId.NODES, record.index)
            active[index] = true
            val node = try { record.create(this) } catch (failure: IllegalArgumentException) {
                throw LibraryFormatException(LibraryFormatCode.CORRUPT, failure.message ?: "Invalid N payload",
                    record.offset, LibrarySectionId.NODES, record.index, failure)
            }
            active[index] = false; nodes[index] = node
            return node
        }
        fun expression(reference: NodeRef): Expression = node(reference) as? Expression ?: SyntaxCodec.invalid("N child must be an expression")
        fun type(reference: NodeRef): TypeSyntax = node(reference) as? TypeSyntax ?: SyntaxCodec.invalid("N child must be a type")
        fun statement(reference: NodeRef): Statement = node(reference) as? Statement ?: SyntaxCodec.invalid("N child must be a statement")
        fun member(reference: NodeRef): TemplateMember = node(reference) as? TemplateMember ?: SyntaxCodec.invalid("N child must be a template member")
    }

    private fun readRaw(
        reader: LibraryBinaryReader, strings: LibraryStrings, tag: Int, scope: ScopeId, offset: Long, index: Long,
    ): RawNode = checked(reader) {
        // Decoder-owned child lists must remain immutable when a decoded builtin graph is
        // shared by independent import sessions. BinaryReader.list already freezes wire lists.
        fun <T, R> List<T>.mapFrozen(transform: (T) -> R): List<R> =
            java.util.Collections.unmodifiableList(map(transform))
        fun ref() = NodeRef(u32())
        fun refs() = list { NodeRef(u32()) }
        fun maybe() = optional { NodeRef(u32()) }
        fun text(): List<Pair<String?, NodeRef?>> = list { tagged { part -> when (part) {
            1 -> strings.value(u32()) to null
            2 -> null to NodeRef(u32())
            else -> fail("Unknown TextPart tag $part", LibraryFormatCode.UNSUPPORTED)
        } } }
        fun parts(values: List<Pair<String?, NodeRef?>>, resolver: NodeResolver) = values.mapFrozen { (text, expression) ->
            if (text != null) TextPart.Text(text) else TextPart.Interpolation(resolver.expression(requireNotNull(expression)))
        }
        fun <T> wireEnum(value: Int, values: Array<T>, code: (T) -> Int): T = values.firstOrNull { code(it) == value }
            ?: fail("Unknown enum value $value in N 0x${tag.toString(16)}", LibraryFormatCode.UNSUPPORTED)
        val create: (NodeResolver) -> SemanticNode = when (tag) {
            SyntaxTags.LITERAL -> ConstantRef(u32()).let { value -> { Expression.Literal(scope, value) } }
            SyntaxTags.NAME -> readName(this, strings).let { value -> { Expression.Name(scope, value) } }
            SyntaxTags.THIS -> TypeTableCodec.readDeclarationReference(this).let { value -> { Expression.This(scope, value) } }
            SyntaxTags.SUPER -> TypeTableCodec.readDeclarationReference(this).let { value -> { Expression.Super(scope, value) } }
            SyntaxTags.TYPE_VALUE -> ref().let { value -> { r -> Expression.TypeValue(scope, r.type(value)) } }
            SyntaxTags.GROUP -> maybe().let { value -> { r -> Expression.Group(scope, value?.let(r::expression)) } }
            SyntaxTags.UNARY -> { val op = strings.value(u32()); val value = ref(); { r -> Expression.Unary(scope, op, r.expression(value)) } }
            SyntaxTags.BINARY -> { val op = strings.value(u32()); val a = ref(); val b = ref(); { r -> Expression.Binary(scope, op, r.expression(a), r.expression(b)) } }
            SyntaxTags.CAST -> { val value = ref(); val target = ref(); { r -> Expression.Cast(scope, r.expression(value), r.type(target)) } }
            SyntaxTags.MEMBER -> {
                val receiver = ref(); val name = strings.value(u32()); val kind = wireEnum(u8(), MemberKeyword.values()) { it.wireValue }
                val candidates = optional { list { TypeTableCodec.readDeclarationReference(this) } };
                { r -> Expression.Member(scope, r.expression(receiver), name, kind, candidates) }
            }
            SyntaxTags.INDEX -> { val receiver = ref(); val value = maybe(); { r -> Expression.Index(scope, r.expression(receiver), value?.let(r::expression)) } }
            SyntaxTags.PROPERTY_FILTER -> { val receiver = ref(); val entries = list { strings.value(u32()) to NodeRef(u32()) }; { r -> Expression.PropertyFilter(scope, r.expression(receiver), entries.mapFrozen { PropertyEntry(it.first, r.expression(it.second)) }) } }
            SyntaxTags.CALL -> {
                val callee = ref(); val readonly = refs(); val normal = list {
                    if (u8() != 1) fail("Legacy/unknown call argument kind", LibraryFormatCode.UNSUPPORTED)
                    NodeRef(u32())
                }; val trailing = maybe();
                { r -> Expression.Call(scope, r.expression(callee), readonly.mapFrozen(r::expression), normal.mapFrozen { Argument(r.expression(it)) }, trailing?.let(r::expression)) }
            }
            SyntaxTags.LAMBDA -> {
                val declaration = DeclarationId(u32()); val parameters = list { DeclarationId(u32()) }
                val explicit = bool(); val body = BodyId(u32()); val tail = maybe();
                { r -> Expression.Lambda(scope, declaration, parameters, explicit, body, tail?.let(r::expression)) }
            }
            SyntaxTags.RANGE -> { val a = maybe(); val b = maybe(); { r -> Expression.Range(scope, a?.let(r::expression), b?.let(r::expression)) } }
            SyntaxTags.COORDINATE -> list {
                Dimension(wireEnum(u8(), DimensionMode.values()) { it.wireValue }, optional { ConstantRef(u32()) })
            }.let { values -> { Expression.Coordinate(scope, values) } }
            SyntaxTags.SELECTOR -> wireEnum(u8(), SelectorKind.values()) { it.wireValue }.let { value -> { Expression.Selector(scope, value) } }
            SyntaxTags.LIST -> refs().let { values -> { r -> Expression.ListValue(scope, values.mapFrozen(r::expression)) } }
            SyntaxTags.COMPOUND -> list { strings.value(u32()) to NodeRef(u32()) }.let { values -> { r -> Expression.Compound(scope, values.mapFrozen { CompoundEntry(it.first, r.expression(it.second)) }) } }
            SyntaxTags.NBT_ARRAY -> { val kind = wireEnum(u8(), NbtArrayElement.values()) { it.wireValue }; val values = list { ConstantRef(u32()) }; { Expression.NbtArray(scope, kind, values) } }
            SyntaxTags.INTERPOLATED_STRING -> text().let { values -> { r -> Expression.InterpolatedString(scope, parts(values, r)) } }
            SyntaxTags.TYPE_NAME -> { val use = readName(this, strings); val resolved = optional { TypeRef(u32()) }; { TypeSyntax.Name(scope, use, resolved) } }
            SyntaxTags.TYPE_APPLY -> { val base = ref(); val args = refs(); val resolved = optional { TypeRef(u32()) }; { r -> TypeSyntax.Apply(scope, r.type(base), args.mapFrozen(r::expression), resolved) } }
            SyntaxTags.CONTAINER_TYPE -> { val kind = wireEnum(u8(), ContainerKind.values()) { it.wireValue }; val argument = ref(); { r -> TypeSyntax.Container(scope, kind, r.type(argument)) } }
            SyntaxTags.VECTOR_TYPE -> u32().let { value -> { TypeSyntax.Vector(scope, value) } }
            SyntaxTags.ENTITY_TYPE -> { val limit = optional { ConstantRef(u32()) }; val names = list { strings.value(u32()) }; { TypeSyntax.Entity(scope, limit, names) } }
            SyntaxTags.UNION_TYPE -> refs().let { values -> { r -> TypeSyntax.Union(scope, values.mapFrozen(r::type)) } }
            SyntaxTags.INTERSECTION_TYPE -> refs().let { values -> { r -> TypeSyntax.Intersection(scope, values.mapFrozen(r::type)) } }
            SyntaxTags.ANONYMOUS_DATA_TYPE -> DeclarationId(u32()).let { value -> { TypeSyntax.AnonymousData(scope, value) } }
            SyntaxTags.FUNCTION_TYPE -> { val parameters = refs(); val result = ref(); val execution = wireEnum(u8(), FunctionExecution.values()) { it.wireValue }; { r -> TypeSyntax.Function(scope, parameters.mapFrozen(r::type), r.type(result), execution) } }
            SyntaxTags.VOID_TYPE -> { { TypeSyntax.Void(scope) } }
            SyntaxTags.INFERRED_TYPE -> ref().let { value -> { r -> TypeSyntax.Inferred(scope, r.expression(value)) } }
            SyntaxTags.BLOCK -> refs().let { values -> { r -> Statement.Block(scope, values.mapFrozen(r::statement)) } }
            SyntaxTags.LOCAL -> { val declaration = DeclarationId(u32()); val initial = maybe(); { r -> Statement.Local(scope, declaration, initial?.let(r::expression)) } }
            SyntaxTags.EVALUATE -> ref().let { value -> { r -> Statement.Evaluate(scope, r.expression(value)) } }
            SyntaxTags.ASSIGN -> { val target = ref(); val op = strings.value(u32()); val value = ref(); { r -> Statement.Assign(scope, r.expression(target), op, r.expression(value)) } }
            SyntaxTags.IF -> { val branches = list { NodeRef(u32()) to NodeRef(u32()) }; val otherwise = maybe(); { r -> Statement.If(scope, branches.mapFrozen { ConditionalBranch(r.expression(it.first), r.statement(it.second)) }, otherwise?.let(r::statement)) } }
            SyntaxTags.WHILE -> { val condition = ref(); val body = ref(); { r -> Statement.While(scope, r.expression(condition), r.statement(body)) } }
            SyntaxTags.DO_WHILE -> { val body = ref(); val condition = ref(); { r -> Statement.DoWhile(scope, r.statement(body), r.expression(condition)) } }
            SyntaxTags.FOREACH -> { val variable = DeclarationId(u32()); val iterable = ref(); val body = ref(); { r -> Statement.Foreach(scope, variable, r.expression(iterable), r.statement(body)) } }
            SyntaxTags.TRY_STORE -> { val body = ref(); val destination = readName(this, strings); { r -> Statement.TryStore(scope, r.statement(body), destination) } }
            SyntaxTags.EXECUTE -> {
                val contexts = list {
                    val path = tagged { kind -> when (kind) {
                        1 -> null
                        2 -> list { NodeRef(u32()) }
                        else -> fail("Unknown ExecutePath tag $kind", LibraryFormatCode.UNSUPPORTED)
                    } }
                    path to NodeRef(u32())
                }; val body = ref();
                { r -> Statement.Execute(scope, contexts.mapFrozen { (path, value) -> ExecuteContext(
                    if (path == null) ExecutePath.As else ExecutePath.Members(path.mapFrozen(r::expression)), r.expression(value)) }, r.statement(body)) }
            }
            SyntaxTags.COMMAND -> text().let { values -> { r -> Statement.Command(scope, parts(values, r)) } }
            SyntaxTags.BREAK -> { { Statement.Break(scope) } }
            SyntaxTags.CONTINUE -> { { Statement.Continue(scope) } }
            SyntaxTags.RETURN -> maybe().let { value -> { r -> Statement.Return(scope, value?.let(r::expression)) } }
            SyntaxTags.EMPTY -> { { Statement.Empty(scope) } }
            SyntaxTags.TEMPLATE_BODY -> refs().let { values -> { r -> TemplateBody(scope, values.mapFrozen(r::member)) } }
            SyntaxTags.MEMBER_DECLARATION -> DeclarationId(u32()).let { value -> { TemplateMember.Declaration(scope, value) } }
            SyntaxTags.ANNOTATION_APPLICATION -> { val annotation = AnnotationId(u32()); val targets = list { DeclarationId(u32()) }; { TemplateMember.AnnotationApplication(scope, annotation, targets) } }
            SyntaxTags.ANNOTATION_CONSTRUCTION -> {
                val provider = TypeTableCodec.readDeclarationReference(this); val arguments = refs(); val targets = list { DeclarationId(u32()) };
                { r -> TemplateMember.AnnotationConstruction(scope, provider, arguments.mapFrozen(r::expression), targets) }
            }
            else -> fail("Unknown N tag $tag", LibraryFormatCode.UNSUPPORTED)
        }
        RawNode(offset, index, create)
    }
}
