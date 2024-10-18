package top.mcfpp.command

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.IntArrayTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.util.Utils
import java.util.*
import kotlin.math.truncate

/**
 * 命令总类，提供了大量用于生成命令的方法。默认提供了一些可替换的位点
 */
object Commands {

    /**
     * `function <function.namespaceID>`
     *
     * @param function 函数对象
     * @return 生成的命令
     */
    fun function(function: Function): Command {
        return Command.build("function").build(function.namespaceID,function.namespaceID)
    }

    /**
     * `scoreboard players get <target.name> <target.object>`
     *
     * @param target 被获取计分板分数的目标
     * @return 生成的命令
     */
    fun sbPlayerGet(target: MCInt): Command{
        return Command.build("scoreboard players get")
            .build(target.name,target.name)
            .build(target.sbObject.toString(),target.sbObject.toString())
    }

    /**
     * `scoreboard players add <target.name> <target.object.toString()> value`
     *
     * @param target 被操作的对象
     * @param value 增加的值
     * @return 生成的命令
     */
    fun sbPlayerAdd(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players add")
            .build(target.name, target.name)
            .build(target.sbObject.toString(), target.sbObject.toString())
            .build("$value")
    }

    /**
     * `scoreboard players operation <a.name> <a.object.toString()> <operation> <b.name> <b.object.toString()>`
     *
     * @param a
     * @param operation 操作的字符串，必须为`=`,`<>`, `+=`, `-=`, `*=`, `/=`, `%=`之一。
     * @param b
     * @return 生成的命令
     */
    fun sbPlayerOperation(a: MCInt, operation: String, b: MCInt): Command {
        return Command.build("scoreboard players operation")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(operation,"operation")
            .build(b.name,b.name)
            .build(b.sbObject.toString(),b.sbObject.toString())
    }

    fun sbPlayerOperation(a: ScoreBool, operation: String, b: MCInt): Command {
        return Command.build("scoreboard players operation")
            .build(a.identifier,a.identifier)
            .build(a.boolObject.toString(),a.boolObject.toString())
            .build(operation,"operation")
            .build(b.name,b.name)
            .build(b.sbObject.toString(),b.sbObject.toString())
    }

    /**
     * `scoreboard players remove <target.name> <target.object.toString()> value`
     *
     * @param target 被操作的对象
     * @param value 减少的值
     * @return 生成的命令
     */
    fun sbPlayerRemove(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players remove ")
            .build(target.name, target.name)
            .build(target.sbObject.toString(), target.sbObject.toString())
            .build(value.toString())
    }

    /**
     * `scoreboard players set <a.name> <a.object.toString()> value`
     *
     * @param a 被设置的对象
     * @param value 设置的值
     *
     * @return 生成的命令
     */
    fun sbPlayerSet(a: MCInt, value: Int): Command {
        return Command.build("scoreboard players set")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(value.toString())
    }

    fun sbPlayerSet(a: ScoreBool, value: Boolean): Command {
        return Command.build("scoreboard players set")
            .build(a.identifier,a.identifier)
            .build(a.boolObject.toString(),a.boolObject.toString())
            .build((if(value) 1 else 0).toString())
    }

    /**
     * `data modify <a> set value <value>`
     *
     * @param a 被设置的nbt的路径
     * @param value 设置的值
     *
     * @return 生成的命令
     */
    fun dataSetValue(a: NBTPath, value: Tag<*>): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set value ${SNBTUtil.toSNBT(value)}")
    }

    /**
     * `data modify <a> set from <b>`
     *
     * @param a 被设置的nbt的路径
     * @param b 从哪里获取值
     *
     * @return 生成的命令
     */
    fun dataSetFrom(a: NBTPath, b: NBTPath): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set from")
            .build(b.toCommandPart())
    }

    /**
     * `data modify <a> merge value <value>`
     *
     * @param a 被设置的nbt的路径
     * @param value 设置的值
     *
     * @return 生成的命令
     */
    fun dataMergeValue(a: NBTPath, value: Tag<*>): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("merge value ${SNBTUtil.toSNBT(value)}")
    }

    fun dataMergeFrom(a: NBTPath, b: NBTPath): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("merge from")
            .build(b.toCommandPart())
    }

    /**
     * 以一个类的对象为执行者，执行一个命令。
     *
     * @param a 执行者
     * @param command 要执行的命令
     * @param hasExecuteRun 是否在execute命令串和要执行的命令之间插入run
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    fun selectRun(a : CanSelectMember, command: Command, hasExecuteRun: Boolean = true) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                if(a.identifier == "this"){
                    return arrayOf(command)
                }
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
            is ObjectVar -> selectRun(a.value, command, hasExecuteRun)
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

    /**
     * 以一个类的对象为执行者，构建一个`execute`命令串，可以继续向后构建命令
     *
     * @param a 执行者
     * @param hasExecuteRun 是否在execute命令串之后添加run
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    fun selectRun(a : CanSelectMember, hasExecuteRun: Boolean = true) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                if(a.identifier == "this"){
                    return arrayOf(Command())
                }
                val qwq = arrayOf(
                    Command.build("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system ${Project.config.rootNamespace}.stack_frame[${a.stackIndex}].${a.identifier}"),
                    Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin")
                )
                if(hasExecuteRun) {
                    qwq.last().build("run","run")
                }
                qwq
            }
            is ObjectVar -> selectRun(a.value, hasExecuteRun)
            is MCFPPClassType -> {
                if(hasExecuteRun){
                    arrayOf(Command.build("execute as ${(a.cls as ObjectClass).uuid}").build("run", "run"))
                }else{
                    arrayOf(Command.build("execute as ${(a.cls as ObjectClass).uuid}"))
                }
            }
            else -> TODO()
        }
        return final
    }

    /**
     * [selectRun]的简化版本，直接传入一个字符串
     *
     * @param a 执行者
     * @param command 要执行的命令
     * @param hasExecuteRun 是否在execute命令串和要执行的命令之间插入run
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    fun selectRun(a : CanSelectMember, command: String, hasExecuteRun: Boolean = true) : Array<Command>{
        return selectRun(a, Command.build(command), hasExecuteRun)
    }

    /**
     * 提供一个沙箱函数环境，在此函数中执行一些操作并捕获生成在此函数的命令并将其返回。
     *
     * @param parent 沙箱函数的父函数，用于控制作用域
     * @param operation 在此沙箱函数中执行的操作。lambda表达式的参数为此沙箱函数
     *
     * @return 捕获的命令
     */
    fun fakeFunction(parent: Function , operation: (fakeFunction: Function) -> Unit) : Array<Command>{
        val l = Function.currFunction
        val f = NoStackFunction("", parent)
        Function.currFunction = f
        operation(f)
        Function.currFunction = l
        return f.commands.toTypedArray()
    }

    /**
     * 创建一个临时函数，可以在此函数中执行一些操作，命令将会生成在此临时函数中。
     *
     * @param parent 临时函数的父函数，用于控制作用域
     * @param operation 在此临时函数中执行的操作。lambda表达式的参数为此临时函数
     *
     * @return 生成的调用临时函数的命令和这个临时函数
     */
    fun tempFunction(parent: Function, operation: (tempFunction: Function) -> Unit) : Pair<Command, Function>{
        val l = Function.currFunction
        val f = NoStackFunction(parent.identifier + "_temp_" + UUID.randomUUID().toString(), parent)
        GlobalField.localNamespaces[Project.currNamespace]!!.field.addFunction(f, false)
        Function.currFunction = f
        operation(f)
        Function.currFunction = l
        return function(f) to f
    }

    /**
     * 以一个实体为执行者，执行一个命令
     *
     * @param entityVar 实体
     * @param command 要执行的命令
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    fun runAsEntity(entityVar: EntityVar, command: Command): Array<Command>{
        return if(entityVar is EntityVarConcrete){
            if(!entityVar.isName){
                arrayOf(Command("execute as ${Utils.fromNBTArrayUUID(entityVar.value as IntArrayTag)} run").build(command))
            }else{
                arrayOf(Command("execute as ${(entityVar.value as StringTag).value} run").build(command))
            }
        }else{
            if(!entityVar.isName){
                arrayOf(
                    Command("data modify storage entity ${ClassPointer.tempItemEntityUUID} Thrower set from").build(entityVar.nbtPath.toCommandPart()),
                    Command("execute as ${ClassPointer.tempItemEntityUUID} on origin run").build(command)
                )
            }else{
                Command("execute as").buildMacro(entityVar).build("run").build(command).buildMacroFunction()
            }
        }
    }

    /**
     * 以一个选择器为执行者，执行一个命令
     *
     * @param selector 选择器
     * @param command 要执行的命令
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    fun runAsEntity(selector: SelectorVar, command: Command): Array<Command>{
        val c = Command("execute as").build(selector.value.toCommandPart()).build("run").build(command)
        return if(c.isMacro){
            c.buildMacroFunction()
        }else{
            arrayOf(c)
        }
    }
}