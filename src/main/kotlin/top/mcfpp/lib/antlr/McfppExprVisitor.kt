package top.mcfpp.lib.antlr

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.*
import top.mcfpp.lang.*
import top.mcfpp.lang.Number
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
import top.mcfpp.util.Utils
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

/**
 * 获取表达式结果用的visitor。解析并计算一个形如a+b*c的表达式。
 */
class McfppExprVisitor : mcfppBaseVisitor<Var?>() {
    private val tempVarCommandCache = HashMap<Var, String>()

    /**
     * 计算一个复杂表达式
     * @param ctx the parse tree
     * @return 表达式的结果
     */
    @Override
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var? {
        Project.ctx = ctx
        return if(ctx.primary() != null){
            visit(ctx.primary())
        }else{
            visit(ctx.conditionalOrExpression())
        }
    }

    /*TODO 三目表达式。可能会实现，但是泠雪是懒狐，不想做。
     *@Override
     *public Var visitConditionalExpression(mcfppParser.ConditionalExpressionContext ctx){
     *    if(ctx.expression().size() == 0){
     *        return visit(ctx.conditionalOrExpression());
     *    }else {
     *        return null;
     *    }
     *}
    */

    /**
     * 计算一个或表达式。例如 a || b。
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitConditionalOrExpression(ctx: mcfppParser.ConditionalOrExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.conditionalAndExpression(0))
        for (i in 1 until ctx.conditionalAndExpression().size) {
            val b: Var? = visit(ctx.conditionalAndExpression(i))
            if(!re!!.isTemp){
                re = re.getTempVar(tempVarCommandCache)
            }
            re = if (re is MCBool && b is MCBool) {
                re.or(b)
            } else {
                Project.error("The operator \"||\" cannot be used between ${re.type} and ${b!!.type}")
                throw IllegalArgumentException("")
            }
        }
        return re
    }

    /**
     * 计算一个与表达式。例如a && b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //和
    @Override
    override fun visitConditionalAndExpression(ctx: mcfppParser.ConditionalAndExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.equalityExpression(0))
        for (i in 1 until ctx.equalityExpression().size) {
            val b: Var? = visit(ctx.equalityExpression(i))
            if(!re!!.isTemp){
                re = re.getTempVar(tempVarCommandCache)
            }
            re = if (re is MCBool && b is MCBool) {
                re.and(b)
            } else {
                Project.error("The operator \"&&\" cannot be used between ${re.type} and ${b!!.type}")
                throw IllegalArgumentException("")
            }
        }
        return re
    }

    /**
     * 计算一个等于或不等于表达式，例如a == b和a != b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitEqualityExpression(ctx: mcfppParser.EqualityExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.relationalExpression(0))
        for (i in 1 until ctx.relationalExpression().size) {
            val b: Var? = visit(ctx.relationalExpression(i))
            if(!re!!.isTemp){
                re = re.getTempVar(tempVarCommandCache)
            }
            if (ctx.op.text.equals("==")) {
                re = if (re is MCInt && b is MCInt) {
                    re.isEqual(b)
                } else if (re is MCBool && b is MCBool) {
                    re.equalCommand(b)
                } else if (re is MCFloat && b is MCFloat){
                    re.isEqual(b)
                } else{
                    Project.error("The operator \"${ctx.op.text}\" cannot be used between ${re.type} and ${b!!.type}")
                    throw IllegalArgumentException("")
                }
            } else {
                re = if (re is MCInt && b is MCInt) {
                    re.notEqual(b)
                } else if (re is MCBool && b is MCBool) {
                    re.notEqualCommand(b)
                } else if (re is MCFloat && b is MCFloat){
                    re.notEqual(b)
                }else{
                    Project.error("The operator \"${ctx.op.text}\" cannot be used between ${re.type} and ${b!!.type}")
                    throw IllegalArgumentException("")
                }
            }
        }
        return re
    }

    /**
     * 计算一个比较表达式，例如a > b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitRelationalExpression(ctx: mcfppParser.RelationalExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.additiveExpression(0))
        if (ctx.additiveExpression().size != 1) {
            val b: Var? = visit(ctx.additiveExpression(1))
            if(!re!!.isTemp){
                re = re.getTempVar(tempVarCommandCache)
            }
            if (re is MCInt && b is MCInt) {
                when (ctx.relationalOp().text) {
                    ">" -> re = re.isGreater(b)
                    ">=" -> re = re.isGreaterOrEqual(b)
                    "<" -> re = re.isLess(b)
                    "<=" -> re = re.isLessOrEqual(b)
                }
            } else if(re is MCFloat && b is MCFloat){
                when (ctx.relationalOp().text) {
                    ">" -> re = re.isGreater(b)
                    ">=" -> re = re.isGreaterOrEqual(b)
                    "<" -> re = re.isLess(b)
                    "<=" -> re = re.isLessOrEqual(b)
                }
            }else {
                Project.error("The operator \"${ctx.relationalOp()}\" cannot be used between ${re.type} and ${b!!.type}")
                throw IllegalArgumentException("")
            }
        }
        return re
    }


    private var visitAdditiveExpressionRe : Var? = null
    /**
     * 计算一个加减法表达式，例如a + b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitAdditiveExpression(ctx: mcfppParser.AdditiveExpressionContext): Var? {
        Project.ctx = ctx
        visitAdditiveExpressionRe = visit(ctx.multiplicativeExpression(0))
        for (i in 1 until ctx.multiplicativeExpression().size) {
            var b: Var? = visit(ctx.multiplicativeExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(visitAdditiveExpressionRe!! != MCFloat.ssObj){
                visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.getTempVar(tempVarCommandCache)
            }
            val qwq = visitAdditiveExpressionRe
            visitAdditiveExpressionRe = if (Objects.equals(ctx.op.text, "+")) {
                if (visitAdditiveExpressionRe is Number<*> && b is Number<*>) {
                    (visitAdditiveExpressionRe as Number<*>).plus(b)
                }else{
                    null
                }
            } else if (Objects.equals(ctx.op.text, "-")) {
                if (visitAdditiveExpressionRe is Number<*> && b is Number<*>) {
                    (visitAdditiveExpressionRe as Number<*>).minus(b)
                } else {
                    null
                }
            } else {
                null
            }
            if(visitAdditiveExpressionRe == null){
                Project.error("The operator \"${ctx.op.text}\" cannot be used between ${qwq!!.type} and ${b!!.type}.")
                Utils.stopCompile(IllegalArgumentException(""))
                exitProcess(1)
            }
        }
        return visitAdditiveExpressionRe
    }

    /**
     * 计算一个乘除法表达式，例如a * b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //乘法
    @Override
    override fun visitMultiplicativeExpression(ctx: mcfppParser.MultiplicativeExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.unaryExpression(0))
        for (i in 1 until ctx.unaryExpression().size) {
            if(visitAdditiveExpressionRe == MCFloat.ssObj){
                visitAdditiveExpressionRe = MCFloat.ssObjToVar()
            }
            var b: Var? = visit(ctx.unaryExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(!re!!.isTemp){
                re = re.getTempVar(tempVarCommandCache)
            }
            val qwq = re
            re = when(ctx.op.text){
                "*" -> {
                    if (re is Number<*> && b is Number<*>) {
                        re.multiple(b)
                    }else{
                        null
                    }
                }
                "/" -> {
                    if (re is Number<*> && b is Number<*>) {
                        re.divide(b)
                    }else{
                        null
                    }
                }
                "%" -> {
                    if (re is MCInt && b is MCInt) {
                        re.modular(b)
                    }else{
                        null
                    }
                }
                else -> null
            }
            if(re == null){
                Project.error("The operator \"${ctx.op.text}\" cannot be used between ${qwq.type} and ${b!!.type}.")
                Utils.stopCompile(IllegalArgumentException(""))
                exitProcess(1)
            }
        }
        return re
    }

    /**
     * 计算一个单目表达式。比如!a 或者 (int)a
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitUnaryExpression(ctx: mcfppParser.UnaryExpressionContext): Var? {
        Project.ctx = ctx
        return if (ctx.rightVarExpression() != null) {
            visit(ctx.rightVarExpression())
        } else if (ctx.unaryExpression() != null) {
            var a: Var? = visit(ctx.unaryExpression())
            if (a is MCBool) {
                if(!a.isTemp){
                    a = a.getTempVar(tempVarCommandCache)
                }
                a.negation()
            } else {
                Project.error("The operator \"!\" cannot be used with ${a!!.type}")
                Utils.stopCompile(IllegalArgumentException(""))
                exitProcess(1)
            }
        } else {
            //类型强制转换
            visit(ctx.castExpression())
        }
    }


    /**
     * 计算一个强制转换表达式。
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitCastExpression(ctx: mcfppParser.CastExpressionContext): Var? {
        Project.ctx = ctx
        val a: Var? = visit(ctx.unaryExpression())
        return a!!.cast(ctx.type().text)
    }

    /**
     * 对获取到的变量进行包装处理
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitRightVarExpression(ctx: mcfppParser.RightVarExpressionContext?): Var {
        return visit(ctx!!.basicExpression())!!
        //return visit(ctx!!.basicExpression())!!.getTempVar(tempVarCommandCache)
    }

    /**
     * 计算一个基本的表达式。可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //基本表达式
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
        var curr: ClassBase
        curr = if (ctx.`var`() != null) {
            val varName = ctx.text.substring(0,ctx.text.indexOf("."))
            val pwp = Function.currFunction.field.getVar(varName)
            //Var
            if(pwp is ClassPointer){
                pwp
            }else if(pwp == null){
                Project.error("Undefined variable:$varName")
                Utils.stopCompile(ArgumentNotMatchException("Undefined variable:$varName"))
                exitProcess(1)
            }else{
                Project.error("$varName is not a class pointer")
                Utils.stopCompile(ArgumentNotMatchException("$varName is not a class pointer"))
                exitProcess(1)
            }
        } else {
            //ClassName
            val clsstr = ctx.className().text.split(":")
            val qwq: Class? = if(clsstr.size == 2) {
                GlobalField.getClass(clsstr[0], clsstr[1])
            }else{
                GlobalField.getClass(null, clsstr[0])
            }
            if (qwq == null) {
                Project.error("Undefined class:" + ctx.className().text)
            }
            ClassType(qwq!!)
        }
        var member: Var? = null
        //开始选择
        val accessModifier = if(Function.currFunction.isClassMember){
            Function.currFunction.ownerClass!!.getAccess(curr.clsType)
        }else{
            Member.AccessModifier.PUBLIC
        }
        var i = 0
        while (i < ctx.selector().size) {
            val re = curr.getMemberVar(ctx.selector(i).text.substring(1),accessModifier)
            member = re.first
            if (member == null) {
                Project.error("Undefined member ${ctx.selector(i).text.substring(1)} in class ${curr.clsType.identifier}")
            }
            if (!re.second){
                Project.error("Cannot access member ${ctx.selector(i).text.substring(1)} in class ${curr.clsType.identifier}")
            }
            i++
            if(i < ctx.selector().size){
                curr = member as ClassBase
            }
        }
        return member
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
        } else {
            //数字
            val num: mcfppParser.ValueContext = ctx.value()
            if (num.INT() != null) {
                return MCInt(Integer.parseInt(num.INT().text))
            } else if (num.STRING() != null) {
                val r: String = num.STRING().text
                return MCString(r.substring(1, r.length - 1))
            }
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
        return if (ctx.Identifier() != null) {
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                //没有数组选取
                val qwq: String = ctx.Identifier().text
                val re: Var? = Function.currFunction.field.getVar(qwq)
                if (re == null) {
                    Project.error("Undefined variable:$qwq")
                }
                re
            } else {
                //TODO 是数组调用
                throw TODOException("")
            }
        } else if (ctx.expression() != null) {
            // '(' expression ')'
            visit(ctx.expression())
        } else if (ctx.constructorCall() != null) {
            // constructorCall
            visit(ctx.constructorCall())
        } else if(ctx.TargetSelector() != null) {
            TODO()
        } else if (ctx.arguments() != null){
            //函数的调用
            //   namespaceID arguments
            //   var selector* arguments
            //   className selector+ arguments
            Function.addCommand("#" + ctx.text)
            //参数获取
            val args: ArrayList<Var> = ArrayList()
            val exprVisitor = McfppExprVisitor()
            if(ctx.arguments().expressionList() != null){
                for (expr in ctx.arguments().expressionList().expression()) {
                    args.add(exprVisitor.visit(expr)!!)
                }
            }
            val functionCallCTX = mcfppParser.FunctionCallContext(ctx,0)
            functionCallCTX.addChild(ctx.arguments())
            //函数对象获取
            val curr = if(ctx.namespaceID() != null)
                McfppFuncVisitor().getFunction(ctx.namespaceID(), FunctionParam.getVarTypes(args))
            else if(ctx.`var`() != null)
                McfppFuncVisitor().getFunction(ctx.`var`(),ctx.selector(), FunctionParam.getVarTypes(args))
            else
                McfppFuncVisitor().getFunction(ctx.className(),ctx.selector(), FunctionParam.getVarTypes(args))
            val func = curr.first
            val obj = curr.second
            if (func == null) {
                Project.error("Function " + ctx.text + " not defined")
                throw FunctionNotDefineException()
            }
            func.invoke(args,obj)
            //函数树
            Function.currFunction.child.add(func)
            func.parent.add(Function.currFunction)
            func.returnVar
        }else{
            //this或者super
            val s = if(ctx.SUPER() != null){
                "super"
            }else{
                "this"
            }
            val re: Var? = Function.currFunction.field.getVar(s)
            if (re == null) {
                Project.error("$s can only be used in member functions.")
            }
            re
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
        constructor.invoke(args, obj.initPointer)
        return obj
    }

    companion object{
        /**
         * 遍历到的计算的树的深度
         */
        var treeDepth : Int = -1
    }
}