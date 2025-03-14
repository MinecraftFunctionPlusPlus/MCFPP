package top.mcfpp.util

object TempPool {

    var varCount = 0

    var functionCount = 0

    var anonymousTemplateCount = 0

    var namespaceCount = 0

    private fun nextVarId(): Int {
        return varCount++
    }

    fun getVarIdentify(): String {
        return "temp_${nextVarId()}"
    }

    private fun nextFunctionId(): Int {
        return functionCount++
    }

    fun getFunctionIdentify(prefix: String): String {
        return "${prefix}_${nextFunctionId()}"
    }

    private fun nextAnonymousTemplateID(): Int {
        return anonymousTemplateCount++
    }

    fun getAnonymousTemplateIdentify(): String {
        return "data_${nextAnonymousTemplateID()}"
    }

    private fun nextNamespaceID(): Int {
        return namespaceCount++
    }

    fun getNamespaceIdentify(): String {
        return "namespace_${nextNamespaceID()}"
    }
}