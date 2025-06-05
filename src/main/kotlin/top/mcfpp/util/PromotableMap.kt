package top.mcfpp.util

/**
 * 一个支持快速提升键值对的数据结构
 */
class PromotableMap<K, V>: Iterable<Pair<K, V>> {
    private data class Node<K, V>(
        val key: K,
        var value: V,
        var prev: Node<K, V>? = null,
        var next: Node<K, V>? = null
    )

    private var head: Node<K, V>? = null
    private var tail: Node<K, V>? = null
    private val nodeMap = mutableMapOf<K, Node<K, V>>()

    val keys: Set<K> = nodeMap.keys
    val values: Collection<V> = nodeMap.values.map { it.value }

    /**
     * 默认构造函数
     */
    constructor()

    /**
     * 使用初始键值对列表构造
     */
    constructor(pairs: List<Pair<K, V>>) {
        for ((key, value) in pairs) {
            this[key] = value
        }
    }

    /**
     * 使用初始映射构造
     */
    constructor(map: Map<K, V>) {
        for ((key, value) in map) {
            this[key] = value
        }
    }

    /**
     * 添加或更新键值对
     * 如果键已存在，则更新值并将其提到首位
     */
    operator fun set(key: K, value: V) {
        val existingNode = nodeMap[key]
        if (existingNode != null) {
            existingNode.value = value
            promoteNode(existingNode)
            return
        }

        val newNode = Node(key, value)
        if (head == null) {
            head = newNode
            tail = newNode
        } else {
            newNode.next = head
            head?.prev = newNode
            head = newNode
        }
        nodeMap[key] = newNode
    }

    /**
     * 获取键对应的值
     */
    operator fun get(key: K): V? = nodeMap[key]?.value

    /**
     * 将指定键的键值对提到首位
     */
    fun promote(key: K) {
        val node = nodeMap[key] ?: return
        promoteNode(node)
    }

    private fun promoteNode(node: Node<K, V>) {
        if (node == head) return

        // 从当前位置移除节点
        node.prev?.next = node.next
        if (node.next != null) {
            node.next?.prev = node.prev
        } else {
            tail = node.prev
        }

        // 移动到头部
        node.prev = null
        node.next = head
        head?.prev = node
        head = node
    }

    /**
     * 移除指定键的键值对
     */
    fun remove(key: K): Boolean {
        val node = nodeMap[key] ?: return false

        node.prev?.next = node.next
        node.next?.prev = node.prev

        if (node == head) head = node.next
        if (node == tail) tail = node.prev

        nodeMap.remove(key)
        return true
    }

    /**
     * 检查是否包含键
     */
    fun containsKey(key: K): Boolean = nodeMap.containsKey(key)

    /**
     * 获取首位键值对
     */
    fun first(): Pair<K, V>? = head?.let { it.key to it.value }

    /**
     * 获取键值对数量
     */
    fun size(): Int = nodeMap.size

    /**
     * 清空结构
     */
    fun clear() {
        head = null
        tail = null
        nodeMap.clear()
    }

    override fun iterator(): Iterator<Pair<K, V>> {
        return object : Iterator<Pair<K, V>> {
            private var currentNode = head
            override fun hasNext(): Boolean = currentNode != null
            override fun next(): Pair<K, V> {
                val node = currentNode ?: throw NoSuchElementException()
                currentNode = node.next
                return node.key to node.value
            }
        }
    }
}
