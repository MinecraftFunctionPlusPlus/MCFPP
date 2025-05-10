package top.mcfpp.antlr

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.model.compound.Class
import top.mcfpp.model.function.Function

class MCFPPGenericClassImVisitor : MCFPPImVisitor() {

    override fun visitClassBody(ctx: mcfppParser.ClassBodyContext): Any? = withCompilationContext(ctx) {
        enterClassBody(ctx)
        visitChildren(ctx)
        exitClassBody(ctx)
        return null
    }

    /**
     * 进入类体。
     * @param ctx the parse tree
     */
    private fun enterClassBody(ctx: mcfppParser.ClassBodyContext) {
        //TODO 注解
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */

    private fun exitClassBody(ctx: mcfppParser.ClassBodyContext) {
        Class.currClass = null
        Function.currFunction = Function.nullFunction
    }

}