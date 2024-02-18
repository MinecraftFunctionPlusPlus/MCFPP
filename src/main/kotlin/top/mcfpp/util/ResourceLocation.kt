package top.mcfpp.util


data class ResourceLocation(
    val namespace: String?,
    val path: String
) {
    override fun toString(): String {
        return this.namespace + ":" + this.path
    }

    companion object {
        fun parse(single: String): ResourceLocation {
            val split = single.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return if (split.size == 1) {
                ResourceLocation("minecraft", split[0])
            } else {
                ResourceLocation(split[0], split[1])
            }
        }

        fun parse(namespace: String?, path: String): ResourceLocation {
            return ResourceLocation(namespace, path)
        }
    }
}