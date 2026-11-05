package top.mcfpp.model.scope

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.OnScoreboard
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.Generic
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.Property
import top.mcfpp.type.*

/**
 * 一个域，储存了字段和方法。
 *
 */
class CompoundDataScope(parent: ArrayList<IScope?>) :
    SimpleScopeWithFunction, IScopeWithVar,
    IScopeWithType, IScopeWithProperty,
    SimpleScopeWithOperator {

    internal var deferredMembers: top.mcfpp.model.compound.DeferredLibraryMembers? = null
    internal fun ensureMembers() { deferredMembers?.materialize() }
    /** Enumerating already-owned functions for cleanup/journaling must never load unused members. */
    internal val registeredProperties: Collection<Property> get() = property.values

    /**
     * 字段
     */
    private val vars: HashMap<String, Var<*>> = HashMap()

    /** Immutable slot facts shared by real layout and pure header inheritance. */
    internal data class FieldContract(val typeId: TypeId, val nullable: Boolean, val isStatic: Boolean,
                                      val isConst: Boolean, val access: Member.AccessModifier, val isFinal: Boolean) {
        fun differences(other: FieldContract): List<String> = buildList {
            if (typeId != other.typeId) add("typeId")
            if (nullable != other.nullable) add("nullable")
            if (isStatic != other.isStatic) add("static")
            if (isConst != other.isConst) add("const")
        }

        fun replacementDifferences(local: FieldContract): List<String> = differences(local) + buildList {
            if (local.access > access) add("access")
            if (isFinal) add("final")
        }
    }

    internal class FieldCandidate<T>(val owner: TypeId, val ancestors: Set<TypeId>,
                                     val contract: FieldContract, val value: T)
    internal class FieldMerge<T>(val origins: List<FieldCandidate<T>>, val effective: List<FieldCandidate<T>>,
                                 val conflicts: List<String>)

    /** Input order is retained for diagnostics, never used to resolve an unrelated tie. */
    internal object InheritedFields {
        fun <T> merge(paths: List<FieldCandidate<T>>): FieldMerge<T> {
            val origins = paths.distinctBy { it.owner }
            val conflicts = origins.flatMapIndexed { index, origin ->
                origins.drop(index + 1).flatMap { origin.contract.differences(it.contract) }
            }.distinct()
            val effective = origins.filter { base -> origins.none { derived ->
                derived.owner != base.owner && base.owner in derived.ancestors && derived.owner !in base.ancestors
            } }
            return FieldMerge(origins, effective, conflicts)
        }

        fun ancestors(owner: CompoundData): Set<TypeId> {
            val result = linkedSetOf<TypeId>()
            val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
            val pending = ArrayDeque<CompoundData>()
            pending.addAll(owner.parent)
            while (pending.isNotEmpty()) {
                val parent = pending.removeFirst()
                if (!visited.add(parent)) continue
                result.add(parent.inheritanceIdentity)
                pending.addAll(parent.parent)
            }
            if (owner is DataTemplate) result.add(DataTemplate.baseDataTemplate.inheritanceIdentity)
            return result
        }
    }

    /** Declaration provenance survives flattening; a copied field is still the same bound slot. */
    internal data class FieldOrigin(val owner: CompoundData, val field: Var<*>, val property: Property?) {
        val typeId = field.type.typeId
        val nullable = field.nullable
        val isStatic = field.isStatic
        val isConst = field.isConst
        val access = field.accessModifier
        val isFinal = field.isFinal || property?.isFinal == true

        val contract = FieldContract(typeId, nullable, isStatic, isConst, access, isFinal)
        fun differences(other: FieldOrigin): List<String> = contract.differences(other.contract)
    }

    internal val fieldOrigins = linkedMapOf<String, List<FieldOrigin>>()

    /**
     * 类型
     */
    private val types : HashMap<String, MCFPPType> = HashMap()

    /**
     * 属性
     */
    private val property: HashMap<String, Property> = HashMap()

    /**
     * 方法
     */
    override var functions: HashMap<String, ArrayList<Function>> = HashMap()

    override var operators: HashMap<String, HashMap<MCFPPType?, Function>> = HashMap()

    /**
     * 父级域。
     */
    @Nullable
    override var parent: ArrayList<IScope?>

    init {
        this.parent = parent
    }

    /** Structural state only; adapters, functions and declaration objects have separate journals. */
    internal class JournalElements internal constructor(
        variables: List<Var<*>>, types: List<MCFPPType>, properties: List<Property>,
        functions: List<Function>, parents: List<IScope>, originOwners: List<CompoundData>
    ) {
        val variables: List<Var<*>> = java.util.Collections.unmodifiableList(ArrayList(variables))
        val types: List<MCFPPType> = java.util.Collections.unmodifiableList(ArrayList(types))
        val properties: List<Property> = java.util.Collections.unmodifiableList(ArrayList(properties))
        val functions: List<Function> = java.util.Collections.unmodifiableList(ArrayList(functions))
        val parents: List<IScope> = java.util.Collections.unmodifiableList(ArrayList(parents))
        val originOwners: List<CompoundData> = java.util.Collections.unmodifiableList(ArrayList(originOwners))
    }

    internal class JournalSnapshot internal constructor(
        val elements: JournalElements,
        private val restoreState: () -> Unit,
        private val recapture: () -> JournalSnapshot
    ) {
        fun restore() = restoreState()
        fun captureCurrent(): JournalSnapshot = recapture()
    }

    internal fun checkpointRelations(): JournalSnapshot = captureRelations(emptyList(), null).snapshot

    /** Current registrations only. Identity leaves never consult an adapter's equality or hash.
     * Share the checkpoint's raw fields and role validation, without evaluating type identities.
     */
    internal fun preparationRelations(): RawPreparationFact =
        captureRelations(emptyList(), null, preparationOnly = true).preparationRelations

    private class RelationCapture(val snapshot: JournalSnapshot, val preparationRelations: RawPreparationFact)

    private fun captureRelations(retained: List<JournalNode>, previous: JournalElements?,
                                 preparationOnly: Boolean = false): RelationCapture {
        val savedFunctions = functions
        val savedOperators = operators
        val savedParent = parent
        val savedDeferred = deferredMembers
        val roots = listOf(
            JournalMap(vars, MapKind.VARIABLE), JournalMap(types, MapKind.TYPE),
            JournalMap(property, MapKind.PROPERTY), JournalMap(functions, MapKind.OVERLOADS),
            JournalMap(operators, MapKind.OPERATORS), JournalMap(fieldOrigins, MapKind.ORIGINS),
            JournalList(parent, ListKind.PARENTS)
        )
        val pending = java.util.ArrayDeque<JournalNode>().apply { addAll(roots); addAll(retained) }
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val mapRoles = java.util.IdentityHashMap<MutableMap<*, *>, MutableSet<MapKind>>()
        val listRoles = java.util.IdentityHashMap<List<*>, MutableSet<ListKind>>()
        val nodes = arrayListOf<JournalNode>()
        val restores = arrayListOf<() -> Unit>()
        val keyPreflights = arrayListOf<() -> Unit>()
        val relations = arrayListOf<RawPreparationFact>()
        val savedVariables = previous?.variables?.toMutableList() ?: arrayListOf()
        val savedTypes = previous?.types?.toMutableList() ?: arrayListOf()
        val savedProperties = previous?.properties?.toMutableList() ?: arrayListOf()
        val savedMembers = previous?.functions?.toMutableList() ?: arrayListOf()
        val savedParents = previous?.parents?.toMutableList() ?: arrayListOf()
        val savedOwners = previous?.originOwners?.toMutableList() ?: arrayListOf()
        fun origin(value: FieldOrigin) {
            savedOwners.add(value.owner); savedVariables.add(value.field)
            value.property?.let(savedProperties::add)
        }
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            val identity = when (node) {
                is JournalMap -> node.table
                is JournalList -> node.items
            }
            // One container can occupy several typed slots through erased host code. Each role
            // must validate its contents, while its physical state is saved/restored only once.
            val newRole = when (node) {
                is JournalMap -> mapRoles.getOrPut(node.table) { hashSetOf() }.add(node.kind)
                is JournalList -> listRoles.getOrPut(node.items) { hashSetOf() }.add(node.kind)
            }
            if (!newRole) continue
            val saveState = seen.add(identity)
            nodes.add(node)
            when (node) {
                is JournalMap -> {
                    check(node.table.javaClass in journalMapClasses) {
                        "Compound scope journal cannot capture map ${node.table.javaClass.name}"
                    }
                    @Suppress("UNCHECKED_CAST")
                    val table = node.table as MutableMap<Any?, Any?>
                    // Keep entries without hashing erased keys or invoking a type's open typeId getter.
                    val entries = table.entries.map { it.key to it.value }
                    for ((key, value) in entries) {
                        if (node.kind == MapKind.FUNCTION) {
                            check(key == null || key is MCFPPType) { "Compound scope journal found an invalid operator key" }
                            (key as MCFPPType?)?.let { journalHashType(it, savedTypes, inspectIdentity = !preparationOnly) }
                        } else check(key is String) { "Compound scope journal found a non-string registration key" }
                        check(when (node.kind) {
                            MapKind.VARIABLE -> value is Var<*>
                            MapKind.TYPE -> value is MCFPPType
                            MapKind.PROPERTY -> value is Property
                            MapKind.OVERLOADS, MapKind.ORIGINS -> value is List<*>
                            MapKind.OPERATORS -> value is MutableMap<*, *>
                            MapKind.FUNCTION -> value is Function
                        }) { "Compound scope journal found an invalid ${node.kind} registration" }
                    }
                    relations.add(RawPreparationFact.row(RawPreparationFact.scalar("map.${node.kind.name}"),
                        RawPreparationFact.identity(node.table), RawPreparationFact.unordered(entries.map { (key, value) ->
                            RawPreparationFact.row(if (node.kind == MapKind.FUNCTION) RawPreparationFact.identity(key)
                                else RawPreparationFact.scalar(key), RawPreparationFact.identity(value))
                        })))
                    if (!preparationOnly && node.kind == MapKind.FUNCTION) keyPreflights += {
                        entries.forEach { (key, _) -> (key as MCFPPType?)?.let { journalHashType(it, arrayListOf()) } }
                    }
                    if (!preparationOnly && saveState) restores += { table.clear(); entries.forEach { (key, value) -> table[key] = value } }
                    for ((_, value) in entries) when (node.kind) {
                        MapKind.VARIABLE -> savedVariables.add(value as Var<*>)
                        MapKind.TYPE -> savedTypes.add(value as MCFPPType)
                        MapKind.PROPERTY -> savedProperties.add(value as Property)
                        MapKind.OVERLOADS -> pending.addLast(JournalList(value as List<*>, ListKind.FUNCTIONS))
                        MapKind.OPERATORS -> pending.addLast(JournalMap(value as MutableMap<*, *>, MapKind.FUNCTION))
                        MapKind.FUNCTION -> savedMembers.add(value as Function)
                        MapKind.ORIGINS -> pending.addLast(JournalList(value as List<*>, ListKind.ORIGINS))
                    }
                }
                is JournalList -> {
                    val shape = node.items.javaClass
                    val mutable = shape == java.util.ArrayList::class.java
                    val fixed = shape.name == "java.util.Arrays\$ArrayList"
                    val immutable = shape.name in journalImmutableLists
                    check(mutable || (node.kind == ListKind.ORIGINS && (fixed || immutable))) {
                        "Compound scope journal cannot capture list ${shape.name}"
                    }
                    val items = node.items.toList()
                    check(items.all { value -> when (node.kind) {
                        ListKind.PARENTS -> value == null || value is IScope
                        ListKind.FUNCTIONS -> value is Function
                        ListKind.ORIGINS -> value is FieldOrigin
                    } }) { "Compound scope journal found an invalid ${node.kind} list element" }
                    relations.add(RawPreparationFact.row(RawPreparationFact.scalar("list.${node.kind.name}"),
                        RawPreparationFact.identity(node.items), RawPreparationFact.sequence(items.map { RawPreparationFact.identity(it) })))
                    if (!preparationOnly && saveState && (mutable || fixed)) {
                        @Suppress("UNCHECKED_CAST")
                        val list = node.items as MutableList<Any?>
                        restores += {
                            if (mutable) { list.clear(); list.addAll(items) }
                            else items.forEachIndexed { index, value -> list[index] = value }
                        }
                    }
                    for (value in items) when (node.kind) {
                        ListKind.PARENTS -> (value as IScope?)?.let(savedParents::add)
                        ListKind.FUNCTIONS -> savedMembers.add(value as Function)
                        ListKind.ORIGINS -> origin(value as FieldOrigin)
                    }
                }
            }
        }
        val elements = JournalElements(
            journalIdentities(savedVariables), journalIdentities(savedTypes), journalIdentities(savedProperties),
            journalIdentities(savedMembers), journalIdentities(savedParents), journalIdentities(savedOwners)
        )
        val snapshot = JournalSnapshot(elements, {
            // The composing declaration journal restores mutable type identities before this map rehash.
            // Recheck their current dependency shapes before changing any captured relationship.
            keyPreflights.forEach { it() }
            functions = savedFunctions; operators = savedOperators; parent = savedParent; deferredMembers = savedDeferred
            restores.forEach { it() }
        }, { captureRelations(nodes, elements, preparationOnly).snapshot })
        return RelationCapture(snapshot, RawPreparationFact.unordered(relations))
    }

    private sealed interface JournalNode
    private class JournalMap(val table: MutableMap<*, *>, val kind: MapKind): JournalNode
    private class JournalList(val items: List<*>, val kind: ListKind): JournalNode
    private enum class MapKind { VARIABLE, TYPE, PROPERTY, OVERLOADS, OPERATORS, FUNCTION, ORIGINS }
    private enum class ListKind { PARENTS, FUNCTIONS, ORIGINS }

    private fun <T : Any> journalIdentities(values: List<T>): List<T> {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<T, Boolean>())
        return values.filter { seen.add(it) }
    }

    private fun journalHashType(
        type: MCFPPType, roots: MutableList<MCFPPType>,
        active: MutableSet<MCFPPType> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>()),
        inspectIdentity: Boolean = true
    ) {
        check(type.javaClass in journalHashTypes) {
            "Compound scope journal cannot hash type ${type.javaClass.name}"
        }
        check(active.add(type)) { "Compound scope journal cannot hash a cyclic generic type" }
        roots.add(type)
        try {
            if (type is MCFPPTypeWithGeneric) {
                val arguments = type.generic
                journalHashList(arguments)
                (arguments as List<*>).forEach {
                    check(it is MCFPPType) { "Compound scope journal found an invalid generic argument" }
                    journalHashType(it, roots, active, inspectIdentity)
                }
            }
            if (type is MCFPPUnionType) (type.types as Array<*>).forEach {
                check(it is MCFPPType) { "Compound scope journal found an invalid union alternative" }
                journalHashType(it, roots, active, inspectIdentity)
            }
            if (type is MCFPPEntityType) type.types?.let { entities ->
                journalHashList(entities)
                check((entities as List<*>).all { it is String }) { "Compound scope journal found an invalid entity identity" }
            }
            // Only exact compiler classes can reach this getter; their nested dependencies were checked above.
            if (inspectIdentity) journalHashIdentity(type.typeId)
        } finally { active.remove(type) }
    }

    private fun journalHashList(items: List<*>) {
        check(items.javaClass == java.util.ArrayList::class.java || items.javaClass == journalFixedList ||
            items.javaClass in journalHashImmutableLists) {
            "Compound scope journal cannot inspect identity list ${items.javaClass.name}"
        }
    }

    private fun journalHashIdentity(value: Any?) {
        // These sealed payload constructors own defensive copies; inspect erased members before hashing them.
        when (value) {
            is TypeId.Builtin, is TypeId.Declaration, is TypeId.Opaque -> Unit
            is TypeId.Applied -> {
                journalHashIdentity(value.constructor)
                (value.arguments as List<*>).forEach { check(it is TypeId); journalHashIdentity(it) }
            }
            is TypeId.Specialized -> {
                journalHashIdentity(value.constructor)
                (value.arguments as List<*>).forEach { check(it is top.mcfpp.analysis.CompilerValue); journalHashIdentity(it) }
            }
            is TypeId.Union -> (value.alternatives as Set<*>).forEach { check(it is TypeId); journalHashIdentity(it) }
            is TypeId.Selector -> check(value.entities == null || (value.entities as List<*>).all { it is String })
            top.mcfpp.analysis.CompilerValue.NullValue, is top.mcfpp.analysis.CompilerValue.Integral,
            is top.mcfpp.analysis.CompilerValue.FloatBits, is top.mcfpp.analysis.CompilerValue.DoubleBits,
            is top.mcfpp.analysis.CompilerValue.Bool, is top.mcfpp.analysis.CompilerValue.Text,
            is top.mcfpp.analysis.CompilerValue.Nbt -> Unit
            is top.mcfpp.analysis.CompilerValue.TypeValue -> journalHashIdentity(value.id)
            is top.mcfpp.analysis.CompilerValue.Typed -> { journalHashIdentity(value.type); journalHashIdentity(value.payload) }
            is top.mcfpp.analysis.CompilerValue.Sequence -> (value.elements as List<*>).forEach {
                check(it is top.mcfpp.analysis.CompilerValue); journalHashIdentity(it)
            }
            is top.mcfpp.analysis.CompilerValue.Record -> (value.fields as Map<*, *>).forEach { (key, item) ->
                check(key is String && item is top.mcfpp.analysis.CompilerValue); journalHashIdentity(item)
            }
            else -> error("Compound scope journal cannot hash an unsupported identity payload")
        }
    }

    private companion object {
        val journalMapClasses = setOf<Class<*>>(java.util.HashMap::class.java, java.util.LinkedHashMap::class.java)
        val journalImmutableLists = setOf(
            "kotlin.collections.EmptyList", "java.util.Collections\$EmptyList", "java.util.Collections\$SingletonList"
        )
        val journalFixedList = java.util.Arrays.asList("").javaClass
        val journalHashImmutableLists = setOf<Class<*>>(
            emptyList<Any>().javaClass, java.util.Collections.emptyList<Any>().javaClass,
            java.util.Collections.singletonList("").javaClass,
            java.util.List.of("").javaClass, java.util.List.of("", "", "").javaClass
        )
        val journalHashTypes = setOf<Class<*>>(
            MCFPPType::class.java, MCFPPConcreteType::class.java, MCFPPGenericParamType::class.java,
            UnresolvedType::class.java, MCFPPTypeAliasType::class.java, MCFPPNotCompiledGenericType::class.java,
            MCFPPDataTemplateType::class.java, MCFPPTypeDataTemplateType::class.java,
            MCFPPObjectDataTemplateType::class.java, MCFPPEnumType::class.java,
            MCFPPGenericDataTemplateType::class.java, MCFPPGenericObjectDataTemplateType::class.java,
            MCFPPUnionType::class.java, MCFPPVectorType::class.java,
            MCFPPEntityType::class.java, MCFPPEntityType.Player::class.java,
            MCFPPListType::class.java, MCFPPImmutableListType::class.java, MCFPPCompoundType::class.java,
            MCFPPDictType::class.java, MCFPPMapType::class.java,
            MCFPPBaseType.Object::class.java, MCFPPBaseType.Any::class.java, MCFPPBaseType.Int::class.java,
            MCFPPBaseType.String::class.java, MCFPPBaseType.Float::class.java, MCFPPBaseType.Bool::class.java,
            MCFPPBaseType.JsonText::class.java, MCFPPBaseType.Range::class.java,
            MCFPPBaseType.Pos2::class.java, MCFPPBaseType.Pos3::class.java,
            MCFPPConcreteType.Type::class.java, MCFPPConcreteType.JavaVar::class.java,
            MCFPPPrivateType.StaticMemberViewType::class.java, MCFPPPrivateType.MCFPPCoordinateDimension::class.java,
            MCFPPPrivateType.Void::class.java, MCFPPPrivateType.Null::class.java, MCFPPPrivateType.Wildcard::class.java,
            MCFPPNBTType.NBT::class.java, MCFPPNBTType.Byte::class.java, MCFPPNBTType.Short::class.java,
            MCFPPNBTType.Long::class.java, MCFPPNBTType.Double::class.java, MCFPPNBTType.ByteArray::class.java,
            MCFPPNBTType.IntArray::class.java, MCFPPNBTType.LongArray::class.java
        )
    }

    /**
     * 复制一个域。
     * @param field 原来的缓存
     */
    constructor(field: CompoundDataScope): this(field.parent) {
        field.ensureMembers()
        parent = field.parent
        //变量复制
        for (key in field.vars.keys) {
            val `var`: Var<*> = field.vars[key]!!
            vars[key] = `var`.clone()
        }
        //函数
        functions.putAll(field.functions)
        //类型
        types.putAll(field.types)
        //属性
        property.putAll(field.property)
    }

    /**
     * 遍历每一个字段
     *
     * @param action 要对字段执行的操作
     * @receiver
     */
    override fun forEachVar(action: (Var<*>) -> Unit){
        ensureMembers()
        for (`var` in vars.values){
            action(`var`)
        }
    }

    //region Var<*>
    override fun putVar(key: String, `var`: Var<*>, forced: Boolean): Boolean {
        ensureMembers()
        if(forced){
            `var`.bindDeclaration(key, vars[key])
            vars[key] = `var`
            return true
        }
        return if (vars.containsKey(key)) {
            false
        } else {
            `var`.bindDeclaration(key)
            vars[key] = `var`
            true
        }
    }

    override fun getVar(key: String): Var<*>? {
        ensureMembers()
        return vars.getOrDefault(key, null)
    }


    override val allVars: Collection<Var<*>>
        get() {
            ensureMembers()
            val vs = ArrayList<Var<*>>()
            for (lv in vars.values){
                vs.add(lv)
            }
            return vs
        }

    override fun containVar(id: String): Boolean {
        ensureMembers()
        return vars.containsKey(id)
    }

    override fun removeVar(id : String): Var<*>?{
        ensureMembers()
        return vars.remove(id)
    }

    //endregion

    //region type
    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        ensureMembers()
        if(forced){
            types[key] = type
            return true
        }
        return if (types.containsKey(key)) {
            false
        } else {
            types[key] = type
            true
        }
    }

    override fun getType(key: String): MCFPPType? {
        ensureMembers()
        if(types.containsKey(key)) return types[key]
        parent.forEach {
            if(it is IScopeWithType){
                val re = it.getType(key)
                if(re!=null) return re
            }
        }
        return null
    }

    override fun containType(id: String): Boolean {
        ensureMembers()
        return types.containsKey(id)
    }

    override fun removeType(id: String): MCFPPType? {
        ensureMembers()
        return types.remove(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        ensureMembers()
        for (t in types.values){
            action(t)
        }
    }

    override val allTypes: Collection<MCFPPType>
        get() { ensureMembers(); return types.values }

    //endregion

    //region Function
    @Nullable
    override fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        return top.mcfpp.model.function.ParameterMatcher.select(getFunctionCandidates(key, Function.currFunction), key, readOnlyArgs, normalArgs)
            ?: UnknownFunction(key)
    }

    /** Resolve all overloads together; only a declaration in a subtype hides a signature. */
    override fun getFunctionCandidates(key: String): List<Function> = getFunctionCandidates(key, null)

    /** A caller can see its lexical private methods on a derived receiver; they are never virtual overrides. */
    internal fun getFunctionCandidates(key: String, caller: Function?): List<Function> {
        val scopes = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundDataScope, Boolean>())
        val declarations = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        val pending = java.util.ArrayDeque<CompoundDataScope>()
        val candidates = arrayListOf<Function>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            val scope = pending.removeLast()
            if (!scopes.add(scope)) continue
            scope.ensureMembers()
            for (candidate in scope.functions[key].orEmpty()) {
                if (candidate.accessModifier >= Member.AccessModifier.PRIVATE) {
                    val owner = candidate.owner
                    val accessible = when {
                        caller == null -> scope === this
                        owner is DataTemplate -> caller.accessTo(owner) >= candidate.accessModifier
                        else -> owner != null && caller.owner === owner && candidate.accessModifier == Member.AccessModifier.PRIVATE
                    }
                    if (!accessible) continue
                }
                if (!declarations.add(candidate)) continue
                val owner = candidate.owner
                // Restored copies of the same bound owner still denote one declaration.
                if (owner != null && candidates.any {
                        it.owner?.inheritanceIdentity == owner.inheritanceIdentity &&
                            top.mcfpp.model.function.ParameterMatcher.sameSignature(it, candidate)
                    }) continue
                candidates.add(candidate)
            }
            scope.parent.asReversed().filterIsInstance<CompoundDataScope>().forEach(pending::addLast)
        }
        // A private declaration binds in its declaring environment even if the receiver's
        // subtype has an unrelated public declaration with that same signature.
        val visible = if (caller == null) candidates else candidates.filter { candidate ->
            candidate.accessModifier >= Member.AccessModifier.PRIVATE || candidates.none { lexical ->
                lexical.accessModifier >= Member.AccessModifier.PRIVATE &&
                    top.mcfpp.model.function.ParameterMatcher.sameSignature(lexical, candidate)
            }
        }
        return visible.filter { candidate ->
            visible.none { other ->
                val base = candidate.owner as? top.mcfpp.model.compound.CompoundData
                val derived = other.owner as? top.mcfpp.model.compound.CompoundData
                other !== candidate && base != null && derived != null &&
                    derived.inheritanceIdentity != base.inheritanceIdentity &&
                    derived.isSubOf(base) && !base.isSubOf(derived) &&
                    top.mcfpp.model.function.ParameterMatcher.sameSignature(other, candidate)
            }
        }
    }

    override fun getFunctionByTypes(key: String, normalArgs: List<MCFPPType>) =
        top.mcfpp.model.function.ParameterMatcher.selectTypes(getFunctionCandidates(key), key, normalArgs)

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function, false)){
            if(force){
                functions[function.identifier]!!.add(function)
                return true
            }
            return false
        }
        if(!functions.containsKey(function.identifier)){
            functions[function.identifier] = ArrayList()
        }
        functions[function.identifier]!!.add(function)
        return true
    }

    override fun hasFunction(function: Function, considerParent: Boolean): Boolean{
        ensureMembers()
        val candidates = if (considerParent) getFunctionCandidates(function.identifier) else functions[function.identifier].orEmpty()
        return candidates.any { top.mcfpp.model.function.ParameterMatcher.sameSignature(it, function) }
    }

    override fun forEachFunction(operation: (Function) -> Any?) {
        ensureMembers()
        super<SimpleScopeWithFunction>.forEachFunction(operation)
    }

    override fun removeFunction(function: Function) {
        ensureMembers()
        super<SimpleScopeWithFunction>.removeFunction(function)
    }

    override fun getOperator(identifier: String, type: MCFPPType?): Function? {
        ensureMembers()
        return super<SimpleScopeWithOperator>.getOperator(identifier, type)
    }

    override fun forEachOperator(operation: (Pair<String, Function>) -> Any?) {
        ensureMembers()
        super<SimpleScopeWithOperator>.forEachOperator(operation)
    }

    override fun addOperator(identifier: String, type: MCFPPType?, operator: Function, force: Boolean): Boolean {
        ensureMembers()
        return super<SimpleScopeWithOperator>.addOperator(identifier, type, operator, force)
    }

    override fun hasOperator(itf: Function): Boolean {
        ensureMembers()
        return super<SimpleScopeWithOperator>.hasOperator(itf)
    }

    override fun hasOperator(identifier: String, type: MCFPPType?): Boolean {
        ensureMembers()
        return super<SimpleScopeWithOperator>.hasOperator(identifier, type)
    }

    override fun removeOperator(identifier: String): MutableCollection<Function>? {
        ensureMembers()
        return super<SimpleScopeWithOperator>.removeOperator(identifier)
    }

    override fun removeOperator(identifier: String, type: MCFPPType?): Function? {
        ensureMembers()
        return super<SimpleScopeWithOperator>.removeOperator(identifier, type)
    }
    //endregion

    //region property
    override fun putProperty(key: String, property: Property, forced: Boolean): Boolean {
        ensureMembers()
        if(forced){
            this.property[key] = property
            return true
        }
        return if (this.property.containsKey(key)) {
            false
        } else {
            this.property[key] = property
            true
        }
    }

    override fun getProperty(key: String): Property? {
        ensureMembers()
        return property.getOrDefault<String, Property?>(key, null)
    }

    override fun containProperty(id: String): Boolean {
        ensureMembers()
        return property.containsKey(id)
    }

    override fun removeProperty(id: String): Property? {
        ensureMembers()
        return property.remove(id)
    }

    override fun forEachProperty(action: (Property) -> Unit) {
        ensureMembers()
        for (p in property.values){
            action(p)
        }
    }

    override val allProperties: Collection<Property>
        get() {
            ensureMembers()
            val ps = ArrayList<Property>()
            for (lv in property.values){
                ps.add(lv)
            }
            return ps
        }
    //endregion

    fun createDataTemplateInstance(selector: DataTemplateObject): CompoundDataScope {
        val re = CompoundDataScope(this)
        re.allVars.forEach {
            if (it.isStatic) return@forEach
            it.parent = selector
            if(it is OnScoreboard){
                it.name = selector.identifier + "_" + it.identifier
            }
            if (it is top.mcfpp.core.lang.MCFloat) {
                it.name = selector.identifier + "_" + it.identifier
                for (component in listOf(it.sign, it.int0, it.int1, it.exp)) component.name = it.name
            }
            if(it.nbtPath.pathList.isEmpty()) return@forEach
            it.nbtPath.pathList.removeLast()
            it.nbtPath.memberIndex(selector.identifier)
            it.nbtPath.memberIndex(it.identifier)
        }
        return re
    }
}
