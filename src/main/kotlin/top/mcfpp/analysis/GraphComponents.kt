package top.mcfpp.analysis

/** Tarjan components in dependency-first order. Each vertex and edge is visited once. */
internal fun <T> graphComponents(vertices: Collection<T>, successors: (T) -> Iterable<T>): List<List<T>> {
    val domain = vertices.toHashSet()
    val indices = hashMapOf<T, Int>()
    val low = hashMapOf<T, Int>()
    val stack = arrayListOf<T>()
    val onStack = hashSetOf<T>()
    val result = arrayListOf<List<T>>()
    fun visit(vertex: T) {
        indices[vertex] = indices.size
        low[vertex] = indices.getValue(vertex)
        stack.add(vertex)
        onStack.add(vertex)
        for (next in successors(vertex)) if (next in domain) {
            if (next !in indices) {
                visit(next)
                low[vertex] = minOf(low.getValue(vertex), low.getValue(next))
            } else if (next in onStack) low[vertex] = minOf(low.getValue(vertex), indices.getValue(next))
        }
        if (low[vertex] == indices[vertex]) {
            val component = arrayListOf<T>()
            do {
                val member = stack.removeAt(stack.lastIndex)
                onStack.remove(member)
                component.add(member)
            } while (member != vertex)
            result.add(component)
        }
    }
    for (vertex in vertices) if (vertex !in indices) visit(vertex)
    return result
}
