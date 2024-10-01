package top.mcfpp.model.accessor

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

class ExpressionMutator(val ctx: mcfppParser.ExpressionContext, field: Var<*>): AbstractMutator(field) {

    var error: Boolean

    init {
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
        error = false
    }

    override fun setter(caller: CanSelectMember, b: Var<*>): Var<*> {
        if(error) return field
        val cs = Commands.fakeFunction(Function.currFunction){
            it.field.putVar("field", field)
            it.field.putVar("value", b)
            field.assignedBy(MCFPPExprVisitor().visit(ctx))
        }
        Function.addCommands(cs)
        return field
    }

}