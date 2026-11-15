package top.mcfpp.type

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.MemberDescriptor
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.model.property.SimpleMutator
import java.util.Collections

enum class ObjectMemberKind { VAR, CONST }

/** Declaration metadata is retained for diagnostics, independently of the semantic identity. */
class ObjectFieldContract(
    val name: String,
    val type: MCFPPType,
    val optional: Boolean = false,
    val kind: ObjectMemberKind = ObjectMemberKind.VAR,
    origins: Set<TypeId> = emptySet(),
    val readable: Boolean = true,
    val writable: Boolean = kind != ObjectMemberKind.CONST
) {
    val origins: Set<TypeId> = Collections.unmodifiableSet(LinkedHashSet(origins))
}

/** An open field contract. Its synthetic template is only an adapter to the existing backend. */
class MCFPPObjectType private constructor(
    fields: Collection<ObjectFieldContract>,
    private val runtimeTemplate: StructuralObjectTemplate
) : MCFPPDataTemplateType(runtimeTemplate, arrayListOf(MCFPPBaseType.Object)) {
    constructor(fields: Collection<ObjectFieldContract>) : this(fields, StructuralObjectTemplate())

    val fields: List<ObjectFieldContract> = Collections.unmodifiableList(fields.sortedBy { it.name })
    val fieldsByName: Map<String, ObjectFieldContract> = Collections.unmodifiableMap(this.fields.associateBy { it.name })

    init {
        require(fieldsByName.size == this.fields.size) { "An object type cannot contain duplicate fields" }
        runtimeTemplate.structuralType = this
    }

    override val typeId: TypeId get() = TypeId.Applied(TypeId.Builtin(TypeCompositions.OBJECT), this.fields.map { field ->
        TypeId.Applied(TypeId.Builtin(TypeCompositions.FIELD), listOf(
            TypeId.Builtin(field.name), field.type.typeId,
            TypeId.Builtin(if (field.optional) TypeCompositions.OPTIONAL else TypeCompositions.REQUIRED)
        ))
    })

    override val typeName: String get() = fields.joinToString(", ", "{", "}") {
        "${it.name}${if (it.optional) "?" else ""}: ${it.type.typeName}"
    }

    override val hasRuntimeRepresentation: Boolean get() = fields.all {
        it.optional && it.type === MCFPPNeverType || it.type.hasRuntimeRepresentation
    }

    internal override val instanceFields: List<Var<*>> get() = templateForRuntime().scope.allVars.toList()

    /** Preparing field Vars is deferred until a value actually needs a runtime adapter. */
    fun templateForRuntime(): DataTemplate = runtimeTemplate.apply { prepareFields() }

    /** Savepoints retain the adapter shell without triggering its lazy field preparation. */
    internal fun rawRuntimeTemplate(): DataTemplate = runtimeTemplate

    override val instanceData: DataTemplate get() = templateForRuntime()

    override fun buildUnConcrete(identifier: String): Var<*> = DataTemplateObject(templateForRuntime(), identifier).also { it.type = this }

    override fun build(identifier: String, value: Any?): Var<*> =
        top.mcfpp.analysis.StorageAccess.literal(this, value as CompilerValue, identifier)

    companion object {
        /** The exact private adapter class is compiler-owned, rather than an arbitrary declaration subclass. */
        internal fun runtimeTemplateOwner(value: CompoundData): MCFPPObjectType? =
            (value as? StructuralObjectTemplate)?.structuralType
    }

    private class StructuralObjectTemplate : DataTemplate("0object", "mcfpp.structural") {
        lateinit var structuralType: MCFPPObjectType
        private var prepared = false
        override val sourceNominalIdentity: TypeId get() = structuralType.typeId
        override fun getType(): MCFPPDataTemplateType = structuralType

        internal override fun checkpointDeclarationState(): DataTemplate.DeclarationStateCheckpoint = runtimeCheckpoint(null)

        private fun runtimeCheckpoint(previous: DataTemplate.DeclarationStateCheckpoint?): DataTemplate.DeclarationStateCheckpoint {
            val state = previous?.captureCurrent() ?: super.checkpointDeclarationState()
            val wasPrepared = prepared
            return DataTemplate.DeclarationStateCheckpoint(state.elements, {
                state.restore()
                prepared = wasPrepared
            }, { runtimeCheckpoint(state) }, state.preparationFacts)
        }

        fun prepareFields() {
            if (prepared) return
            prepared = true
            for (contract in structuralType.fields) {
                val field = contract.type.buildUnConcrete(contract.name)
                field.type = contract.type
                field.nullable = contract.optional
                field.isConst = contract.kind == ObjectMemberKind.CONST
                field.isStatic = false
                field.declaredParentTemplate = this
                if (field.isConst && contract.type is MCFPPLiteralType)
                    field.declarationConstant = CompilerValue.Typed(contract.type.typeId, contract.type.value)
                scope.putVar(contract.name, field)
                scope.putProperty(contract.name, Property(contract.name, if (contract.readable) SimpleAccessor() else null,
                    if (field.isConst || !contract.writable) null else SimpleMutator()).apply {
                    isStatic = false
                    declaredParentTemplate = this@StructuralObjectTemplate
                })
            }
        }
    }
}

/** A primitive value in type position; the payload is an immutable compiler value. */
class MCFPPLiteralType(val primitive: MCFPPType, value: CompilerValue) : MCFPPType(arrayListOf(primitive)) {
    val value: CompilerValue = canonicalValue(primitive, value)
    override val typeId: TypeId = TypeId.Specialized(TypeId.Builtin(TypeCompositions.LITERAL),
        listOf(CompilerValue.TypeValue(primitive.typeId), this.value))
    override val hasRuntimeRepresentation: Boolean get() = primitive.hasRuntimeRepresentation
    override val instanceData get() = primitive.instanceData
    override val objectData get() = primitive.objectData
    override val nbtType get() = primitive.nbtType
    override val typeName: String get() = when (val literal = value) {
        is CompilerValue.Integral -> literal.value.toString()
        is CompilerValue.Bool -> literal.value.toString()
        is CompilerValue.Text -> "\"${literal.value}\""
        is CompilerValue.FloatBits -> Float.fromBits(literal.bits).toString()
        is CompilerValue.DoubleBits -> Double.fromBits(literal.bits).toString()
        else -> error("Invalid primitive literal")
    }

    override fun buildUnConcrete(identifier: String): Var<*> = primitive.buildUnConcrete(identifier).also { it.type = this }
    override fun build(identifier: String, value: Any?): Var<*> =
        (if (value is CompilerValue) top.mcfpp.analysis.StorageAccess.literal(this, value, identifier)
        else primitive.build(identifier, value)).also { it.type = this }

    companion object {
        private fun canonicalValue(primitive: MCFPPType, input: CompilerValue): CompilerValue {
            require(primitive !is MCFPPLiteralType) { "A literal's parent must be a primitive type" }
            var value = input
            while (value is CompilerValue.Typed) {
                require(value.type == primitive.typeId) { "Literal payload has a different primitive type" }
                value = value.payload
            }
            val valid = when (primitive) {
                MCFPPBaseType.Int, MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long -> value is CompilerValue.Integral
                MCFPPBaseType.Float -> value is CompilerValue.FloatBits
                MCFPPNBTType.Double -> value is CompilerValue.DoubleBits
                MCFPPBaseType.Bool -> value is CompilerValue.Bool
                MCFPPBaseType.String -> value is CompilerValue.Text
                else -> false
            }
            require(valid) { "Literal types require a matching primitive compiler value" }
            return value
        }
    }
}

/** An uninhabited type; optional fields of this type can only be absent. */
object MCFPPNeverType : MCFPPType() {
    override val typeId: TypeId = TypeId.Builtin(TypeCompositions.NEVER)
    override val typeName: String get() = "never"
    override val hasRuntimeRepresentation: Boolean get() = false
    override fun buildUnConcrete(identifier: String): Var<*> = UnknownVar(identifier).also { it.type = this }
}

/** A normalized residual intersection, used when no complete object/primitive reduction is known. */
class MCFPPIntersectionType(components: Collection<MCFPPType>) : MCFPPType() {
    val components: List<MCFPPType> = Collections.unmodifiableList(components.flatMap {
        if (it is MCFPPIntersectionType) it.components else listOf(it)
    }.distinctBy { it.typeId }.sortedBy { it.typeId.toString() })
    init { require(this.components.isNotEmpty()) { "An intersection requires a component" } }
    override val typeId: TypeId get() = TypeId.Applied(TypeId.Builtin(TypeCompositions.INTERSECTION), this.components.map { it.typeId })
    override val typeName: String get() = components.joinToString(" & ") { it.typeName }
    override val hasRuntimeRepresentation: Boolean get() = false

    /** Signature collection needs an adapter before the declaration barrier fixes its contract. */
    override fun buildUnConcrete(identifier: String): Var<*> = NBTBasedData(identifier).also { it.type = this }
}

/** Central, allocation-free-in-storage type composition and shape projection. */
object TypeCompositions {
    const val OBJECT = "type.object"
    const val FIELD = "type.object.field"
    const val REQUIRED = "type.object.required"
    const val OPTIONAL = "type.object.optional"
    const val LITERAL = "type.literal"
    const val INTERSECTION = "type.intersection"
    const val NEVER = "type.never"

    fun objectShape(type: MCFPPType): MCFPPObjectType? = when (type) {
        is MCFPPObjectType -> type
        is MCFPPDataTemplateType -> MCFPPObjectType(type.instanceFields.map { field ->
            val descriptor = MemberDescriptor.of(field, type.template.scope.getProperty(field.identifier))
            val fieldType = if (field.isConst && field.type !is MCFPPLiteralType && field.declarationConstant != null)
                literalOrNull(field.type, field.declarationConstant!!) ?: field.type else field.type
            ObjectFieldContract(field.identifier, fieldType, descriptor.optional,
                if (field.isConst) ObjectMemberKind.CONST else ObjectMemberKind.VAR,
                setOf((field.declaredParentTemplate ?: type.template).inheritanceIdentity), descriptor.readable, descriptor.writable)
        })
        is MCFPPIntersectionType -> intersection(*type.components.toTypedArray()).let {
            if (it is MCFPPObjectType) it else null
        }
        else -> null
    }

    fun literalOrNull(primitive: MCFPPType, value: CompilerValue): MCFPPLiteralType? =
        try { MCFPPLiteralType(primitive, value) } catch (_: IllegalArgumentException) { null }

    fun widen(type: MCFPPType): MCFPPType = when (type) {
        is MCFPPLiteralType -> type.primitive
        is MCFPPObjectType -> MCFPPObjectType(type.fields.map {
            ObjectFieldContract(it.name, if (it.kind == ObjectMemberKind.CONST) it.type else widen(it.type), it.optional, it.kind,
                it.origins, it.readable, it.writable)
        })
        is MCFPPListType -> MCFPPListType(widen(type.generic.single()))
        is MCFPPDictType -> MCFPPDictType(widen(type.generic.single()))
        is MCFPPMapType -> MCFPPMapType(widen(type.generic.single()))
        is MCFPPUnionType -> union(type.types.map(::widen))
        else -> type
    }

    fun intersection(vararg types: MCFPPType): MCFPPType = intersect(types.toList(), hashSetOf())

    /** Preserve operand declarations until their inherited fields and declaration effects are final. */
    fun deferIntersection(types: Iterable<MCFPPType>): MCFPPType {
        val components = mergeEquivalentObjectContracts(types.flatMap { if (it is MCFPPIntersectionType) it.components else listOf(it) })
        return when (components.size) {
            0 -> MCFPPBaseType.Object
            1 -> components.single()
            else -> MCFPPIntersectionType(components)
        }
    }

    /** Pure finalization after the caller has completed every referenced nominal declaration. */
    fun normalizeResolved(type: MCFPPType): MCFPPType = normalizeResolved(type,
        Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>()), hashSetOf())

    private fun normalizeResolved(type: MCFPPType, active: MutableSet<MCFPPType>, intersections: MutableSet<TypeId>): MCFPPType {
        if (!active.add(type)) return type
        try {
            fun element(container: MCFPPTypeWithGeneric, build: (MCFPPType) -> MCFPPType): MCFPPType {
                val previous = container.generic.single()
                val normalized = normalizeResolved(previous, active, intersections)
                return if (normalized === previous) type else build(normalized)
            }
            return when (type) {
                is MCFPPIntersectionType -> {
                    val identity = type.typeId
                    if (!intersections.add(identity)) type else try {
                        val normalized = intersection(type.components.map { normalizeResolved(it, active, intersections) })
                        if (normalized is MCFPPIntersectionType && normalized.typeId == identity) type
                        else normalizeResolved(normalized, active, intersections)
                    } finally { intersections.remove(identity) }
                }
                is MCFPPObjectType -> {
                    val fields = type.fields.map { field ->
                        ObjectFieldContract(field.name, normalizeResolved(field.type, active, intersections), field.optional, field.kind,
                            field.origins, field.readable, field.writable)
                    }
                    if (fields.indices.all { fields[it].type === type.fields[it].type }) type else MCFPPObjectType(fields)
                }
                is MCFPPUnionType -> {
                    val alternatives = type.types.map { normalizeResolved(it, active, intersections) }
                    if (!type.deferredComposition && alternatives.indices.all { alternatives[it] === type.types[it] }) type
                    else union(alternatives)
                }
                is MCFPPFunctionType -> {
                    val parameters = type.parameters.map { normalizeResolved(it, active, intersections) }
                    val result = normalizeResolved(type.result, active, intersections)
                    val contexts = type.contexts.map { normalizeResolved(it, active, intersections) }
                    if (parameters.indices.all { parameters[it] === type.parameters[it] } && result === type.result &&
                        contexts.indices.all { contexts[it] === type.contexts[it] }) type
                    else MCFPPFunctionType(parameters, result, type.executionCategory, contexts)
                }
                is MCFPPListType -> element(type, ::MCFPPListType)
                is MCFPPImmutableListType -> element(type, ::MCFPPImmutableListType)
                is MCFPPDictType -> element(type, ::MCFPPDictType)
                is MCFPPMapType -> element(type, ::MCFPPMapType)
                else -> type
            }
        } finally {
            active.remove(type)
        }
    }

    fun hasDeferredComposition(type: MCFPPType): Boolean {
        val seen = Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
        fun visit(current: MCFPPType): Boolean {
            if (!seen.add(current)) return false
            return when (current) {
                is MCFPPIntersectionType -> true
                is MCFPPObjectType -> current.fields.any { visit(it.type) }
                is MCFPPUnionType -> current.deferredComposition || current.types.any(::visit)
                is MCFPPTypeWithGeneric -> current.generic.any(::visit)
                else -> false
            }
        }
        return visit(type)
    }

    fun hasUncompletedNominal(type: MCFPPType): Boolean {
        val seen = Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
        fun visit(current: MCFPPType): Boolean {
            if (!seen.add(current)) return false
            return when (current) {
                is MCFPPObjectType -> current.fields.any { visit(it.type) }
                is MCFPPDataTemplateType -> !current.rawTemplateDeclaration().hasCompletedDeclaration
                is MCFPPIntersectionType -> current.components.any(::visit)
                is MCFPPUnionType -> current.types.any(::visit)
                is MCFPPTypeWithGeneric -> current.generic.any(::visit)
                else -> false
            }
        }
        return visit(type)
    }

    private fun intersect(types: Collection<MCFPPType>, active: MutableSet<List<TypeId>>): MCFPPType {
        val components = mergeEquivalentObjectContracts(types.flatMap { if (it is MCFPPIntersectionType) it.components else listOf(it) })
            .sortedBy { it.typeId.toString() }
        if (components.any { it === MCFPPNeverType }) return MCFPPNeverType
        if (components.isEmpty()) return MCFPPBaseType.Object
        if (components.size == 1) return components.single()
        val key = components.map { it.typeId }
        // Recursive fields retain a finite reference to the same component declarations.
        // The immutable identity never embeds a cyclic JVM object graph.
        if (!active.add(key)) return MCFPPIntersectionType(components)
        try { return intersectComponents(components, active) } finally { active.remove(key) }
    }

    private fun intersectComponents(components: List<MCFPPType>, active: MutableSet<List<TypeId>>): MCFPPType {
        val unionComponent = components.filterIsInstance<MCFPPUnionType>().firstOrNull()
        if (unionComponent != null) return union(*unionComponent.types.map { alternative ->
            intersect(components.filterNot { it === unionComponent } + alternative, active)
        }.toTypedArray())
        val reduced = components.filterIndexed { index, component -> components.indices.none { other ->
            other != index && TypeRelations.isSubtype(components[other], component) &&
                preservesAccess(component, components[other]) &&
                (!TypeRelations.isSubtype(component, components[other]) || other < index)
        } }
        if (reduced.size == 1) return reduced.single()
        val shapes = reduced.map(::objectShape)
        if (shapes.all { it != null }) {
            val grouped = shapes.filterNotNull().flatMap { it.fields }.groupBy { it.name }.toSortedMap()
            val fields = arrayListOf<ObjectFieldContract>()
            for ((name, declarations) in grouped) {
                val fieldType = intersect(declarations.map { it.type }, active)
                val optional = declarations.all { it.optional }
                if (!optional && fieldType === MCFPPNeverType) return MCFPPNeverType
                fields.add(ObjectFieldContract(name, fieldType, optional,
                    if (declarations.any { it.kind == ObjectMemberKind.CONST }) ObjectMemberKind.CONST else ObjectMemberKind.VAR,
                    declarations.flatMap { it.origins }.toSet(), declarations.all { it.readable }, declarations.all { it.writable }))
            }
            return MCFPPObjectType(fields)
        }
        if (reduced.any { it is MCFPPLiteralType } || reduced.all { primitive(it) } ||
            shapes.any { it != null } && reduced.any(::primitive)) return MCFPPNeverType
        return MCFPPIntersectionType(reduced)
    }

    fun intersection(types: Iterable<MCFPPType>): MCFPPType = intersection(*types.toList().toTypedArray())

    fun union(vararg types: MCFPPType): MCFPPType = normalizeUnionAlternatives(types.toList()).let {
        when (it.size) { 0 -> MCFPPNeverType; 1 -> it.single(); else -> MCFPPUnionType(*it.toTypedArray()) }
    }

    fun union(types: Iterable<MCFPPType>): MCFPPType = union(*types.toList().toTypedArray())

    fun deferUnion(types: Iterable<MCFPPType>): MCFPPType = normalizeUnionAlternatives(types.toList(), false).let {
        when (it.size) { 0 -> MCFPPNeverType; 1 -> it.single(); else -> MCFPPUnionType.deferred(it) }
    }

    internal fun normalizeUnionAlternatives(types: Collection<MCFPPType>, subsume: Boolean = true): List<MCFPPType> {
        val flattened = mergeEquivalentObjectContracts(types.flatMap { if (it is MCFPPUnionType) it.types.toList() else listOf(it) }
            .filterNot { it === MCFPPNeverType }).sortedBy { it.typeId.toString() }
        if (!subsume || flattened.any(::hasDeferredComposition)) return flattened
        return flattened.filterIndexed { index, type -> flattened.indices.none { other ->
            other != index && TypeRelations.isSubtype(type, flattened[other]) &&
                preservesAccess(type, flattened[other]) &&
                (!TypeRelations.isSubtype(flattened[other], type) || other < index)
        } }
    }

    /** Equal data identities can still carry different member access declarations. */
    private fun mergeEquivalentObjectContracts(types: List<MCFPPType>): List<MCFPPType> = types.groupBy { it.typeId }.values.map { group ->
        val first = group.first()
        if (group.size == 1 || group.any { it !is MCFPPObjectType }) first
        else MCFPPObjectType((first as MCFPPObjectType).fields.map { field ->
            val declarations = group.map { (it as MCFPPObjectType).fieldsByName.getValue(field.name) }
            ObjectFieldContract(field.name, mergeEquivalentObjectContracts(declarations.map { it.type }).single(), field.optional,
                if (declarations.any { it.kind == ObjectMemberKind.CONST }) ObjectMemberKind.CONST else ObjectMemberKind.VAR,
                declarations.flatMap { it.origins }.toSet(), declarations.all { it.readable }, declarations.all { it.writable })
        })
    }

    /** Removing an alternative must not grant a capability unavailable on that alternative. */
    private fun preservesAccess(source: MCFPPType, representative: MCFPPType,
                                visited: MutableSet<Pair<TypeId, TypeId>> = hashSetOf()): Boolean {
        val supplied = objectShape(source) ?: return true
        val exposed = objectShape(representative) ?: return true
        val shared = exposed.fields.mapNotNull { required -> supplied.fieldsByName[required.name]?.let { it to required } }
        if (shared.any { (actual, required) -> required.readable && !actual.readable || required.writable && !actual.writable }) return false
        if (!visited.add(source.typeId to representative.typeId)) return true
        return shared.all { (actual, required) -> preservesAccess(actual.type, required.type, visited) }
    }

    private fun primitive(type: MCFPPType): Boolean = type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float,
        MCFPPBaseType.Bool, MCFPPBaseType.String, MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long, MCFPPNBTType.Double)

    fun isCompositionIdentity(id: TypeId): Boolean = when (id) {
        is TypeId.Builtin -> id.key == NEVER
        is TypeId.Applied -> (id.constructor as? TypeId.Builtin)?.key in setOf(OBJECT, INTERSECTION)
        is TypeId.Specialized -> id.constructor == TypeId.Builtin(LITERAL)
        else -> false
    }

    /** Restore reserved identities through existing Applied/Specialized library wire formats. */
    fun restore(id: TypeId, resolve: (TypeId) -> MCFPPType?): MCFPPType? = when {
        id == MCFPPNeverType.typeId -> MCFPPNeverType
        id is TypeId.Specialized && id.constructor == TypeId.Builtin(LITERAL) -> {
            val primitive = (id.arguments.getOrNull(0) as? CompilerValue.TypeValue)?.id?.let(resolve)
            if (primitive == null || id.arguments.size != 2) null else literalOrNull(primitive, id.arguments[1])
        }
        id is TypeId.Applied && id.constructor == TypeId.Builtin(OBJECT) -> {
            val fields = arrayListOf<ObjectFieldContract>()
            var valid = true
            for (entry in id.arguments) {
                val field = entry as? TypeId.Applied
                val name = (field?.arguments?.getOrNull(0) as? TypeId.Builtin)?.key
                val fieldType = field?.arguments?.getOrNull(1)?.let(resolve)
                val presence = field?.arguments?.getOrNull(2) as? TypeId.Builtin
                if (field == null || field.constructor != TypeId.Builtin(FIELD) || field.arguments.size != 3 || name == null ||
                    fieldType == null || presence?.key !in setOf(REQUIRED, OPTIONAL)) { valid = false; break }
                fields.add(ObjectFieldContract(name, fieldType, presence?.key == OPTIONAL))
            }
            if (valid) MCFPPObjectType(fields) else null
        }
        id is TypeId.Applied && id.constructor == TypeId.Builtin(INTERSECTION) -> {
            val parts = id.arguments.map(resolve)
            if (parts.isEmpty() || parts.any { it == null }) null else intersection(*parts.filterNotNull().toTypedArray())
        }
        else -> null
    }
}
