package top.mcfpp.io.info

import top.mcfpp.model.function.FunctionTag

data class FunctionTagInfo(
    var namespace: String,
    var identifier: String
): ModelInfo<FunctionTag> {
    override fun get(): FunctionTag {
        infoCache[this]?.let { return it }
        val v = FunctionTag(namespace, identifier)
        infoCache[this] = v
        return v
    }

    companion object {

        private val infoCache = HashMap<FunctionTagInfo, FunctionTag>()

        fun resetCache() {
            infoCache.clear()
        }

        fun from(tag: FunctionTag): FunctionTagInfo {
            return FunctionTagInfo(tag.namespace, tag.identifier)
        }

    }

}
