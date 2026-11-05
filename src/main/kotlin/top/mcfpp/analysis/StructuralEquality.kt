package top.mcfpp.analysis

import java.util.IdentityHashMap

/** Immutable type/value DAGs may share children on both sides of a comparison. Remember pairs
 * for this comparison only, so equals does not unfold both DAGs into exponential trees.
 */
internal object StructuralEquality {
    private val active = ThreadLocal<IdentityHashMap<Any, IdentityHashMap<Any, Boolean?>>>()

    fun compare(left: Any, right: Any, contents: () -> Boolean): Boolean {
        if (left === right) return true
        val previous = active.get()
        val context = previous ?: IdentityHashMap<Any, IdentityHashMap<Any, Boolean?>>().also(active::set)
        val pairs = context.getOrPut(left) { IdentityHashMap() }
        if (pairs.containsKey(right)) return pairs[right] ?: true
        pairs[right] = null
        try { return contents().also { pairs[right] = it } }
        finally { if (previous == null) active.remove() }
    }
}
