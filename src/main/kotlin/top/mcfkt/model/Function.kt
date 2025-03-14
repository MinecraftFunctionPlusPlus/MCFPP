package top.mcfkt.model

import top.mcfkt.model.Function.Companion.VarModifier.*
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

class Function(val function: Function) {

    fun insert(command: Command){
        if(command.isMacro){
            function.commands.addAll(command.buildMacroFunction())
        }else{
            function.commands.add(command)
        }
    }

    fun var_(type: MCFPPType, identifier: String = TempPool.getVarIdentify(), modifier: VarModifier = NONE, init: Var<*>? = null): Var<*>{
        //函数变量，生成
        var `var` = if(modifier == IMPORT){
            val qwq = type.buildUnConcrete(identifier, Function.currFunction)
            qwq.hasAssigned = true
            qwq
        }else{
            type.build(identifier, Function.currFunction)
        }
        //变量注册
        //一定是函数变量
        if (Function.currField.containVar(identifier)) {
            LogProcessor.error("Duplicate defined variable name:$identifier")
        }
        Function.addComment("field: " + type.typeName + " " + identifier)
        `var`.nbtPath = NBTPath.getNormalStackPath(`var`)
        //变量初始化
        if (init != null) {
            `var` = `var`.assignedBy(init)
        }
        when(modifier){
            CONST -> {
                if(!`var`.hasAssigned){
                    LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                }
                `var`.isConst = true
            }
            DYNAMIC -> {
                if(`var` is MCFPPValue<*> && `var`.hasAssigned){
                    `var` = `var`.toDynamic(false)
                }else if(`var` is MCFPPValue<*>){
                    `var` = type.buildUnConcrete(identifier, Function.currFunction)
                }
            }
            else -> {}
        }
        Function.currField.putVar(`var`.identifier, `var`, true)
        return `var`
    }

    fun var_(init: Var<*>, identifier: String = TempPool.getVarIdentify(), modifier: VarModifier = NONE): Var<*>{
        //自动判断类型
        var `var` = if(modifier == IMPORT){
            val qwq = init.type.buildUnConcrete(identifier, Function.currFunction)
            qwq.hasAssigned = true
            qwq
        }else{
            init.type.build(identifier, Function.currFunction)
        }
        `var`.nbtPath = NBTPath.getNormalStackPath(`var`)
        //变量赋值
        `var` = `var`.assignedBy(init)
        //一定是函数变量
        if (!Function.currField.putVar(identifier, `var`, false)) {
            LogProcessor.error("Duplicate defined variable name:$identifier")
        }
        when(modifier){
            CONST -> {
                if(!`var`.hasAssigned){
                    LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                }
                `var`.isConst = true
            }
            DYNAMIC -> {
                if(`var` is MCFPPValue<*>){
                    `var`.toDynamic(true)
                }
            }
            else -> {}
        }
        return `var`
    }

    companion object {
        enum class VarModifier {
            NONE, DYNAMIC, CONST, IMPORT
        }
    }

}