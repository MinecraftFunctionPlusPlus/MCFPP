package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import top.mcfpp.Project
import top.mcfpp.model.*
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import top.mcfpp.util.Utils
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.io.path.name


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
        LogProcessor.debug("Clearing output folder...")
        //清空原输出文件夹
        delAllFile(File("$path/${Project.config.name}"))
        LogProcessor.debug("Copy libs...")
        //复制库
        for (lib in Project.config.includes){
            val filePath = if(!lib.endsWith("/.mclib")) {
                "$lib/.mclib"
            }else{
                lib
            }
            //逐行读取
            val fileReader = FileReader(filePath)
            val jsonString = fileReader.readText()
            fileReader.close()
            //解析json
            val json = JSONObject.parse(jsonString) as JSONObject
            val scr = json.getString("src")
            if(scr != null){
                val scrPath = filePath.substring(0,filePath.lastIndexOf(".")) + scr
                val qwq = Paths.get(scrPath)
                // 获取所有子文件夹
                val subdirectories = Files.walk(qwq, 1)
                    .filter(Files::isDirectory)
                    .skip(1)
                    .collect(Collectors.toList())
                for (subdirectory in subdirectories) {
                    //复制文件夹
                    delAllFile(File(path + "\\" + subdirectory.name))
                    copyAllFiles(subdirectory.absolutePathString(),path + "\\" + subdirectory.name)
                }
            }
        }
        LogProcessor.debug("Creating datapack...")
        //生成
        val datapackMcMeta = DatapackMcMeta(
            DatapackMcMeta.Pack(
                Utils.getVersion(Project.config.version!!),
                Project.config.description!!
            )
        )
        val datapackMcMetaJson: String = JSON.toJSONString(datapackMcMeta)
        //创建文件夹
        try {
            Files.createDirectories(Paths.get("$path/${Project.config.name}/data"))
            //创建pack.mcmeta
            Files.write(Paths.get("$path/${Project.config.name}/pack.mcmeta"), datapackMcMetaJson.toByteArray())
            for(namespace in GlobalField.localNamespaces){
                val currPath = "$path\\${Project.config.name}\\data\\${namespace.key}"
                namespace.value.field.forEachFunction {f ->
                    run {
                        if (f is Native) {
                            return@run
                        }
                        LogProcessor.debug("Writing File: $currPath\\function\\${f.nameWithNamespace}.mcfunction")
                        Files.createDirectories(Paths.get("$currPath/function"))
                        Files.write(
                            Paths.get("$currPath/function/${f.nameWithNamespace}.mcfunction"),
                            f.cmdStr.toByteArray()
                        )
                        if(f.compiledFunctions.isNotEmpty()){
                            for (cf in f.compiledFunctions.values){
                                LogProcessor.debug("Writing File: $currPath\\function\\${cf.nameWithNamespace}.mcfunction")
                                Files.createDirectories(Paths.get("$currPath/function"))
                                Files.write(
                                    Paths.get("$currPath/function/${cf.nameWithNamespace}.mcfunction"),
                                    cf.cmdStr.toByteArray()
                                )
                            }

                        }
                    }
                }
                namespace.value.field.forEachClass { cls ->
                    run {
                        //成员
                        cls.field.forEachFunction { f->
                            run {
                                if (f is Native) {
                                    return@run
                                }
                                LogProcessor.debug("Writing File: $currPath\\function\\" + f.nameWithNamespace + ".mcfunction")
                                //TODO 可能无法正确创建文件夹
                                Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(cls.identifier)))
                                if (f is ExtensionFunction){
                                    Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(cls.identifier) + "/ex"))
                                }
                                Files.write(
                                    Paths.get("$currPath/function/"  + f.nameWithNamespace + ".mcfunction"),
                                    f.cmdStr.toByteArray()
                                )
                                if(f.compiledFunctions.isNotEmpty()){
                                    for (cf in f.compiledFunctions.values) {
                                        LogProcessor.debug("Writing File: $currPath\\function\\" + cf.nameWithNamespace + ".mcfunction")
                                        //TODO 可能无法正确创建文件夹
                                        Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(cls.identifier)))
                                        Files.write(Paths.get("$currPath/function/" + cf.nameWithNamespace + ".mcfunction"), cf.cmdStr.toByteArray())
                                    }
                                }
                            }
                        }
                        //构造函数
                        cls.constructors.forEach{ c ->
                            run {
                                LogProcessor.debug("Writing File: $currPath\\function\\"  + c.nameWithNamespace + ".mcfunction")
                                //TODO 可能无法正确创建文件夹
                                Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(cls.identifier)))
                                Files.write(
                                    Paths.get("$currPath/function/" + c.nameWithNamespace + ".mcfunction"),
                                    c.cmdStr.toByteArray()
                                )
                                if(c.compiledFunctions.isNotEmpty()){
                                    for (cf in c.compiledFunctions.values) {
                                        LogProcessor.debug("Writing File: $currPath\\function\\" + cf.nameWithNamespace + ".mcfunction")
                                        //TODO 可能无法正确创建文件夹
                                        Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(cls.identifier)))
                                        Files.write(Paths.get("$currPath/function/" + cf.nameWithNamespace + ".mcfunction"), cf.cmdStr.toByteArray())
                                    }
                                }
                            }
                        }
                    }
                }

                namespace.value.field.forEachTemplate { t ->
                    run {
                        //成员
                        t.field.forEachFunction { f->
                            run {
                                if (f is Native) {
                                    return@run
                                }
                                LogProcessor.debug("Writing File: $currPath\\function\\" + f.nameWithNamespace + ".mcfunction")
                                //TODO 可能无法正确创建文件夹
                                Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(t.identifier)))
                                if (f is ExtensionFunction){
                                    Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(t.identifier) + "/ex"))
                                }
                                Files.write(
                                    Paths.get("$currPath/function/"  + f.nameWithNamespace + ".mcfunction"),
                                    f.cmdStr.toByteArray()
                                )
                                if(f.compiledFunctions.isNotEmpty()){
                                    for (cf in f.compiledFunctions.values) {
                                        LogProcessor.debug("Writing File: $currPath\\function\\" + cf.nameWithNamespace + ".mcfunction")
                                        //TODO 可能无法正确创建文件夹
                                        Files.createDirectories(Paths.get("$currPath/function/" + StringHelper.toLowerCase(t.identifier)))
                                        Files.write(Paths.get("$currPath/function/" + cf.nameWithNamespace + ".mcfunction"), cf.cmdStr.toByteArray())
                                    }
                                }
                            }
                        }
                    }
                }
                namespace.value.field.forEachObject { obj ->
                    run {
                        //成员
                        obj.field.forEachFunction { f->
                            run {
                                if (f is Native) {
                                    return@run
                                }
                                LogProcessor.debug("Writing File: $currPath\\function\\" + f.nameWithNamespace + ".mcfunction")
                                //TODO 可能无法正确创建文件夹
                                Files.createDirectories(Paths.get("$currPath\\function\\" + StringHelper.toLowerCase(obj.identifier) + "\\static"))
                                if (f is ExtensionFunction){
                                    Files.createDirectories(Paths.get("$currPath\\function\\" + StringHelper.toLowerCase(obj.identifier) + "\\static\\ex"))
                                }
                                Files.write(
                                    Paths.get("$currPath/function/"  + f.nameWithNamespace + ".mcfunction"),
                                    f.cmdStr.toByteArray()
                                )
                                if(f.compiledFunctions.isNotEmpty()){
                                    for (cf in f.compiledFunctions.values) {
                                        LogProcessor.debug("Writing File: $currPath\\function\\" + cf.nameWithNamespace + ".mcfunction")
                                        //TODO 可能无法正确创建文件夹
                                        Files.createDirectories(Paths.get("$currPath\\function/" + StringHelper.toLowerCase(obj.identifier)))
                                        Files.write(Paths.get("$currPath/function/" + cf.nameWithNamespace + ".mcfunction"), cf.cmdStr.toByteArray())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            //写入标签json文件
            for (tag in GlobalField.functionTags.values) {
                LogProcessor.debug("Writing File: " + path + "\\${Project.config.name}\\data\\" + tag.namespace + "\\tags\\functions\\" + tag.tag + ".json")
                Files.createDirectories(Paths.get(path + "/${Project.config.name}/data/" + tag.namespace + "/tags/functions"))
                Files.write(
                    Paths.get(path + "/${Project.config.name}/data/" + tag.namespace + "/tags/functions/" + tag.tag + ".json"),
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

    @Throws(IOException::class)
    fun copyAllFiles(src: String, dst: String) {
        val srcFolder = Paths.get(src)
        val dstFolder = Paths.get(dst)
        Files.walkFileTree(srcFolder, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetPath = dstFolder.resolve(srcFolder.relativize(dir))
                try {
                    Files.copy(dir, targetPath, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: FileAlreadyExistsException) {
                    if (!Files.isDirectory(targetPath)) throw e
                }
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.copy(file, dstFolder.resolve(srcFolder.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
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