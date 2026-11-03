package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.type.TypeId
import top.mcfpp.util.MCUUID
import top.mcfpp.util.Module
import top.mcfpp.util.TempPool
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Project relationships, phase state and temporary-name allocation only.
 * [elements] enrolls declarations, functions, adapters and files in the composing deep journals.
 * ASTs, token streams, callbacks, paths and class loaders are retained input/service references;
 * this capability never executes them or promises to restore edits inside those host objects.
 */
internal class ProjectStateJournal private constructor(
    private val retained: List<Root>,
    private val restores: List<() -> Unit>,
    val elements: List<Any>
) {
    fun restore() {
        restores.forEach { it() }
    }
    fun captureCurrent(): ProjectStateJournal = capture(retained)

    fun prepare(action: () -> Unit): PreparedCompilerCommit {
        var failure: Throwable? = null
        val after: ProjectStateJournal
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
            }
        }
        return object : PreparedCompilerCommit {
            override fun apply() = after.restore()
            override fun rollback() = restore()
        }
    }

    private class Root(val target: Any, val read: (Capture) -> Unit)
    private enum class Items { STRING, MODULE, PACKAGE, TEMPLATE, FILE, CONTEXT, CALLBACK }
    private enum class Keys { CONSTANT, FILE, STRING, PACKAGE, OBJECT_BODY, OBJECT_DEPENDENCY }

    companion object {
        fun checkpoint(): ProjectStateJournal = capture(emptyList())
        private fun capture(retained: List<Root>): ProjectStateJournal {
            val capture = Capture()
            capture.project()
            retained.forEach { it.read(capture) }
            return ProjectStateJournal(
                capture.retained.toList(),
                capture.restores.toList(),
                Collections.unmodifiableList(capture.elements.toList())
            )
        }

        private val fixedSizeListClass: Class<*> = java.util.Arrays.asList("").javaClass
        private val immutableLists = setOf(
            "kotlin.collections.EmptyList",
            "java.util.Collections\$EmptyList",
            "java.util.Collections\$SingletonList",
            "java.util.ImmutableCollections\$List12",
            "java.util.ImmutableCollections\$ListN"
        )
    }

    private class Capture {
        val retained = arrayListOf<Root>()
        val restores = arrayListOf<() -> Unit>()
        val elements = arrayListOf<Any>()
        private val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        private val published = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

        private fun retain(target: Any, read: (Capture) -> Unit, snapshot: () -> Unit) {
            if (!seen.add(target)) return
            retained.add(Root(target, read))
            snapshot()
        }
        private fun publish(value: Any?) {
            if (value is Var<*> || value is Function || value is DataTemplate || value is MCFPPFile) {
                if (published.add(value)) {
                    elements.add(value)
                    retain(value, { it.publish(value) }) { }
                }
            }
        }

        fun project() {
            val state = Project.journalState()
            // The state also owns the compilation-unit budget slot. Restoring the owner keeps
            // already consumed node/depth allocations; only a newer Project.init replaces it.
            restores.add { state.restore() }
            list(state.anonymous, Items.TEMPLATE)
            list(state.pending, Items.TEMPLATE)
            typeSet(state.headers)
            typeSet(state.invalidHeaders)
            typeSet(state.boundParents)
            objectSet(state.preparedObjects)
            map(state.initializerBodies, Keys.OBJECT_BODY)
            map(state.initializerDependencies, Keys.OBJECT_DEPENDENCY)
            list(state.activeObjects, Items.TEMPLATE)
            list(state.sourceFiles, Items.FILE)
            state.entryFunctions.forEach(::publish)
            publish(Project.mcfppSystemTick)

            val config = Project.config
            val modules = Project.modules
            val trees = Project.trees
            val tokens = Project.tokens
            val currentFile = MCFPPFile.currFile
            val namespace = Project.currNamespace
            val stage = Project.compileStage
            val loader = Project.classLoader
            val errors = Project.errorCount
            val warnings = Project.warningCount
            val processors = Project.stageProcessor.copyOf()
            val counts = intArrayOf(
                TempPool.varCount,
                TempPool.functionCount,
                TempPool.functionParam,
                TempPool.anonymousTemplateCount,
                TempPool.namespaceCount
            )
            restores.add {
                Project.config = config
                Project.modules = modules
                Project.trees = trees
                Project.tokens = tokens
                MCFPPFile.currFile = currentFile
                Project.currNamespace = namespace
                Project.compileStage = stage
                Project.classLoader = loader
                Project.errorCount = errors
                Project.warningCount = warnings
                processors.forEachIndexed { index, callbacks -> Project.stageProcessor[index] = callbacks }
                TempPool.varCount = counts[0]
                TempPool.functionCount = counts[1]
                TempPool.functionParam = counts[2]
                TempPool.anonymousTemplateCount = counts[3]
                TempPool.namespaceCount = counts[4]
            }
            this.config(config)
            list(modules, Items.MODULE)
            map(trees, Keys.FILE)
            map(tokens, Keys.FILE)
            publish(currentFile)
            list(Project.ctx, Items.CONTEXT)
            map(Project.constants, Keys.CONSTANT)
            map(Project.macroFunction, Keys.STRING)
            processors.forEach { list(it, Items.CALLBACK) }
        }

        private fun item(value: Any?, kind: Items) {
            val valid = when (kind) {
                Items.STRING -> value is String
                Items.MODULE -> value?.javaClass == Module::class.java
                Items.PACKAGE -> value?.javaClass == Module.Companion.Package::class.java
                Items.TEMPLATE -> value is DataTemplate
                Items.FILE -> value is MCFPPFile
                Items.CONTEXT -> value is org.antlr.v4.runtime.ParserRuleContext
                Items.CALLBACK -> value is Function0<*>
            }
            check(valid) { "Project journal cannot capture $kind element" }
        }

        private fun follow(value: Any?, kind: Items) {
            when (kind) {
                Items.MODULE -> module(value as Module)
                Items.PACKAGE -> pack(value as Module.Companion.Package)
                else -> publish(value)
            }
        }

        private fun list(target: List<*>, kind: Items) {
            check(
                target.javaClass in setOf<Class<*>>(
                    ArrayList::class.java,
                    java.util.LinkedList::class.java,
                    ArrayDeque::class.java,
                    fixedSizeListClass
                ) ||
                    target.javaClass.name in immutableLists
            ) {
                "Project journal cannot capture list ${target.javaClass.name}"
            }
            // Validate before casting generic elements, traversing children or installing a restore.
            val items = target.toList()
            items.forEach { item(it, kind) }
            retain(target, { it.list(target, kind) }) {
                if (target.javaClass == fixedSizeListClass) {
                    @Suppress("UNCHECKED_CAST")
                    val mutable = target as MutableList<Any?>
                    restores.add { items.forEachIndexed { index, value -> mutable[index] = value } }
                } else if (target.javaClass.name !in immutableLists) {
                    @Suppress("UNCHECKED_CAST")
                    val mutable = target as MutableList<Any?>
                    restores.add {
                        mutable.clear()
                        mutable.addAll(items)
                    }
                }
                items.forEach { follow(it, kind) }
            }
        }

        private fun map(target: Map<*, *>, kind: Keys) {
            check(
                target.javaClass in setOf<Class<*>>(
                    HashMap::class.java,
                    LinkedHashMap::class.java,
                    IdentityHashMap::class.java
                )
            ) { "Project journal cannot capture map ${target.javaClass.name}" }
            val entries = target.entries.map { it.key to it.value }
            entries.forEach { (key, value) ->
                when (kind) {
                    Keys.CONSTANT -> {
                        safeKey(key)
                        check(value is Var<*>)
                    }
                    Keys.FILE -> check(key is MCFPPFile)
                    Keys.STRING -> check(key is String && value is String)
                    Keys.PACKAGE -> check(key?.javaClass == Module.Companion.Package::class.java && value is Boolean)
                    Keys.OBJECT_BODY -> check(key is DataTemplate && value is Function)
                    Keys.OBJECT_DEPENDENCY -> check(key is DataTemplate && value is Set<*>)
                }
            }
            retain(target, { it.map(target, kind) }) {
                @Suppress("UNCHECKED_CAST")
                val mutable = target as MutableMap<Any?, Any?>
                restores.add {
                    mutable.clear()
                    entries.forEach { (key, value) -> mutable[key] = value }
                }
                entries.forEach { (key, value) ->
                    publish(key)
                    publish(value)
                    if (kind == Keys.PACKAGE) pack(key as Module.Companion.Package)
                    if (kind == Keys.OBJECT_DEPENDENCY) objectSet(value as Set<*>)
                }
            }
        }

        /** These identity sets are private compiler-created initializer relationships. */
        private fun objectSet(target: Set<*>) {
            check(target.javaClass == Project.InitializerSet::class.java) {
                "Project journal requires a compiler identity initializer set"
            }
            val items = target.toList()
            check(items.all { it is DataTemplate }) { "Project journal requires template initializer elements" }
            retain(target, { it.objectSet(target) }) {
                @Suppress("UNCHECKED_CAST")
                val mutable = target as MutableSet<Any?>
                restores.add {
                    mutable.clear()
                    mutable.addAll(items)
                }
                items.forEach(::publish)
            }
        }

        private fun typeSet(target: Set<*>) {
            check(
                target.javaClass in setOf<Class<*>>(HashSet::class.java, LinkedHashSet::class.java) ||
                    target.javaClass.name in setOf("kotlin.collections.EmptySet", "java.util.Collections\$EmptySet")
            ) {
                "Project journal cannot capture header set ${target.javaClass.name}"
            }
            val items = target.toList()
            items.forEach {
                check(it is TypeId)
                safeKey(it)
            }
            retain(target, { it.typeSet(target) }) {
                if (target.javaClass == HashSet::class.java || target.javaClass == LinkedHashSet::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    val mutable = target as MutableSet<Any?>
                    restores.add {
                        mutable.clear()
                        mutable.addAll(items)
                    }
                }
            }
        }

        private fun config(target: ProjectConfig) {
            check(target.javaClass == ProjectConfig::class.java) { "Project journal requires the compiler ProjectConfig shape" }
            retain(target, { it.config(target) }) {
                val version = target.version
                val namespace = target.rootNamespace
                val output = target.targetPath
                val comment = target.commentLevel
                val root = target.root
                val name = target.name
                val description = target.description
                val includes = target.includes
                val jars = target.jars
                val source = target.sourcePath
                val noDatapack = target.noDatapack
                val copyImport = target.copyImport
                val uuid = target.tempItemEntityUUID
                val noMcLib = target.noMcLib
                val data = target.dataPath
                val macroExpansion = target.macroExpansion
                val library = target.library
                val irOutput = target.irOutputPath
                val optimization = target.optimization
                restores.add {
                    target.version = version
                    target.rootNamespace = namespace
                    target.targetPath = output
                    target.commentLevel = comment
                    target.root = root
                    target.name = name
                    target.description = description
                    target.includes = includes
                    target.jars = jars
                    target.sourcePath = source
                    target.noDatapack = noDatapack
                    target.copyImport = copyImport
                    target.tempItemEntityUUID = uuid
                    target.noMcLib = noMcLib
                    target.dataPath = data
                    target.macroExpansion = macroExpansion
                    target.library = library
                    target.irOutputPath = irOutput
                    target.optimization = optimization
                }
                list(target.stdLib, Items.STRING)
                list(target.stdNamespace, Items.STRING)
                list(includes, Items.STRING)
                list(jars, Items.STRING)
                this.uuid(uuid)
            }
        }

        private fun module(target: Module) {
            retain(target, { it.module(target) }) {
                val id = target.id
                val base = target.base
                val packages = target.packages
                val type = target.type
                val path = target.resourcePath
                restores.add {
                    target.id = id
                    target.base = base
                    target.packages = packages
                    target.type = type
                    target.resourcePath = path
                }
                list(base, Items.PACKAGE)
                map(packages, Keys.PACKAGE)
            }
        }

        private fun pack(target: Module.Companion.Package) {
            retain(target, { it.pack(target) }) {
                val id = target.id
                val depends = target.depends
                val conflict = target.conflict
                val provides = target.provides
                restores.add {
                    target.id = id
                    target.depends = depends
                    target.conflict = conflict
                    target.provides = provides
                }
                list(depends, Items.STRING)
                list(conflict, Items.STRING)
                list(provides, Items.STRING)
            }
        }

        private fun uuid(target: MCUUID) {
            retain(target, { it.uuid(target) }) {
                val tag = target.uuidNBT
                check(tag.javaClass == IntArrayTag::class.java) { "Project journal requires an ordinary UUID int-array tag" }
                val array = tag.value
                restores.add { tag.value = array }
                intArray(array)
            }
        }

        private fun intArray(target: IntArray) {
            retain(target, { it.intArray(target) }) {
                val content = target.clone()
                restores.add { content.copyInto(target) }
            }
        }

        /** HashMap/HashSet restoration may hash keys, so recursively preflight their immutable payloads. */
        private fun safeKey(value: Any?) {
            when (value) {
                null, is String, is Int, is Long, is Short, is Byte, is Boolean, is Char, is Float, is Double,
                is java.util.UUID -> Unit
                is Pair<*, *> -> {
                    safeKey(value.first)
                    safeKey(value.second)
                }
                is TypeId -> when (value) {
                    is TypeId.Builtin, is TypeId.Declaration, is TypeId.Opaque -> Unit
                    is TypeId.Applied -> {
                        safeKey(value.constructor)
                        (value.arguments as List<*>).forEach {
                            check(it is TypeId)
                            safeKey(it)
                        }
                    }
                    is TypeId.Specialized -> {
                        safeKey(value.constructor)
                        (value.arguments as List<*>).forEach {
                            check(it is CompilerValue)
                            safeKey(it)
                        }
                    }
                    is TypeId.Union -> (value.alternatives as Set<*>).forEach {
                        check(it is TypeId)
                        safeKey(it)
                    }
                    is TypeId.Selector -> check(value.entities == null || (value.entities as List<*>).all { it is String })
                }
                is CompilerValue -> when (value) {
                    CompilerValue.NullValue, is CompilerValue.Integral, is CompilerValue.FloatBits,
                    is CompilerValue.DoubleBits, is CompilerValue.Bool, is CompilerValue.Text, is CompilerValue.Nbt -> Unit
                    is CompilerValue.TypeValue -> safeKey(value.id)
                    is CompilerValue.Typed -> {
                        safeKey(value.type)
                        safeKey(value.payload)
                    }
                    is CompilerValue.Sequence -> (value.elements as List<*>).forEach {
                        check(it is CompilerValue)
                        safeKey(it)
                    }
                    is CompilerValue.Record -> (value.fields as Map<*, *>).forEach { (key, item) ->
                        check(key is String && item is CompilerValue)
                        safeKey(item)
                    }
                }
                else -> error("Project journal cannot hash an unsupported constant/header key")
            }
        }
    }
}
