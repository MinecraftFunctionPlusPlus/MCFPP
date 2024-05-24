package top.mcfpp.util

import top.mcfpp.Project
import top.mcfpp.command.Command
import java.util.*

object MacroHelper {

    /**
     * 将此命令以宏命令的方式调用。宏命令的转换方式参考[Command.toMacro]
     *
     * @return 用来调用 执行宏参数的函数 的function命令
     */
    fun addMacroCommand(command: Command): Command {
        val f = UUID.randomUUID().toString()
        Project.macroFunction[f] = command.toMacro()
        return Command.build("function mcfpp:dynamic/$f")
    }

}