package top.mcfpp.util

import com.alibaba.fastjson2.JSONObject
import org.apache.tools.zip.ZipFile
import top.mcfpp.io.FileUtils.copyAllFiles
import top.mcfpp.io.FileUtils.extractFolderFromInner
import top.mcfpp.io.FileUtils.extractTo
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

enum class ModuleType {
    ZIP,
    JAR,
    DIR,
    INNER
}

class Module(var id: String) {

    var base: ArrayList<Package> = ArrayList()

    var packages: HashMap<Package, Boolean> = HashMap()

    var type: ModuleType = ModuleType.DIR

    var resourcePath: Path = Path(".")

    fun extract(targetRoot: Path){
        when(type){
            ModuleType.DIR -> extractFromDir(targetRoot)
            ModuleType.JAR, ModuleType.ZIP -> extractFromJarOrZip(targetRoot)
            ModuleType.INNER -> extractFromInner(targetRoot)
        }
    }

    private fun extractFromDir(targetRoot: Path){
        val sourceRoot = resourcePath.resolve(id).resolve("data")
        for (b in base){
            val target = targetRoot.resolve(b.id)
            for (provide in b.provides){
                val source = sourceRoot.resolve(provide)
                if(!source.toFile().exists() ||!source.toFile().isDirectory){
                    continue
                }
                copyAllFiles(source.absolutePathString(), target.absolutePathString())
            }
        }
        for (p in packages.filter { it.value }.keys){
            val target = targetRoot.resolve(p.id)
            for (provide in p.provides){
                val source = sourceRoot.resolve(provide)
                if(!source.toFile().exists() ||!source.toFile().isDirectory){
                    continue
                }
                copyAllFiles(source.absolutePathString(), target.absolutePathString())
            }
        }
    }

    private fun extractFromJarOrZip(targetRoot: Path){
        ZipFile(resourcePath.toFile()).use {
            for (b in base){
                val target = targetRoot.resolve(b.id)
                for (provide in b.provides){
                    val source = "$id/data/$provide"
                    it.extractTo(source, target.absolutePathString())
                }
            }
            for (p in packages.filter { entry -> entry.value }.keys){
                val target = targetRoot.resolve(p.id)
                for (provide in p.provides){
                    val source = "$id/data/$provide"
                    it.extractTo(source, target.absolutePathString())
                }
            }
        }
    }

    private fun extractFromInner(targetRoot: Path){
        for (b in base){
            val target = targetRoot.resolve(b.id)
            for (provide in b.provides){
                val source = "datapack/$id/data/$provide"
                extractFolderFromInner(source, target.absolutePathString())
            }
        }
        for (p in packages.filter { entry -> entry.value }.keys){
            val target = targetRoot.resolve(p.id)
            for (provide in p.provides){
                val source = "datapack/$id/data/$provide"
                extractFolderFromInner(source, target.absolutePathString())
            }
        }
    }

    companion object {

        fun fromJson(json: JSONObject): ArrayList<Module>{
            val modules = ArrayList<Module>()
            for ((id, mjson) in json) {
                mjson as JSONObject
                val module = Module(id)
                val base = mjson.getJSONObject("base")
                for (i in base) {
                    module.base.add(Package(i.key).apply { provides.addFirst(i.value as String) })
                }
                val packages = mjson.getJSONObject("packages")?: continue
                for ((key, value) in packages) {
                    if(value is String){
                        val qwq = Package(key)
                        qwq.provides = listOf(value)
                        module.packages[qwq] = false
                    }else if(value is JSONObject){
                        val `package` = Package(key)
                        module.packages[`package`] = false
                        `package`.depends = value.getJSONArray("depends").map { it as String }
                        `package`.conflict = value.getJSONArray("conflict").map { it as String }
                        `package`.provides = value.getJSONArray("provides").map { it as String }
                    }
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