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
            //不是类的成员
            val pwp = ctx.namespaceID().text.split(":")
            val qwq: Function? = if (pwp.size == 1) {
                GlobalField.getFunction(null , pwp[0], args)
            } else {
                GlobalField.getFunction(pwp[0], pwp[1], args)
            }
            return Pair<Function?, ClassBase?>(qwq,null)

        } else if (ctx.varWithSelector() != null) {
            //是类的成员方法
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
                val clsstr = selectCtx.className().text.split(":")
                val i : String
                val nsp : String?
                if(clsstr.size == 1){
                    i = clsstr[0]
                    nsp = null
                }else{
                    i = clsstr[1]
                    nsp = clsstr[0]
                }
                val qwq: Class? = GlobalField.getClass(nsp, i)
                if (qwq == null) {
                    Project.error("Undefined class:" + selectCtx.className().text)
                }
                ClassType(qwq!!)
            }
            var member: ClassBase?
            val accessModifier = if(Function.currFunction.isClassMember){
                Function.currFunction.parentClass!!.getAccess(curr.clsType)
            }else{
                ClassMember.AccessModifier.PUBLIC
            }
            //开始选择成员对象。最后一个成员应该是函数。
            var i = 0
            while (i < selectCtx.selector().size - 1) {
                val re = curr.getMemberVar(selectCtx.selector(i).text.substring(1), accessModifier)
                member = re.first as ClassBase?
                if (member == null) {
                    Project.error("Undefined member ${selectCtx.selector(i).text.substring(1)} in class ${curr.clsType.identifier}")
                }
                if (!re.second){
                    Project.error("Cannot access member ${selectCtx.selector(i).text.substring(1)} in class ${curr.clsType.identifier}")
                }
                i++
                if(i < selectCtx.selector().size){
                    curr = member as ClassBase
                }
            }
            //开始选择函数
            val func = curr.getMemberFunction(selectCtx.selector(i).text.substring(1),args,accessModifier)
            if (!func.second){
                Project.error("Cannot access member ${selectCtx.selector(i).text.substring(1)} in class ${curr.clsType.identifier}")
            }
            return Pair(func.first,curr)
        } else {
            TODO()
        }
    }

}