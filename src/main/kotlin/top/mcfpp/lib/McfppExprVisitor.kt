package top.mcfpp.lib

import mcfppBaseVisitor
import top.mcfpp.Project
import top.mcfpp.exception.*
import top.mcfpp.lang.*
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.collections.ArrayList

/**
 * 获取表达式结果用的visitor。解析并计算一个形如a+b*c的表达式。
 */
class McfppExprVisitor : mcfppBaseVisitor<Var?>() {
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
    //TODO 三目表达式。可能会实现，但是泠雪是懒狐，不想做。
    //@Override
    //public Var visitConditionalExpression(mcfppParser.ConditionalExpressionContext ctx){
    //    if(ctx.expression().size() == 0){
    //        return visit(ctx.conditionalOrExpression());
    //    }else {
    //        return null;
    //    }
    //}
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
            re = if (re is MCBool && b is MCBool) {
                re.or(b)
            } else {
                throw ArgumentNotMatchException("运算符'||'只能用于bool类型")
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
            re = if (re is MCBool && b is MCBool) {
                re.and(b)
            } else {
                throw ArgumentNotMatchException("运算符'&&'只能用于bool类型")
            }
        }
        return re
    }

    /**
     * 计算一个等于或不等于表达式，例如a == b和a != b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //等于或不等于
    @Override
    override fun visitEqualityExpression(ctx: mcfppParser.EqualityExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.relationalExpression(0))
        for (i in 1 until ctx.relationalExpression().size) {
            val b: Var? = visit(ctx.relationalExpression(i))
            if (ctx.op.text.equals("==")) {
                if (re is MCInt && b is MCInt) {
                    re = re.isEqual(b)
                } else if (re is MCBool && b is MCBool) {
                    re = re.equalCommand(b)
                }
            } else {
                if (re is MCInt && b is MCInt) {
                    re = re.notEqual(b)
                } else if (re is MCBool && b is MCBool) {
                    re = re.notEqualCommand(b)
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
            if (re is MCInt && b is MCInt) {
                when (ctx.relationalOp().text) {
                    ">" -> re = re.isGreater(b)
                    ">=" -> re = re.isGreaterOrEqual(b)
                    "<" -> re = re.isLess(b)
                    "<=" -> re = re.isLessOrEqual(b)
                }
            } else {
                TODO()
            }
        }
        return re
    }

    /**
     * 计算一个加减法表达式，例如a + b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitAdditiveExpression(ctx: mcfppParser.AdditiveExpressionContext): Var? {
        Project.ctx = ctx
        var re: Var? = visit(ctx.multiplicativeExpression(0))
        for (i in 1 until ctx.multiplicativeExpression().size) {
            val b: Var? = visit(ctx.multiplicativeExpression(i))
            re = if (Objects.equals(ctx.op.text, "+")) {
                if (re is MCInt && b is MCInt) {
                    re.plus(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else if (Objects.equals(ctx.op.text, "-")) {
                if (re is MCInt && b is MCInt) {
                    re.minus(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else {
                return null
            }
        }
        return re
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
            val b: Var? = visit(ctx.unaryExpression(i))
            re = if (ctx.op.text == "*") {
                if (re is MCInt && b is MCInt) {
                    re.multiple(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else if (ctx.op.text == "/") {
                if (re is MCInt && b is MCInt) {
                    re.divide(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else if (ctx.op.text == "%") {
                if (re is MCInt && b is MCInt) {
                    re.modular(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else {
                return null
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
            val a: Var? = visit(ctx.unaryExpression())
            if (a is MCBool) {
                a.negation()
            } else {
                throw ArgumentNotMatchException("运算符'!'只能用于bool类型")
            }
        } else {
            //类型强制转换
            visit(ctx.castExpression())
        }
    }

    /**
     * 对获取到的变量进行包装处理
     *
     * @param ctx
     * @return
     */
    @Override
    override fun visitRightVarExpression(ctx: mcfppParser.RightVarExpressionContext?): Var {
        return visit(ctx!!.basicExpression())!!.getTempVar()
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
        var curr: CanSelectMember
        curr = if (ctx.`var`() != null) {
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
            val qwq: Class? = Project.global.cache.classes[ctx.className().text]
            if (qwq == null) {
                Project.error("Undefined class:" + ctx.className().text)
            }
            ClassType(qwq!!)
        }
        var member: Var? = null
        //开始选择
        var i = 0
        while (i < ctx.selector().size) {
            member = curr.getVarMember(ctx.selector(i).text.substring(1))
            if (member == null) {
                Project.error("Undefined member " + ctx.selector(i).text.substring(1) + " in class " + curr.Class()!!.identifier)
            }
            i++
            if(i < ctx.selector().size){
                curr = member as CanSelectMember
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
    override fun visitVar(ctx: mcfppParser.VarContext): Var? {
        Project.ctx = ctx
        return if (ctx.Identifier() != null) {
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                //没有数组选取
                val qwq: String = ctx.Identifier().text
                val re: Var? = Function.currFunction.getVar(qwq)
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
        } else{
            //this或者super
            val s = if(ctx.SUPER() != null){
                "super"
            }else{
                "this"
            }
            val re: Var? = Function.currFunction.getVar(s)
            if (re == null) {
                Project.error("$s can only be used in member functions.")
            }
            re
        }
    }

    @Override
    override fun visitConstructorCall(ctx: mcfppParser.ConstructorCallContext): Var? {
        Project.ctx = ctx
        val cls: Class? = Project.global.cache.classes.getOrDefault(ctx.className().text, null)
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
        //如果是native类
        if (cls is NativeClass) {
            //创建新实例并返回
            return try {
                cls.newInstance(args)
            } catch (e: InvocationTargetException) {
                Project.error("Catch Exception when instantiate native class: " + cls.cls + "\n" + e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            } catch (e: InstantiationException) {
                Project.error("Catch Exception when instantiate native class: " + cls.cls + "\n" + e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                Project.error("Catch Exception when instantiate native class: " + cls.cls + "\n" + e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                Project.error("Catch Exception when instantiate native class: " + cls.cls + "\n" + e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
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
}