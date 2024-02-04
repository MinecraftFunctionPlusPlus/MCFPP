package top.mcfpp.commandline

import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Configurator
import top.mcfpp.MCFPP
import java.io.FileInputStream
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Date

fun main(){
    val source: ConfigurationSource
    try {
        source = ConfigurationSource(FileInputStream("log4j2.xml"))
        Configurator.initialize(null,source)
    }catch (e:Exception){
        println("Failed to load log4j2.xml")
    }
    println("MCFPP ${MCFPP.version} (${LocalDateTime.now()})")
}