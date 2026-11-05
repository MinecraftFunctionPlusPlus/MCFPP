package top.mcfpp.util

import com.alibaba.fastjson2.JSONObject

/** Bundled module metadata and its session-owned, in-memory resource bytes. */
class Module(var id: String) {
    var base: ArrayList<Package> = ArrayList()
    var packages: HashMap<Package, Boolean> = HashMap()

    /** Recompiled bodies replace saved resources without changing the shared protocol document. */
    internal var resourceSnapshot: Map<String, ByteArray>? = null

    internal fun resources(): Map<String, ByteArray> {
        val snapshot = checkNotNull(resourceSnapshot) { "The bundled module has not been bound" }
        val result = linkedMapOf<String, ByteArray>()
        for (pack in base + packages.filterValues { it }.keys) {
            for (provide in pack.provides) {
                val prefix = "datapack/$id/data/$provide/"
                for ((path, bytes) in snapshot) {
                    if (path.startsWith(prefix)) result["${pack.id}/${path.removePrefix(prefix)}"] = bytes
                }
            }
        }
        return result
    }

    companion object {
        fun fromJson(json: JSONObject): ArrayList<Module> {
            val modules = ArrayList<Module>()
            for ((id, value) in json) {
                val metadata = value as JSONObject
                val module = Module(id)
                for ((name, provide) in metadata.getJSONObject("base")) {
                    module.base.add(Package(name).apply { provides = listOf(provide as String) })
                }
                for ((name, specification) in metadata.getJSONObject("packages") ?: JSONObject()) {
                    val pack = Package(name)
                    if (specification is String) pack.provides = listOf(specification)
                    else if (specification is JSONObject) {
                        pack.depends = specification.getJSONArray("depends").map { it as String }
                        pack.conflict = specification.getJSONArray("conflict").map { it as String }
                        pack.provides = specification.getJSONArray("provides").map { it as String }
                    }
                    module.packages[pack] = false
                }
                modules.add(module)
            }
            return modules
        }

        class Package(var id: String) {
            var depends: List<String> = ArrayList()
            var conflict: List<String> = ArrayList()
            var provides: List<String> = ArrayList()
        }
    }
}
