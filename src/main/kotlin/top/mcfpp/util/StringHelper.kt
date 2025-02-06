package top.mcfpp.util

object StringHelper {

    fun Char.isLegal(): Boolean {
        return isLowerCase() || isDigit() || arrayOf('_','-','/','.').contains(this)
    }

    fun String.toSnakeCase(): String {
        val s = StringBuilder("")
        for (c in this.withIndex()) {
            if(c.value.isLowerCase() || c.value.isDigit() || c.value == '_' || c.value == '-' || c.value == '.'){
                s.append(c.value)
            }else if (c.value.isUpperCase()){
                s.append("_").append(c.value.lowercase())
            }else{
                s.append("u").append(c.value.code.toString(16))
            }
        }
        return s.toString()
    }

    fun String.pathToNamespace(): String{
        return this.replace("\\", ".").replace("/", ".")
    }

    fun String.splitNamespaceID(): Pair<String?, String>{
        val s = this.split(":")
        if(s.size == 1){
            return Pair(null, s[0])
        }
        return Pair(s[0], s[1])
    }

    fun Pair<Float?, Float?>.toRangeStr(): String{
        return buildString {
            if(first != null) append(first)
            append("..")
            if(second != null) append(second)
        }
    }
}