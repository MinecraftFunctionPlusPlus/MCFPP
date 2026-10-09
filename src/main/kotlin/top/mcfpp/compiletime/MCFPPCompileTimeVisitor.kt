package top.mcfpp.compiletime

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.Var
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.util.LogProcessor
import top.mcfpp.model.function.Function

class MCFPPCompileTimeVisitor(
    var field:CompileTimeFunctionScope,
): MCFPPImVisitor() {
    val exprVisitor = MCFPPExprVisitor()

    var curBreak = false
    var curContinue = false
    var curReturn = false
    var returnValue:Var<*>? = null

    private fun closedCondition(value: Any?): Boolean? {
        val variable = value as? Var<*> ?: return null
        if (variable.isError) return null
        var snapshot = StorageAccess.snapshot(variable)
        while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
        return (snapshot as? CompilerValue.Bool)?.value ?: run {
            LogProcessor.error("Compile-time conditions require a complete boolean value")
            null
        }
    }

    override fun visitCurlBlock(ctx: mcfppParser.CurlBlockContext): Any? {
        Function.forcedField = field
        for(statement in ctx.statement()) {
            if(!curReturn){
                visit(statement)
            }
        }
        Function.forcedField = null
        return null
    }

    override fun visitIfStatement(ctx: mcfppParser.IfStatementContext): Any? {
        val condtion= exprVisitor.visit(ctx.bucketExpression().expression())
        if(closedCondition(condtion) ?: return null){
            visit(ctx.block())
        }
        else{
            var elseIfBool = false
            for(elseIfStatementContext in ctx.elseIfStatement()){
                val elseIfCondition = exprVisitor.visit(elseIfStatementContext.bucketExpression().expression())
                if(closedCondition(elseIfCondition) ?: return null){
                    visit(elseIfStatementContext.block())
                    elseIfBool = true
                    break
                }
            }
            if(!elseIfBool&&ctx.elseStatement()!=null){
                visit(ctx.elseStatement().block())
            }
        }
        return null
    }

    override fun visitReturnStatement(ctx: mcfppParser.ReturnStatementContext): Any? {
        curReturn = true
        returnValue = exprVisitor.visit(ctx.expression())
        return null
    }



    override fun visitWhileStatement(ctx: mcfppParser.WhileStatementContext): Any? {
        while(true){
            val condition = exprVisitor.visit(ctx.bucketExpression().expression())
            if(closedCondition(condition) ?: return null){
                visit(ctx.block())
                if(curBreak||curReturn){
                    curBreak = false
                    break
                }
                else if(curContinue){
                    curContinue = false
                }
            }
            else{
                return null
            }
        }
        return null
    }

    override fun visitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext): Any? {
        visit(ctx.block())
        if(!curBreak||!curReturn||!curContinue){
            while(true){
                val condition = exprVisitor.visit(ctx.bucketExpression().expression())
                if(closedCondition(condition) ?: return null){
                    visit(ctx.block())
                    if(curBreak||curReturn){
                        curBreak = false
                        break
                    }
                    else if(curContinue){
                        curContinue = false
                    }
                }
                else{
                    return null
                }
            }
        }
        return null
    }


    override fun visitControlStatement(ctx: mcfppParser.ControlStatementContext): Any? {
        if(ctx.CONTINUE()!=null){
            curContinue = true
            return null
        }
        if(ctx.BREAK()!=null) {
            curBreak = true
            return null
        }
        return null
    }

    override fun visitTryStoreStatement(ctx: mcfppParser.TryStoreStatementContext): Any? {
        return super.visitTryStoreStatement(ctx)
    }

    override fun visitBlock(ctx: mcfppParser.BlockContext): Any? {
        for(statement in ctx.curlBlock()?.statement()?: listOf(ctx.statement())){
            if(curBreak||curContinue||curReturn){
                break
            }
            visit(statement)
        }
        return null
    }
}
