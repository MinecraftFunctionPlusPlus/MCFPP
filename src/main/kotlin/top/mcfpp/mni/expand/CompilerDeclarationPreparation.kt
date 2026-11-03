package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.analysis.CompletionTarget
import top.mcfpp.analysis.DeclarationCompletionRequest
import top.mcfpp.analysis.DeclarationDomainStamp
import top.mcfpp.analysis.HeaderPhase
import top.mcfpp.analysis.PreparationDiagnostic
import top.mcfpp.analysis.PreparationFactCode
import top.mcfpp.analysis.PreparationFactFailure
import top.mcfpp.analysis.PreparedHeaderBatch
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.FileScope
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.model.scope.SimpleLibScope
import top.mcfpp.type.MCFPPTypeAliasType
import top.mcfpp.util.LogProcessor
import java.util.Collections
import java.util.IdentityHashMap

/** A revision is the actual post-barrier version of every original input domain. Consumers must
 * rebuild their projections and keep checking this proof; a counter cannot detect detached edits.
 */
internal class DeclarationRevision internal constructor(domains: List<DeclarationDomainStamp>) {
    private val domains = Collections.unmodifiableList(ArrayList(domains))
    fun requireCurrent() = domains.forEach { it.requireCurrent() }
}

internal sealed interface DeclarationCompletionResult {
    class Completed(val revision: DeclarationRevision) : DeclarationCompletionResult
    class Rejected(diagnostics: List<PreparationDiagnostic>) : DeclarationCompletionResult {
        val diagnostics: List<PreparationDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))
    }
}

/** The only mutating counterpart of pure signature preparation. A batch transfers its staged
 * canonical shells once. Host declaration effects execute once in prepare; commit restores their
 * captured result instead of invoking visitors or annotations again.
 */
internal class CompilerDeclarationPreparation {
    fun complete(
        batch: PreparedHeaderBatch,
        requests: List<DeclarationCompletionRequest> = batch.requests
    ): DeclarationCompletionResult {
        val source = requests.firstOrNull()?.source ?: batch.headers.firstOrNull()?.source ?: SourceSpan.unknown()
        var compilerDiagnostics: List<PreparationDiagnostic> = emptyList()
        fun rejected(problem: Throwable): DeclarationCompletionResult.Rejected {
            val fact = problem as? PreparationFactFailure
            return DeclarationCompletionResult.Rejected(
                compilerDiagnostics + PreparationDiagnostic(
                    fact?.code ?: PreparationFactCode.UNPREPARED_TYPE,
                    fact?.source ?: source,
                    problem.message ?: "Declaration completion failed",
                    problem
                )
            )
        }
        var restoreReservations: List<() -> Unit> = emptyList()
        var journal: CompilerStateJournal? = null
        try {
            batch.requireOpen()
            check(requests.size == batch.requests.size && requests.indices.all { requests[it] === batch.requests[it] }) {
                "Declaration completion requires the batch's complete request set"
            }
            val capabilities = batch.domainStamps().map { it.forCompletion() }
            check(capabilities.isNotEmpty()) { "Declaration completion requires its original declaration domains" }
            val before = CompilerStateJournal.checkpointDomains(
                capabilities,
                batch.headers.flatMap { listOf(it.prototype, it.model) }
            )
            journal = before
            restoreReservations = batch.headers.map { it.checkpoint() }
            val targets = ArrayList<DeclarationCompletionRequest>()
            val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            requests.forEach { if (seen.add(it.target.identity)) targets.add(it) }
            check(targets.isNotEmpty() || batch.headers.isNotEmpty()) { "Declaration completion has no pending requests" }
            check(
                batch.headers.any { it.phase != HeaderPhase.COMPLETE } ||
                    targets.any { request ->
                        when (val target = request.target) {
                            is CompletionTarget.Header -> target.reservation.phase != HeaderPhase.COMPLETE
                            is CompletionTarget.Alias -> target.alias.cachedTarget == null
                            is CompletionTarget.Declaration -> !target.template.hasCompletedDeclaration
                        }
                    }
            ) { "Declaration completion made no progress: all requested declarations are already complete" }

            var revision: DeclarationRevision? = null
            var completedReservations: List<() -> Unit> = emptyList()
            val errorsBefore = Project.errorCount
            val commit = before.prepare {
                val diagnostic = LogProcessor.beginDiagnosticCapture()
                var successful = false
                try {
                    val adopted = batch.adoptForCompletion()
                    // Adoption has changed the domain. Do not validate its old stamp again.
                    Project.completePreparedTemplateDeclarations {
                        val roots = arrayListOf<DataTemplate>()
                        adopted.headers.forEach { reservation ->
                            roots.add(reservation.prototype.materializePreparedHeader(reservation))
                        }
                        targets.forEach { request ->
                            when (val target = request.target) {
                                is CompletionTarget.Header -> {
                                    check(adopted.headers.any { it === target.reservation }) { "Completion header is outside its adopted batch" }
                                }
                                is CompletionTarget.Declaration -> roots.add(target.template)
                                is CompletionTarget.Alias -> resolveAlias(target.alias, request)
                            }
                        }
                        roots
                    }
                    check(Project.errorCount == errorsBefore) { "Declaration completion reported compiler errors" }
                    targets.forEach { request ->
                        when (val target = request.target) {
                            is CompletionTarget.Header -> check(target.reservation.phase == HeaderPhase.COMPLETE) {
                                "Declaration completion made no progress for its canonical header"
                            }
                            is CompletionTarget.Alias -> check(target.alias.cachedTarget != null) {
                                "Declaration completion made no progress resolving its alias"
                            }
                            is CompletionTarget.Declaration -> check(target.template.hasCompletedDeclaration) {
                                "Declaration completion made no progress for its declaration"
                            }
                        }
                    }
                    revision = DeclarationRevision(capabilities.map { it.refresh() })
                    completedReservations = adopted.headers.map { it.checkpoint() }
                    successful = true
                } finally {
                    val publication = diagnostic.seal()
                    compilerDiagnostics = publication.diagnostics.filter { it.level == top.mcfpp.util.LogLevel.ERROR }.map {
                        PreparationDiagnostic(PreparationFactCode.UNPREPARED_TYPE, source, it.message, it.originalThrowable)
                    }
                    if (successful) publication.publishLogs()
                }
            }
            // Unpublished reservations were not in the before prototype cache. Their phase/name
            // savepoints compose with the deep journal for both successful and failed capture.
            restoreReservations.forEach { it() }
            try {
                commit.apply()
                completedReservations.forEach { it() }
                return DeclarationCompletionResult.Completed(checkNotNull(revision))
            } catch (problem: Throwable) {
                commit.rollback()
                throw problem
            }
        } catch (problem: Throwable) {
            journal?.restore()
            restoreReservations.forEach { it() }
            return rejected(problem)
        }
    }

    /** Source aliases live in a namespace table, while their imports belong to the declaring file.
     * Locate the already parsed source by syntax identity; never borrow the caller's current scope.
     */
    private fun resolveAlias(alias: MCFPPTypeAliasType, request: DeclarationCompletionRequest) {
        if (alias.cachedTarget != null) return
        var syntax: org.antlr.v4.runtime.tree.ParseTree = alias.typeContext
        while (syntax.parent != null) syntax = syntax.parent
        val file = Project.trees.entries.firstOrNull { it.value === syntax }?.key
        if (file != null) {
            file.withDeclarationContext {
                file.resolveImports()
                check(alias.resolve(file.field) != null) { "Cannot resolve declaration alias in its source file" }
            }
            return
        }
        val scopes = request.lexical.domainRoots.filterIsInstance<SimpleLibScope>().flatMap {
            listOf(it) + (it as? NamespaceScope)?.journalFileFields.orEmpty()
        }
        val owner = scopes.firstOrNull { scope -> scope.journalTypeAliases.values.any { it === alias } }
            ?: error("Cannot find the original declaration scope of the completion alias")
        val namespace = when (owner) {
            is NamespaceScope -> owner.identifier
            is FileScope -> owner.checkpointNamespaceField().elements.firstOrNull()?.identifier
            else -> null
        } ?: request.lexical.namespace
        val callerNamespace = Project.currNamespace
        val callerFile = MCFPPFile.currFile
        try {
            Project.currNamespace = namespace
            MCFPPFile.currFile = null
            check(alias.resolve(owner) != null) { "Cannot resolve alias in its declaration scope" }
        } finally {
            Project.currNamespace = callerNamespace
            MCFPPFile.currFile = callerFile
        }
    }
}
