package top.mcfpp.util


/**
 * 一个Minecraft中的命名空间ID。如果不指定命名空间，则默认为Minecraft
 */

data class ResourceLocation(
    /**
     * 命名空间
     */
    val namespace: String = "minecraft",
    /**
     * 路径，或者ID
     */
    val path: String
) {
    override fun toString(): String {
        return this.namespace + ":" + this.path
    }

    companion object {
        /**
         * 将一个字符串解析为命名空间
         */
        fun parse(single: String): ResourceLocation {
            val split = single.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return if (split.size == 1) {
                ResourceLocation("minecraft", split[0])
            } else {
                ResourceLocation(split[0], split[1])
            }
        }

        /**
         * 将一对字符串解析为命名空间
         * @param namespace 命名空间
         * @param path 路径
         */
        fun parse(namespace: String = "minecraft", path: String): ResourceLocation {
            return ResourceLocation(namespace, path)
        }
    }
}