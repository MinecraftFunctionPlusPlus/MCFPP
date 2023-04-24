package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.exception.*
import top.alumopper.mcfpp.lang.*
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
        return visit(ctx.conditionalOrExpression())
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
        var re: Var? = visit(ctx.conditionalAndExpression(0))
        for (i in 1 until ctx.conditionalAndExpression().size) {
            val b: Var? = visit(ctx.conditionalAndExpression(i))
            re = if (re is MCBool && b is MCBool) {
                re.orCommand(b)
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
        var re: Var? = visit(ctx.equalityExpression(0))
        for (i in 1 until ctx.equalityExpression().size) {
            val b: Var? = visit(ctx.equalityExpression(i))
            re = if (re is MCBool && b is MCBool) {
                re.andCommand(b)
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
        var re: Var? = visit(ctx.relationalExpression(0))
        for (i in 1 until ctx.relationalExpression().size) {
            val b: Var? = visit(ctx.relationalExpression(i))
            if (ctx.op.text.equals("==")) {
                if (re is MCInt && b is MCInt) {
                    re = re.equalCommand(b)
                } else if (re is MCBool && b is MCBool) {
                    re = re.equalCommand(b)
                }
            } else {
                if (re is MCInt && b is MCInt) {
                    re = re.notEqualCommand(b)
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
        var re: Var? = visit(ctx.additiveExpression(0))
        if (ctx.additiveExpression().size != 1) {
            val b: Var? = visit(ctx.additiveExpression(1))
            if (re is MCInt && b is MCInt) {
                when (ctx.relationalOp().text) {
                    ">" -> re = re.greaterCommand(b)
                    ">=" -> re = re.greaterOrEqualCommand(b)
                    "<" -> re = re.lessCommand(b)
                    "<=" -> re = re.lessOrEqualCommand(b)
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
        var re: Var? = visit(ctx.multiplicativeExpression(0))
        for (i in 1 until ctx.multiplicativeExpression().size) {
            val b: Var? = visit(ctx.multiplicativeExpression(i))
            re = if (Objects.equals(ctx.op.text, "+")) {
                if (re is MCInt && b is MCInt) {
                    re.addCommand(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else if (Objects.equals(ctx.op.text, "-")) {
                if (re is MCInt && b is MCInt) {
                    re.minusCommand(b)
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
        var re: Var? = visit(ctx.unaryExpression(0))
        for (i in 1 until ctx.unaryExpression().size) {
            val b: Var? = visit(ctx.unaryExpression(i))
            re = if (Objects.equals(ctx.op.text, "*")) {
                if (re is MCInt && b is MCInt) {
                    re.multipleCommand(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else if (Objects.equals(ctx.op.text, "/")) {
                if (re is MCInt && b is MCInt) {
                    re.divideCommand(b)
                } else {
                    //TODO
                    throw TODOException("")
                }
            } else if (Objects.equals(ctx.op.text, "%")) {
                if (re is MCInt && b is MCInt) {
                    re.modularCommand(b)
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
        return if (ctx.basicExpression() != null) {
            visit(ctx.basicExpression())
        } else if (ctx.unaryExpression() != null) {
            val a: Var? = visit(ctx.unaryExpression())
            if (a is MCBool) {
                a.opposeCommand()
            } else {
                throw ArgumentNotMatchException("运算符'!'只能用于bool类型")
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
        return if (ctx.primary() != null) {
            visit(ctx.primary())
        } else {
            //Var With Selector
            visit(ctx.varWithSelector())
        }
    }

    @Override
    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var? {
        var curr: CanSelectMember
        curr = if (ctx.`var`() != null) {
            //Var
            Function.currFunction!!.getVar(ctx.text) as ClassPointer
        } else {
            //ClassName
            val qwq: Class? = Project.global.cache!!.classes.getOrDefault(ctx.className().text, null)
            if (qwq == null) {
                Project.logger.error(
                    "Undefined class:" + ctx.className().text +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
                Project.errorCount++
            }
            ClassType(qwq!!)
        }
        var member: Var? = null
        //开始选择
        var i = 0
        while (i < ctx.selector().size) {
            member = curr.getVarMember(ctx.selector(i).text.substring(1))
            if (member == null) {
                Project.logger.error(
                    "Undefined member " + ctx.selector(i).text + " in class " + curr.Class()!!.identifier +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
                Project.errorCount++
            }
            i++
            curr = (member as CanSelectMember?)!!
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
        return if (ctx.Identifier() != null) {
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                //没有数组选取
                val qwq: String = ctx.Identifier().text
                val re: Var? = Function.currFunction!!.getVar(qwq)
                //TODO 从类的成员中选取。待定特性。
                /*
                if(re == null && Function.currFunction.Class() != null){
                    re = Function.currFunction.Class().getMemberVar(ctx.getText());
                }
                */if (re == null) {
                    Project.logger.error(
                        "Undefined variable:" + qwq +
                                " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                    )
                    Project.errorCount++
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
        } else {
            //TODO
            throw TODOException("")
        }
    }

    @Override
    override fun visitConstructorCall(ctx: mcfppParser.ConstructorCallContext): Var? {
        val cls: Class? = Project.global.cache!!.classes.getOrDefault(ctx.className().text, null)
        if (cls == null) {
            Project.logger.error(
                "Undefined class:" + ctx.className().text +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
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
                Project.logger.error(
                    "Catch Exception when instantiate native class: " + cls.cls +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
                Project.logger.error(e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            } catch (e: InstantiationException) {
                Project.logger.error(
                    "Catch Exception when instantiate native class: " + cls.cls +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
                Project.logger.error(e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                Project.logger.error(
                    "Catch Exception when instantiate native class: " + cls.cls +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
                Project.logger.error(e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                Project.logger.error(
                    "Catch Exception when instantiate native class: " + cls.cls +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
                Project.logger.error(e.message + " " + e.cause + "\n")
                throw RuntimeException(e)
            }
        }
        cls!!
        val constructor = cls.getConstructor(FunctionParam.getVarTypes(args))
        if (constructor == null) {
            Project.logger.error(
                "No constructor like: " + FunctionParam.getVarTypes(args) + " defined in class " + ctx.className()
                    .text +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
            throw FunctionNotDefineException()
        }
        //调用构造函数
        val obj: ClassObject = cls.newInstance()
        constructor.invoke(args, ctx.getStart().line, obj.initPointer)
        //调用函数
        return obj
    }
}