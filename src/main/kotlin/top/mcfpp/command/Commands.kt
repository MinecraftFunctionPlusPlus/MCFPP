package top.mcfpp.command

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.core.lang.ClassPointer
import top.mcfpp.core.lang.EntityVar
import top.mcfpp.core.lang.EntityVarConcrete
import top.mcfpp.core.lang.MCInt
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.util.Utils
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

    fun selectRun(a : CanSelectMember, command: Command, hasExecuteRun: Boolean = true) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                val qwq = arrayOf(
                    Command.build("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system ${Project.config.rootNamespace}.stack_frame[${a.stackIndex}].${a.identifier}"),
                    Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin")
                )
                if(hasExecuteRun) {
                    qwq.last().build("run","run").build(command)
                }else{
                    qwq.last().build(command)
                }
                qwq
            }
            is MCFPPClassType -> {
                if(hasExecuteRun){
                    arrayOf(Command.build("execute as ${(a.cls as ObjectClass).uuid} run").build(command))
                }else{
                    arrayOf(Command.build("execute as ${(a.cls as ObjectClass).uuid}").build(command))
                }
            }
            else -> TODO()
        }
        return final
    }

    fun selectRun(a : CanSelectMember, hasExecuteRun: Boolean = true) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                val qwq = arrayOf(
                    Command.build("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system ${Project.config.rootNamespace}.stack_frame[${a.stackIndex}].${a.identifier}"),
                    Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin")
                )
                if(hasExecuteRun) {
                    qwq.last().build("run","run")
                }
                qwq
            }
            is MCFPPClassType -> {
                if(hasExecuteRun){
                    arrayOf(Command.build("execute as ${(a.cls as ObjectClass).uuid} run"))
                }else{
                    arrayOf(Command.build("execute as ${(a.cls as ObjectClass).uuid}"))
                }
            }
            else -> TODO()
        }
        return final
    }

    fun selectRun(a : CanSelectMember, command: String, hasExecuteRun: Boolean = true) : Array<Command>{
        return selectRun(a, Command.build(command), hasExecuteRun)
    }

    fun fakeFunction(parent: Function , operation: () -> Unit) : Array<Command>{
        val l = Function.currFunction
        val f = NoStackFunction("", parent)
        Function.currFunction = f
        operation()
        Function.currFunction = l
        return f.commands.toTypedArray()
    }

    fun runAsEntity(entityVar: EntityVar, command: Command): Array<Command>{
        return if(entityVar is EntityVarConcrete){
            arrayOf(Command("execute ${Utils.fromNBTArrayUUID(entityVar.value)} run").build(command))
        }else{
            arrayOf(
                Command("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from").build(entityVar.nbtPath.toCommandPart()),
                Command("execute as ${ClassPointer.tempItemEntityUUID} on origin run").build(command)
            )
        }
    }
}