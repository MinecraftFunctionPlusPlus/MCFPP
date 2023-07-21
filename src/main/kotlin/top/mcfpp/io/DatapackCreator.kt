package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.lib.*
import java.io.*
import java.nio.file.*

/**
 * 用于创建一个数据包的框架。
 * 一个完整的数据包包含(加粗者为重要组成部分，也是默认包含的部分):
 *
 *  * **进度(advancement)**
 *  * 聊天类型(chat_type)
 *  * 数据包(datapacks)
 *  * **函数(functions)**
 *  * **战利品表(loot_tables)**
 *  * **谓词(predicates)**
 *  * 结构(structures)
 *  * 配方(recipes)
 *  * **物品修饰器(item_modifiers)**
 *  * **标签(tags)**
 *  * 维度(dimension)
 *  * 维度类型(dimension_type)
 *  * 世界生成(worldgen)
 *
 * 加粗的部分表示对一般数据包的逻辑实现几乎必不可少的部分。
 *
 *
 */
object DatapackCreator {

    /**
     * 在指定的路径生成一个数据包的框架
     * @param path 路径
     */
    fun createDatapack(path: String) {
        Project.debug("Clearing output folder...")
        //清空原输出文件夹
        delAllFile(File("$path/${Project.name}"))
        Project.debug("Creating datapack...")
        //生成
        val datapackMcMeta = DatapackMcMeta(
            DatapackMcMeta.Pack(
                Project.getVersion(Project.version!!),
                Project.description!!
            )
        )
        val datapackMcMetaJson: String = JSON.toJSONString(datapackMcMeta)
        //创建文件夹
        try {
            Files.createDirectories(Paths.get("$path/${Project.name}/data"))
            //创建pack.mcmeta
            Files.write(Paths.get("$path/${Project.name}/pack.mcmeta"), datapackMcMetaJson.toByteArray())
            for(namespace in GlobalField.localNamespaces){
                val currPath = "$path/${Project.name}/data/${namespace.key}"
                namespace.value.forEachFunction {f ->
                    run {
                        if (f is Native) {
                            return@run
                        }
                        Project.debug("Writing File: $currPath/functions/${f.IdentifyWithParams}.mcfunction")
                        Files.createDirectories(Paths.get("$currPath/functions"))
                        Files.write(
                            Paths.get("$currPath/functions/${f.IdentifyWithParams}.mcfunction"),
                            f.cmdStr.toByteArray()
                        )
                    }
                }
                namespace.value.forEachClass { cls ->
                    run {
                        if (cls is Native) {
                            return@run
                        }
                        //成员
                        cls.field.forEachFunction { f->
                            run {
                                if (f is Native) {
                                    return@run
                                }
                                Project.debug("$currPath/functions/" + f.IdentifyWithParams + ".mcfunction")
                                //TODO 可能无法正确创建文件夹
                                Files.createDirectories(Paths.get("$currPath/functions/" + cls.identifier))
                                Files.write(
                                    Paths.get("$currPath/functions/" + f.IdentifyWithParams + ".mcfunction"),
                                    f.cmdStr.toByteArray()
                                )
                            }
                        }
                        cls.field.forEachFunction { f ->
                            run {
                                if (f is Native) {
                                    return@run
                                }
                                Project.debug("Writing File: " + path + "/${Project.name}/data/" + cls.namespace + "/functions/" + f.IdentifyWithParams + ".mcfunction")
                                //TODO 可能无法正确创建文件夹
                                Files.createDirectories(Paths.get(path + "/${Project.name}/data/" + f.namespace + "/functions/" + cls.identifier + "/static"))
                                Files.write(
                                    Paths.get(path + "/${Project.name}/data/" + cls.namespace + "/functions/" + f.IdentifyWithParams + ".mcfunction"),
                                    f.cmdStr.toByteArray()
                                )
                            }
                        }
                    }
                }
            }
            //写入标签json文件
            for (tag in GlobalField.functionTags.values) {
                Project.debug("Writing File: " + path + "/${Project.name}/data/" + tag.namespace + "/tags/functions/" + tag.tag + ".json")
                Files.createDirectories(Paths.get(path + "/${Project.name}/data/" + tag.namespace + "/tags/functions"))
                Files.write(
                    Paths.get(path + "/${Project.name}/data/" + tag.namespace + "/tags/functions/" + tag.tag + ".json"),
                    tag.tagJSON.toByteArray()
                )
            }
        } catch (e: IOException) {
            throw e
        }
    }

    /**
     * 删除原有的数据包中的全部内容。
     * @param directory 文件或文件夹
     */
    private fun delAllFile(directory: File) {
        if (!directory.isDirectory) {
            directory.delete()
        } else {
            val files: Array<out File>? = directory.listFiles()

            // 空文件夹
            if (files!!.isEmpty()) {
                directory.delete()
                return
            }

            // 删除子文件夹和子文件
            for (file in files) {
                    if (file.isDirectory) {
                        delAllFile(file)
                    } else {
                        file.delete()
                    }
                }

            // 删除文件夹本身
            directory.delete()
        }
    }

    /**
     * 数据包的元数据。用于创建pack.mcmeta文件。
     *
     * @property pack
     * @constructor Create empty Datapack mc meta
     */
    internal class DatapackMcMeta(var pack: Pack) {
        internal class Pack(var pack_format: Int, var description: String)
    }
}