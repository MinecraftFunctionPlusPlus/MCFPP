package top.mcfpp.io

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.io.FileUtils.delAllFile
import top.mcfpp.model.Namespace
import top.mcfpp.model.Native
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper.toSnakeCase
import top.mcfpp.util.Utils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


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
                module.extract(Paths.get(path, "Imports", "data"))
            }
            val importMcMetaJson = packMcMetaJson(Project.config.version, "MCFPP imports")
            Files.write(Paths.get("$path/Imports/pack.mcmeta"), importMcMetaJson.toByteArray())

        }

        LogProcessor.debug("Creating datapack...")
        //生成
        val datapackMcMetaJson = packMcMetaJson(Project.config.version, Project.config.description)
        //创建文件夹
        try {
            Files.createDirectories(Paths.get("$path/${Project.config.name}/data"))
            //创建pack.mcmeta
            Files.write(Paths.get("$path/${Project.config.name}/pack.mcmeta"), datapackMcMetaJson.toByteArray())
            //写入函数文件
            for(namespace in GlobalScope.localNamespaces){
                genNamespace(Paths.get(path), namespace)
            }
            for (namespace in GlobalScope.stdNamespaces){
                genNamespace(Paths.get(path), namespace)
            }
            genCompiledLibraryFunctions(Paths.get(path))
            //写入宏函数
            for ((function, command) in Project.macroFunction){
                val currPath = Paths.get(path, Project.config.name, "data", "mcfpp", "function", "dynamic", "${function}.mcfunction")
                LogProcessor.debug("Writing File: $currPath")
                Files.createDirectories(currPath.parent)
                Files.write(currPath, command.toByteArray())
            }
            //写入标签json文件
            for (tag in GlobalScope.functionTags.values) {
                val tagPath = Paths.get(path, Project.config.name, "data", tag.namespace, "tags", "function", "${tag.identifier}.json")
                LogProcessor.debug("Writing File: $tagPath")
                Files.createDirectories(tagPath.parent)
                Files.write(tagPath, tag.tagJSON.toByteArray())
            }
        } catch (e: IOException) {
            throw e
        }
        //如果有额外数据，复制并覆盖可能的重复文件
        if(Project.config.dataPath != null){
            LogProcessor.debug("Copying extra data...")
            Utils.copyRecursively(Project.config.dataPath!!, Paths.get(path, Project.config.name, "data"), true)
        }
    }

    private fun genFunction(currPath: Path, f: Function){
        if (f is Native) return
        f.commands.analyzeAll()
        val directory = if (f is ExtensionFunction) currPath.resolve("ex") else currPath
        val output = directory.resolve("${f.identifierWithParamType.toSnakeCase()}.mcfunction")
        LogProcessor.debug("Writing File: $output")
        Files.createDirectories(directory)
        Files.write(output, f.cmdStr.toByteArray())
    }

    /** Imported bodies are generated in the consumer pack at their actual call targets. */
    private fun genCompiledLibraryFunctions(path: Path) {
        val functions = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        val compounds = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())

        fun exportFunction(function: Function) {
            if (!functions.add(function)) return
            if (function !is Native && function.bodyCompiled) {
                function.commands.analyzeAll()
                val id = function.namespaceID
                val output = path.resolve(Project.config.name).resolve("data")
                    .resolve(id.namespace).resolve("function").resolve("${id.identifier}.mcfunction")
                LogProcessor.debug("Writing File: $output")
                Files.createDirectories(output.parent)
                Files.write(output, function.cmdStr.toByteArray())
            }
            function.compiledFunctions.values.forEach(::exportFunction)
        }

        fun exportCompound(compound: CompoundData) {
            if (!compounds.add(compound)) return
            compound.scope.forEachFunction(::exportFunction)
            if (compound is DataTemplate) {
                compound.constructors.forEach(::exportFunction)
                compound.companionObject?.let(::exportCompound)
            }
            if (compound is GenericDataTemplate) {
                compound.compiledTemplates.values.forEach(::exportCompound)
            }
        }

        for (namespace in GlobalScope.libNamespaces.values) {
            val scope = namespace.scope
            scope.forEachFunction(::exportFunction)
            scope.template.values.forEach(::exportCompound)
            scope.genericTemplate.values.forEach(::exportCompound)
            scope.interfaces.values.forEach(::exportCompound)
            scope.genericInterfaces.values.forEach(::exportCompound)
            scope.objects.forEach(::exportCompound)
        }
    }

    private fun genTemplateFunction(currPath: Path, f: Function){
        if (f is Native) return
        genFunction(currPath, f)
        f.compiledFunctions.values.forEach { genFunction(currPath, it) }
    }

    private fun genObject(currPath: Path, obj: CompoundData){
        //成员
        obj.scope.forEachFunction {
            genFunction(currPath.resolve("function").resolve(obj.identifier.toSnakeCase()).resolve("static"), it)
            it.compiledFunctions.values.forEach {qwq ->
                genFunction(currPath.resolve("function").resolve(obj.identifier.toSnakeCase()).resolve("static"), qwq)
            }
        }
        if (obj is DataTemplate) obj.constructors.forEach {
            genTemplateFunction(currPath.resolve("function").resolve(obj.identifier.toSnakeCase()).resolve("static"), it)
        }
    }

    private fun genTemplate(currPath: Path, t: DataTemplate){
        //成员
        t.scope.forEachFunction {
            genTemplateFunction(currPath.resolve("function").resolve(t.identifier.toSnakeCase()), it)
        }
        t.constructors.forEach {
            genTemplateFunction(currPath.resolve("function").resolve(t.identifier.toSnakeCase()), it)
        }
    }

    private fun genNamespace(path: Path, namespace: MutableMap.MutableEntry<String, Namespace>) {
        val currPath = path.resolve(Project.config.name).resolve("data").resolve(namespace.key)
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<CompoundData, Boolean>())
        fun exportCompound(compound: CompoundData) {
            if (!visited.add(compound)) return
            when (compound) {
                is ObjectCompoundData -> genObject(currPath, compound)
                is DataTemplate -> genTemplate(currPath, compound)
            }
            if (compound is GenericDataTemplate) compound.compiledTemplates.values.forEach(::exportCompound)
        }

        namespace.value.scope.forEachFunction {
            genFunction(currPath.resolve("function"), it)
            it.compiledFunctions.values.forEach { qwq ->
                genFunction(currPath.resolve("function"), qwq)
            }
        }

        namespace.value.scope.forEachTemplate {
            exportCompound(it)
        }

        namespace.value.scope.forEachObject {
            exportCompound(it)
        }
    }


    /**
     * 数据包的元数据。用于创建pack.mcmeta文件。
     *
     * @property pack
     * @constructor Create empty Datapack mc meta
     */
    internal fun packMcMetaJson(version: String, description: String): String {
        val pack = linkedMapOf<String, Any>("description" to description)
        val capability = top.mcfpp.command.TargetCapabilities.forVersion(version)
            ?: throw IllegalArgumentException("Unsupported version: $version")
        val format = capability.packFormat
        if (format == null) {
            pack["pack_format"] = Utils.getVersion(version)
        } else {
            pack["min_format"] = format
            pack["max_format"] = format
        }
        return JSON.toJSONString(mapOf("pack" to pack))
    }
}
