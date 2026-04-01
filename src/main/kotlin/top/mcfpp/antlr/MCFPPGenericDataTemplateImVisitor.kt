package top.mcfpp.antlr

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function

class MCFPPGenericDataTemplateImVisitor : MCFPPImVisitor() {

    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext): Any? = withCompilationContext(ctx) {
        enterTemplateBody(ctx)
        visitChildren(ctx)
        exitTemplateBody(ctx)
        return null
    }

    private fun enterTemplateBody(ctx: mcfppParser.TemplateBodyContext) {
        //TODO 注解
    }

    private fun exitTemplateBody(ctx: mcfppParser.TemplateBodyContext) {
        DataTemplate.currTemplate = null
        Function.currFunction = Function.nullFunction
    }

}