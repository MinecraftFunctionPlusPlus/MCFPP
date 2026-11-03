package top.mcfpp.project

import com.alibaba.fastjson2.JSON
import top.mcfpp.MCFPP
import top.mcfpp.api.library.LibraryCompilerApi
import java.nio.file.Files

/** Editor data is derived from the same prepared dependencies and Gradle source sets as a build. */
object IdeProjectModel {
    fun create(project: ProjectDefinition, prepared: PreparedProject): Map<String, Any?> {
        val request = prepared.compilerRequest(LibraryCompilerApi::class.java.classLoader)
        val libraries = (request["libraries"] as? List<*>).orEmpty().map { value ->
            val library = value as Map<*, *>
            val binary = library["binary"] as ByteArray
            val digest = MniBuild.sha(binary)
            val file = project.generated.resolve("ide-libraries/$digest.mclib")
            if (!Files.isRegularFile(file)) atomicWrite(file, binary)
            mapOf("coordinate" to library["coordinate"], "version" to library["version"], "path" to file.toString())
        }
        val mniFile = project.generated.resolve("gradle/ide-model.json")
        val mni = if (project.mniEnabled && Files.isRegularFile(mniFile)) JSON.parseObject(Files.readString(mniFile)) else emptyMap<String, Any>()
        fun path(key: String, default: String) = project.root.resolve(project.values["root"] as? String ?: "")
            .resolve(project.values[key] as? String ?: default).normalize().toString()
        return linkedMapOf(
            "schema" to 1, "compilerVersion" to MCFPP.VERSION, "toolchain" to ProjectDefinition.TOOLCHAIN,
            "root" to project.root.toString(), "name" to (project.values["name"] ?: project.root.fileName.toString()),
            "namespace" to (project.values["namespace"] ?: project.root.fileName.toString()),
            "minecraftVersion" to (project.values["version"] ?: "1.21.8"),
            "sourcePath" to path("sourcePath", "src/main/mcfpp"),
            "dataPath" to path("dataPath", "src/main/resources/data"), "targetPath" to project.output.toString(),
            "assetsPath" to path("assetsPath", "src/main/resources/assets"),
            "datapackPath" to project.output.resolve(project.values["name"] as? String ?: project.root.fileName.toString()).toString(),
            "resourcepackPath" to project.output.resolve((project.values["name"] as? String ?: project.root.fileName.toString()) + "-resources").toString(),
            "entrypoints" to (project.values["entrypoints"] ?: emptyMap<String, String>()),
            "mniEnabled" to project.mniEnabled, "mni" to mni, "libraries" to libraries,
            "plugins" to (request["plugins"] as? List<*>).orEmpty().map { entry ->
                (entry as Map<*, *>).filterKeys { it in setOf("coordinate", "version", "path") }.mapValues { (_, v) -> v.toString() }
            }
        )
    }
}
