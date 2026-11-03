package top.mcfpp.test.util

import com.alibaba.fastjson2.JSON
import top.mcfpp.project.toml
import java.nio.file.Files
import java.nio.file.Path

/** Test declarations use JSON builders; production inputs written by this helper are TOML. */
fun writeProjectFixture(path: Path, declaration: String): Path {
    fun values(value: Any?): Any = when (value) {
        null -> "invalid-null"
        is Map<*, *> -> value.entries.associate { it.key as String to values(it.value) }
        is List<*> -> value.map(::values)
        is java.math.BigInteger -> if (value.bitLength() > 63) value.toString() else value
        else -> value
    }
    @Suppress("UNCHECKED_CAST")
    val fields = LinkedHashMap(values(JSON.parseObject(declaration)) as Map<String, Any?>)
    fields.putIfAbsent("schema", 1)
    fields.putIfAbsent("name", path.fileName.toString().substringBeforeLast('.'))
    fields.putIfAbsent("sourcePath", ".")
    return Files.writeString(path, toml(fields))
}
