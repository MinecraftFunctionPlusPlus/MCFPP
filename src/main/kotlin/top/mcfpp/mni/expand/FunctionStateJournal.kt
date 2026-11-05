package top.mcfpp.mni.expand

import top.mcfpp.analysis.Effect
import top.mcfpp.analysis.EffectAnalysis
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageCheckpoint
import top.mcfpp.analysis.StorageCheckpointCollector
import top.mcfpp.analysis.StoredData
import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.doc.*
import top.mcfpp.model.Generic
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.InternalFunctionScope
import top.mcfpp.model.scope.NoStackFunctionScope
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Identity-preserving savepoint for enrolled functions and their compiler-owned graph.
 * Enroll all existing functions that native compilation may reach. Project registries,
 * non-function scopes, declaration/type metadata and diagnostics need the global journal.
 * AST and typed IR are compiler inputs: replacement is saved, arbitrary host edits inside
 * those input graphs are outside this capability.
 */
internal class FunctionStateJournal private constructor(
    private val enrolled: List<Any>,
    private val restores: List<() -> Unit>,
    private val storage: StorageCheckpoint,
    private val effects: EffectAnalysis.EffectGraphSnapshot
) {
    fun restore() {
        restores.forEach { it() }
        storage.restore()
        effects.restore()
    }

    /** Retains detached before-state objects as well as newly reachable after-state objects. */
    fun captureCurrent(): FunctionStateJournal = capture(enrolled, storage, effects)

    internal fun captureCurrent(
        functions: Iterable<Function>, values: Iterable<Var<*>>, collector: StorageCheckpointCollector? = null
    ): FunctionStateJournal = capture(enrolled + functions + values, storage, effects, collector)

    /** Executes preparation once, then leaves the compiler at the before-state even on failure. */
    fun prepare(action: () -> Unit): PreparedCompilerCommit {
        val caller = Function.currFunction
        val forcedScope = Function.forcedField
        var failure: Throwable? = null
        val after: FunctionStateJournal
        try {
            action()
            after = captureCurrent()
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        } finally {
            try {
                restore()
            } catch (rollbackFailure: Throwable) {
                if (failure != null) failure.addSuppressed(rollbackFailure) else throw rollbackFailure
            } finally {
                Function.currFunction = caller
                Function.forcedField = forcedScope
            }
        }
        return object : PreparedCompilerCommit {
            override fun apply() = after.restore()
            override fun rollback() = restore()
        }
    }

    companion object {
        fun checkpoint(
            functions: Iterable<Function>, values: Iterable<Var<*>> = emptyList(), collector: StorageCheckpointCollector? = null
        ): FunctionStateJournal = capture(functions.toList() + values.toList(), null, collector = collector)

        private fun capture(
            roots: Iterable<Any>,
            retainedStorage: StorageCheckpoint?,
            retainedEffects: EffectAnalysis.EffectGraphSnapshot? = null,
            collector: StorageCheckpointCollector? = null
        ): FunctionStateJournal {
            val pending = ArrayDeque<Any>().apply { roots.forEach(::addLast) }
            val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            val enrolled = arrayListOf<Any>()
            val restores = arrayListOf<() -> Unit>()
            val values = arrayListOf<Var<*>>()
            val stores = arrayListOf<StoredData>()
            val runtimeEffects = arrayListOf<Effect>()
            fun enqueue(value: Any?) {
                if (value != null) pending.addLast(value)
            }
            fun <T> list(items: MutableList<T>) {
                check(items.javaClass == ArrayList::class.java || items.javaClass == top.mcfpp.command.CommandList::class.java) {
                    "Function journal cannot capture list ${items.javaClass.name}"
                }
                @Suppress("UNCHECKED_CAST")
                enqueue(ListRoot(items as MutableList<Any?>))
            }
            fun <K, V> map(items: MutableMap<K, V>) {
                check(items.javaClass == HashMap::class.java || items.javaClass == LinkedHashMap::class.java) {
                    "Function journal cannot capture map ${items.javaClass.name}"
                }
                @Suppress("UNCHECKED_CAST")
                enqueue(MapRoot(items as MutableMap<Any?, Any?>))
            }
            fun <T> set(items: MutableSet<T>) {
                check(items.javaClass == HashSet::class.java || items.javaClass == LinkedHashSet::class.java) {
                    "Function journal cannot capture set ${items.javaClass.name}"
                }
                @Suppress("UNCHECKED_CAST")
                enqueue(SetRoot(items as MutableSet<Any?>))
            }
            fun follow(item: Any?) {
                when (item) {
                    is Function, is FunctionScope, is FunctionParam, is FunctionTag, is Command,
                    is Var<*>, is StoredData, is FunctionContext, is Document -> enqueue(item)
                    is Function.FrameExit -> enqueue(item.function)
                }
            }
            while (pending.isNotEmpty()) {
                val value = pending.removeFirst()
                val identity = when (value) {
                    is ListRoot -> value.items
                    is MapRoot -> value.items
                    is SetRoot -> value.items
                    else -> value
                }
                if (!seen.add(identity)) continue
                enrolled += value
                when (value) {
                    is Function -> {
                        check(value.javaClass in supportedFunctions) {
                            "Function journal cannot capture ${value.javaClass.name}"
                        }
                        restores += value.checkpointFunctionState()
                        runtimeEffects.add(value.runtimeEffect)
                        enqueue(value.superFunction)
                        enqueue(value.scope)
                        enqueue(value.returnVar)
                        enqueue(value.context)
                        enqueue(value.document)
                        value.journalPrivateValues().forEach(::enqueue)
                        value.journalExitStores().forEach(::enqueue)
                        val commands = value.commands
                        list(commands)
                        list(value.normalParams)
                        list(value.tags)
                        list(value.child)
                        list(value.parent)
                        list(value.innerFunction)
                        list(value.frameExits)
                        list(value.annotations)
                        map(value.compiledFunctions)
                        map(value.staticRefValue)
                        set(value.excludedArgIndex)
                        if (value.boundReadonlyNames is HashSet<*>) {
                            @Suppress("UNCHECKED_CAST")
                            set(value.boundReadonlyNames as MutableSet<String>)
                        }
                        val readonly = when (value) {
                            is NativeFunction -> value.readOnlyParams
                            is Generic<*> -> value.readOnlyParams
                            is UnresolvedNativeFunction -> value.readOnlyParams
                            else -> null
                        }
                        readonly?.let {
                            list(it)
                            it.forEach(::enqueue)
                        }
                        when (value) {
                            is NativeFunction -> {
                                val method = value.javaMethod
                                val methodName = value.javaMethodName
                                val caller = value.caller
                                val constant = value.returnsConstWhenArgsConst
                                restores += {
                                    value.javaMethod = method
                                    value.javaMethodName = methodName
                                    value.caller = caller
                                    value.returnsConstWhenArgsConst = constant
                                }
                            }
                            is UnresolvedNativeFunction -> {
                                val data = value.data
                                restores += { value.data = data }
                            }
                            is UnknownFunction -> {
                                check(
                                    value.data == null ||
                                        value.data is String ||
                                        value.data is Boolean ||
                                        value.data is Byte ||
                                        value.data is Short ||
                                        value.data is Int ||
                                        value.data is Long ||
                                        value.data is Float ||
                                        value.data is Double ||
                                        value.data is Char
                                ) {
                                    "Function journal cannot capture unknown-function payload ${value.data?.javaClass?.name}"
                                }
                                val data = value.data
                                restores += { value.data = data }
                            }
                        }
                    }
                    is Command -> {
                        restores += value.checkpointCommandState()
                        value.journalValues().forEach(::enqueue)
                    }
                    is FunctionScope -> {
                        check(value.javaClass in supportedScopes) {
                            "Function journal cannot capture scope ${value.javaClass.name}"
                        }
                        if (value is top.mcfpp.analysis.lambda.LambdaCaptureScope) {
                            val saved = value.checkpointCaptures()
                            restores += saved.second
                            saved.first.forEach(::enqueue)
                        }
                        val parents = value.parent
                        list(parents)
                        map(value.vars)
                        map(value.types)
                        set(value.fieldVarSet)
                        set(value.fieldTypeSet)
                        val parentItems = parents.toList()
                        val vars = LinkedHashMap(value.vars)
                        val types = HashMap(value.types)
                        val fields = value.fieldVarSet.toSet()
                        val typeFields = value.fieldTypeSet.toSet()
                        restores += {
                            value.parent = parents
                            parents.clear()
                            parents.addAll(parentItems)
                            value.vars.clear()
                            value.vars.putAll(vars)
                            value.types.clear()
                            value.types.putAll(types)
                            value.fieldVarSet.clear()
                            value.fieldVarSet.addAll(fields)
                            value.fieldTypeSet.clear()
                            value.fieldTypeSet.addAll(typeFields)
                        }
                        vars.values.forEach(::enqueue)
                        parentItems.filterIsInstance<FunctionScope>().forEach(::enqueue)
                    }
                    is FunctionParam -> {
                        val type = value.type
                        val name = value.identifier
                        val default = value.hasDefault
                        val readonly = value.isReadOnly
                        val typeName = value.typeName
                        val defaultValue = value.defaultValue
                        val defaultTypes = value.defaultTypes
                        check(
                            defaultTypes.javaClass in setOf<Class<*>>(java.util.HashMap::class.java, java.util.LinkedHashMap::class.java) ||
                                defaultTypes.javaClass.name in setOf(
                                    "kotlin.collections.EmptyMap",
                                    "java.util.Collections\$EmptyMap",
                                    "java.util.Collections\$SingletonMap"
                                )
                        ) {
                            "Function journal cannot capture parameter type map ${defaultTypes.javaClass.name}"
                        }
                        val entries = LinkedHashMap(defaultTypes)
                        val context = value.defaultContext
                        if (defaultTypes is HashMap<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            map(defaultTypes as MutableMap<top.mcfpp.type.TypeId, top.mcfpp.type.MCFPPType>)
                        }
                        restores += {
                            value.type = type
                            value.identifier = name
                            value.hasDefault = default
                            value.isReadOnly = readonly
                            value.typeName = typeName
                            value.defaultValue = defaultValue
                            value.defaultTypes = defaultTypes
                            if (defaultTypes is HashMap<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                (defaultTypes as MutableMap<top.mcfpp.type.TypeId, top.mcfpp.type.MCFPPType>).apply {
                                    clear()
                                    putAll(entries)
                                }
                            }
                            value.defaultContext = context
                        }
                        enqueue(value.function)
                    }
                    is FunctionTag -> {
                        val namespace = value.namespace
                        val name = value.identifier
                        val functions = value.functions
                        list(functions)
                        val saved = functions.toList()
                        restores += {
                            value.namespace = namespace
                            value.identifier = name
                            value.functions = functions
                            functions.clear()
                            functions.addAll(saved)
                        }
                        saved.forEach(::enqueue)
                    }
                    is FunctionContext -> {
                        val caller = value.caller
                        restores += { value.caller = caller }
                        (caller as? Var<*>)?.let(::enqueue)
                    }
                    is Document -> {
                        list(value.tags)
                        check(value.tags.all { it.javaClass in supportedDocumentTags }) {
                            "Function journal cannot capture custom document tags"
                        }
                    }
                    is Var<*> -> values += value
                    is StoredData -> stores += value
                    is ListRoot -> {
                        check(value.items.javaClass == ArrayList::class.java || value.items.javaClass == top.mcfpp.command.CommandList::class.java) {
                            "Function journal cannot capture list ${value.items.javaClass.name}"
                        }
                        val saved = value.items.toList()
                        restores += {
                            value.items.clear()
                            value.items.addAll(saved)
                        }
                        saved.forEach(::follow)
                    }
                    is MapRoot -> {
                        check(value.items.javaClass == HashMap::class.java || value.items.javaClass == LinkedHashMap::class.java) {
                            "Function journal cannot capture map ${value.items.javaClass.name}"
                        }
                        val saved = LinkedHashMap(value.items)
                        restores += {
                            value.items.clear()
                            value.items.putAll(saved)
                        }
                        saved.values.forEach(::follow)
                    }
                    is SetRoot -> {
                        check(value.items.javaClass == HashSet::class.java || value.items.javaClass == LinkedHashSet::class.java) {
                            "Function journal cannot capture set ${value.items.javaClass.name}"
                        }
                        val saved = value.items.toList()
                        restores += {
                            value.items.clear()
                            value.items.addAll(saved)
                        }
                        saved.forEach(::follow)
                    }
                    else -> error("Function journal cannot capture ${value.javaClass.name}")
                }
            }
            val storageRoots = values + stores
            val savedStorage = retainedStorage?.captureCurrent(storageRoots, collector) ?: StorageAccess.checkpointGraph(storageRoots, collector)
            val savedEffects = retainedEffects?.captureCurrent(runtimeEffects) ?: EffectAnalysis.checkpointEffectGraph(runtimeEffects)
            return FunctionStateJournal(enrolled, restores, savedStorage, savedEffects)
        }

        private class ListRoot(val items: MutableList<Any?>)
        private class MapRoot(val items: MutableMap<Any?, Any?>)
        private class SetRoot(val items: MutableSet<Any?>)

        private val supportedFunctions = setOf<Class<*>>(
            Function::class.java, NativeFunction::class.java, GenericFunction::class.java,
            ExtensionFunction::class.java, GenericExtensionFunction::class.java, InlineFunction::class.java,
            InternalFunction::class.java, NoStackFunction::class.java, MCFunction::class.java,
            DataTemplateConstructor::class.java, TypeDataTemplateConstructor::class.java,
            UnknownFunction::class.java, UnresolvedFunction::class.java, UnresolvedNativeFunction::class.java
        )
        private val supportedScopes = setOf<Class<*>>(
            FunctionScope::class.java, top.mcfpp.analysis.lambda.LambdaCaptureScope::class.java,
            InternalFunctionScope::class.java,
            NoStackFunctionScope::class.java
        )
        private val supportedDocumentTags = setOf<Class<*>>(
            SinceTag::class.java, DeprecatedTag::class.java, SeeTag::class.java, VersionTag::class.java,
            AuthorTag::class.java, BaseTag::class.java, ParamTag::class.java, ReturnTag::class.java,
            ThrowsTag::class.java, ContextTag::class.java
        )
    }
}
