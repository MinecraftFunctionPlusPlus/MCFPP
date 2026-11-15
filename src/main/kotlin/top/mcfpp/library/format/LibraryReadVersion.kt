package top.mcfpp.library.format

/** Decode historical payloads explicitly; standalone current codecs default to the current schema. */
internal object LibraryReadVersion {
    private val version = ThreadLocal<Long>()
    val current: Long get() = version.get() ?: LibraryWire.CURRENT_VERSION
    fun <T> withVersion(value: Long, body: () -> T): T {
        val previous = version.get()
        version.set(value)
        try { return body() } finally { if (previous == null) version.remove() else version.set(previous) }
    }
    fun builtin(key: String): String = if (current == 1L) when (key) {
        "context.pos" -> "context.execution"
        "context.entity" -> "context.entity_execution"
        else -> key
    } else key
}
