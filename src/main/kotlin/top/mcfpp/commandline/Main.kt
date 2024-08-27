package top.mcfpp.commandline

import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.MCFPP
import top.mcfpp.core.lang.UnresolvedVar
import top.mcfpp.model.field.GlobalField
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
    for(namespace in GlobalField.libNamespaces.values){
        namespace.field.forEachClass { c ->
            run {
                for (v in c.field.allVars){
                    if(v is UnresolvedVar){
                        c.field.putVar(c.identifier, v, true)
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
            print("> ")
        }
        val line = readln()
        if(line.startsWith("get ")){
            val name = line.substring(4)
            val v = compiler.defaultFile.topFunction.field.getVar(name)
            if(v == null){
                println("No such variable")
            }else{
                println(v)
            }
            continue
        }
        when(line){
            "help" -> printHelp()
            "quit" -> return
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

fun printHelp() {
    val helpMessage = """
        Welcome to MCFPP ${MCFPP.version}'s help utility! If this is your first time using
        MCFPP, you should definitely check out the documentation at
        https://www.mcfpp.top/.
        
        Enter any valid MCFPP code to execute in this interactive shell.
        
        To display version details, enter "version".
        
        To quit the shell, enter "quit".
    """.trimIndent()
    println(helpMessage)
}
