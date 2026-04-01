package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.io.FileUtils.delAllFile
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.toSnakeCase
import top.mcfpp.util.Utils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path


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

        if(Project.config.copyImport){
            LogProcessor.debug("Copy libs...")
            //标准库
            delAllFile(File("$path/Imports"))
            //新建文件夹
            File("$path/Imports/data").mkdirs()
            //复制库
            for(module in Project.modules){
                module.extract(Path("$path/Imports/data"))
            }
            val importMcMeta = DatapackMcMeta(
                DatapackMcMeta.Pack(
                    Utils.getVersion(Project.config.version),
                    "MCFPP imports"
                )
            )
            val importMcMetaJson: String = JSON.toJSONString(importMcMeta)
            Files.write(Paths.get("$path/Imports/pack.mcmeta"), importMcMetaJson.toByteArray())

        }

        LogProcessor.debug("Creating datapack...")
        //生成
        val datapackMcMeta = DatapackMcMeta(
            DatapackMcMeta.Pack(
                Utils.getVersion(Project.config.version),
                Project.config.description
            )
        )
        val datapackMcMetaJson: String = JSON.toJSONString(datapackMcMeta)
        //创建文件夹
        try {
            Files.createDirectories(Paths.get("$path/${Project.config.name}/data"))
            //创建pack.mcmeta
            Files.write(Paths.get("$path/${Project.config.name}/pack.mcmeta"), datapackMcMetaJson.toByteArray())
            //写入函数文件
            for(namespace in GlobalScope.localNamespaces){
                genNamespace(path, namespace)
            }
            for (namespace in GlobalScope.stdNamespaces){
                genNamespace(path, namespace)
            }
            //写入宏函数
            for ((function, command) in Project.macroFunction){
                val currPath = "$path/${Project.config.name}/data/mcfpp/function/dynamic/${function}.mcfunction"
                LogProcessor.debug("Writing File: $currPath")
                Files.createDirectories(Paths.get(currPath).parent)
                Files.write(Paths.get(currPath), command.toByteArray())
            }
            //写入标签json文件
            for (tag in GlobalScope.functionTags.values) {
                LogProcessor.debug("Writing File: " + path + "\\${Project.config.name}\\data\\" + tag.namespace + "\\tags\\function\\" + tag.identifier + ".json")
                Files.createDirectories(Paths.get(path + "/${Project.config.name}/data/" + tag.namespace + "/tags/function"))
                Files.write(
                    Paths.get(path + "/${Project.config.name}/data/" + tag.namespace + "/tags/function/" + tag.identifier + ".json"),
                    tag.tagJSON.toByteArray()
                )
            }
        } catch (e: IOException) {
            throw e
        }
        //如果有额外数据，复制并覆盖可能的重复文件
        if(Project.config.dataPath != null){
            LogProcessor.debug("Copying extra data...")
            Utils.copyRecursively(Project.config.dataPath!!, Paths.get(path + "\\${Project.config.name}\\data"), true)
        }
    }

    private fun genFunction(currPath: String, f: Function){
        if (f is Native) return
        LogProcessor.debug("Writing File: $currPath\\${f.identifierWithParamType.toSnakeCase()}.mcfunction")
        f.commands.analyzeAll()
        val path = if(f is ExtensionFunction){
            "$currPath\\ex"
        }else{
            currPath
        }
        Files.createDirectories(Paths.get(path))
        Files.write(Paths.get("$path\\${f.identifierWithParamType.toSnakeCase()}.mcfunction"), f.cmdStr.toByteArray())
        if(f.compiledFunctions.isNotEmpty()){
            for (cf in f.compiledFunctions.values) {
                LogProcessor.debug("Writing File: $currPath\\${cf.identifierWithParamType.toSnakeCase()}.mcfunction")
                f.commands.analyzeAll()
                Files.write(Paths.get("$path\\${f.identifierWithParamType.toSnakeCase()}.mcfunction"), cf.cmdStr.toByteArray())
            }
        }
    }

    private fun genTemplateFunction(currPath: String, f: Function){
        if (f is Native) return
        val path = if(f is ExtensionFunction) "$currPath\\ex" else currPath
        Files.createDirectories(Paths.get(path))
        for (cf in f.compiledFunctions.values) {
            LogProcessor.debug("Writing File: $currPath\\${cf.identifierWithParamType.toSnakeCase()}.mcfunction")
            f.commands.analyzeAll()
            Files.write(Paths.get("$path\\${f.identifierWithParamType.toSnakeCase()}.mcfunction"), cf.cmdStr.toByteArray())
        }
    }

    private fun genObject(currPath: String, obj: CompoundData){
        //成员
        obj.scope.forEachFunction {
            genFunction("${currPath}\\function\\${obj.identifier.toSnakeCase()}\\static", it)
            it.compiledFunctions.values.forEach {qwq ->
                genFunction("${currPath}\\function\\${obj.identifier.toSnakeCase()}\\static", qwq)
            }
        }
    }

    private fun genTemplate(currPath: String, t: DataTemplate){
        //成员
        t.scope.forEachFunction {
            genTemplateFunction("$currPath\\function\\${t.identifier.toSnakeCase()}", it)
            it.compiledFunctions.values.forEach {qwq ->
                genTemplateFunction("$currPath\\function\\${t.identifier.toSnakeCase()}", qwq)
            }
        }
        t.constructors.forEach {
            genTemplateFunction("$currPath\\function\\${t.identifier.toSnakeCase()}", it)
            it.compiledFunctions.values.forEach {qwq ->
                genTemplateFunction("$currPath\\function\\${t.identifier.toSnakeCase()}", qwq)
            }
        }
    }

    private fun genNamespace(path: String, namespace: MutableMap.MutableEntry<String, Namespace>) {
        val currPath = "$path\\${Project.config.name}\\data\\${namespace.key}"

        namespace.value.scope.forEachFunction {
            genFunction("$currPath\\function", it)
            it.compiledFunctions.values.forEach { qwq ->
                genFunction("$currPath\\function", qwq)
            }
        }

        namespace.value.scope.forEachTemplate {
            genTemplate(currPath, it)
        }

        namespace.value.scope.forEachObject {
            genObject(currPath, it)
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