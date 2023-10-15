package top.mcfpp.command

import top.mcfpp.lib.Function
import top.mcfpp.lang.MCInt

/**
 * 命令总类，提供了大量用于生成命令的方法。默认提供了一些可替换的位点
 */
object Commands {

    /**
     * function <function.namespaceID>
     *
     * @param function
     * @return
     */
    fun function(function: Function): Command {
        return Command.build("function").build(function.namespaceID,function.namespaceID)
    }

    /**
     * scoreboard players add <target.name> <target.object.toString()> value
     *
     * @param target
     * @param value
     * @return
     */
    fun sbPlayerAdd(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players add ")
            .build(target.name, target.name)
            .build(" ")
            .build(target.`object`.toString(), target.`object`.toString())
            .build(" $value")
    }

    /**
     * scoreboard players operation <a.name> <a.object.toString()> <operation> <b.name> <b.object.toString()>
     *
     * @param a
     * @param operation
     * @param b
     * @return
     */
    fun sbPlayerOperation(a: MCInt, operation: String, b: MCInt): Command {
        return Command.build("scoreboard players operation ")
            .build(a.name,a.name).build(" ")
            .build(a.`object`.toString(),a.`object`.toString()).build(" ")
            .build(operation,"operation").build(" ")
            .build(b.name,b.name).build(" ")
            .build(b.`object`.toString(),b.`object`.toString())
    }

    /**
     * scoreboard players remove <target.name> <target.object.toString()> value
     *
     * @param target
     * @param value
     * @return
     */
    fun sbPlayerRemove(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players remove ")
            .build(target.name,target.name).build(" ")
            .build(target.`object`.toString(),target.`object`.toString()).build(" ")
            .build(value.toString())
    }

    fun sbPlayerSet(a: MCInt, value: Int): Command {
        return Command.build("scoreboard players set ")
            .build(a.name,a.name).build(" ")
            .build(a.`object`.toString(),a.`object`.toString()).build(" ")
            .build(value.toString())
    }
}