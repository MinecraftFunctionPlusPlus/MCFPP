package top.mcfpp.mni.expand

import top.mcfpp.model.scope.CompoundDataScope
import java.util.Collections
import java.util.IdentityHashMap

/** Mapping/relationship savepoint. Its elements are enrollment roots, not cloned declarations. */
internal class CompoundScopeJournal private constructor(
    private val scopes: List<CompoundDataScope>,
    private val snapshots: List<CompoundDataScope.JournalSnapshot>
) {
    /** Includes detached enrolled elements so the composing journal can capture their after-state. */
    val elements = CompoundDataScope.JournalElements(
        identities(snapshots.flatMap { it.elements.variables }),
        identities(snapshots.flatMap { it.elements.types }),
        identities(snapshots.flatMap { it.elements.properties }),
        identities(snapshots.flatMap { it.elements.functions }),
        identities(snapshots.flatMap { it.elements.parents }),
        identities(snapshots.flatMap { it.elements.originOwners })
    )

    fun restore() {
        snapshots.forEach { it.restore() }
    }

    fun captureCurrent(): CompoundScopeJournal = capture(scopes, snapshots)

    companion object {
        fun checkpoint(scopes: Iterable<CompoundDataScope>): CompoundScopeJournal = capture(scopes.toList(), emptyList())

        private fun capture(
            retainedScopes: List<CompoundDataScope>,
            retainedSnapshots: List<CompoundDataScope.JournalSnapshot>
        ): CompoundScopeJournal {
            val pending = ArrayDeque<CompoundDataScope>().apply { addAll(retainedScopes) }
            val seen = Collections.newSetFromMap(IdentityHashMap<CompoundDataScope, Boolean>())
            val scopes = arrayListOf<CompoundDataScope>()
            val snapshots = arrayListOf<CompoundDataScope.JournalSnapshot>()
            val retained = IdentityHashMap<CompoundDataScope, CompoundDataScope.JournalSnapshot>()
            retainedScopes.forEachIndexed { index, scope -> retainedSnapshots.getOrNull(index)?.let { retained[scope] = it } }
            while (pending.isNotEmpty()) {
                val scope = pending.removeFirst()
                if (!seen.add(scope)) continue
                val snapshot = retained[scope]?.captureCurrent() ?: scope.checkpointRelations()
                scopes.add(scope)
                snapshots.add(snapshot)
                snapshot.elements.parents.filterIsInstance<CompoundDataScope>().forEach(pending::addLast)
            }
            return CompoundScopeJournal(scopes, snapshots)
        }

        private fun <T : Any> identities(values: List<T>): List<T> {
            val seen = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
            return values.filter { seen.add(it) }
        }
    }
}
