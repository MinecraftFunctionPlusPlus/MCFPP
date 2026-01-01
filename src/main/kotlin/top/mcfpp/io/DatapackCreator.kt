package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.io.FileUtils.delAllFile
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.toSnakeCase
import top.mcfpp.util.Utils
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes


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
        val basePath = Path(path)
        val datapackPath = basePath.resolve(Project.config.name)

        LogProcessor.debug("Clearing output folder...")
        //清空原输出文件夹
        delAllFile(datapackPath.toFile())

        if(Project.config.copyImport){
            LogProcessor.debug("Copy libs...")
            //标准库
            val importsPath = basePath.resolve("Imports")
            delAllFile(importsPath.toFile())
            //新建文件夹
            importsPath.resolve("data").createDirectories()
            //复制库
            for(module in Project.modules){
                module.extract(importsPath.resolve("data"))
            }
            val importMcMeta = DatapackMcMeta(
                DatapackMcMeta.Pack(
                    Utils.getVersion(Project.config.version),
                    "MCFPP imports"
                )
            )
            val importMcMetaJson: String = JSON.toJSONString(importMcMeta)
            importsPath.resolve("pack.mcmeta").writeBytes(importMcMetaJson.toByteArray())
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
            datapackPath.resolve("data").createDirectories()
            //创建pack.mcmeta
            datapackPath.resolve("pack.mcmeta").writeBytes(datapackMcMetaJson.toByteArray())
            //写入函数文件
            for(namespace in GlobalScope.localNamespaces){
                genNamespace(datapackPath, namespace)
            }
            for (namespace in GlobalScope.stdNamespaces){
                genNamespace(datapackPath, namespace)
            }
            //写入宏函数
            for ((function, command) in Project.macroFunction){
                val currPath = datapackPath.resolve("data").resolve("mcfpp").resolve("function").resolve("dynamic").resolve("${function}.mcfunction")
                LogProcessor.debug("Writing File: $currPath")
                currPath.parent.createDirectories()
                currPath.writeBytes(command.toByteArray())
            }
            //写入标签json文件
            for (tag in GlobalScope.functionTags.values) {
                val tagPath = datapackPath.resolve("data").resolve(tag.namespace).resolve("tags").resolve("function")
                val jsonPath = tagPath.resolve("${tag.identifier}.json")
                LogProcessor.debug("Writing File: ${jsonPath}")
                tagPath.createDirectories()
                jsonPath.writeBytes(tag.tagJSON.toByteArray())
            }
        } catch (e: IOException) {
            throw e
        }
    }

    private fun genFunction(currPath: Path, f: Function){
        if (f is Native) return
        LogProcessor.debug("Writing File: ${currPath.resolve("${f.identifierWithParamType.toSnakeCase()}.mcfunction")}")
        f.commands.analyzeAll()
        val path = if(f is ExtensionFunction){
            currPath.resolve("ex")
        }else{
            currPath
        }
        path.createDirectories()
        path.resolve("${f.identifierWithParamType.toSnakeCase()}.mcfunction").writeBytes(f.cmdStr.toByteArray())
        if(f.compiledFunctions.isNotEmpty()){
            for (cf in f.compiledFunctions.values) {
                LogProcessor.debug("Writing File: ${currPath.resolve("${cf.identifierWithParamType.toSnakeCase()}.mcfunction")}")
                f.commands.analyzeAll()
                path.resolve("${f.identifierWithParamType.toSnakeCase()}.mcfunction").writeBytes(cf.cmdStr.toByteArray())
            }
        }
    }

    private fun genTemplateFunction(currPath: Path, f: Function){
        if (f is Native) return
        val path = if(f is ExtensionFunction) currPath.resolve("ex") else currPath
        path.createDirectories()
        for (cf in f.compiledFunctions.values) {
            LogProcessor.debug("Writing File: ${currPath.resolve("${cf.identifierWithParamType.toSnakeCase()}.mcfunction")}")
            f.commands.analyzeAll()
            path.resolve("${f.identifierWithParamType.toSnakeCase()}.mcfunction").writeBytes(cf.cmdStr.toByteArray())
        }
    }

    private fun genObject(currPath: Path, obj: CompoundData){
        //成员
        obj.field.forEachFunction {
            genFunction(currPath.resolve("function").resolve(obj.identifier.toSnakeCase()).resolve("static"), it)
        }
    }

    private fun genTemplate(currPath: Path, t: DataTemplate){
        //成员
        t.field.forEachFunction {
            genTemplateFunction(currPath.resolve("function").resolve(t.identifier.toSnakeCase()), it)
        }
        t.constructors.forEach {
            genTemplateFunction(currPath.resolve("function").resolve(t.identifier.toSnakeCase()), it)
        }
    }

    private fun genClass(currPath: Path, cls: Class) {
        //成员
        cls.field.forEachFunction {
            genFunction(currPath.resolve("function").resolve(cls.identifier.toSnakeCase()), it)
        }
        cls.constructors.forEach {
            genFunction(currPath.resolve("function").resolve(cls.identifier.toSnakeCase()), it)
        }
    }

    private fun genNamespace(datapackPath: Path, namespace: MutableMap.MutableEntry<String, Namespace>) {
        val currPath = datapackPath.resolve("data").resolve(namespace.key)

        namespace.value.field.forEachFunction {
            genFunction(currPath.resolve("function"), it)
        }

        namespace.value.field.forEachClass {
            genClass(currPath, it)
        }

        namespace.value.field.forEachTemplate {
            genTemplate(currPath, it)
        }

        namespace.value.field.forEachObject {
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