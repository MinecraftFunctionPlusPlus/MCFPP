package top.alumopper.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.lib.*
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
 */
object DatapackCreator {
    /**
     * TODO
     * 在指定的路径生成一个数据包的框架
     * @param path 路径
     */
    fun createDatapack(path: String) {
        Project.logger.debug("Clearing output folder...")
        //清空原输出文件夹
        delAllFile(File("$path/out"))
        Project.logger.debug("Creating datapack...")
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
            Files.createDirectories(Paths.get("$path/out/data"))
            //创建pack.mcmeta
            Files.write(Paths.get("$path/out/pack.mcmeta"), datapackMcMetaJson.toByteArray())
            //写入函数文件
            for (f in Project.global.cache!!.functions) {
                if (f is Native) {
                    continue
                }
                Project.logger.debug("Writing File: " + path + "/out/data/" + f.namespace + "/functions/" + f.name + ".mcfunction")
                Files.createDirectories(Paths.get(path + "/out/data/" + f.namespace + "/functions"))
                Files.write(
                    Paths.get(path + "/out/data/" + f.namespace + "/functions/" + f.name + ".mcfunction"),
                    f.cmdStr.toByteArray()
                )
            }
            //写入类的函数文件
            for (cls in Project.global.cache!!.classes.values) {
                if (cls is Native) {
                    continue
                }
                //成员
                for (f in cls.cache!!.functions) {
                    if (f is Native) {
                        continue
                    }
                    Project.logger.debug("Writing File: " + path + "/out/data/" + cls.namespace + "/functions/" + cls.identifier + "/" + f.name + ".mcfunction")
                    Files.createDirectories(Paths.get(path + "/out/data/" + cls.namespace + "/functions/" + cls.identifier))
                    Files.write(
                        Paths.get(path + "/out/data/" + cls.namespace + "/functions/" + cls.identifier + "/" + f.name + ".mcfunction"),
                        f.cmdStr.toByteArray()
                    )
                }
                //静态
                for (f in cls.staticCache!!.functions) {
                    if (f is Native) {
                        continue
                    }
                    Project.logger.debug("Writing File: " + path + "/out/data/" + cls.namespace + "/functions/" + cls.identifier + "/static/" + f.name + ".mcfunction")
                    Files.createDirectories(Paths.get(path + "/out/data/" + f.namespace + "/functions/" + cls.identifier + "/static"))
                    Files.write(
                        Paths.get(path + "/out/data/" + cls.namespace + "/functions/" + cls.identifier + "/static/" + f.name + ".mcfunction"),
                        f.cmdStr.toByteArray()
                    )
                }
            }
            //写入标签json文件
            for (tag in Project.global.functionTags!!.values) {
                Project.logger.debug("Writing File: " + path + "/out/data/" + tag.namespace + "/tags/functions/" + tag.tag + ".json")
                Files.write(
                    Paths.get(path + "/out/data/" + tag.namespace + "/tags/functions/" + tag.tag + ".json"),
                    tag.tagJSON.toByteArray()
                )
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 删除文件或文件夹
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

    internal class DatapackMcMeta(var pack: Pack) {
        internal class Pack(var pack_format: Int, var description: String)
    }
}