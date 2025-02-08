package top.mcfpp.model.property

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser.ExpressionContext
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class ExpressionAccessor: AbstractAccessor{

    var error: Boolean

    val ctx: ExpressionContext

    constructor(ctx: ExpressionContext, field: Var<*>) {
        Commands.fakeFunction(Function.currFunction){
            it.field.putVar("field", field)
            val test: Var<*> = MCFPPExprVisitor().visit(ctx)
            if(!test.type.isSubOf(field.type)) {
                LogProcessor.error("Expression type mismatch: ${test.type} and ${field.type}")
                error = true
            }else{
                error = false
            }
        }
        error = false
        this.ctx = ctx
    }

    constructor(ctx: ExpressionContext){
        this.ctx = ctx
        error = false
    }

    override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        if(error) return field
        var v : Var<*>? = null
        val cs = Commands.fakeFunction(Function.currFunction){
            it.field.putVar("field", field)
            v = MCFPPExprVisitor().visit(ctx)
        }
        Function.addCommands(cs)
        return v!!
    }

}