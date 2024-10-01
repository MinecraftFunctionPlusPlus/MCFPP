package top.mcfpp.command

import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.exception.CommandException
import top.mcfpp.lib.MemberPath
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * 一条编译时动态命令。包含了可以被替换的字符串位点，每个位点都有一个唯一的ID。
 *
 * @constructor Create empty Command
 */
open class Command {

    protected val commandParts = ArrayList<ICommandPart>()

    private val tags = ArrayList<String>()

    /**
     * 可替换位点。键是位点的id，值是这个位点在commandStringList中的位置
     */
    private val replacePoint = HashMap<String,Int>()

    var isCompleted = false

    val isMacro: Boolean
        get() = commandParts.any { it is MacroPart }

    constructor(command: String){
        commandParts.add(CommandPart(command))
    }

    constructor(command: String, pointID: String){
        replacePoint[pointID] = commandParts.size
        commandParts.add(CommandPart(command))
    }

    /**
     * 这个动态命令是否拥有指定的tag
     *
     * @param tag 目标tag
     * @return 是否有这个tag
     */
    fun hasTag(tag: String): Boolean{
        return tags.contains(tag)
    }

    /**
     * 将这个动态命令解析为字符串
     *
     * @return
     */
    open fun analyze(): String{
        val sb = StringBuilder()
        for (c in commandParts){
            sb.append(c)
        }
        commandParts.clear()
        commandParts.add(CommandPart(sb.toString()))
        replacePoint.clear()
        return sb.toString()
    }

    /**
     * 替换目标位点中的字符串
     *
     * @param pointID 位点的ID
     * @param target 目标字符串
     *
     * @return 如果没有可以替换的位点，则返回false
     */
    private fun replace(pointID: String, target: String): Boolean{
        val point = replacePoint[pointID] ?: return false
        commandParts[point] = CommandPart(target)
        return true
    }

    /**
     * 替换目标位点中的字符串
     *
     * @param pointID 位点的ID
     * @param target 目标命令
     *
     * @return 如果没有可以替换的位点，则返回false
     */
    private fun replace(pointID: String, target: Command): Boolean{
        val point = replacePoint[pointID] ?: return false
        commandParts.removeAt(point)
        for (c in target.commandParts.withIndex()){
            commandParts.add(point + c.index, c.value)
        }
        return true
    }

    fun replace(vararg pointIDtoTarget: Pair<String,String>): Int{
        var suc = 0
        for (pt in pointIDtoTarget){
            if(replace(pt.first,pt.second)) suc++
        }
        return suc
    }

    fun get(pointID: String): String?{
        val point = replacePoint[pointID] ?: return null
        return commandParts[point].toString()
    }

    fun prepend(command: String, withBlank: Boolean = true){
        if(isCompleted){
            throw CommandException("Try to prepend argument to a completed command")
        }
        if(withBlank) commandParts.add(0,CommandPart(" "))
        commandParts.add(0, CommandPart(command))
    }

    fun prepend(command: Command, withBlank: Boolean = true){
        if(isCompleted){
            throw CommandException("Try to prepend argument to a completed command")
        }
        for (c in command.commandParts){
            commandParts.add(0,c)
        }
        if(withBlank) commandParts.add(0,CommandPart(" "))
        replacePoint.putAll(command.replacePoint)
    }

    /**
     * 在这条命令的末尾继续构建命令
     *
     * @param command 固定的命令字符串
     * @return
     */
    fun build(command: String, withBlank: Boolean = true) : Command{
        if(withBlank) commandParts.add(CommandPart(" "))
        commandParts.add(CommandPart(command))
        return this
    }

    fun build(command: Command, withBlank: Boolean = true): Command{
        if(withBlank) commandParts.add(CommandPart(" "))
        for (kv in command.replacePoint){
            replacePoint[kv.key] = kv.value + commandParts.size
        }
        commandParts.addAll(command.commandParts)
        return this
    }

    /**
     * 在这条命令的末尾继续构建命令
     *
     * @param command 可被替换的命令字符串
     * @param pointID 命令字符串的位点ID
     * @return
     */
    fun build(command: String, pointID: String, withBlank: Boolean = true) : Command{
        if(withBlank) commandParts.add(CommandPart(" "))
        replacePoint[pointID] = commandParts.size
        commandParts.add(CommandPart(command))
        return this
    }

    /**
     * 在这条命令的末尾构建一个宏参数。得到的命令需要使用[buildMacroFunction]方法进行转换才能使用。
     *
     * 插入的命令片段的值为空字符串，替换位点的id为宏参数
     */
    fun buildMacro(id: Var<*>, withBlank: Boolean = true) = build("", "$$id", withBlank)

    /**
     * 将此命令以宏命令的方式调用。自动确定宏参数的路径。
     *
     * @return 用来调用 *执行宏参数的函数* 的function命令，以及传入宏参数使用的值
     */
    fun buildMacroFunction() : Array<Command>{
        if(!isMacro) return arrayOf(this)
        val f = UUID.randomUUID().toString()
        val sharedPath = NBTPath.getMaxImmediateSharedPath(*commandParts.filterIsInstance<MacroPart>().map { it.v.nbtPath }.toTypedArray())?: NBTPath.macroTemp
        Project.macroFunction[f] = this.toString()
        val argPass = ArrayList<Command>()
        for (v in commandParts.filterIsInstance<MacroPart>().map { it.v }){
            if(v.nbtPath.pathList.last() !is MemberPath || !sharedPath.isImmediateParentOf(v.nbtPath)){
                //变量需要传递
                argPass.addAll(
                    Commands.fakeFunction(Function.nullFunction) {
                        v.clone().apply { sharedPath.memberIndex(v.identifier) }.assignedBy(v)
                    }
                )
            }
        }
        argPass.add(Command.build("function mcfpp:dynamic/$f with").build(sharedPath.toCommandPart()))
        return argPass.toTypedArray()
    }

    /**
     * 将此命令以宏命令的方式调用。手动指定宏参数的路径
     *
     * @param nbtPath 用于替换宏参数的nbt路径
     * @return 用来调用 *执行宏参数的函数* 的function命令。无需自行补全with参数
     */
    fun buildMacroFunction(nbtPath: NBTPath): Command{
        if(!isMacro) return this
        val f = UUID.randomUUID().toString()
        Project.macroFunction[f] = this.toString()
        return Command.build("function mcfpp:dynamic/$f with").build(nbtPath.toCommandPart())
    }

    override fun toString(): String{
        val sb = StringBuilder()
        for (c in commandParts){
            sb.append(c)
        }
        return sb.toString()
    }

    fun clone(): Command{
        val c = Command("")
        c.commandParts.clear()
        c.commandParts.addAll(commandParts)
        c.replacePoint.putAll(replacePoint)
        return c
    }

    companion object{

        /**
         * 构建命令
         *
         * @param command 固定的命令字符串
         * @return
         */
        fun build(command: String) : Command{
            return Command(command)
        }

        /**
         * 构建命令
         *
         * @param command 可被替换的命令字符串
         * @param pointID 命令字符串的位点ID
         * @return
         */
        fun build(command: String, pointID: String) : Command{
            return Command(command, pointID)
        }
    }

    protected interface ICommandPart
    private data class CommandPart(var command: String): ICommandPart{
        override fun toString(): String {
            return command
        }
    }
    private data class MacroPart(var v: Var<*>): ICommandPart{
        override fun toString(): String {
            return v.identifier
        }
    }
}