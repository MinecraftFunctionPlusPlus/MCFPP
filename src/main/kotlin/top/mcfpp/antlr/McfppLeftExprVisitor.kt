package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.ClassNotDefineException
import top.mcfpp.exception.FunctionNotDefineException
import top.mcfpp.exception.TODOException
import top.mcfpp.lang.*
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
import top.mcfpp.util.StringHelper

class McfppLeftExprVisitor : mcfppParserBaseVisitor<Var?>(){
    private var currSelector : CanSelectMember? = null

    /**
     * 计算一个基本的表达式。可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitBasicExpression(ctx: mcfppParser.BasicExpressionContext): Var? {
        Project.ctx = ctx
        return if (ctx.primary() != null) {
            visit(ctx.primary())
        } else {
            visit(ctx.varWithSelector())
        }
    }

    /**
     * 从类中选择一个成员。返回的成员包含了它所在的对象的信息
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var? {
        Project.ctx = ctx
        currSelector = if(ctx.primary() != null){
            visit(ctx.primary())
        }else{
            if(ctx.type().className() != null){
                //ClassName
                val clsstr = StringHelper.splitNamespaceID(ctx.type().text)
                val qwq: Class? = GlobalField.getClass(clsstr.first, clsstr.second)
                if (qwq == null) {
                    Project.error("Undefined class:" + ctx.type().className().text)
                    throw ClassNotDefineException()
                }
                ClassType(qwq!!)
            }else{
                CompoundDataType(
                    //基本类型
                    when(ctx.type().text){
                        "int" -> MCInt.data
                        else -> TODO()
                    }
                )
            }
        }
        for (selector in ctx.selector().subList(0,ctx.selector().size-1)){
            visit(selector)
        }
        return visit(ctx.selector(ctx.selector().size-1).`var`())
    }

    @Override
    override fun visitSelector(ctx: mcfppParser.SelectorContext?): Var? {
        currSelector = visit(ctx!!.`var`())!!.getTempVar()
        return null
    }

    /**
     * 一个初级表达式，可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitPrimary(ctx: mcfppParser.PrimaryContext): Var? {
        Project.ctx = ctx
        if (ctx.`var`() != null) {
            //变量
            return visit(ctx.`var`())
        } else if (ctx.value() != null) {
            //数字
            val num: mcfppParser.ValueContext = ctx.value()
            if (num.IntegerLiteral() != null) {
                return MCInt(Integer.parseInt(num.IntegerLiteral().text))
            } else if (num.LineString() != null) {
                val r: String = num.LineString().text
                return MCString(r.substring(1, r.length - 1))
            }
        } else if(ctx.constructorCall() != null){
            return visit(ctx.constructorCall())
        } else{
            //this或者super
            val s = if(ctx.SUPER() != null){
                "super"
            }else{
                "this"
            }
            val re: Var? = Function.field.getVar(s)
            if (re == null) {
                Project.error("$s can only be used in member functions.")
            }
            return re
        }
        return null
    }

    /**
     * 变量
     * @param ctx the parse tree
     * @return 变量
     */
    @Override
    @InsertCommand
    override fun visitVar(ctx: mcfppParser.VarContext): Var? {
        Project.ctx = ctx
        return if (ctx.Identifier() != null && ctx.arguments() == null) {
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                //没有数组选取
                val qwq: String = ctx.Identifier().text
                if(currSelector == null){
                    val re: Var? = Function.field.getVar(qwq)
                    if (re == null) {
                        Project.error("Undefined variable:$qwq")
                    }
                    re
                }else{
                    val cpd = when(currSelector){
                        is CompoundDataType -> (currSelector as CompoundDataType).dataType
                        is ClassPointer -> (currSelector as ClassPointer).clsType
                        else -> TODO()
                    }
                    val am = if(Function.currFunction !is ExtensionFunction && Function.currFunction.ownerType == Function.Companion.OwnerType.CLASS){
                        Function.currFunction.parentClass()!!.getAccess(cpd)
                    }else if(Function.currFunction !is ExtensionFunction && Function.currFunction.ownerType == Function.Companion.OwnerType.STRUCT){
                        Function.currFunction.parentStruct()!!.getAccess(cpd)
                    }else{
                        Member.AccessModifier.PUBLIC
                    }
                    val re  = currSelector!!.getMemberVar(qwq,am)
                    if (re.first == null) {
                        Project.error("Undefined variable:$qwq")
                    }
                    if (!re.second){
                        Project.error("Cannot access member $qwq")
                    }
                    re.first
                }
            } else {
                //TODO 是数组调用
                throw TODOException("")
            }
        } else if (ctx.expression() != null) {
            // '(' expression ')'
            visit(ctx.expression())
        } else {
            //函数的调用
            Function.addCommand("#" + ctx.text)
            //参数获取
            val args: ArrayList<Var> = ArrayList()
            val exprVisitor = McfppExprVisitor()
            if(ctx.arguments().expressionList() != null){
                for (expr in ctx.arguments().expressionList().expression()) {
                    args.add(exprVisitor.visit(expr)!!)
                }
            }
            val p = StringHelper.splitNamespaceID(ctx.namespaceID().text)
            val func = if(currSelector == null){
                McfppFuncManager().getFunction(p.first,p.second, FunctionParam.getVarTypes(args))
            }else{
                if(p.first != null){
                    Project.warn("Invalid namespace usage ${p.first} in function call ")
                }
                McfppFuncManager().getFunction(currSelector!!,p.second, FunctionParam.getVarTypes(args))
            }
            if (func == null) {
                Project.error("Function " + ctx.text + " not defined")
                throw FunctionNotDefineException()
            }
            func.invoke(args,currSelector)
            //函数树
            Function.currFunction.child.add(func)
            func.parent.add(Function.currFunction)
            func.returnVar
        }
    }

    @Override
    override fun visitConstructorCall(ctx: mcfppParser.ConstructorCallContext): Var? {
        Project.ctx = ctx
        val clsstr = ctx.className().text.split(":")
        val cls: Class? = if(clsstr.size == 2) {
            GlobalField.getClass(clsstr[0], clsstr[1])
        }else{
            GlobalField.getClass(null, clsstr[0])
        }
        if (cls == null) {
            Project.error("Undefined class:" + ctx.className().text)
            throw ClassNotDefineException()
        }
        //获取参数列表
        //参数获取
        val args: ArrayList<Var> = ArrayList()
        val exprVisitor = McfppExprVisitor()
        if (ctx.arguments().expressionList() != null) {
            for (expr in ctx.arguments().expressionList().expression()) {
                args.add(exprVisitor.visit(expr)!!)
            }
        }
        cls!!
        val constructor = cls.getConstructor(FunctionParam.getVarTypes(args))
        if (constructor == null) {
            Project.error("No constructor like: " + FunctionParam.getVarTypes(args) + " defined in class " + ctx.className().text)
            throw FunctionNotDefineException()
        }
        //获取对象
        val obj: ClassObject = cls.newInstance()
        //调用构造函数
        constructor.invoke(args, caller = obj.initPointer)
        return obj
    }
}