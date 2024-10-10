package top.mcfpp.command

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
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
 * 一条命令。在MCFPP编译过程中，一条命令可能会被分为多个命令片段，由接口`ICommandPart`实现。一个`Command`对象可以包含多个`CommandPart`对象。命令中的片段可以被字符串标签标记，从而能够对命令的某些片段进行替换操作。
 *
 * 要开始构建一个命令，可以使用`Command`的伴随对象中的`Command.build`方法，或者使用`Command(String)`构造函数。此后，可以继续通过链式调用成员方法`build(String)`或者`build(Command)`来添加命令片段，或者使用`build(command:String, pointID: String)`来在添加命令片段的同时，标记该片段的替换标签为`pointID`。
 *
 * 对于宏命令而言，可以使用成员方法`buildMacro(id: Var<*>)`来构建一个宏命令，其中`id`代表的变量就是要传递给宏的变量。随后，构建出的宏命令需要再使用`buildMacroFunction`方法将宏命令转换为宏函数来调用，从而提高宏命令的效率。此方法将会返回一个新的`Command`对象，即调用此宏函数的命令。
 *
 * 要替换命令中的一些片段，可以使用成员方法`replace(pointID: String, target: String)`或者`replace(pointID: String, target: Command)`。它将会把标记为`pointID`的片段替换为`target`。
 *
 * @constructor Create empty Command
 *
 * @see CommandPart
 * @see MacroPart
 */
open class Command {

    /**
     * 命令片段的列表
     */
    protected val commandParts = ArrayList<ICommandPart>()

    /**
     * 命令的标签列表
     */
    private val tags = ArrayList<String>()

    /**
     * 可替换位点。键是位点的id，值是这个位点在commandStringList中的位置
     */
    private val replacePoint = HashMap<String,Int>()

    /**
     * 这个命令是否已经构建完成。若已经完成，则无法再向这个命令中添加新的命令片段，也不能再替换命令片段
     */
    var isCompleted = false

    /**
     * 只读。这个命令是否是一个宏命令
     */
    val isMacro: Boolean
        get() = commandParts.any { it is MacroPart }

    /**
     * 创建一个新的命令，拥有一个固定的命令片段
     *
     * @param command 命令片段的内容
     */
    constructor(command: String){
        commandParts.add(CommandPart(command))
    }

    /**
     * 创建一个新的命令，拥有一个固定的命令片段，并且标记这个命令片段的替换位点
     *
     * @param command 命令片段的内容
     * @param pointID 替换位点的ID
     */
    constructor(command: String, pointID: String){
        replacePoint[pointID] = commandParts.size
        commandParts.add(CommandPart(command))
    }

    /**
     * 这个命令是否拥有指定的tag
     *
     * @param tag 目标tag
     * @return 是否有这个tag
     */
    fun hasTag(tag: String): Boolean{
        return tags.contains(tag)
    }

    /**
     * 将这个命令解析为字符串，同时合并所有的命令片段并清空替换位点
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

    /**
     * 替换多个目标位点中的字符串
     *
     * @param pointIDtoTarget 多个目标位点的ID和目标字符串
     */
    fun replace(vararg pointIDtoTarget: Pair<String,String>): Int{
        var suc = 0
        for (pt in pointIDtoTarget){
            if(replace(pt.first,pt.second)) suc++
        }
        return suc
    }

    /**
     * 获取指定标签对应的命令片段的字符串
     */
    fun get(pointID: String): String?{
        val point = replacePoint[pointID] ?: return null
        return commandParts[point].toString()
    }

    /**
     * 在这条命令的开头插入命令
     *
     * @param command 固定的命令字符串
     * @param withBlank 是否在插入的命令和原命令之间加一个空格
     */
    fun prepend(command: String, withBlank: Boolean = true){
        if(isCompleted){
            throw CommandException("Try to prepend argument to a completed command")
        }
        if(withBlank) commandParts.add(0,CommandPart(" "))
        commandParts.add(0, CommandPart(command))
    }

    /**
     * 在这条命令的开头插入命令
     *
     * @param command 插入的命令
     * @param withBlank 是否在插入的命令和原命令之间加一个空格
     */
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
     * @param withBlank 是否在插入的命令和原命令之间加一个空格
     *
     * @return 构建后的命令
     */
    fun build(command: String, withBlank: Boolean = true) : Command{
        if(withBlank) commandParts.add(CommandPart(" "))
        commandParts.add(CommandPart(command))
        return this
    }

    /**
     * 在这条命令的末尾继续构建命令
     *
     * @param command 可被替换的命令字符串
     * @return 构建后的命令
     */
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
     * @return 构建后的命令
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

    /**
     * 将此命令解析为一个字符串
     *
     * @return 解析后的字符串
     */
    override fun toString(): String{
        val sb = StringBuilder()
        for (c in commandParts){
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * 复制这个命令
     *
     * @return 复制后的命令
     */
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

    /**
     * 表示一个命令片段，作为命令的一部分。一个命令对象中包含数个命令片段。
     */
    protected interface ICommandPart

    /**
     * 一个普通的命令片段，由一个固定的字符串构成
     *
     * @param command 命令片段的字符串
     */
    private data class CommandPart(var command: String): ICommandPart{
        override fun toString(): String {
            return command
        }
    }

    /**
     * 一个宏命令片段，由一个变量构成。这个变量就是要传递给宏命令的宏参数
     *
     * @param v 宏命令片段的变量
     */
    private data class MacroPart(var v: Var<*>): ICommandPart{
        override fun toString(): String {
            return v.identifier
        }
    }
}