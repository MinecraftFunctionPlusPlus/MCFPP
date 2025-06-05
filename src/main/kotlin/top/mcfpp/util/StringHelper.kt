package top.mcfpp.util

import top.mcfpp.lib.NamespaceID

object StringHelper {

    @JvmStatic
    fun Char.isLegal(): Boolean {
        return isLowerCase() || isDigit() || arrayOf('_','-','/','.').contains(this)
    }

    @JvmStatic
    fun String.toCamelCase(capitalizeFirstLetter: Boolean = false): String {
        return split('_', '-', '.').mapIndexed { index, part ->
            when {
                index == 0 && !capitalizeFirstLetter -> part
                else -> part.replaceFirstChar { it.uppercase() }
            }
        }.joinToString("")
    }

    @JvmStatic
    fun String.toSnakeCase(): String {
        return buildString(length + 4) {
            for ((index, char) in this@toSnakeCase.withIndex()) {
                when {
                    char.isLowerCase() || char.isDigit() || char in "_-." -> append(char)
                    char.isUpperCase() -> {
                        if (index > 0) append('_')
                        append(char.lowercase())
                    }
                    else -> append("u${char.code.toString(16)}")
                }
            }
        }
    }

    @JvmStatic
    fun String.pathToNamespace(): String{
        return this.replace("\\", ".").replace("/", ".")
    }

    @JvmStatic
    fun String.splitNamespaceID(): Pair<String?, String>{
        val s = this.split(":")
        if(s.size == 1){
            return Pair(null, s[0])
        }
        return Pair(s[0], s[1])
    }

    @JvmStatic
    fun String.toNamespaceID(): NamespaceID{
        val qwq = splitNamespaceID()
        return NamespaceID(qwq.first, qwq.second)
    }

    @JvmStatic
    fun Pair<Float?, Float?>.toRangeStr(): String{
        return buildString {
            if(first != null) append(first)
            append("..")
            if(second != null) append(second)
        }
    }

    @JvmStatic
    fun String.splitMNIParam(): Pair<String, String?>{
        val s = this.split("=", limit = 2).map { it.trim() }
        if(s.size == 1){
            return Pair(s[0], "")
        }
        return Pair(s[0], s[1])
    }

    @JvmStatic
    fun String.addEscapes(): String{
        return this.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}