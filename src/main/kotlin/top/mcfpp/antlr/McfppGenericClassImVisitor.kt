package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.model.Class
import top.mcfpp.model.function.Function

class McfppGenericClassImVisitor : McfppImVisitor() {

    override fun visitClassBody(ctx: mcfppParser.ClassBodyContext): Any? {
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
        Project.ctx = ctx
        //TODO 注解
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */

    private fun exitClassBody(ctx: mcfppParser.ClassBodyContext) {
        Project.ctx = ctx
        Class.currClass = null
        Function.currFunction = Function.nullFunction
    }

}