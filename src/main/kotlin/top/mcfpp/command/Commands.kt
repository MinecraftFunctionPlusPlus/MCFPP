package top.mcfpp.command

import top.mcfpp.lib.Function
import java.util.*
import top.mcfpp.lang.MCInt

/**
 * 命令总类，提供了大量用于生成命令的方法，避免频繁的新建类（调用命令类的各种极度抽象的构造方法（x
 * 尽量不要打开或编辑这个文件，不然会感受痛苦。
 * 尽量少地使用这个文件中的内容
 */
object Commands {
    fun Function(function: Function): String {
        return "function " + function.namespaceID.lowercase(Locale.getDefault())
    }

    fun SbPlayerAdd(target: MCInt, value: Int): String {
        return "scoreboard players add " + target.name + " " + target.`object` + " " + value
    }

    fun SbPlayerOperation(a: MCInt, operation: String, b: MCInt): String {
        return "scoreboard players operation " + a.name + " " + a.`object` + " " + operation + " " + b.name + " " + b.`object`
    }

    fun SbPlayerRemove(target: MCInt, value: Int): String {
        return "scoreboard players remove " + target.name + " " + target.`object` + " " + value
    }

    fun SbPlayerSet(a: MCInt, value: Int): String {
        return "scoreboard players set " + a.name + " " + a.`object` + " " + value
    }
}