package top.mcfpp.model.function

import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import top.mcfpp.Project

/**
 * 一个函数的标签
 */
class FunctionTag(
    namespace: String?,
    /**
     * 函数标签的名字
     */
    var tag: String
) {
    /**
     * 函数标签的命名空间
     */
    var namespace: String

    /**
     * 这个标签含有那些函数
     */
    var functions: ArrayList<Function> = ArrayList()

    init {
        if (namespace == null) {
            if (tag == "tick" || tag == "load") {
                this.namespace = MINECRAFT
            } else {
                this.namespace = Project.currNamespace
            }
        } else {
            this.namespace = namespace
        }
    }

    val namespaceID: String
        get() = "$namespace:$tag"
    val tagJSON: String
        get() {
            val json = JSONObject()
            val values = JSONArray()
            for (f in functions) {
                values.add(f.namespaceID)
            }
            json["values"] = values
            return json.toString(JSONWriter.Feature.PrettyFormat)
        }

    @Override
    override fun equals(other: Any?): Boolean {
        return if (other is FunctionTag) {
            other.namespace == namespace && other.tag == tag
        } else false
    }

    override fun hashCode(): Int {
        return namespace.hashCode()
    }

    companion object {
        const val MINECRAFT = "minecraft"
        val TICK = FunctionTag(MINECRAFT, "tick")
        val LOAD = FunctionTag(MINECRAFT, "load")
    }
}