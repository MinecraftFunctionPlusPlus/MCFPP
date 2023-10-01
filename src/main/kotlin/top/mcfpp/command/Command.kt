package top.mcfpp.command

import java.lang.StringBuilder

/**
 * 一条编译时动态命令
 *
 * @constructor Create empty Command
 */
class Command {
    val argsList = ArrayList<String>()

    fun analyze(): String{
        val sb = StringBuilder()
        for (arg in argsList){
            sb.append(arg)
        }
        return sb.toString()
    }
}