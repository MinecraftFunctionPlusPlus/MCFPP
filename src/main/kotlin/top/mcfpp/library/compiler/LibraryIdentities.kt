package top.mcfpp.library.compiler

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.core.lang.context.ContextTypes
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.format.TypeTableCodec
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.type.*
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import java.util.Collections

data class LibraryIdentity(val library: PackageKey, val classifier: String)

data class LibraryDeclarationIdentity(
    val id: DeclarationId,
    val key: DeclarationKey? = null,
    val origin: AnonymousOrigin? = null,
    /** Deterministic owner/semantic child path for a private non-anonymous declaration. */
    val privatePath: List<String>? = null,
) {
    init { require(listOf(key, origin, privatePath).count { it != null } == 1) { "Supply exactly one stable declaration locator" } }
}

/** Shell allocation is separate from binding, so A→B→A declaration references are legal. */
class DeclarationShell<D : Any> internal constructor(
    val library: LibraryIdentity,
    val declaration: LibraryDeclarationIdentity,
) {
    val sessionIdentity: TypeId.Opaque = TypeId.fresh() as TypeId.Opaque
    private var value: D? = null
    val isBound: Boolean get() = value != null

    fun bind(value: D) {
        check(this.value == null || this.value === value) { "Declaration shell is already bound" }
        if (value is top.mcfpp.model.compound.CompoundData) value.bindSessionIdentity(sessionIdentity)
        this.value = value
    }

    fun binding(): D = checkNotNull(value) { "Declaration ${declaration.id.value} is not bound yet" }

    internal fun checkpointForReuse(): () -> Unit {
        val saved = value
        return { value = saved }
    }
}

/** One instance belongs to one import session, never to GlobalScope or a process-wide cache. */
class LibraryIdentities<D : Any> {
    private data class Registered<D : Any>(
        val dependencies: List<LibraryIdentity>,
        val declarations: Map<DeclarationId, DeclarationShell<D>>,
        val publicKeys: Map<List<Byte>, DeclarationShell<D>>,
    )
    private val libraries = LinkedHashMap<LibraryIdentity, Registered<D>>()
    private val parameters = HashMap<Triple<DeclarationShell<D>, ParameterRole, Long>, TypeId.Opaque>()
    private val locals = HashMap<Pair<LibraryIdentity, TypeRef>, TypeId.Opaque>()

    fun register(
        library: LibraryIdentity,
        dependencies: List<LibraryIdentity>,
        declarations: List<LibraryDeclarationIdentity>,
    ): List<DeclarationShell<D>> {
        check(library !in libraries) { "Library is already registered: $library" }
        require(dependencies.distinct().size == dependencies.size) { "Duplicate dependency identity" }
        require(declarations.map { it.id }.distinct().size == declarations.size) { "Duplicate local declaration id" }
        val shells = declarations.map { DeclarationShell<D>(library, it) }
        val public = LinkedHashMap<List<Byte>, DeclarationShell<D>>()
        val origins = HashSet<List<Byte>>()
        shells.forEach { shell ->
            val identity = locatorBytes(shell.declaration).toList()
            require(origins.add(identity)) { "Duplicate declaration locator" }
            shell.declaration.key?.let { key ->
                require(public.put(TypeTableCodec.declarationKeyBytes(key).toList(), shell) == null) { "Duplicate public declaration key" }
            }
        }
        libraries[library] = Registered(Collections.unmodifiableList(ArrayList(dependencies)),
            shells.associateBy { it.declaration.id }, public)
        return Collections.unmodifiableList(shells)
    }

    fun resolve(library: LibraryIdentity, reference: DeclarationReference): DeclarationShell<D> {
        val source = checkNotNull(libraries[library]) { "Library is not registered: $library" }
        return when (reference) {
            is DeclarationReference.Local -> requireNotNull(source.declarations[reference.target]) {
                "Unknown local declaration ${reference.target.value} in $library"
            }
            is DeclarationReference.Dependency -> {
                require(reference.dependencyIndex <= source.dependencies.size.toLong()) { "Dependency reference is out of bounds" }
                val target = source.dependencies[(reference.dependencyIndex - 1).toInt()]
                val registered = checkNotNull(libraries[target]) { "Dependency has not been registered: $target" }
                requireNotNull(registered.publicKeys[TypeTableCodec.declarationKeyBytes(reference.key).toList()]) {
                    "Unresolved dependency declaration ${reference.key.namespace}:${reference.key.name} in $target"
                }
            }
        }
    }

    fun parameter(library: LibraryIdentity, owner: DeclarationReference, role: ParameterRole, index: Long): TypeId.Opaque {
        require(index in 0..0xffff_ffffL)
        return parameters.getOrPut(Triple(resolve(library, owner), role, index)) { TypeId.fresh() as TypeId.Opaque }
    }

    /** For a remaining private opaque type explicitly registered by its owning table reference. */
    fun localType(library: LibraryIdentity, reference: TypeRef): TypeId.Opaque {
        check(library in libraries) { "Library is not registered: $library" }
        return locals.getOrPut(library to reference) { TypeId.fresh() as TypeId.Opaque }
    }

    fun canonicalBindings(
        library: LibraryIdentity,
        nodeKey: (NodeRef) -> ByteArray = { error("Semantic node key is not registered: $it") },
        environmentKey: (EnvironmentId) -> ByteArray = { error("Lexical environment key is not registered: $it") },
    ): CanonicalIdentityBindings = object : CanonicalIdentityBindings {
        override fun localDeclaration(reference: DeclarationId): ByteArray =
            locatorBytes(resolve(library, DeclarationReference.Local(reference)).declaration)
        override fun dependency(index: Long): ByteArray {
            val source = checkNotNull(libraries[library])
            require(index in 1..source.dependencies.size.toLong()) { "Dependency reference is out of bounds" }
            return libraryBytes(source.dependencies[(index - 1).toInt()])
        }
        override fun node(reference: NodeRef): ByteArray = nodeKey(reference)
        override fun environment(reference: EnvironmentId): ByteArray = environmentKey(reference)
    }

    fun clear() { parameters.clear(); locals.clear(); libraries.clear() }

    internal fun checkpointForReuse(): () -> Unit {
        val state = top.mcfpp.library.api.ReusableState()
        state.map(parameters); state.map(locals)
        libraries.values.forEach { registered ->
            registered.declarations.values.forEach { state.add(it.checkpointForReuse()) }
        }
        return state::restore
    }

    private fun locatorBytes(value: LibraryDeclarationIdentity): ByteArray = LibraryBinaryWriter().apply {
        when {
            value.key != null -> tagged(1) { TypeTableCodec.writeDeclarationKey(this, value.key) }
            value.origin != null -> tagged(2) { TypeTableCodec.writeAnonymousOrigin(this, value.origin) }
            else -> tagged(3) { list(requireNotNull(value.privatePath)) { string(it) } }
        }
    }.toByteArray()

    private fun libraryBytes(value: LibraryIdentity): ByteArray = LibraryBinaryWriter().apply {
        TypeTableCodec.writePackage(this, value.library); string(value.classifier)
    }.toByteArray()
}

/** Source-to-protocol names are enumerated here, never obtained from a display name or reflection. */
object LibraryBuiltinTypes {
    private val values: Map<String, MCFPPType> by lazy {
        linkedMapOf(
            "object" to MCFPPBaseType.Object, "any" to MCFPPBaseType.Any,
            "int" to MCFPPBaseType.Int, "float" to MCFPPBaseType.Float, "bool" to MCFPPBaseType.Bool,
            "string" to MCFPPBaseType.String, "text" to MCFPPBaseType.JsonText, "range" to MCFPPBaseType.Range,
            "pos2" to MCFPPBaseType.Pos2, "pos3" to MCFPPBaseType.Pos3,
            "nbt" to MCFPPNBTType.NBT, "byte" to MCFPPNBTType.Byte, "short" to MCFPPNBTType.Short,
            "long" to MCFPPNBTType.Long, "double" to MCFPPNBTType.Double,
            "ByteArray" to MCFPPNBTType.ByteArray, "IntArray" to MCFPPNBTType.IntArray, "LongArray" to MCFPPNBTType.LongArray,
            "Type" to MCFPPConcreteType.Type, "java" to MCFPPConcreteType.JavaVar,
            "void" to MCFPPPrivateType.Void, "null" to MCFPPPrivateType.Null, "wildcard" to MCFPPPrivateType.Wildcard,
            "static-member-view" to MCFPPPrivateType.StaticMemberViewType,
            "coordinate.dimension" to MCFPPPrivateType.MCFPPCoordinateDimension,
            "entity" to MCFPPEntityType.NormalSelector, "ScoreRef" to MCFPPScoreRefType,
            "DataObject" to DataTemplate.baseDataTemplate.getType(),
            "context.execution" to ContextTypes.ExecutionContext, "context.pos" to ContextTypes.PosContext,
            "context.entity" to ContextTypes.EntityContext, "context.relation" to ContextTypes.RelationContext,
            "context.position" to ContextTypes.Position,
        )
    }
    private val constructors = setOf("list", "ImmutableList", "map", "dict", "NBTRef")
    fun protocolKey(id: TypeId): String? = values.entries.firstOrNull { it.value.typeId == id }?.key
        ?: (id as? TypeId.Builtin)?.key?.takeIf { it in constructors || it.removePrefix("constructor:") in constructors && it.startsWith("constructor:") }

    fun restore(key: String): MCFPPType = values[key] ?: when {
        key in constructors -> requireNotNull(MCFPPType.nativeConstructor(key))
        key.startsWith("constructor:") && key.removePrefix("constructor:") in constructors ->
            requireNotNull(MCFPPType.nativeConstructor(key.removePrefix("constructor:")))
        key.startsWith("vec") && key.drop(3).matches(Regex("[1-9][0-9]*")) ->
            MCFPPVectorType(requireNotNull(key.drop(3).toIntOrNull()) { "Vector dimension exceeds the compiler range" })
        else -> error("LIB_TYPE_UNSUPPORTED: Unregistered builtin key $key")
    }
}

/** Declaration capture/import supplies identities and actual models without global name lookup. */
interface CompilerTypeBindings {
    fun declaration(id: TypeId): DeclarationReference? = null
    fun parameter(id: TypeId.Opaque): LibraryType.Parameter? = null
    fun dependent(id: TypeId.Opaque): LibraryType.Dependent? = null
    fun restoreDeclaration(reference: DeclarationReference): MCFPPType = error("Declaration type binding is not registered: $reference")
    fun restoreParameter(value: LibraryType.Parameter): MCFPPType = error("Parameter owner binding is not registered: $value")
    fun restoreDependent(value: LibraryType.Dependent): MCFPPType = error("Dependent tree binding is not registered: $value")
    fun specialize(constructor: MCFPPType, arguments: List<CompilerValue>): MCFPPType =
        error("Readonly specialization binding is not registered")
    fun apply(constructor: MCFPPType, arguments: List<MCFPPType>): MCFPPType =
        error("Applied type constructor binding is not registered")
    fun intersection(components: List<MCFPPType>, declaration: DeclarationId?): MCFPPType =
        error("Intersection declaration binding is not registered")
    fun functionConstant(value: ConstantValue.FunctionValue, restore: (ConstantRef) -> CompilerValue): CompilerValue =
        error("Function capture binding is not registered")
}

interface CompilerTypeCapture {
    fun type(value: MCFPPType): TypeRef
    fun identity(id: TypeId): TypeRef
    fun constant(value: CompilerValue): ConstantRef
    fun snapshot(): LibraryTables
}

/** Captures complete type/immutable-value closures, including types reachable only through values. */
class CompilerTypeTableCapture(private val bindings: CompilerTypeBindings) : CompilerTypeCapture {
    private val types = ArrayList<LibraryType>()
    private val constants = ArrayList<ConstantValue>()
    private val typeReferences = HashMap<TypeId, TypeRef>()
    private val constantReferences = HashMap<CompilerValue, ConstantRef>()
    private val active = HashSet<TypeId>()

    override fun type(value: MCFPPType): TypeRef = if (value is MCFPPTypeAliasType)
        type(requireNotNull(value.cachedTarget) { "Resolve aliases before library capture" }) else identity(value.typeId)

    override fun identity(id: TypeId): TypeRef {
        typeReferences[id]?.let { return it }
        check(active.add(id)) { "Recursive structural identity requires a declaration shell" }
        val value: LibraryType = LibraryBuiltinTypes.protocolKey(id)?.let { LibraryType.Builtin(it) } ?: when (id) {
            is TypeId.Builtin -> LibraryType.Builtin(requireNotNull(LibraryBuiltinTypes.protocolKey(id)) {
                "LIB_TYPE_UNSUPPORTED: No protocol mapping for builtin ${id.key}"
            })
            is TypeId.Declaration -> LibraryType.Declaration(requireNotNull(bindings.declaration(id)) {
                "Register the owning declaration for ${id.namespace}:${id.name}"
            })
            is TypeId.Opaque -> bindings.parameter(id) ?: bindings.dependent(id)
                ?: bindings.declaration(id)?.let { LibraryType.Declaration(it) }
                ?: error("Register an owner/parameter/tree for opaque identity before exporting it")
            is TypeId.Applied -> applied(id)
            is TypeId.Specialized -> LibraryType.Specialized(identity(id.constructor), id.arguments.map(::constant))
            is TypeId.Union -> LibraryType.Union(id.alternatives.map(::identity))
            is TypeId.Selector -> error("Legacy entity subtype metadata cannot be exported")
        }
        val reference = TypeRef(types.size + 1L)
        types.add(value); typeReferences[id] = reference; active.remove(id)
        return reference
    }

    private fun applied(id: TypeId.Applied): LibraryType {
        val key = (id.constructor as? TypeId.Builtin)?.key
        if (key == "vector") {
            val dimension = (id.arguments.singleOrNull() as? TypeId.Builtin)?.key
            require(dimension != null && dimension.matches(Regex("[1-9][0-9]*"))) { "Invalid vector identity" }
            return LibraryType.Builtin("vec$dimension")
        }
        if (key == TypeId.FUNCTION_CONSTRUCTOR) {
            require(id.arguments.size == 3)
            val marker = (id.arguments[0] as? TypeId.Builtin)?.key
            val execution = when (marker) {
                "func.execution:normal" -> FunctionExecution.NORMAL
                "func.execution:paused" -> FunctionExecution.PAUSED
                "func.execution:generator" -> FunctionExecution.GENERATOR
                else -> error("Invalid function execution identity")
            }
            val parameters = id.arguments[1] as? TypeId.Applied ?: error("Invalid function parameter identity")
            require(parameters.constructor == TypeId.Builtin(TypeId.FUNCTION_PARAMETERS))
            return LibraryType.Function(execution, parameters.arguments.map(::identity), identity(id.arguments[2]))
        }
        if (key == "NBTRef") return LibraryType.NbtRef(identity(id.arguments.single()))
        return LibraryType.Applied(identity(id.constructor), id.arguments.map(::identity))
    }

    override fun constant(value: CompilerValue): ConstantRef {
        constantReferences[value]?.let { return it }
        val record = when (value) {
            CompilerValue.NullValue -> ConstantValue.Null
            is CompilerValue.Integral -> ConstantValue.Integral(value.value)
            is CompilerValue.FloatBits -> ConstantValue.FloatBits(value.bits)
            is CompilerValue.DoubleBits -> ConstantValue.DoubleBits(value.bits)
            is CompilerValue.Bool -> ConstantValue.Bool(value.value)
            is CompilerValue.Text -> ConstantValue.Text(value.value)
            is CompilerValue.TypeValue -> ConstantValue.TypeValue(identity(value.id))
            is CompilerValue.Nbt -> ConstantValue.Nbt(NbtEncoding.snbt(NbtEncoding.parse(value.snbt)))
            is CompilerValue.Sequence -> ConstantValue.Sequence(value.elements.map(::constant))
            is CompilerValue.Record -> ConstantValue.Record(value.fields.entries.sortedWith { a, b -> compareUtf8(a.key, b.key) }
                .map { ConstantField(it.key, constant(it.value)) })
            is CompilerValue.Typed -> ConstantValue.Typed(identity(value.type), constant(value.payload))
        }
        val reference = ConstantRef(constants.size + 1L)
        constants.add(record); constantReferences[value] = reference
        return reference
    }

    override fun snapshot(): LibraryTables = LibraryTables(types, constants)
    fun finish(identities: CanonicalIdentityBindings): CanonicalLibraryTables = TypeTableCodec.canonicalize(snapshot(), identities)
}

/** Restores compiler types only through registered language constructors and explicit bindings. */
class CompilerTypeTableRestore(private val tables: LibraryTables, private val bindings: CompilerTypeBindings) {
    private val types = HashMap<TypeRef, MCFPPType>()
    private val identities = HashMap<TypeRef, TypeId>()
    private val constants = HashMap<ConstantRef, CompilerValue>()
    private val activeTypes = HashSet<TypeRef>()
    private val activeConstants = HashSet<ConstantRef>()

    init { TypeTableCodec.validate(tables) }

    internal fun checkpointForReuse(): () -> Unit {
        check(activeTypes.isEmpty() && activeConstants.isEmpty()) { "Cannot checkpoint an active type restoration" }
        val state = top.mcfpp.library.api.ReusableState()
        state.map(types); state.map(identities); state.map(constants)
        state.set(activeTypes); state.set(activeConstants)
        return state::restore
    }

    fun type(reference: TypeRef): MCFPPType {
        types[reference]?.let { return it }
        check(activeTypes.add(reference)) { "Recursive type must resolve through a registered declaration shell" }
        val result = when (val value = tables.type(reference)) {
            is LibraryType.Builtin -> LibraryBuiltinTypes.restore(value.key)
            is LibraryType.Declaration -> bindings.restoreDeclaration(value.target)
            is LibraryType.Parameter -> bindings.restoreParameter(value)
            is LibraryType.Dependent -> bindings.restoreDependent(value)
            is LibraryType.Applied -> {
                val constructor = tables.type(value.constructor) as? LibraryType.Builtin
                val arguments = value.arguments.map(::type)
                when (constructor?.key) {
                    "list", "constructor:list" -> MCFPPListType(arguments.single())
                    "ImmutableList", "constructor:ImmutableList" -> MCFPPImmutableListType(arguments.single())
                    "dict", "constructor:dict" -> MCFPPDictType(arguments.single())
                    "map", "constructor:map" -> MCFPPMapType(arguments.single())
                    "NBTRef", "constructor:NBTRef" -> MCFPPNBTRefType(arguments.single())
                    else -> bindings.apply(type(value.constructor), arguments)
                }
            }
            is LibraryType.Specialized -> bindings.specialize(type(value.constructor), value.arguments.map(::constant))
            is LibraryType.Union -> MCFPPUnionType(*value.alternatives.map(::type).toTypedArray())
            is LibraryType.Intersection -> bindings.intersection(value.components.map(::type), value.declaration)
            is LibraryType.Function -> MCFPPFunctionType(value.parameters.map(::type), type(value.result), when (value.execution) {
                FunctionExecution.NORMAL -> MCFPPFunctionType.ExecutionCategory.NORMAL
                FunctionExecution.PAUSED -> MCFPPFunctionType.ExecutionCategory.PAUSED
                FunctionExecution.GENERATOR -> MCFPPFunctionType.ExecutionCategory.GENERATOR
            })
            is LibraryType.NbtRef -> MCFPPNBTRefType(type(value.value))
            is LibraryType.UnresolvedNominal -> error("LIB_TYPE_UNRESOLVED: Pending nominal identity ${value.kind} ${value.namespace}:${value.name} requires an actual declaration before materialization")
        }
        activeTypes.remove(reference); types[reference] = result
        return result
    }

    fun constant(reference: ConstantRef): CompilerValue {
        constants[reference]?.let { return it }
        check(activeConstants.add(reference)) { "Cycle in compiler constant graph" }
        val result: CompilerValue = when (val value = tables.constant(reference)) {
            ConstantValue.Null -> CompilerValue.NullValue
            is ConstantValue.Integral -> CompilerValue.Integral(value.value)
            is ConstantValue.FloatBits -> CompilerValue.FloatBits(value.bits)
            is ConstantValue.DoubleBits -> CompilerValue.DoubleBits(value.bits)
            is ConstantValue.Bool -> CompilerValue.Bool(value.value)
            is ConstantValue.Text -> CompilerValue.Text(value.value)
            is ConstantValue.TypeValue -> CompilerValue.TypeValue(identity(value.value))
            is ConstantValue.Nbt -> CompilerValue.Nbt(value.snbt)
            is ConstantValue.Sequence -> CompilerValue.Sequence(value.elements.map(::constant))
            is ConstantValue.Record -> CompilerValue.Record(value.fields.associate { it.name to constant(it.value) })
            is ConstantValue.Typed -> CompilerValue.Typed(identity(value.type), constant(value.value))
            is ConstantValue.FunctionValue -> bindings.functionConstant(value, ::constant)
        }
        activeConstants.remove(reference); constants[reference] = result
        return result
    }

    /** Restore identity data without specializing a generic declaration or executing a body. */
    fun identity(reference: TypeRef): TypeId = identities.getOrPut(reference) { when (val value = tables.type(reference)) {
        is LibraryType.Builtin -> if (value.key in setOf("list", "ImmutableList", "map", "dict", "NBTRef") || value.key.startsWith("constructor:"))
            TypeId.Builtin(value.key) else LibraryBuiltinTypes.restore(value.key).typeId
        is LibraryType.Declaration -> bindings.restoreDeclaration(value.target).typeId
        is LibraryType.UnresolvedNominal -> TypeId.Declaration(value.kind, value.namespace, value.name)
        is LibraryType.Parameter -> bindings.restoreParameter(value).typeId
        is LibraryType.Dependent -> bindings.restoreDependent(value).typeId
        is LibraryType.Applied -> TypeId.Applied(identity(value.constructor), value.arguments.map(::identity))
        is LibraryType.Specialized -> TypeId.Specialized(identity(value.constructor), value.arguments.map(::constant))
        is LibraryType.Union -> TypeId.Union(value.alternatives.map(::identity).toSet())
        is LibraryType.Intersection -> value.declaration?.let { bindings.restoreDeclaration(DeclarationReference.Local(it)).typeId }
            ?: UnionDataTemplate.identityFor(value.components.map(::identity))
        is LibraryType.Function -> TypeId.function(value.parameters.map(::identity), identity(value.result), when (value.execution) {
            FunctionExecution.NORMAL -> "normal"; FunctionExecution.PAUSED -> "paused"; FunctionExecution.GENERATOR -> "generator"
        })
        is LibraryType.NbtRef -> TypeId.Applied(TypeId.Builtin("NBTRef"), listOf(identity(value.value)))
    } }
}
