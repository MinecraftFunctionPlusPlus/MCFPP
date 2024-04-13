package top.mcfpp.command

import top.mcfpp.exception.CommandException
import java.lang.StringBuilder

/**
 * 一条编译时动态命令。包含了可以被替换的字符串位点，每个位点都有一个唯一的ID。
 *
 * @constructor Create empty Command
 */
open class Command {

    protected val commandStringList = ArrayList<String>()

    private val tags = ArrayList<String>()

    private val replacePoint = HashMap<String,Int>()

    var isCompleted = false

    constructor(command: String){
        commandStringList.add(command)
    }

    constructor(command: String, pointID: String){
        replacePoint[pointID] = commandStringList.size
        commandStringList.add(command)
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
        for (c in commandStringList){
            sb.append(c)
        }
        commandStringList.clear()
        commandStringList.add(sb.toString())
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
        commandStringList[point] = target
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
        return commandStringList[point]
    }

    fun prepend(command: String){
        if(isCompleted){
            throw CommandException("Try to prepend argument to a completed command")
        }
        commandStringList.add(0,command)
    }

    fun prepend(command: Command){
        if(isCompleted){
            throw CommandException("Try to prepend argument to a completed command")
        }
        for (c in command.commandStringList){
            commandStringList.add(0,c)
        }
        replacePoint.putAll(command.replacePoint)
    }

    /**
     * 在这条命令的末尾继续构建命令
     *
     * @param command 固定的命令字符串
     * @return
     */
    fun build(command: String) : Command{
        commandStringList.add(command)
        return this
    }

    fun build(command: Command): Command{
        for (kv in command.replacePoint){
            replacePoint[kv.key] = kv.value + commandStringList.size
        }
        commandStringList.addAll(command.commandStringList)
        return this
    }

    /**
     * 在这条命令的末尾继续构建命令
     *
     * @param command 可被替换的命令字符串
     * @param pointID 命令字符串的位点ID
     * @return
     */
    fun build(command: String, pointID: String) : Command{
        replacePoint[pointID] = commandStringList.size
        commandStringList.add(command)
        return this
    }

    override fun toString(): String{
        val sb = StringBuilder()
        for (c in commandStringList){
            sb.append(c)
        }
        return sb.toString()
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
}