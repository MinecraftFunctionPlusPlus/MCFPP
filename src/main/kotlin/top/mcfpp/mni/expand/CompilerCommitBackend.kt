package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.mni.api.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.util.LogProcessor

/**
 * Production transaction backend for expansion-owned compiler artifacts.
 *
 * Every successful preparation executes exactly one [CompilerStateJournal.prepare]: helper
 * definitions, resources, generated names, diagnostics and all enlisted participant domains are
 * written while that single outer journal is open, the cumulative after state is captured, and the
 * original before state is restored in `finally`. Applying the returned mutation restores the one
 * cumulative snapshot and publishes captured diagnostics; it never invokes a native/MNI host body,
 * a macro callback or a plan emitter a second time.
 *
 * Helpers are lowered and emitted from their FINAL validated bodies while their arenas are still
 * open. The session closes those arenas only after the root transaction has applied this commit.
 */
internal class CompilerCommitBackend : CompilerCumulativeBackend {
    private var source: CompilerSourceBindings? = null

    /** The session and backend are mutually recursive; bind once after source capture succeeds. */
    fun attach(bindings: CompilerSourceBindings): CompilerCommitBackend {
        val existing = source
        require(existing == null || existing === bindings) { "A production commit backend belongs to one source binding domain" }
        source = bindings
        return this
    }

    override fun prepare(changes: CompilerChanges): PreparedCompilerCommit = prepare(changes, emptyList())

    override fun prepare(changes: CompilerChanges, participants: List<CompilerJournalParticipant>): PreparedCompilerCommit {
        val bindings = requireNotNull(source) { "A production commit backend requires its captured source bindings" }
        bindings.requireCurrent()
        validateNames(changes)
        val generations = participants.map {
            requireNotNull(it.generation()) {
                "Journal participant ${it.domains} cannot join one cumulative production transaction"
            }
        }
        // The journal discovers newly emitted helper functions/resources from the same compiler
        // registries on its AFTER capture, so no separately prepared snapshot can overwrite them.
        return CompilerStateJournal.checkpoint().prepare {
            // The source proof belongs here, before the first controlled registry write. Helper
            // registration below mutates NamespaceScope.functions, so bindHelper must not re-run
            // the old pre-write declaration snapshot after the first helper has been registered.
            bindings.requireCurrent()
            emitHelpers(bindings, changes.helpers)
            emitResources(bindings, changes.resources)
            emitWarnings(changes.warnings)
            generations.forEach { generation -> generation() }
        }
    }

    /** Reserved names are a compiler domain: every emitted helper/resource must consume one. */
    private fun validateNames(changes: CompilerChanges) {
        val values = changes.names.map { it.name }
        require(values.distinct().size == values.size) { "A compiler transaction cannot reserve one generated name twice" }
        val reserved = values.toSet()
        changes.helpers.forEach {
            require(it.function.name() in reserved) { "Helper '${it.function.name()}' was not reserved in this transaction" }
        }
        changes.resources.forEach {
            require(it.name in reserved) { "Resource '${it.name}' was not reserved in this transaction" }
        }
    }

    private fun emitHelpers(bindings: CompilerSourceBindings, helpers: List<StagedHelper>) {
        // Register every canonical helper identity before lowering any body so a helper may call
        // another helper without resolving through a display name or a stale staging handle.
        val emitted = helpers.map { helper ->
            requireNotNull(helper.finalCode) { "Helper '${helper.function.name()}' has not passed final validation" }
            val name = helper.function.name()
            val namespace = namespace(name.substringBefore(':'))
            val function = NoStackFunction(name.substringAfter(':'), bindings.target).apply {
                this.namespace = name.substringBefore(':')
                bodyCompiled = true
            }
            function.returnType = bindings.compilerType(helper.function.returnType())
            helper.function.parameters().forEach { parameter ->
                function.normalParams.add(FunctionParam(bindings.compilerType(parameter.type()), parameter.name(), function))
            }
            require(namespace.scope.addFunction(function, false)) { "Helper '${name}' collides with a published function" }
            bindings.bindHelper(helper, function)
            helper to function
        }
        // Lower every helper to its detached CFG before emitting any body, so a REF helper body
        // can specialize another helper (or itself) from a complete plan table.
        val plans = emitted.map { (helper, _) ->
            val final = requireNotNull(helper.finalCode)
            // A helper body carries the real execution context of its validated root code. The
            // lowering target must name that exact context; substituting null would fail the
            // CodeLowerer context guard and could silently change context-bound lowering.
            val context = (final.code as OwnedCode).metadata.executionContextId
            val plan = CodeLowerer().lower(
                final,
                LoweringTarget((helper.function as ImmutableFunctionRef<*>).key, context, bindings.loweringTarget.domain)
            )
            bindings.requireDomain(plan.domain)
            bindings.helper((helper.function as ImmutableFunctionRef<*>).key)?.plan = plan
            plan
        }
        emitted.forEachIndexed { ordinal, (helper, function) ->
            val plan = plans[ordinal]
            val reservation = bindings.session.reserveCompilerName("helper_${ordinal}_plan", "backend")
            val blocks = plan.blocks.associate { it.id to bindings.session.reserveCompilerName("helper_block_${it.id.index}", "block") }
            val returnSlot = if ((helper.function.returnType() as ImmutableTypeRef<*>).kind == TypeKind.VOID) null
            else NBTPath.stack.intIndex(0).memberIndex("return").toCommandPart().analyze()
            CompilerPlanEmitter(bindings, plan, reservation, blocks, function, returnSlot).generate()
        }
    }

    private fun emitResources(bindings: CompilerSourceBindings, resources: List<StagedResource>) {
        resources.forEach { resource ->
            when (resource.kind) {
                ResourceKind.OBJECTIVE -> {
                    val record = resource.data.value() as? ConstantValue.Record
                        ?: throw CodeModelException(resource.source, "An objective resource requires a structured descriptor")
                    val criteria = record.fields["criteria"] as? ConstantValue.Text
                        ?: throw CodeModelException(resource.source, "An objective resource requires a criteria string")
                    if (criteria.value != "dummy")
                        throw CodeModelException(resource.source, "Only a dummy objective resource can be published")
                    @Suppress("UNUSED_VARIABLE")
                    val objective = SbObject(resource.name, criteria.value, null)
                }
                ResourceKind.STORAGE -> {
                    val record = resource.data.value() as? ConstantValue.Record
                        ?: throw CodeModelException(resource.source, "A storage resource requires a structured descriptor")
                    val initial = record.fields["initial"]
                        ?: throw CodeModelException(resource.source, "A storage resource requires an initial value")
                    val tag = StorageAccess.snapshotTag(compilerValue(bindings, initial))
                        ?: throw CodeModelException(resource.source, "Unsupported structured storage value")
                    // Initial data is a real load-time write, not an unapplied name reservation.
                    Project.projectLoad.runInFunction {
                        Function.addCommand("data modify storage ${resource.name} set value ${Tag.toSNBT(tag)}")
                    }
                }
                ResourceKind.INTERNAL_FUNCTION ->
                    throw CodeModelException(resource.source, "Internal function resources must use HelperService")
            }
        }
    }

    private fun emitWarnings(warnings: List<ExpansionDiagnostic>) {
        warnings.forEach { warning ->
            val source = warning.source
            // Use the detached diagnostic route: no ambient AST context is reread while the
            // single compiler journal owns publication. The warning counter remains journaled.
            LogProcessor.rawWarn(
                LogProcessor.logger,
                "${source.file}:${source.startLine}:${source.startColumn}: ${warning.message}"
            )
            LogProcessor.recordProjectWarning()
        }
    }

    private fun namespace(identifier: String): Namespace {
        val namespaces = GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        return namespaces.firstOrNull { it.identifier == identifier }
            ?: GlobalScope.localNamespaces.getOrPut(identifier) { Namespace(identifier) }
    }

    private fun compilerValue(bindings: CompilerSourceBindings, value: ConstantValue): CompilerValue = when (value) {
        is ConstantValue.Integral -> CompilerValue.Integral(value.value)
        is ConstantValue.FloatBits -> CompilerValue.FloatBits(value.bits)
        is ConstantValue.DoubleBits -> CompilerValue.DoubleBits(value.bits)
        is ConstantValue.Bool -> CompilerValue.Bool(value.value)
        is ConstantValue.Text -> CompilerValue.Text(value.value)
        is ConstantValue.Nbt -> CompilerValue.Nbt(value.snbt)
        is ConstantValue.TypeValue -> CompilerValue.TypeValue(bindings.compilerType(value.type).typeId)
        ConstantValue.NullValue -> CompilerValue.NullValue
        is ConstantValue.Sequence -> CompilerValue.Sequence(value.elements.map { compilerValue(bindings, it) })
        is ConstantValue.Record -> CompilerValue.Record(value.fields.mapValues { (_, child) -> compilerValue(bindings, child) })
    }
}
