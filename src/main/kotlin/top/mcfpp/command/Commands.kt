package top.mcfpp.command

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import java.util.*

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
     * scoreboard players get <target.name> <target.object>
     *
     * @param target
     * @return
     */
    fun sbPlayerGet(target: MCInt): Command{
        return Command.build("scoreboard players get")
            .build(target.name,target.name)
            .build(target.sbObject.toString(),target.sbObject.toString())
    }

    /**
     * scoreboard players add <target.name> <target.object.toString()> value
     *
     * @param target
     * @param value
     * @return
     */
    fun sbPlayerAdd(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players add")
            .build(target.name, target.name)
            .build(target.sbObject.toString(), target.sbObject.toString())
            .build("$value")
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
        return Command.build("scoreboard players operation")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(operation,"operation")
            .build(b.name,b.name)
            .build(b.sbObject.toString(),b.sbObject.toString())
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
            .build(target.name, target.name)
            .build(target.sbObject.toString(), target.sbObject.toString())
            .build(value.toString())
    }

    fun sbPlayerSet(a: MCInt, value: Int): Command {
        return Command.build("scoreboard players set ")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(value.toString())
    }

    fun dataSetValue(a: NBTPath, value: Tag<*>): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set value ${SNBTUtil.toSNBT(value)}")
    }

    fun dataSetFrom(a: NBTPath, b: NBTPath): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set from")
            .build(b.toCommandPart())
    }

    fun selectRun(a : CanSelectMember, command: Command) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                arrayOf(
                    Command.build("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system ${Project.config.rootNamespace}.stack_frame[${a.stackIndex}].${a.identifier}"),
                    Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin").build("run","run").build(command)
                )
            }
            is MCFPPClassType -> {
                arrayOf(Command.build("execute as ${a.cls.uuid} run").build(command))
            }
            else -> TODO()
        }
        return final
    }

    fun selectRun(a : CanSelectMember) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                arrayOf(
                    Command.build("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system ${Project.config.rootNamespace}.stack_frame[${a.stackIndex}].${a.identifier}"),
                    Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin").build("run","run")
                )
            }
            is MCFPPClassType -> {
                arrayOf(Command.build("execute as ${a.cls.uuid}").build("run","run"))
            }
            else -> TODO()
        }
        return final
    }

    fun selectRun(a : CanSelectMember, command: String) : Array<Command>{
        return selectRun(a, Command.build(command))
    }

    /**
     * 将此命令以宏命令的方式调用。宏命令的转换方式参考[Command.toMacro]
     *
     * @return 用来调用 执行宏参数的函数 的function命令
     */
    fun buildMacroCommand(command: Command) : Command{
        val f = UUID.randomUUID().toString()
        Project.macroFunction[f] = command.toMacro()
        return Command.build("function mcfpp:dynamic/$f")
    }

    fun fakeFunction(parent: Function , operation: () -> Unit) : Array<Command>{
        val l = Function.currFunction
        val f = NoStackFunction("", parent)
        Function.currFunction = f
        operation()
        Function.currFunction = l
        return f.commands.toTypedArray()
    }
}