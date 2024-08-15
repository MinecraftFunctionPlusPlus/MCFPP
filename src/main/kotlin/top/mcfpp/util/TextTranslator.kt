package top.mcfpp.util

import com.alibaba.fastjson2.JSONObject
import com.ibm.icu.impl.data.ResourceReader
import java.nio.charset.StandardCharsets
import java.util.Locale

object TextTranslator {

    val supportedLanguage = arrayOf("en", "zh")

    val translatableTextMap = HashMap<String, HashMap<String, String>>()

    val language = Locale.getDefault().language

    init {
        //获取资源文件夹下的所有语言文件
        for (lang in supportedLanguage){
            val inputStream = ResourceReader::class.java.classLoader.getResourceAsStream("lang/$lang.json")
            if (inputStream == null) {
                LogProcessor.error("Cannot find language file at: lang/$lang.json")
                break
            }
            // 读取文件内容
            val fileContent = String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            // 解析json
            val json = JSONObject.parse(fileContent) as JSONObject
            val keySet = HashMap<String, String>()
            for (key in json.keys){
                val value = json.getString(key)
                keySet[key] = value
            }
            translatableTextMap[lang] = keySet
        }
    }

    fun String.translate(vararg args: String): String {
        val langMap = if(!translatableTextMap.containsKey(language)){
            translatableTextMap["en"]!!
        }else{
            translatableTextMap[language]!!
        }
        val translatedText = langMap[this] ?: return this
        return translatedText.format(*args)
    }

    const val CAST_ERROR = "error.mcfpp.castError"
    const val VOID_CAST_ERROR = "error.mcfpp.voidCastError"
    const val ASSIGN_ERROR = "error.mcfpp.assignError"

    const val REDUNDANT_CAST_WARN = "warn.mcfpp.redundantCast"
}