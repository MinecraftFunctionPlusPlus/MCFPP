package top.mcfpp.commandline

import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.MCFPP
import top.mcfpp.Project
import top.mcfpp.io.LibReader
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lib.field.GlobalField
import top.mcfpp.util.UwU
import java.io.FileInputStream
import java.time.Instant

fun main(){
    val source: ConfigurationSource
    try {
        source = ConfigurationSource(FileInputStream("log4j2.xml"))
        Configurator.initialize(null,source)
    }catch (e:Exception){
        println("Failed to load log4j2.xml")
    }
    //导入基础库
    for (include in Project.config.stdLib) {
        LibReader.read(include)
    }
    for(namespace in GlobalField.libNamespaces.values){
        namespace.forEachClass { c ->
            run {
                for (v in c.field.allVars){
                    if(v is UnresolvedVar){
                        c.field.putVar(c.identifier, v, true)
                    }
                }
                for (v in c.staticField.allVars){
                    if(v is UnresolvedVar){
                        c.staticField.putVar(c.identifier, v, true)
                    }
                }
            }
        }
    }
    GlobalField.init()
    println("MCFPP ${MCFPP.version} (${Instant.now()})")
    println("Tips: " + UwU.tip) //生成tips
    val compiler = LineCompiler()
    //等待输入
    while(true){
        if(compiler.leftBraces == 0){
            print(">")
        }
        when(val line = readln()){
            "exit" -> return
            "version" -> println("MCFPP ${MCFPP.version}")
            else -> {
                try {
                    compiler.compile(line)
                }catch (e:Exception){
                    e.printStackTrace()
                    //throw e
                }
            }
        }

    }
}