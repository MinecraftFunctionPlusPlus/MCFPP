package top.mcfpp.core.lang.bool

import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class FunctionBool(identifier: String , function: Function): BaseBool(identifier), MCFPPValue<Function> {

    override var value: Function = function
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
        return ExecuteBool(this, true)
    }

    override fun and(a: Var<*>): Var<*>? {
        return ExecuteBool(this).and(a)
    }

    override fun or(a: Var<*>): Var<*>? {
        return ExecuteBool(this).or(a)
    }

    override fun toCommandPart(): Command {
        return Command("function ${value.namespaceID}")
    }

    override fun doAssignedBy(b: Var<*>): BaseBool {
        when(b){
            is FunctionBool -> {
                value = b.value
                return this
            }

            is ScoreBool -> {
                val re = ScoreBool(identifier).apply { name = this@FunctionBool.name }
                return re.assignedBy(this)
            }

            is BaseBool -> {
                replacedBy(b)
                return b.clone().apply { identifier = this@FunctionBool.identifier }
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return b is BaseBool
    }

    override fun clone(): FunctionBool {
        return FunctionBool(identifier, value)
    }

    override fun getTempVar(): FunctionBool = this

    override fun storeToStack() {}

    override fun getFromStack() {}

}