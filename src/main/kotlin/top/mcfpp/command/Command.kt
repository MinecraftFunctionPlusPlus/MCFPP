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

    /**
     * 可替换位点。键是位点的id，值是这个位点在commandStringList中的位置
     */
    private val replacePoint = HashMap<String,Int>()

    var isCompleted = false

    val isMacro: Boolean
        get() = commandStringList.any { it.startsWith("$") }

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
     * 转为宏命令。将会把所有可以替换的，且id以$开头的位点设置为宏参数。宏参数的名字和替换位点id相同
     */
    fun toMacro() : String{
        val sb = StringBuilder("$")
        for (c in commandStringList.indices){
            if(replacePoint.containsValue(c)){
                sb.append("$(${replacePoint.filter { it.value == c && it.key.startsWith("$") }.keys.first().substring(1)}) ")
            }else{
                sb.append(commandStringList[c]).append(" ")
            }
        }
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
        commandStringList.removeAt(point)
        for (c in target.commandStringList.withIndex()){
            commandStringList.add(point + c.index, c.value)
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
    fun build(command: String, blank: Boolean = true) : Command{
        if(blank) commandStringList.add(" ")
        commandStringList.add(command)
        return this
    }

    fun build(command: Command, blank: Boolean = true): Command{
        if(blank) commandStringList.add(" ")
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
    fun build(command: String, pointID: String, blank: Boolean = true) : Command{
        if(blank) commandStringList.add(" ")
        replacePoint[pointID] = commandStringList.size
        commandStringList.add(command)
        return this
    }

    fun buildMacro(id: String, blank: Boolean = true) = build("", "$$id", blank)

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