package top.mcfpp.util

object StringHelper {
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
}