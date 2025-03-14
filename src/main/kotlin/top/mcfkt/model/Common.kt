package top.mcfkt.model

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.DatapackCreator
import top.mcfpp.model.Namespace
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import kotlin.io.path.absolutePathString

fun config(operation: ProjectConfig.() -> Unit){
    Project.config.operation()
}

fun namespace(identifier: String, operation: top.mcfkt.model.Namespace.() -> Unit){
    val l = Project.currNamespace
    val id = "$l.$identifier"
    val ns = Namespace(id)
    GlobalField.localNamespaces[id] = ns
    Project.currNamespace = id
    Namespace(ns).operation()
    Project.currNamespace = l
}

fun namespace(operation: top.mcfkt.model.Namespace.() -> Unit){
    namespace(TempPool.getNamespaceIdentify(), operation)
}

fun export(){
    Project.optimization() //优化
    Project.genIndex() //生成索引
    Project.ctx = null
    if(!Project.config.noDatapack){
        Project.compileStage++
        try{
            DatapackCreator.createDatapack(Project.config.targetPath!!.absolutePathString()) //生成数据包
        }catch (e: Exception){
            LogProcessor.error("Cannot create datapack in path: ${Project.config.targetPath}", e)
        }
        Project.stageProcessor[Project.compileStage].forEach { it() }
    }
    if(CompileSettings.printAll) GlobalField.printAll()
}