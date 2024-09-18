package top.mcfpp.compiletime

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.bool.MCBoolConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function

class MCFPPCompileTimeVisitor(
    var field:CompileTimeFunctionField,
): MCFPPImVisitor() {
    val exprVisitor = MCFPPExprVisitor()

    var curBreak = false;
    var curContinue = false;
    var curReturn = false;
    var returnValue:Var<*>? = null;

    override fun visitFunctionBody(ctx: mcfppParser.FunctionBodyContext): Any? {
        Function.forcedField = field
        for(statement in ctx.statement()) {
            if(!curReturn){
                visit(statement)
            }
        }
        Function.forcedField = null
        return null
    }
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext): Any? {
        return super.visitFieldDeclaration(ctx)
    }

    override fun visitStatementExpression(ctx: mcfppParser.StatementExpressionContext): Any? {
        return super.visitStatementExpression(ctx)
    }

    override fun visitIfStatement(ctx: mcfppParser.IfStatementContext): Any? {
        val condtion= exprVisitor.visit(ctx.expression())
        if(condtion is MCBoolConcrete && condtion.value){
            visit(ctx.ifBlock())
        }
        else{
            var elseIfBool = false
            for(elseIfStatementContext in ctx.elseIfStatement()){
                val elseIfCondition = exprVisitor.visit(elseIfStatementContext.expression())
                if(elseIfCondition is MCBoolConcrete && elseIfCondition.value){
                    visit(elseIfStatementContext.ifBlock())
                    elseIfBool = true
                    break
                }
            }
            if(!elseIfBool&&ctx.elseStatement()!=null){
                visit(ctx.elseStatement().ifBlock())
            }
        }
        return null
    }

    override fun visitIfBlock(ctx: mcfppParser.IfBlockContext): Any? {
        return super.visitBlock(ctx.block())
    }

    override fun visitForStatement(ctx: mcfppParser.ForStatementContext): Any? {
        val forControlContext = ctx.forControl()
        visit(forControlContext.forInit())
        while(true){
            val condition = exprVisitor.visit(forControlContext.expression())
            if(condition is MCBoolConcrete && condition.value){
                visit(ctx.forBlock())
                if(curBreak||curReturn){
                    curBreak = false
                    break
                }
                else if(curContinue){
                    curContinue = false
                }
                visit(forControlContext.forUpdate())
            }
            else{
                return null
            }
        }
        return null
    }

    override fun visitForInit(ctx: mcfppParser.ForInitContext): Any? {
        visitChildren(ctx)
        return null
    }

    override fun visitForUpdate(ctx: mcfppParser.ForUpdateContext): Any? {
        for(statementExpression in ctx.statementExpression()){
            visit(statementExpression)
        }
        return null
    }

    override fun visitForBlock(ctx: mcfppParser.ForBlockContext): Any? {
        return visitBlock(ctx.block())
    }


    override fun visitReturnStatement(ctx: mcfppParser.ReturnStatementContext): Any? {
        curReturn = true
        returnValue = exprVisitor.visit(ctx.expression())
        return null
    }



    override fun visitWhileStatement(ctx: mcfppParser.WhileStatementContext): Any? {
        while(true){
            val condition = exprVisitor.visit(ctx.expression())
            if(condition is MCBoolConcrete && condition.value){
                visit(ctx.whileBlock())
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
    override fun visitWhileBlock(ctx: mcfppParser.WhileBlockContext): Any? {
        return visitBlock(ctx.block())
    }

    override fun visitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext): Any? {
        visit(ctx.doWhileBlock())
        if(!curBreak||!curReturn||!curContinue){
            while(true){
                val condition = exprVisitor.visit(ctx.expression())
                if(condition is MCBoolConcrete && condition.value){
                    visit(ctx.doWhileBlock())
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

    override fun visitDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext): Any? {
        return visitBlock(ctx.block())
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

    override fun visitOrgCommand(ctx: mcfppParser.OrgCommandContext): Any? {
        return super.visitOrgCommand(ctx)
    }

    override fun visitSelfAddOrMinusStatement(ctx: mcfppParser.SelfAddOrMinusStatementContext): Any? {
        return super.visitSelfAddOrMinusStatement(ctx)
    }

    override fun visitTryStoreStatement(ctx: mcfppParser.TryStoreStatementContext): Any? {
        return super.visitTryStoreStatement(ctx)
    }

    override fun visitBlock(ctx: mcfppParser.BlockContext): Any? {
        for(statement in ctx.statement()){
            if(curBreak||curContinue||curReturn){
                break
            }
            visit(statement)
        }
        return null
    }
}