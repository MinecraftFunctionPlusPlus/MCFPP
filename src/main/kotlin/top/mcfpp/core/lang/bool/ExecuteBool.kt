package top.mcfpp.core.lang.bool

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class ExecuteBool(): BaseBool(), MCFPPValue<ArrayList<AbstractBoolPart>> {

    override var value = ArrayList<AbstractBoolPart>()

    constructor(bool: BaseBool, isNegation: Boolean = false): this(){
        if(bool is ExecuteBool){
            if(isNegation){
                value.addAll((bool.negation() as ExecuteBool).value)
            }else{
                value.addAll(bool.value)
            }
        }else{
            value.add(ExecuteBoolPart(isNegation, bool))
        }
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val re = ScoreBool(this.identifier)
        val cmd = Command.build("execute")
            .build("store score ${re.identifier} ${re.boolObject}")
            .build(toCommandPart())
        Function.addCommand(cmd)
        if(replace){
            replacedBy(re)
        }
        return re
    }

    override fun toScoreBool(): ScoreBool {
        return toDynamic(true) as ScoreBool
    }

    override fun negation(): Var<*> {
        for (b in value){
            b.negation = !b.negation
        }
        return this
    }

    override fun and(a: Var<*>): Var<*>? {
        return when(a){
            is ExecuteBool -> {
                value.addAll(a.value)
                this
            }

            is BaseBool -> {
                value.add(ExecuteBoolPart(false, a))
                this
            }

            else -> null
        }
    }

    override fun or(a: Var<*>): Var<*>? {
        val qwq = and(a)?.negation() as BaseBool?
        qwq?:return null
        val (_, function) = Commands.tempFunction(Function.currFunction){
            val command = Command.build("execute")
                .build(qwq.toCommandPart())
                .build("run return 0")
            Function.addCommand(command)
            Function.addCommand("return 1")
        }
        return FunctionBool(identifier, function)
    }

    override fun toCommandPart(): Command {
        val command = Command()
        for (bool in value){
            if(bool is ExecuteBoolPart && bool.bool !is ExecuteBool || bool !is CommandBoolPart){
                command.build(if(!bool.negation) "if" else "unless")
            }
            command.build(bool.toCommandPart())
        }
        return command
    }

    override fun doAssignedBy(b: Var<*>): ExecuteBool {
        when(b){
            is ExecuteBool -> {
                value.clear()
                value.addAll(b.value)
                return this
            }

            is BaseBool -> {
                value.clear()
                value.add(ExecuteBoolPart(false, b))
                return this
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    override fun clone(): ExecuteBool {
        val re = ExecuteBool()
        re.value.addAll(value.map { it.clone() })
        return re
    }

    override fun getTempVar(): ExecuteBool = this

    override fun storeToStack() {}

    override fun getFromStack() {}
}

interface AbstractBoolPart{

    var negation: Boolean

    fun clone(): AbstractBoolPart

    fun toCommandPart(): Command
}

data class ExecuteBoolPart(override var negation: Boolean, val bool: BaseBool): AbstractBoolPart{
    override fun clone(): ExecuteBoolPart{
        return ExecuteBoolPart(negation, bool.clone())
    }

    override fun toCommandPart(): Command {
        return bool.toCommandPart()
    }
}

data class CommandBoolPart(override var negation: Boolean, val command: Command): AbstractBoolPart{

    override fun clone(): AbstractBoolPart {
        return CommandBoolPart(negation, command)
    }

    override fun toCommandPart(): Command {
        return command
    }

}