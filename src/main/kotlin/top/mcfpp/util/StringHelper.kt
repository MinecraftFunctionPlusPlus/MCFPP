package top.mcfpp.util

object StringHelper {

    /**
     * 将字符串转换为可以被mc识别的小写形式。默认将“大写字母”改为“_小写字母”
     * @param str
     * @return
     */
    fun toLowerCase(str: String): String {
        val s = StringBuilder("")
        for (c in str.withIndex()) {
            if(c.value.isUpperCase()){
                s.append("_").append(c.value.lowercase())
            }else{
                s.append(c.value)
            }
        }
        return s.toString()
    }

    fun toLegalIdentifier(str: String): String {
        val s = StringBuilder("")
        for (c in str.withIndex()) {
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

    fun splitNamespaceID(str: String): Pair<String?, String>{
        val s = str.split(":")
        if(s.size == 1){
            return Pair(null, s[0])
        }
        return Pair(s[0], s[1])
    }
}