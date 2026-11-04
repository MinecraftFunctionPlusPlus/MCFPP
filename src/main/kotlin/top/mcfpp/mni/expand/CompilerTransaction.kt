package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

internal data class StagedHelper(val function: FunctionRef<*>, val code: PrevalidatedCode, val finalCode: ValidatedCode? = null)
internal data class StagedResource(val kind: ResourceKind, val name: String, val data: ResourceData, val source: SourceSpan)
internal data class StagedName(val logicalKey: String, val name: String)

/** The bridge must stage all of these domains, including mutations made by legacy MNI. */
internal enum class CompilerDomain { COMMANDS, FUNCTIONS, SCOPES, RESOURCES, CALL_GRAPH, NAMES, FLOW, DECLARATIONS, DIAGNOSTICS }

internal class CompilerChanges internal constructor(
    helpers: List<StagedHelper>,
    resources: List<StagedResource>,
    names: List<StagedName>,
    warnings: List<ExpansionDiagnostic>
) {
    val helpers = immutableList(helpers)
    val resources = immutableList(resources)
    val names = immutableList(names)
    val warnings = immutableList(warnings)
}

/** prepare must not publish. rollback must also undo a partially failed apply. */
internal interface PreparedCompilerCommit {
    fun apply()
    fun rollback()
}

/** No default backend silently publishes only part of a compiler transaction. */
internal fun interface CompilerTransactionBackend {
    fun prepare(changes: CompilerChanges): PreparedCompilerCommit
}

/**
 * A production backend can own the one outer journal for every enlisted domain. It receives the
 * participants in the same order in which a non-composing transaction would prepare them, so a
 * helper/resource generation and the root plan emission are captured cumulatively instead of
 * overwriting each other with independent before/after snapshots.
 */
internal interface CompilerCumulativeBackend : CompilerTransactionBackend {
    fun prepare(changes: CompilerChanges, participants: List<CompilerJournalParticipant>): PreparedCompilerCommit
}

/** Writes may join an explicit outer savepoint instead of preparing another full snapshot. */
internal interface CompilerEnclosedBackend : CompilerCumulativeBackend {
    /** Return false without writes when no enclosing savepoint is available. */
    fun commitEnclosed(changes: CompilerChanges, participants: List<CompilerJournalParticipant>, requireOpen: () -> Unit): Boolean
}

/** Journal participants are compiler-owned; the macro API cannot enlist arbitrary callbacks. */
internal interface CompilerJournalParticipant {
    val domains: Set<CompilerDomain>
    fun prepare(): PreparedCompilerCommit

    /**
     * The exact, proof-checked compiler writes this participant contributes to a cumulative
     * transaction. It is executed once while the single outer journal is open. Null means the
     * participant only knows how to prepare an independent snapshot.
     */
    fun generation(): (() -> Unit)? = null
}

internal class CompilerTransaction private constructor(
    private val backend: CompilerTransactionBackend,
    private val parent: CompilerTransaction?
) : AutoCloseable {
    constructor(backend: CompilerTransactionBackend) : this(backend, null)
    private enum class Status { OPEN, MERGED, COMMITTED, DISCARDED }
    private var status = Status.OPEN
    private val helpers = arrayListOf<StagedHelper>()
    private val resources = arrayListOf<StagedResource>()
    private val names = arrayListOf<StagedName>()
    private val warnings = arrayListOf<ExpansionDiagnostic>()
    private val participants = arrayListOf<CompilerJournalParticipant>()
    private val children = arrayListOf<CompilerTransaction>()
    val isOpen: Boolean get() = status == Status.OPEN
    private fun open() {
        check(isOpen) { "Compiler transaction is closed" }
    }

    fun snapshot(): CompilerChanges {
        open()
        return CompilerChanges(helpers, resources, names, warnings)
    }

    fun child(): CompilerTransaction {
        open()
        return CompilerTransaction(backend, this).also { children += it }
    }

    fun stage(helper: StagedHelper) {
        open()
        val key = (helper.function as ImmutableFunctionRef<*>).key
        require(allHelpers().none { (it.function as ImmutableFunctionRef<*>).key == key }) { "Duplicate staged helper" }
        helpers += helper
    }

    fun stage(resource: StagedResource) {
        open()
        require(allResources().none { it.kind == resource.kind && it.name == resource.name }) { "Duplicate staged resource" }
        resources += resource
    }

    fun stage(name: StagedName) {
        open()
        require(names.none { it.name == name.name }) { "Duplicate staged name" }
        names += name
    }
    fun warning(diagnostic: ExpansionDiagnostic) {
        open()
        warnings += diagnostic
    }
    fun enlist(participant: CompilerJournalParticipant) {
        open()
        require(participant.domains.isNotEmpty()) { "A journal participant must declare its mutation domains" }
        participants += participant
    }

    fun allHelpers(): List<StagedHelper> {
        open()
        return immutableList((parent?.allHelpers() ?: emptyList()) + helpers)
    }
    private fun allResources(): List<StagedResource> {
        open()
        return (parent?.allResources() ?: emptyList()) + resources
    }

    fun finalizeHelper(function: FunctionRef<*>, code: ValidatedCode) {
        open()
        val index = helpers.indexOfFirst { (it.function as ImmutableFunctionRef<*>).key == (function as ImmutableFunctionRef<*>).key }
        require(index >= 0) { "Helper does not belong to this transaction savepoint" }
        val previous = helpers[index]
        require(code.code is Block) { "A helper body must remain a block" }
        val original = previous.code.environment
        require(code.environment.arena === original.arena && code.environment.resultRegion == original.resultRegion) { "Helper validation owner or result region changed" }
        require(
            code.environment.externalBindings.size == original.externalBindings.size &&
                code.environment.externalBindings.zip(original.externalBindings).all { (left, right) -> left === right }
        ) { "Helper parameter or capture capabilities changed" }
        val checked = CodeValidator().validate(code.code, code.environment, ValidationPhase.FINAL) as ValidatedCode
        helpers[index] = previous.copy(finalCode = checked)
    }

    /** A nested savepoint becomes detached state owned by its parent, without publishing. */
    fun merge() {
        open()
        val destination = requireNotNull(parent) { "Only a child transaction can merge" }
        destination.open()
        check(children.none { it.isOpen }) { "A child savepoint is still open" }
        val helperKeys = (destination.allHelpers() + helpers).map { (it.function as ImmutableFunctionRef<*>).key }
        require(helperKeys.distinct().size == helperKeys.size) { "Duplicate helper at savepoint merge" }
        val resourceKeys = (destination.allResources() + resources).map { it.kind to it.name }
        require(resourceKeys.distinct().size == resourceKeys.size) { "Duplicate resource at savepoint merge" }
        val nameKeys = (destination.names + names).map { it.name }
        require(nameKeys.distinct().size == nameKeys.size) { "Duplicate generated name at savepoint merge" }
        destination.helpers += helpers
        destination.resources += resources
        destination.names += names
        destination.warnings += warnings
        destination.participants += participants
        status = Status.MERGED
    }

    /** Compiler pipeline only: call after complete expansion, final validation and lowering. */
    fun commit() {
        open()
        check(parent == null) { "A child transaction cannot publish independently" }
        check(children.none { it.isOpen }) { "A child savepoint is still open" }
        check(helpers.all { it.finalCode != null }) { "A staged helper has not passed final validation" }
        val prepared = arrayListOf<PreparedCompilerCommit>()
        val attempted = arrayListOf<PreparedCompilerCommit>()
        try {
            val changes = snapshot()
            if (backend is CompilerEnclosedBackend && backend.commitEnclosed(changes, participants, ::open)) {
                status = Status.COMMITTED
                return
            }
            // The cumulative backend owns a single outer journal including command, helper,
            // resource, name, diagnostic and participant domains. It must not be combined with
            // independently prepared participant snapshots, which would overwrite earlier writes.
            if (backend is CompilerCumulativeBackend) {
                prepared += backend.prepare(changes, participants)
            } else {
                prepared += backend.prepare(changes)
                open() // prepare may reenter compiler lifecycle hooks and revoke this transaction.
                participants.forEach {
                    open()
                    prepared += it.prepare()
                    open()
                }
            }
            prepared.forEach { mutation ->
                open()
                attempted += mutation // Register rollback before an apply that may throw halfway through.
                mutation.apply()
                open() // A callback that discarded and returned cannot make the commit succeed.
            }
            status = Status.COMMITTED
        } catch (failure: Throwable) {
            attempted.asReversed().forEach { mutation ->
                try {
                    mutation.rollback()
                } catch (rollbackFailure: Throwable) {
                    failure.addSuppressed(rollbackFailure)
                }
            }
            discard()
            throw failure
        }
    }

    fun discard() {
        if (!isOpen) return
        children.asReversed().forEach { it.discard() }
        helpers.clear()
        resources.clear()
        names.clear()
        warnings.clear()
        participants.clear()
        status = Status.DISCARDED
    }

    override fun close() {
        discard()
    }
}
