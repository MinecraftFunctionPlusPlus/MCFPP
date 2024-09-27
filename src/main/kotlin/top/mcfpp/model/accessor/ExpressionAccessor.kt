package top.mcfpp.model.accessor

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class ExpressionAccessor(val ctx: mcfppParser.ExpressionContext, field: Var<*>): AbstractAccessor(field) {

    var error: Boolean

    init {
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
    }

    override fun getter(caller: CanSelectMember): Var<*> {
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