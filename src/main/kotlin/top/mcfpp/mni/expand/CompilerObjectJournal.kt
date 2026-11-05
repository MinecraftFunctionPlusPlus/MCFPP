package top.mcfpp.mni.expand

import top.mcfpp.analysis.EffectAnalysis
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageCheckpoint
import top.mcfpp.analysis.StorageCheckpointCollector
import top.mcfpp.analysis.StoredData
import top.mcfpp.command.Command
import top.mcfpp.command.CommandList
import top.mcfpp.command.Comment
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.*
import top.mcfpp.lib.*
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.InternalFunctionScope
import top.mcfpp.model.scope.NoStackFunctionScope
import top.mcfpp.type.MCFPPEntityType
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Compiler-owned object graph savepoint, including lazy instance caches and active effect frames.
 * Enroll existing adapters/functions that a native call can reach. Declaration/type/annotation
 * internals and Project registries are owned by the global journal. Standard native closures are
 * retained as opaque immutable references: their compiler writes are journaled through enrolled
 * objects, but arbitrary captured host objects are outside this capability.
 */
internal class CompilerObjectJournal private constructor(
    private val enrolled: List<Any>,
    private val restores: List<() -> Unit>,
    private val scopes: List<Pair<CompoundDataScope, CompoundDataScope.JournalSnapshot>>,
    private val functions: FunctionStateJournal,
    private val storage: StorageCheckpoint,
    private val effects: EffectAnalysis.JournalSnapshot,
    private val scores: List<Pair<Var<*>, MCFPPEntityType.ScoresStateCheckpoint>>,
    elements: List<Any>
) {
    /** Retained relationship roots for declaration/type/annotation composition, without resolving them. */
    val elements: List<Any> = Collections.unmodifiableList(ArrayList(elements))

    fun restore() {
        scores.forEach { it.second.restore() }
        restores.forEach { it() }
        scopes.forEach { it.second.restore() }
        functions.restore()
        storage.restore()
        effects.restore()
    }

    /** Detached published caches, members, accessors and frames remain enrollment roots. */
    fun captureCurrent(): CompilerObjectJournal = capture(enrolled, scopes, functions, storage, effects, scores)

    internal fun captureCurrent(
        values: Iterable<Var<*>>, functions: Iterable<Function>, collector: StorageCheckpointCollector? = null,
        scopeSnapshots: IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>? = null
    ): CompilerObjectJournal = capture(enrolled + rootList(values) + rootList(functions), scopes, this.functions, storage, effects, scores, collector, scopeSnapshots)

    /** Legacy preparation runs once. Applying its captured result does not invoke native code. */
    fun prepare(action: () -> Unit): PreparedCompilerCommit {
        val caller = Function.currFunction
        val forced = Function.forcedField
        var failure: Throwable? = null
        val after: CompilerObjectJournal
        try {
            action()
            after = captureCurrent()
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        } finally {
            try {
                restore()
            } catch (rollback: Throwable) {
                if (failure != null) failure.addSuppressed(rollback) else throw rollback
            } finally {
                Function.currFunction = caller
                Function.forcedField = forced
            }
        }
        return object : PreparedCompilerCommit {
            override fun apply() = after.restore()
            override fun rollback() = restore()
        }
    }

    companion object {
        fun checkpoint(
            values: Iterable<Var<*>> = emptyList(),
            functions: Iterable<Function> = emptyList(),
            collector: StorageCheckpointCollector? = null,
            scopeSnapshots: IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>? = null
        ): CompilerObjectJournal = capture(rootList(values) + rootList(functions), emptyList(), null, null, null, emptyList(), collector, scopeSnapshots)

        private fun <T> rootList(values: Iterable<T>): List<T> {
            check(
                values.javaClass in setOf<Class<*>>(
                    ArrayList::class.java,
                    java.util.LinkedList::class.java,
                    HashSet::class.java,
                    LinkedHashSet::class.java
                ) ||
                    values.javaClass.name in fixedLists
            ) {
                "Object journal cannot traverse roots ${values.javaClass.name}"
            }
            return values.toList()
        }

        private fun capture(
            roots: List<Any>,
            retainedScopes: List<Pair<CompoundDataScope, CompoundDataScope.JournalSnapshot>>,
            retainedFunctions: FunctionStateJournal?,
            retainedStorage: StorageCheckpoint?,
            retainedEffects: EffectAnalysis.JournalSnapshot?,
            retainedScores: List<Pair<Var<*>, MCFPPEntityType.ScoresStateCheckpoint>>,
            collector: StorageCheckpointCollector? = null,
            scopeSnapshots: IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>? = null
        ): CompilerObjectJournal {
            val effects = retainedEffects?.captureCurrent() ?: EffectAnalysis.checkpointActualEffects()
            val pending = ArrayDeque<Any>().apply {
                addAll(roots)
                addAll(effects.functions)
            }
            val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            val enrolled = arrayListOf<Any>()
            val restores = arrayListOf<() -> Unit>()
            val scopes = arrayListOf<Pair<CompoundDataScope, CompoundDataScope.JournalSnapshot>>()
            val oldScopes = IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>().apply {
                retainedScopes.forEach { (scope, snapshot) -> put(scope, snapshot) }
            }
            val functions = arrayListOf<Function>()
            val values = arrayListOf<Var<*>>()
            val properties = arrayListOf<Property>()
            val scores = arrayListOf<Pair<Var<*>, MCFPPEntityType.ScoresStateCheckpoint>>()
            val oldScores = IdentityHashMap<Var<*>, MCFPPEntityType.ScoresStateCheckpoint>().apply {
                retainedScores.forEach { (value, snapshot) -> put(value, snapshot) }
            }
            val declarationRoots = arrayListOf<Any>()
            fun enqueue(value: Any?) {
                if (value != null) pending.addLast(value)
            }
            fun <T> list(items: List<T>): List<T> {
                check(items.javaClass == ArrayList::class.java || items.javaClass == CommandList::class.java) {
                    "Object journal cannot traverse list ${items.javaClass.name}"
                }
                return items.toList()
            }
            fun <K, V> map(items: Map<K, V>): List<V> {
                check(items.javaClass == HashMap::class.java || items.javaClass == LinkedHashMap::class.java) {
                    "Object journal cannot traverse map ${items.javaClass.name}"
                }
                return items.values.toList()
            }
            while (pending.isNotEmpty()) {
                val value = pending.removeFirst()
                if (!seen.add(value)) continue
                enrolled.add(value)
                when (value) {
                    is Var<*> -> {
                        // Var has overridable getters; class checks precede all property access.
                        val ownedScores = MCFPPEntityType.isOwnedScores(value)
                        check(value.javaClass in supportedValues || ownedScores) { "Object journal cannot capture adapter ${value.javaClass.name}" }
                        if (ownedScores) {
                            val snapshot = oldScores[value]?.captureCurrent() ?: MCFPPEntityType.checkpointScores(value)
                            scores.add(value to snapshot)
                            declarationRoots.addAll(snapshot.elements)
                            // Only raw adapters enter this journal. Type roots remain opaque until
                            // the declaration deep journal has checked their exact compiler shape.
                            snapshot.elements.filterIsInstance<Var<*>>().forEach(::enqueue)
                        }
                        check(value.annotations.javaClass == ArrayList::class.java) {
                            "Object journal cannot capture annotation list ${value.annotations.javaClass.name}"
                        }
                        values.add(value)
                        (value.parent as? Var<*>)?.let(::enqueue)
                        enqueue(value.nbtPath)
                        enqueue(value.storageBinding?.data)
                        if (value is MCNumber<*>) enqueue(value.sbObject)
                        if (value is ScoreBool) enqueue(value.boolObject)
                        when (value) {
                            is DataTemplateObject -> {
                                val cache = value.journalInstanceField()
                                restores += { value.restoreJournalInstanceField(cache) }
                                enqueue(cache)
                            }
                            is PropertyVar -> {
                                enqueue(value.property)
                                enqueue(value.field)
                                enqueue(value.caller)
                            }
                            is MCFloat -> {
                                enqueue(value.sign)
                                enqueue(value.int0)
                                enqueue(value.int1)
                                enqueue(value.exp)
                            }
                        }
                        // In particular, TypeDataTemplateObject.delegateVar, coordinate components,
                        // container members and PropertyVar.get() are computed accesses, not fields.
                    }
                    is CompoundDataScope -> {
                        // Declaration preflight has already captured these exact raw relations
                        // in this synchronous savepoint. Share that immutable checkpoint; an
                        // object-only or detached scope still takes its own complete snapshot.
                        val snapshot = scopeSnapshots?.get(value) ?: oldScopes[value]?.captureCurrent() ?: value.checkpointRelations()
                        scopes.add(value to snapshot)
                        snapshot.elements.variables.forEach(::enqueue)
                        snapshot.elements.properties.forEach(::enqueue)
                        snapshot.elements.functions.forEach(::enqueue)
                        snapshot.elements.parents.filterIsInstance<CompoundDataScope>().forEach(::enqueue)
                    }
                    is Property -> {
                        properties.add(value)
                        enqueue(value.parent)
                        enqueue(value.accessor)
                        enqueue(value.mutator)
                    }
                    is FunctionAccessor -> {
                        val function = value.function
                        restores += { value.function = function }
                        enqueue(function)
                    }
                    is NativeMutator -> {
                        val function = value.function
                        restores += { value.function = function }
                        enqueue(function)
                    }
                    is FunctionMutator -> enqueue(value.function)
                    is NativeAccessor -> enqueue(value.function)
                    is SimpleAccessor, is SimpleMutator, is AnonymousNativeMutator -> Unit
                    is AbstractAccessor, is AbstractMutator -> error("Object journal cannot capture accessor ${value.javaClass.name}")
                    is Function -> {
                        check(value.javaClass in supportedFunctions) { "Object journal cannot capture function ${value.javaClass.name}" }
                        functions.add(value)
                        enqueue(value.scope)
                        enqueue(value.returnVar)
                        enqueue(value.context.caller as? Var<*>)
                        enqueue(value.superFunction)
                        value.journalPrivateValues().forEach(::enqueue)
                        value.journalExitStores().forEach(::enqueue)
                        list(value.child).forEach(::enqueue)
                        list(value.parent).forEach(::enqueue)
                        list(value.innerFunction).forEach(::enqueue)
                        list(value.frameExits).forEach { enqueue(it.function) }
                        map(value.compiledFunctions).forEach(::enqueue)
                        map(value.staticRefValue).forEach(::enqueue)
                        list(value.commands).forEach(::enqueue)
                        list(value.tags).forEach(::enqueue)
                    }
                    is FunctionTag -> list(value.functions).forEach(::enqueue)
                    is FunctionScope -> {
                        check(value.javaClass in supportedFunctionScopes) { "Object journal cannot capture scope ${value.javaClass.name}" }
                        if (value is top.mcfpp.analysis.lambda.LambdaCaptureScope)
                            value.checkpointCaptures().first.forEach(::enqueue)
                        map(value.vars).forEach(::enqueue)
                        list(value.parent).filterIsInstance<FunctionScope>().forEach(::enqueue)
                    }
                    is Command -> {
                        check(value.javaClass == Command::class.java || value.javaClass == Comment::class.java) {
                            "Object journal cannot capture command ${value.javaClass.name}"
                        }
                        value.journalValues().forEach(::enqueue)
                    }
                    // Follow the storage capability's reference edges too: display components and
                    // NBT path adapters can reach an object through their parent binding.
                    is StoredData -> {
                        enqueue(value.path)
                        value.retainedSources().forEach(::enqueue)
                    }
                    is SbObject -> enqueue(value.display)
                    is NBTPath -> {
                        when (val source = value.source) {
                            is EntitySource -> enqueue(source.entity)
                            is BlockSource -> enqueue(source.pos)
                            is StorageSource -> Unit
                            else -> error("Object journal cannot capture NBT source ${source.javaClass.name}")
                        }
                        list(value.pathList).forEach(::enqueue)
                    }
                    is IntPath -> enqueue(value.value)
                    is MemberPath -> enqueue(value.value)
                    is NBTPredicatePath -> enqueue(value.value)
                    is IteratorPath -> Unit
                    is ChatComponent -> {
                        check(value.javaClass in supportedChat) { "Object journal cannot capture chat ${value.javaClass.name}" }
                        list(value.styles).forEach(::enqueue)
                        when (value) {
                            is ListChatComponent -> list(value.components).forEach(::enqueue)
                            is TranslatableChatComponent -> value.args?.let { args ->
                                check(
                                    args.javaClass == ArrayList::class.java ||
                                        args.javaClass == java.util.LinkedList::class.java ||
                                        args.javaClass.name in fixedLists
                                ) { "Object journal cannot capture chat arguments ${args.javaClass.name}" }
                                args.forEach(::enqueue)
                            }
                            is ScoreChatComponent -> enqueue(value.value)
                            is NBTChatComponent -> {
                                enqueue(value.nbt)
                                enqueue(value.separator)
                            }
                            is SelectorChatComponent -> enqueue(value.separator)
                        }
                    }
                    is ChatComponentStyle -> when (value) {
                        is HoverEventShowTextStyle -> enqueue(value.content)
                        is HoverEventShowEntityStyle -> {
                            enqueue(value.name)
                            enqueue(value.uuid)
                        }
                        is ColorStyle, is BoldStyle, is ItalicStyle, is UnderlineStyle, is StrikethroughStyle,
                        is ObfuscatedStyle, is InsertionStyle, is ClickEventStyle -> Unit
                        else -> error("Object journal cannot capture chat style ${value.javaClass.name}")
                    }
                    else -> error("Object journal cannot capture ${value.javaClass.name}")
                }
            }
            // Preflight the reachable adapter/accessor graph above before invoking deeper capabilities.
            val storageCollector = collector ?: StorageCheckpointCollector()
            val functionStates = retainedFunctions?.captureCurrent(functions, values, storageCollector)
                ?: FunctionStateJournal.checkpoint(functions, values, storageCollector)
            val storageRoots = values + properties
            val enrolledStorage = retainedStorage?.captureCurrent(storageRoots, storageCollector)
                ?: StorageAccess.checkpointGraph(storageRoots, storageCollector)
            val storageStates = if (collector == null) storageCollector.checkpoint() else enrolledStorage
            val published = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            val elements = (enrolled + declarationRoots).filter { published.add(it) }
            return CompilerObjectJournal(enrolled, restores, scopes, functionStates, storageStates, effects, scores, elements)
        }

        private val supportedFunctionScopes = setOf<Class<*>>(
            FunctionScope::class.java, top.mcfpp.analysis.lambda.LambdaCaptureScope::class.java,
            InternalFunctionScope::class.java,
            NoStackFunctionScope::class.java
        )
        private val fixedLists = top.mcfpp.util.CollectionImplementations.immutableListNames +
            top.mcfpp.util.CollectionImplementations.fixedList.name
        private val supportedChat = setOf<Class<*>>(
            ListChatComponent::class.java,
            PlainChatComponent::class.java,
            EncodedChatComponent::class.java,
            TranslatableChatComponent::class.java,
            ScoreChatComponent::class.java,
            NBTChatComponent::class.java,
            SelectorChatComponent::class.java,
            KeybindChatComponent::class.java
        )
        private val supportedFunctions = setOf<Class<*>>(
            Function::class.java, NativeFunction::class.java, GenericFunction::class.java,
            ExtensionFunction::class.java, GenericExtensionFunction::class.java, InlineFunction::class.java,
            InternalFunction::class.java, NoStackFunction::class.java, MCFunction::class.java,
            DataTemplateConstructor::class.java, TypeDataTemplateConstructor::class.java,
            UnknownFunction::class.java, UnresolvedFunction::class.java, UnresolvedNativeFunction::class.java
        )
        private val supportedValues = setOf<Class<*>>(
            top.mcfpp.core.lang.ref.ReferenceVar::class.java,
            MCInt::class.java, MCFloat::class.java, MCAny::class.java, MCObject::class.java, top.mcfpp.core.lang.FunctionValue::class.java,
            ScoreBool::class.java, DataTemplateObject::class.java, TypeDataTemplateObject::class.java,
            StaticMemberView::class.java, EnumVar::class.java, PropertyVar::class.java,
            MCFPPTypeVar::class.java, UnknownVar::class.java, UnresolvedVar::class.java,
            RangeVar::class.java, VectorVar::class.java, Pos3Var::class.java, Pos2Var::class.java,
            PosDimension::class.java, SelectorVar::class.java, JsonText::class.java, ImmutableList::class.java,
            NBTBasedData::class.java, NBTList::class.java, NBTDictionary::class.java, NBTMap::class.java,
            MCString::class.java, MCByte::class.java, MCShort::class.java, MCLong::class.java, MCDouble::class.java,
            NBTByteArray::class.java, NBTIntArray::class.java, NBTLongArray::class.java, EntityUUIDVar::class.java,
            UnionTypeVar::class.java, Null::class.java, top.mcfpp.core.lang.Void::class.java,
            top.mcfpp.core.lang.context.ExecutionContextVar::class.java,
            top.mcfpp.core.lang.context.BoundExecutorVar::class.java,
            top.mcfpp.core.lang.context.BoundPositionVar::class.java
        )
    }
}
