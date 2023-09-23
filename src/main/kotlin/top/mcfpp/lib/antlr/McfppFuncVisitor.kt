package top.mcfpp.lib.antlr

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.lang.*
import top.mcfpp.lib.Class
import top.mcfpp.lib.Function
import top.mcfpp.lib.GlobalField
import top.mcfpp.lib.Member
import kotlin.reflect.KFunction

/**
 * 获取函数用的visitor
 */
class McfppFuncVisitor : mcfppBaseVisitor<Function?>() {

    /**
     * 获取非成员函数
     *
     * @param ctx
     * @param args
     * @return
     */
    fun getFunction(ctx: mcfppParser.NamespaceIDContext, args: ArrayList<String>): Pair<Function?, Var?>{
        Project.ctx = ctx
        val pwp = ctx.text.split(":")
        val qwq: Function? = if (pwp.size == 1) {
            GlobalField.getFunction(null, pwp[0], args)
        } else {
            GlobalField.getFunction(pwp[0], pwp[1], args)
        }
        return Pair<Function?, ClassBase?>(qwq,null)
    }

    /**
     * 获取成员函数
     *
     * @param primaryCtx
     * @param sctCtx
     * @param args
     * @return
     */
    fun getFunction(
        primaryCtx: mcfppParser.PrimaryContext,
        sctCtx: List<mcfppParser.SelectorContext>,
        args: ArrayList<String>
    ): Pair<Function?, Var?>{
        //是类的成员方法
        var curr: Var
        val getter : KFunction<Pair<Function?, Boolean>>
        val accessModifier : Member.AccessModifier
        if(primaryCtx.`var`() != null){
            val varName = primaryCtx.`var`().text
            //Var
            val v = Function.currFunction.field.getVar(varName)
            if(v == null){
                Project.error("Undefined var $varName")
            }
            if(v is ClassPointer){
                //类指针
                curr = Function.currFunction.field.getVar(varName) as ClassPointer
                var member: ClassBase?
                accessModifier = if(Function.currFunction.isClassMember){
                    Function.currFunction.ownerClass!!.getAccess(curr.clsType)
                }else{
                    Member.AccessModifier.PUBLIC
                }
                //开始选择成员对象。最后一个成员应该是函数。
                var i = 0
                while (i < sctCtx.size - 1) {
                    val re = curr.getMemberVar(sctCtx[i].text.substring(1), accessModifier)
                    member = re.first as ClassBase?
                    if (member == null) {
                        Project.error("Undefined member ${sctCtx[i].text.substring(1)} in class ${(curr as ClassBase).clsType.identifier}")
                    }
                    if (!re.second){
                        Project.error("Cannot access member ${sctCtx[i].text.substring(1)} in class ${(curr as ClassBase).clsType.identifier}")
                    }
                    i++
                    if(i < sctCtx.size){
                        curr = member as ClassBase
                    }
                }
            }else{
                //基本类型
                curr = v!!
                accessModifier = Member.AccessModifier.PUBLIC
            }
            getter = curr::getMemberFunction
        }else {
            //常量
            curr = if (primaryCtx.value().INT() != null) {
                MCInt(primaryCtx.value().text.toInt())
            } else if (primaryCtx.value().FLOAT() != null) {
                MCFloat(primaryCtx.value().text.toFloat())
            } else if (primaryCtx.value().BOOL() != null) {
                MCBool(primaryCtx.value().text.toBoolean())
            } else {
                MCString(primaryCtx.value().text)
            }
            getter = curr::getMemberFunction
            accessModifier = Member.AccessModifier.PUBLIC
        }
        //开始选择函数
        val func = getter.call(sctCtx.last().text.substring(1),args,accessModifier)
        if (!func.second){
            Project.error("Cannot access member ${sctCtx.last().text.substring(1)}")
        }
        return Pair(func.first,curr)
    }

    /**
     * 获取静态函数
     *
     * @param clsCtx
     * @param sctCtx
     * @param args
     * @return
     */
    fun getFunction(
        clsCtx: mcfppParser.ClassNameContext,
        sctCtx: List<mcfppParser.SelectorContext>,
        args: ArrayList<String>
    ): Pair<Function?, Var?>{
        //是类的成员方法
        var curr: ClassBase
        //ClassName
        val clsStr = clsCtx.text.split(":")
        val id : String
        val nsp : String?
        if(clsStr.size == 1){
            id = clsStr[0]
            nsp = null
        }else{
            id = clsStr[1]
            nsp = clsStr[0]
        }
        val qwq: Class? = GlobalField.getClass(nsp, id)
        if (qwq == null) {
            Project.error("Undefined class:" + clsCtx.text)
        }
        curr = ClassType(qwq!!)
        var member: ClassBase?
        val accessModifier = if(Function.currFunction.isClassMember){
            Function.currFunction.ownerClass!!.getAccess(curr.clsType)
        }else{
            Member.AccessModifier.PUBLIC
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

    /**
     * 获取函数
     *
     * @param ctx 函数调用的上下文
     * @param args 调用函数时传入的参数的类型
     * @return 返回获得的函数以及调用这个函数的对象（可能不存在）
     */
    fun getFunction(ctx: mcfppParser.FunctionCallContext, args: ArrayList<String>): Pair<Function?, Var?> {
        Project.ctx = ctx
        return if (ctx.namespaceID() != null && ctx.varWithSelector() == null) {
            getFunction(ctx.namespaceID(),args)
        } else if (ctx.varWithSelector() != null) {
            //是成员方法
            if(ctx.varWithSelector().primary() != null)
                getFunction(ctx.varWithSelector().primary(), ctx.varWithSelector().selector(), args)
            else
                getFunction(ctx.varWithSelector().className(), ctx.varWithSelector().selector(), args)
        } else {
            TODO()
        }
    }

}