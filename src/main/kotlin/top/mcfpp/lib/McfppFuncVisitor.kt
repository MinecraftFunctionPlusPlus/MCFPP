package top.mcfpp.lib

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.exception.ArgumentNotMatchException
import top.mcfpp.lang.*

/**
 * 获取函数用的visitor
 */
class McfppFuncVisitor : mcfppBaseVisitor<Function?>() {

    fun getFunction(ctx: mcfppParser.NamespaceIDContext, args: ArrayList<String>): Pair<Function?, ClassBase?>{
        Project.ctx = ctx
        val pwp = ctx.text.split(":")
        val qwq: Function? = if (pwp.size == 1) {
            GlobalField.getFunction(null , pwp[0], args)
        } else {
            GlobalField.getFunction(pwp[0], pwp[1], args)
        }
        return Pair<Function?, ClassBase?>(qwq,null)
    }

    fun getFunction(varCtx: mcfppParser.VarContext, sctCtx: List<mcfppParser.SelectorContext>, args: ArrayList<String>): Pair<Function?, ClassBase?>{
        //是类的成员方法
        var curr: ClassBase
        val varName = varCtx.text.substring(0,varCtx.text.indexOf("."))
        //Var
        if(Function.currFunction.field.getVar(varName) is ClassPointer){
            curr = Function.currFunction.field.getVar(varName) as ClassPointer
        }else{
            Project.error("$varName is not a class pointer")
            throw ArgumentNotMatchException("$varName is not a class pointer")
        }
        var member: ClassBase?
        val accessModifier = if(Function.currFunction.isClassMember){
            Function.currFunction.parentClass!!.getAccess(curr.clsType)
        }else{
            ClassMember.AccessModifier.PUBLIC
        }
        //开始选择成员对象。最后一个成员应该是函数。
        var i = 0
        while (i < sctCtx.size - 1) {
            val re = curr.getMemberVar(sctCtx[i].text.substring(1), accessModifier)
            member = re.first as ClassBase?
            if (member == null) {
                Project.error("Undefined member ${sctCtx[i].text.substring(1)} in class ${curr.clsType.identifier}")
            }
            if (!re.second){
                Project.error("Cannot access member ${sctCtx[i].text.substring(1)} in class ${curr.clsType.identifier}")
            }
            i++
            if(i < sctCtx.size){
                curr = member as ClassBase
            }
        }
        //开始选择函数
        val func = curr.getMemberFunction(sctCtx[i].text.substring(1),args,accessModifier)
        if (!func.second){
            Project.error("Cannot access member ${sctCtx[i].text.substring(1)} in class ${curr.clsType.identifier}")
        }
        return Pair(func.first,curr)
    }

    fun getFunction(clsCtx: mcfppParser.ClassNameContext, sctCtx: List<mcfppParser.SelectorContext>, args: ArrayList<String>): Pair<Function?, ClassBase?>{
        //是类的成员方法
        var curr: ClassBase
        //ClassName
        val clsstr = clsCtx.text.split(":")
        val id : String
        val nsp : String?
        if(clsstr.size == 1){
            id = clsstr[0]
            nsp = null
        }else{
            id = clsstr[1]
            nsp = clsstr[0]
        }
        val qwq: Class? = GlobalField.getClass(nsp, id)
        if (qwq == null) {
            Project.error("Undefined class:" + clsCtx.text)
        }
        curr = ClassType(qwq!!)
        var member: ClassBase?
        val accessModifier = if(Function.currFunction.isClassMember){
            Function.currFunction.parentClass!!.getAccess(curr.clsType)
        }else{
            ClassMember.AccessModifier.PUBLIC
        }
        //开始选择成员对象。最后一个成员应该是函数。
        var i = 0
        while (i < sctCtx.size - 1) {
            val re = curr.getMemberVar(sctCtx[i].text.substring(1), accessModifier)
            member = re.first as ClassBase?
            if (member == null) {
                Project.error("Undefined member ${sctCtx[i].text.substring(1)} in class ${curr.clsType.identifier}")
            }
            if (!re.second){
                Project.error("Cannot access member ${sctCtx[i].text.substring(1)} in class ${curr.clsType.identifier}")
            }
            i++
            if(i < sctCtx.size){
                curr = member as ClassBase
            }
        }
        //开始选择函数
        val func = curr.getMemberFunction(sctCtx[i].text.substring(1),args,accessModifier)
        if (!func.second){
            Project.error("Cannot access member ${sctCtx[i].text.substring(1)} in class ${curr.clsType.identifier}")
        }
        return Pair(func.first,curr)
    }

    fun getFunction(ctx: mcfppParser.FunctionCallContext, args: ArrayList<String>): Pair<Function?, ClassBase?> {
        Project.ctx = ctx
        return if (ctx.namespaceID() != null && ctx.varWithSelector() == null) {
            getFunction(ctx.namespaceID(),args)
        } else if (ctx.varWithSelector() != null) {
            //是类的成员方法
            if(ctx.varWithSelector().`var`() != null)
                getFunction(ctx.varWithSelector().`var`(), ctx.varWithSelector().selector(), args)
            else
                getFunction(ctx.varWithSelector().className(), ctx.varWithSelector().selector(), args)
        } else {
            TODO()
        }
    }

}