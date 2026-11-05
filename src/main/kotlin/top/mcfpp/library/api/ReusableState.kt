package top.mcfpp.library.api

/**
 * Copies registrations while retaining their original containers. The compiler's deep journal
 * owns the mutable objects referenced by these registrations; this snapshot owns only the
 * session directories that must forget objects created by an individual compilation.
 */
internal class ReusableState {
    private val restores = arrayListOf<() -> Unit>()

    fun <K, V> map(target: MutableMap<K, V>) {
        // Keep identity-map keys intact, and avoid hashing mutable compiler objects during capture.
        val entries = target.entries.map { it.key to it.value }
        restores += { target.clear(); entries.forEach { (key, value) -> target[key] = value } }
    }

    fun <T> list(target: MutableList<T>) {
        val values = target.toList()
        restores += { target.clear(); target.addAll(values) }
    }

    fun <T> set(target: MutableSet<T>) {
        val values = target.toList()
        restores += { target.clear(); target.addAll(values) }
    }

    fun add(restore: () -> Unit) { restores += restore }

    fun restore() { restores.forEach { it() } }
}
