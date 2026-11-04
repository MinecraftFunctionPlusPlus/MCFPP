package top.mcfpp.mni.expand

import top.mcfpp.CompileSettings
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.DeclarationDomainStamp
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.NamespaceRelationCheckpoint
import top.mcfpp.model.scope.SimpleLibScope
import top.mcfpp.util.LogProcessor
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A compiler savepoint, including detached roots from earlier generations. Input AST/Info,
 * class loaders and host closures retain their input identities; their arbitrary internals
 * are outside the compiler journals. No native body is executed by capture or restoration.
 */
internal class CompilerStateJournal private constructor(
    private val project: ProjectStateJournal,
    private val namespaces: List<NamespaceStateJournal>,
    private val declarations: List<DeclarationStateJournal>,
    private val files: List<FileState>,
    private val fileNamespaces: IdentityHashMap<Namespace, NamespaceInput>,
    private val ambient: Ambient
) {
    /** Restore hash dependencies before rebuilding namespace and file registration maps. */
    fun restore() {
        try {
            declarations.forEach { it.restore() }
            namespaces.forEach { it.restore() }
            fileNamespaces.values.forEach { it.restore() }
            files.forEach { it.restore() }
            project.restore()
        } finally {
            ambient.restore()
        }
    }

    fun captureCurrent(): CompilerStateJournal = capture(this)

    /** Enclosed generation joins this savepoint; a failed child revokes the whole preparation. */
    fun prepareCumulative(action: (CompilerEnclosingPreparation) -> Unit): PreparedCompilerCommit = prepare {
        val enclosing = CompilerEnclosingPreparation()
        try {
            action(enclosing)
            enclosing.requireSuccessful()
        } finally {
            enclosing.close()
        }
    }

    /**
     * Execute once. Compiler diagnostics remain visible to the action and the after snapshot;
     * only logger publication is detached. Applying a nested commit joins its parent's capture.
     * Repeated apply/rollback restores state, but an applied publication is never emitted twice.
     */
    fun prepare(action: () -> Unit): PreparedCompilerCommit {
        val diagnostic = LogProcessor.beginDiagnosticCapture()
        var failure: Throwable? = null
        val after: CompilerStateJournal
        val publication: LogProcessor.DiagnosticPublication
        try {
            action()
            after = captureCurrent()
            publication = diagnostic.seal()
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        } finally {
            try {
                diagnostic.close()
            } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else {
                    failure = cleanup
                    throw cleanup
                }
            } finally {
                try {
                    restore()
                } catch (rollback: Throwable) {
                    if (failure != null) failure.addSuppressed(rollback) else throw rollback
                }
            }
        }
        return object : PreparedCompilerCommit {
            private var published = false
            override fun apply() {
                after.restore()
                if (!published) {
                    // Mark first: an external logger failure must not cause duplicate delivery.
                    published = true
                    publication.publishLogs()
                }
            }
            override fun rollback() = restore()
        }
    }

    private class Ambient {
        val function = Function.currFunction
        val nullFunction = Function.nullFunction
        val extraFunction = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        private val debug = CompileSettings.isDebug
        private val ignore = CompileSettings.ignoreStdLib
        private val lib = CompileSettings.isLib
        private val maxLoop = CompileSettings.maxWhileInline
        private val printAll = CompileSettings.printAll
        private val fold = CompileSettings.foldIRConstants
        private val logger = LogProcessor.logger
        private val level = LogProcessor.level

        fun restore() {
            Function.nullFunction = nullFunction
            Function.extraFunction = extraFunction
            Function.currFunction = function
            Function.forcedField = forced
            DataTemplate.currTemplate = template
            CompileSettings.isDebug = debug
            CompileSettings.ignoreStdLib = ignore
            CompileSettings.isLib = lib
            CompileSettings.maxWhileInline = maxLoop
            CompileSettings.printAll = printAll
            CompileSettings.foldIRConstants = fold
            LogProcessor.logger = logger
            LogProcessor.level = level
        }
    }

    private class FileState(val file: MCFPPFile) {
        val namespace: Namespace
        private val syntax: Boolean
        private val imports: List<Pair<String, String>>
        init {
            check(file.javaClass == MCFPPFile::class.java) { "Compiler journal requires a compiler source file" }
            namespace = file.namespace
            check(namespace.javaClass == Namespace::class.java)
            syntax = file.syntaxError
            val table: Map<*, *> = file.unsolvedImports
            check(table.javaClass == HashMap::class.java) { "Compiler journal cannot capture import map ${table.javaClass.name}" }
            val values = table.entries.map { it.key to it.value }
            check(values.all { it.first is String && it.second is String }) { "Compiler journal requires string import names" }
            @Suppress("UNCHECKED_CAST")
            imports = values as List<Pair<String, String>>
        }
        fun restore() {
            file.namespace = namespace
            file.syntaxError = syntax
            file.unsolvedImports.clear()
            imports.forEach { (name, member) -> file.unsolvedImports[name] = member }
        }
    }

    /** File namespaces can be detached from GlobalScope, so retain their private inputs too. */
    private class NamespaceInput(
        val macros: NamespaceRelationCheckpoint<MacroFunctionInfo>,
        val processors: TemplateProcessorRegistry.RawCheckpoint
    ) {
        fun restore() {
            macros.restore()
            processors.restore()
        }
    }

    companion object {
        fun checkpoint(): CompilerStateJournal = capture(null)

        /** Declaration preparation may own canonical models not yet published in any scope.
         * Enroll them through the same deep preflight and file discovery as global declarations.
         * Their declaration snapshots retain these roots, including detached later generations.
         */
        fun checkpoint(additionalRoots: List<Any>): CompilerStateJournal {
            return capture(null, checkedDeclarations(additionalRoots))
        }

        private fun checkedDeclarations(additionalRoots: List<Any>): List<Any> {
            check(additionalRoots.javaClass in rootLists) { "Compiler journal requires an owned declaration root list" }
            return (additionalRoots as List<*>).map {
                check(it is Function || it is DataTemplate) { "Compiler journal additional roots must be declarations" }
                checkNotNull(it)
            }
        }

        /** Whole prepared domains are a separate capability from arbitrary additional roots. */
        fun checkpointDomains(
            domains: List<DeclarationDomainStamp.CompletionCapability>,
            additionalRoots: List<Any> = emptyList()
        ): CompilerStateJournal {
            check(domains.javaClass in rootLists) { "Compiler journal requires an owned completion domain list" }
            val checked = (domains as List<*>).map {
                check(it is DeclarationDomainStamp.CompletionCapability) { "Compiler journal requires completion domain capabilities" }
                it
            }
            val declarations = checkedDeclarations(additionalRoots)
            // Validate every domain before collecting any roots or taking compiler savepoints.
            checked.forEach { it.requireCurrent() }
            val roots = arrayListOf<Any>()
            val seen = identities<Any>()
            checked.forEach { domain -> domain.enroll { if (seen.add(it)) roots.add(it) } }
            return capture(null, declarations, roots)
        }

        private val rootLists = setOf<Class<*>>(
            ArrayList::class.java,
            java.util.LinkedList::class.java,
            java.util.Arrays.asList(1, 2).javaClass,
            emptyList<Any>().javaClass,
            listOf(1).javaClass,
            Collections.emptyList<Any>().javaClass,
            java.util.List.of(1).javaClass,
            java.util.List.of(1, 2, 3).javaClass
        )

        private fun <T : Any> identities(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())

        private fun capture(
            previous: CompilerStateJournal?,
            additionalRoots: List<Any> = emptyList(),
            domainRoots: List<Any> = emptyList()
        ): CompilerStateJournal {
            // Validate complete declaration graphs before reading any declarationFile slot.
            // Files and library scopes use the existing FileState/namespace fixed point. All
            // other original roots (including independent types and formals) enter deep capture.
            val deepRoots = additionalRoots + domainRoots.filter {
                it !== GlobalScope && it !is MCFPPFile && it !is SimpleLibScope
            }
            val extra = if (deepRoots.isEmpty()) null else DeclarationStateJournal.checkpoint(deepRoots)
            val ambient = Ambient()
            val project = previous?.project?.captureCurrent() ?: ProjectStateJournal.checkpoint()
            val namespaces = previous?.namespaces?.mapTo(arrayListOf()) { it.captureCurrent() }
                ?: arrayListOf(NamespaceStateJournal.checkpoint())
            val declarations = previous?.declarations?.mapTo(arrayListOf()) { it.captureCurrent() } ?: arrayListOf()
            extra?.let(declarations::add)
            val files = arrayListOf<FileState>()
            val fileNamespaces = IdentityHashMap<Namespace, NamespaceInput>()
            val knownFiles = identities<MCFPPFile>()
            val knownScopes = identities<SimpleLibScope>()
            val knownDeep = identities<Any>()
            val inspected = identities<Any>()
            val pending = ArrayDeque<Any>()
            fun enqueue(value: Any?) {
                if (value != null && inspected.add(value)) pending.addLast(value)
            }
            fun namespaceInputs(namespace: Namespace) {
                if (fileNamespaces.containsKey(namespace)) return
                check(namespace.javaClass == Namespace::class.java)
                val old = previous?.fileNamespaces?.get(namespace)
                val macros = old?.macros?.captureCurrent() ?: namespace.checkpointMacroDeclarations()
                val processors = old?.processors?.captureCurrent() ?: namespace.templateProcessors.checkpoint()
                fileNamespaces[namespace] = NamespaceInput(macros, processors)
                macros.elements.forEach(::enqueue)
                // Raw checkpoints defensively own these lists. Reject erased records before fields.
                (processors.retainedRegistrations as List<*>).forEach {
                    check(it?.javaClass == TemplateProcessorRegistration::class.java) { "Invalid processor registration" }
                }
                (processors.retainedRequests as List<*>).forEach {
                    check(it?.javaClass == TemplateProcessorRequest::class.java) { "Invalid processor request" }
                    val request = it as TemplateProcessorRequest
                    check(request.registration.javaClass == TemplateProcessorRegistration::class.java)
                    enqueue(request.target)
                    enqueue(request.sourceFile)
                    (request.arguments as List<*>).forEach { argument ->
                        check(argument is CompilerValue.Typed) { "Compiler journal requires typed processor arguments" }
                        enqueue(argument)
                    }
                }
                enqueue(namespace.scope)
            }
            project.elements.forEach(::enqueue)
            previous?.files?.forEach { enqueue(it.file) }
            previous?.fileNamespaces?.keys?.forEach(::namespaceInputs)
            enqueue(MCFPPFile.currFile)
            enqueue(ambient.function)
            enqueue(ambient.forced)
            enqueue(ambient.template)
            enqueue(ambient.nullFunction)
            enqueue(ambient.extraFunction)
            domainRoots.forEach { if (it !== GlobalScope) enqueue(it) }
            namespaces.forEach { snapshot ->
                snapshot.elements.forEach(::enqueue)
                snapshot.sourceFiles.forEach(::enqueue)
            }
            declarations.forEach { snapshot ->
                knownDeep.addAll(snapshot.elements)
                snapshot.elements.forEach { value ->
                    if (value is Function) enqueue(value.declarationFile)
                    if (value is DataTemplate) enqueue(value.declarationFile)
                    enqueue(value)
                }
            }

            // Discover file scopes and declaration files to a fixed point. Deep preflight always
            // precedes reading a declaration's raw file slot, including retained detached roots.
            while (pending.isNotEmpty()) {
                val batch = arrayListOf<Any>()
                val scopes = arrayListOf<SimpleLibScope>()
                while (pending.isNotEmpty()) {
                    val value = pending.removeFirst()
                    when (value) {
                        is MCFPPFile -> if (knownFiles.add(value)) {
                            val state = FileState(value)
                            files.add(state)
                            namespaceInputs(state.namespace)
                            enqueue(value.field)
                            enqueue(value.topFunction)
                        }
                        is SimpleLibScope -> {
                            if (knownScopes.add(value)) scopes.add(value)
                            if (knownDeep.add(value)) batch.add(value)
                        }
                        else -> if (knownDeep.add(value)) batch.add(value)
                    }
                }
                if (scopes.isNotEmpty()) {
                    val snapshot = NamespaceStateJournal.checkpoint(scopes)
                    namespaces.add(snapshot)
                    snapshot.elements.forEach(::enqueue)
                    snapshot.sourceFiles.forEach(::enqueue)
                }
                if (batch.isNotEmpty() || declarations.isEmpty()) {
                    val snapshot = DeclarationStateJournal.checkpoint(batch)
                    declarations.add(snapshot)
                    snapshot.elements.forEach { value ->
                        knownDeep.add(value)
                        // Exact owned shapes have now been checked by the declaration capability.
                        if (value is Function) enqueue(value.declarationFile)
                        if (value is DataTemplate) enqueue(value.declarationFile)
                        enqueue(value)
                    }
                }
            }
            return CompilerStateJournal(project, namespaces.toList(), declarations.toList(), files.toList(), fileNamespaces, ambient)
        }
    }
}

/** An explicit, short-lived capability to write under an enclosing full compiler savepoint. */
internal class CompilerEnclosingPreparation internal constructor() {
    private var open = true
    private var failure: Throwable? = null

    fun generate(action: () -> Unit) {
        check(open) { "The enclosing compiler preparation is closed" }
        requireSuccessful()
        try {
            action()
        } catch (problem: Throwable) {
            failure = problem
            throw problem
        }
    }

    fun requireSuccessful() {
        failure?.let { throw it }
    }

    fun close() { open = false }
}
