package top.mcfpp.lib

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.exception.ArgumentNotMatchException
import top.mcfpp.lang.*

/**
 * 获取函数用的visitor
 */
class McfppFuncVisitor : mcfppBaseVisitor<Function?>() {
    fun getFunction(ctx: mcfppParser.FunctionCallContext, args: ArrayList<String>): Pair<Function?, ClassBase?> {
        Project.ctx = ctx
        if (ctx.namespaceID() != null && ctx.varWithSelector() == null) {
            val qwq: Function? = if (ctx.namespaceID().Identifier().size == 1) {
                Project.global.cache.getFunction(Project.currNamespace, ctx.namespaceID().Identifier(0).text, args)
            } else {
                Project.global.cache.getFunction(
                    ctx.namespaceID().Identifier(0).text,
                    ctx.namespaceID().Identifier(1).text,
                    args
                )
            }
            return Pair<Function?, ClassBase?>(qwq,null)
        } else if (ctx.varWithSelector() != null) {
            val selectCtx = ctx.varWithSelector()
            var curr: ClassBase
            curr = if (selectCtx.`var`() != null) {
                val varName = ctx.text.substring(0,ctx.text.indexOf("."))
                //Var
                if(Function.currFunction.getVar(varName) is ClassPointer){
                    Function.currFunction.getVar(varName) as ClassPointer
                }else{
                    Project.error("$varName is not a class pointer")
                    throw ArgumentNotMatchException("$varName is not a class pointer")
                }
            } else {
                //ClassName
                val qwq: Class? = Project.global.cache.classes[selectCtx.className().text]
                if (qwq == null) {
                    Project.error("Undefined class:" + selectCtx.className().text)
                }
                ClassType(qwq!!)
            }
            var member: ClassBase?
            //开始选择成员对象。最后一个成员应该是函数。
            var i = 0
            while (i < selectCtx.selector().size - 1) {
                member = curr.getVarMember(selectCtx.selector(i).text.substring(1)) as ClassBase?
                if (member == null) {
                    Project.error("Undefined member " + selectCtx.selector(i).text.substring(1) + " in class " + curr.Class()!!.identifier)
                }
                i++
                if(i < selectCtx.selector().size){
                    curr = member as ClassBase
                }
            }
            //开始选择函数
            val func = curr.getFunctionMember(selectCtx.selector(i).text.substring(1),args)
            return Pair(func,curr)
        } else {
            TODO()
        }
    }

}