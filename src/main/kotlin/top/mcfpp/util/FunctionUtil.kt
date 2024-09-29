package top.mcfpp.util

import net.querz.nbt.io.SNBTUtil
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.EnumVar
import top.mcfpp.model.EnumMember
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import java.util.*
import kotlin.math.ceil
import kotlin.math.log
import kotlin.math.pow

object FunctionUtil {

    /**
     * 穷举一个枚举变量。使用记分板树的方法
     *
     * @param enum 枚举变量
     * @param command 用于穷举的命令
     * @param replacePoint 用于替换[command]的位点名称。枚举变量的自定义数据[EnumMember.data]将会替换这个位点。
     *
     * @return 生成的调用记分板树的Function
     */
    fun enumWalk(enum: EnumVar, command: Command, replacePoint: String): Function{

        /**
         * @param function 要分割的函数
         * @param num 分割的数量
         */
        fun divideFunction(function: NoStackFunction, num: Int = 3){
            val id = UUID.randomUUID().toString()
            //确认分块大小
            val log = log(function.commands.size.toDouble(), num.toDouble()).toInt()
            var partSize = num.toDouble().pow(log).toInt()
            var part = function.commands.size / partSize
            if(part <= 1) {
                partSize /= 3
            }
            //如果每块只有一条命令，就不进行分割了
            if(partSize <= 1) return
            part = ceil(function.commands.size.toDouble() / partSize.toDouble()).toInt()
            //分块使用的函数
            val parts = Array(part){ NoStackFunction(function.identifier + "_" + it , function) }
            for (i in 0 until function.commands.size){
                parts[i / partSize].commands.add(function.commands[i])
            }
            //将命令置换为分块的调用命令
            function.commands.clear()
            for (p in parts.indices){
                function.commands.add("execute if score " + enum.asIntVar().identifier + " " + enum.asIntVar().sbObject
                        + " matches " + p*partSize + ".." + (p+1)*partSize + " run return run run "
                        + Commands.function(parts[p]))
            }
            function.innerFunction.addAll(parts)
            parts.forEach { divideFunction(it) }
        }

        if(command.isMacro){
            LogProcessor.error("[InnerError]Cannot use macro command in scoreboardTree.")
        }
        //生成初始穷举命令
        val function = NoStackFunction("scoreboardTree_${enum.identifier}", Function.currFunction)
        val member = enum.enum.members
        for (m in member){
            val newCommand = command.clone()
            newCommand.replace(replacePoint to SNBTUtil.toSNBT(m.value.data))
            function.commands.add("execute if score " + enum.asIntVar().identifier + " " + enum.asIntVar().sbObject
                    + " matches " + m.value.value + " run return run run " + newCommand)
        }
        //递归分割初始命令
        if(function.commands.size > 20){
            divideFunction(function, 3)
        }else{
            divideFunction(function, 4)
        }
        return function
    }

}