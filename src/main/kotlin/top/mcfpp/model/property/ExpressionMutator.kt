package top.mcfpp.model.property

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class ExpressionMutator: AbstractMutator {

    val ctx: mcfppParser.ExpressionContext

    var error: Boolean

    constructor(ctx: mcfppParser.ExpressionContext){
        this.ctx = ctx
        error = false
    }

    constructor(ctx: mcfppParser.ExpressionContext, field: Var<*>) {
        Commands.fakeFunction(Function.currFunction){
            it.field.putVar("field", field)
            it.field.putVar("value", field.type.build("value"))
            val test: Var<*> = MCFPPExprVisitor().visit(ctx)
            if(!test.type.isSubOf(field.type)) {
                LogProcessor.error("Expression type mismatch: ${test.type} and ${field.type}")
                error = true
            }else{
                error = false
            }
        }
        this.ctx = ctx
        error = false
    }

    override fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        var qwq = field
        if(error) return qwq
        val cs = Commands.fakeFunction(Function.currFunction){
            it.field.putVar("field", field)
            it.field.putVar("value", b)
            qwq = field.assignedBy(MCFPPExprVisitor().visit(ctx))
        }
        Function.addCommands(cs)
        return qwq
    }

}