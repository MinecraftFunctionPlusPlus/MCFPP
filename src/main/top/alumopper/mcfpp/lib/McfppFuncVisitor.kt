package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project

/**
 * 获取函数用的visitor
 */
class McfppFuncVisitor : mcfppBaseVisitor<Function?>() {
    fun getFunction(ctx: mcfppParser.FunctionCallContext, args: ArrayList<String>?): Function? {
        return if (ctx.namespaceID() != null && ctx.basicExpression() == null) {
            val qwq: Function? = if (ctx.namespaceID().Identifier().size == 1) {
                Project.global.cache!!.getFunction(Project.currNamespace, ctx.namespaceID().Identifier(0).text, args)
            } else {
                Project.global.cache!!.getFunction(
                    ctx.namespaceID().Identifier(0).text,
                    ctx.namespaceID().Identifier(1).text,
                    args
                )
            }
            qwq
        } else if (ctx.basicExpression() != null) {
            TODO()
        } else {
            TODO()
        }
    }
}