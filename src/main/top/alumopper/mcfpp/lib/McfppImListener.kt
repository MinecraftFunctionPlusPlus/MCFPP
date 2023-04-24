package top.alumopper.mcfpp.lib

import org.antlr.v4.runtime.RuleContext
import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.command.Commands
import top.alumopper.mcfpp.exception.*
import top.alumopper.mcfpp.lang.*
import top.alumopper.mcfpp.lib.mcfppParser.*

class McfppImListener : mcfppBaseListener() {
    /**
     * 进入一个函数体
     * @param ctx the parse tree
     */
    @Override
    override fun enterFunctionBody(ctx: FunctionBodyContext) {
        var f: Function
        //获取函数对象
        if (ctx.parent.parent !is ClassMemberContext) {
            //不是类成员
            //创建函数对象
            val parent: FunctionDeclarationContext = ctx.parent as FunctionDeclarationContext
            f = if (parent.namespaceID().Identifier().size == 1) {
                Function(parent.namespaceID().Identifier(0).text)
            } else {
                Function(
                    parent.namespaceID().Identifier(0).text,
                    parent.namespaceID().Identifier(1).text
                )
            }
            //解析参数
            if (parent.parameterList() != null) {
                f.addParams((ctx.parent as FunctionDeclarationContext).parameterList())
            }
            //获取缓存中的对象
            f = Project.global.cache.getFunction(f.namespace, f.name, f.paramTypeList)!!
        } else if (ctx.parent is ConstructorDeclarationContext) {
            //是构造函数
            //创建构造函数对象并解析参数
            val temp = Function("temp")
            if ((ctx.parent as ConstructorDeclarationContext).parameterList() != null) {
                temp.addParams((ctx.parent as ConstructorDeclarationContext).parameterList())
            }
            //获取缓存中的对象
            f = Class.currClass!!.getConstructor(FunctionParam.toStringList(temp.params))!!
        } else {
            //是类的成员函数
            //创建函数对象并解析参数
            val qwq: ClassFunctionDeclarationContext = ctx.parent as ClassFunctionDeclarationContext
            f = Function(qwq.Identifier().text, Class.currClass, false)
            if (qwq.parameterList() != null) {
                f.addParams(qwq.parameterList())
            }
            //获取缓存中的对象
            f = Class.currClass!!.cache!!.getFunction(f.namespace, f.name, f.paramTypeList)!!
        }
        Function.currFunction = f
    }

    /**
     * 离开一个函数体
     * @param ctx the parse tree
     */
    @Override
    override fun exitFunctionBody(ctx: FunctionBodyContext?) {
        if (Class.currClass == null) {
            //不在类中
            Function.currFunction = Project.global.globalInit
        } else {
            Function.currFunction = Class.currClass!!.classPreInit
        }
    }

    /**
     * 进入命名空间声明的时候
     * @param ctx the parse tree
     */
    @Override
    override fun exitNamespaceDeclaration(ctx: NamespaceDeclarationContext) {
        Project.currNamespace = ctx.Identifier().text
    }

    /**
     * 变量声明
     * @param ctx the parse tree
     */
    @Override
    override fun exitFieldDeclaration(ctx: FieldDeclarationContext) {
        //变量生成

        //变量生成
        val `var`: Var = if (ctx.parent is ClassMemberContext) {
            return
        } else {
            //函数变量，生成
            Var.build(ctx, Function.currFunction!!)!!
        }
        //变量注册
        //一定是函数变量
        if (!Function.currFunction!!.cache.putVar(ctx.Identifier().text, `var`)) {
            Project.logger.error(
                "Duplicate defined variable name:" + ctx.Identifier().text +
                        " at " + Project.currFile!!.name + " line:" + ctx.getStart().line
            )
            Project.errorCount++
            throw VariableDuplicationException()
        }
        Function.addCommand(
            "#" + ctx.type().text + " " + ctx.Identifier()
                .text + if (ctx.expression() != null) " = " + ctx.expression().text else ""
        )
        //变量初始化
        if (ctx.expression() != null) {
            val init: Var = McfppExprVisitor().visit(ctx.expression())!!
            try {
                `var`.assign(init)
            } catch (e: VariableConverseException) {
                Project.logger.error(
                    "Cannot convert " + init.javaClass + " to " + `var`.javaClass +
                            " at " + Function.currFunction!!.GetID() + " line:" + ctx.getStart().line
                )
                Project.errorCount++
                throw VariableConverseException()
            }
        }
    }

    /**
     * 一个赋值的语句
     * @param ctx the parse tree
     */
    @Override
    override fun exitStatementExpression(ctx: StatementExpressionContext) {
        Function.addCommand("#" + ctx.text)
        val left: Var = McfppExprVisitor().visit(ctx.basicExpression())!!
        if (left.isConst == Var.ConstStatus.ASSIGNED) {
            Project.logger.error(
                "Cannot assign a constant repeatedly: " + left.key +
                        " at " + Function.currFunction!!.GetID() + " line:" + ctx.getStart().line
            )
            Project.errorCount++
            throw ConstChangeException()
        } else if (left.isConst == Var.ConstStatus.NULL) {
            left.isConst = Var.ConstStatus.ASSIGNED
        }
        val right: Var = McfppExprVisitor().visit(ctx.expression())!!
        try {
            left.assign(right)
        } catch (e: VariableConverseException) {
            Project.logger.error(
                "Cannot convert " + right.javaClass + " to " + left.javaClass +
                        " at " + Function.currFunction!!.GetID() + " line:" + ctx.getStart().line
            )
            Project.errorCount++
            throw VariableConverseException()
        }
    }

    /**
     * 自加或自减语句
     * @param ctx the parse tree
     */
    @Override
    override fun exitSelfAddOrMinusStatement(ctx: SelfAddOrMinusStatementContext) {
        Function.addCommand("#" + ctx.text)
        val re: Var? = Function.currFunction!!.getVar(ctx.selfAddOrMinusExpression().Identifier().text)
        if (re == null) {
            Project.logger.error(
                "Undefined variable:" + ctx.selfAddOrMinusExpression().Identifier().text +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
            Project.errorCount++
            throw VariableNotDefineException()
        }
        if (ctx.selfAddOrMinusExpression().op.text.equals("++")) {
            if (re is MCInt) {
                if (re.isConcrete) {
                    re.value = re.value!! + 1
                } else {
                    Function.addCommand(Commands.SbPlayerAdd(re, 1))
                }
            }
        } else {
            if (re is MCInt) {
                if (re.isConcrete) {
                    re.value = re.value!! - 1
                } else {
                    Function.addCommand(Commands.SbPlayerRemove(re, 1))
                }
            }
        }
    }

    /**
     * 调用一个函数。参考：
     * [[命令] [数据包] 局部变量、程序控制流程在数据包中的实现 借助汇编语言函数堆栈思想 - mcbbs](https://www.mcbbs.net/thread-1393132-1-1.html)
     * @param ctx the parse tree
     */
    @Override
    override fun exitFunctionCall(ctx: FunctionCallContext) {
        Function.addCommand("#" + ctx.text)
        //参数获取
        val args: ArrayList<Var> = ArrayList()
        val exprVisitor = McfppExprVisitor()
        for (expr in ctx.arguments().expressionList().expression()) {
            args.add(exprVisitor.visit(expr)!!)
        }
        //函数对象获取
        val curr = McfppFuncVisitor().getFunction(ctx, FunctionParam.getVarTypes(args))
        if (curr == null) {
            Project.logger.error(
                "Function " + ctx.text + " not defined " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
            throw FunctionNotDefineException()
        }
        if (curr is NativeFunction) {
            //是native方法
            if (curr.isClassMember) {
                //TODO
                throw TODOException("")
            } else {
                curr.invoke(args, ctx.getStart().line)
            }
            return
        }
        curr.invoke(args, ctx.getStart().line)
        //函数树
        Function.currFunction!!.child.add(curr)
        curr.parent.add(Function.currFunction!!)
    }

    var lastBool: MCBool? = null //if语句的条件
    //TODO 条件判断语句实现方式与参考文章有出入，可能存在bug
    /**
     * 进入if语句块
     * @param ctx the parse tree
     */
    @Override
    override fun enterIfBlock(ctx: IfBlockContext) {
        Function.addCommand("#if start")
        val parent: IfStatementContext = ctx.parent as IfStatementContext
        //是if语句，获取参数
        val index: Int = parent.ifBlock().indexOf(ctx)
        //匿名函数的定义
        val f: Function = InternalFunction("_if_", Function.currFunction!!)
        Project.global.cache.functions.add(f)
        if (index == 0) {
            //第一个if
            val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
            if (exp.isConcrete && exp.value) {
                //函数调用的命令
                //给子函数开栈
                Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
                Function.addCommand(Commands.Function(f))
                Project.logger.warn(
                    "The condition is always true. " +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
            } else if (exp.isConcrete) {
                Function.addCommand("#" + Commands.Function(f))
                Project.logger.warn(
                    "The condition is always false. " +
                            " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
                )
            } else {
                //给子函数开栈
                Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
                Function.addCommand(
                    "execute " +
                            "if score " + exp.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run " + Commands.Function(f)
                )
            }
            lastBool = exp
        } else {
            //else语句
            Function.addCommand(
                "execute " +
                        "unless score " + lastBool!!.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(f)
            )
        }
        Function.currFunction = f
    }

    /**
     * 离开if语句块
     * @param ctx the parse tree
     */
    @Override
    override fun exitIfBlock(ctx: IfBlockContext?) {
        Function.currFunction = Function.currFunction!!.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.addCommand("#if end")
    }

    /**
     * 进入else-if语句块
     * @param ctx the parse tree
     */
    @Override
    override fun enterElseIfStatement(ctx: ElseIfStatementContext) {
        Function.addCommand("#else if start")
        //匿名函数的定义
        val f: Function = InternalFunction("_if_", Function.currFunction!!)
        Project.global.cache.functions.add(f)
        if (lastBool!!.isConcrete && !lastBool!!.value) {
            //函数调用的命令
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand("#" + Commands.Function(f))
            Project.logger.warn(
                "The condition is always false. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        } else if (lastBool!!.isConcrete) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(Commands.Function(f))
            Project.logger.warn(
                "The condition is always true. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        } else {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "unless score " + lastBool!!.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(f)
            )
        }
        lastBool = null
        Function.currFunction = f
    }

    /**
     * 离开else-if语句块
     * @param ctx the parse tree
     */
    @Override
    override fun exitElseIfStatement(ctx: ElseIfStatementContext?) {
        Function.currFunction = Function.currFunction!!.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.addCommand("#else if end")
    }

    /**
     * 进入while语句块
     * @param ctx the parse tree
     */
    @Override
    override fun enterWhileBlock(ctx: WhileBlockContext) {
        Function.addCommand("#while start")
        val parent: WhileStatementContext = ctx.parent as WhileStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
        //匿名函数的定义
        val f: Function = InternalFunction("_while_", Function.currFunction!!)
        f.child.add(f)
        f.parent.add(f)
        Project.global.cache.functions.add(f)
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(Commands.Function(f))
            Project.logger.warn(
                "The condition is always true. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand("#" + Commands.Function(f))
            Project.logger.warn(
                "The condition is always false. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        } else {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if score " + exp.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(f)
            )
        }
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    /**
     * 离开while语句块
     * @param ctx the parse tree
     */
    @Override
    override fun exitWhileBlock(ctx: WhileBlockContext) {
        if (!Function.isBreak && Function.isLastFunctionEnd != 0) {
            Function.currFunction = Function.currFunction!!.parent[0]
        }
        //递归调用函数
        //重新计算表达式
        val parent: WhileStatementContext = ctx.parent as WhileStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
        //给子函数开栈
        Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
        Function.addCommand(
            "execute " +
                    "if score " + exp.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                    "run " + Commands.Function(Function.currFunction!!)
        )
        //调用完毕，将子函数的栈销毁
        Function.currFunction = Function.currFunction!!.parent[0]
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.addCommand("#while end")
    }

    /**
     * 进入do-while语句块，开始匿名函数调用
     * @param ctx the parse tree
     */
    @Override
    override fun enterDoWhileBlock(ctx: DoWhileBlockContext?) {
        Function.addCommand("#do while start")
        //匿名函数的定义
        val f: Function = InternalFunction("_dowhile_", Function.currFunction!!)
        f.child.add(f)
        f.parent.add(f)
        Project.global.cache.functions.add(f)
        //给子函数开栈
        Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
        Function.addCommand(Commands.Function(f))
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    /**
     * 离开do-while语句
     * @param ctx the parse tree
     */
    @Override
    override fun exitDoWhileStatement(ctx: DoWhileStatementContext) {
        val exp: MCBool = McfppExprVisitor().visit(ctx.expression()) as MCBool
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(Commands.Function(Function.currFunction!!))
            Project.logger.warn(
                "The condition is always true. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStop().line
            )
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand("#" + Commands.Function(Function.currFunction!!))
            Project.logger.warn(
                "The condition is always false. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStop().line
            )
        } else {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if score " + exp.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(Function.currFunction!!)
            )
        }
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        //调用完毕，将子函数的栈销毁
        Function.currFunction = Function.currFunction!!.parent[0]
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.addCommand("#do while end")
    }

    /**
     * 整个for语句本身额外有一个栈，无条件调用函数
     * @param ctx the parse tree
     */
    @Override
    override fun enterForStatement(ctx: ForStatementContext?) {
        Function.addCommand("#for start")
        Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
        val forFunc: Function = InternalFunction("_for_", Function.currFunction!!)
        forFunc.parent.add(Function.currFunction!!)
        Project.global.cache.functions.add(forFunc)
        Function.addCommand(Commands.Function(forFunc))
        Function.currFunction = forFunc
    }

    @Override
    override fun exitForStatement(ctx: ForStatementContext?) {
        Function.currFunction = Function.currFunction!!.parent[0]
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.addCommand("#for end")
    }

    /**
     * 进入for update语句块。
     * 由于在编译过程中，编译器会首先编译for语句的for control部分，也就是for后面的括号，这就意味着forUpdate语句将会先forBlock
     * 被写入到命令函数中。因此我们需要将forUpdate语句中的命令临时放在一个函数内部，然后在forBlock调用完毕后加上中间的命令
     *
     *
     * 值得注意的是，for update和for block相对于整个父函数的栈的位置应该是相同的，如果它们想要调用父函数中声明的变量，都应该
     * 用索引[1]来访问，因此可以直接将for update中的命令转移到for block中而不会出现任何问题。
     *
     * @param ctx the parse tree
     */
    @Override
    override fun enterForUpdate(ctx: ForUpdateContext?) {
        Function.currFunction = InternalFunction("_forblock_", Function.currFunction!!)
    }

    var forupdate: Function? = null

    /**
     * 离开for update。将for update缓存，同时切换当前函数为父函数
     * @param ctx the parse tree
     */
    @Override
    override fun exitForUpdate(ctx: ForUpdateContext?) {
        forupdate = Function.currFunction
        Function.currFunction = forupdate!!.parent[0]
    }

    /**
     * 进入for block语句。此时当前函数为父函数
     * @param ctx the parse tree
     */
    @Override
    override fun enterForBlock(ctx: ForBlockContext) {
        val parent: ForStatementContext = ctx.parent as ForStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.forControl().expression()) as MCBool
        //匿名函数的定义。这里才是正式的for函数哦喵
        val f: Function = InternalFunction("_forblock_", Function.currFunction!!)
        f.child.add(f)
        f.parent.add(f)
        Project.global.cache.functions.add(f)
        if (exp.isConcrete && exp.value) {
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(Commands.Function(f))
            Project.logger.warn(
                "The condition is always true. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        } else if (exp.isConcrete) {
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand("#" + Commands.Function(f))
            Project.logger.warn(
                "The condition is always false. " +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        } else {
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if score " + exp.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(f)
            )
        }
        //调用完毕，将子函数的栈销毁。这条命令仍然是在for函数中的。
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    /**
     * 离开for block语句。此时当前函数仍然是for的函数
     * @param ctx the parse tree
     */
    @Override
    override fun exitForBlock(ctx: ForBlockContext) {
        //for update的命令压入
        Function.currFunction!!.commands.addAll(forupdate!!.commands)
        forupdate = null
        //递归调用函数
        //重新计算表达式
        val parent: ForStatementContext = ctx.parent as ForStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.forControl().expression()) as MCBool
        //这里就需要给子函数开栈
        Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
        Function.addCommand(
            "execute " +
                    "if score " + exp.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                    "run " + Commands.Function(Function.currFunction!!)
        )
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        Function.currFunction = Function.currFunction!!.parent[0]
    }

    @Override
    override fun exitOrgCommand(ctx: OrgCommandContext) {
        Function.addCommand(ctx.text.substring(1))
    }

    /**
     * 进入任意语句，检查此函数是否还能继续添加语句
     * @param ctx the parse tree
     */
    @Override
    override fun enterStatement(ctx: StatementContext) {
        if (Function.currFunction!!.isEnd) {
            Project.logger.warn(
                "Unreachable code: " + ctx.text +
                        " at " + Project.currFile!!.name + " line: " + ctx.getStart().line
            )
        }
        if (Function.isLastFunctionEnd == 1) {
            //循环经历了break语句的洗礼，后面的语句需要全部放在匿名函数中。
            Function.addCommand("#" + (if (Function.isBreak) "break" else "continue") + " function")
            //匿名函数的定义
            val f: Function = InternalFunction(
                "_" + (if (Function.isBreak) "break" else "continue") + "_",
                Function.currFunction!!
            )
            f.child.add(f)
            f.parent.add(f)
            Project.global.cache.functions.add(f)
            //给函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "unless score " + temp!!.identifier + " " + SbObject.MCS_boolean + " matches 1 " +
                        "run " + Commands.Function(f)
            )
            //调用完毕，将子函数的栈销毁
            Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
            Function.currFunction = f //后续块中的命令解析到递归的函数中
        }
    }

    private var temp: MCBool? = null
    @Override
    override fun exitControlStatement(ctx: ControlStatementContext) {
        if (!inLoopStatement(ctx)) {
            Project.logger.error(
                "'continue' or 'break' can only be used in loop statements: " +
                        " at " + Project.currFile!!.getName() + " line: " + ctx.getStart().line
            )
            throw SyntaxException()
        }
        if (Function.currFunction!!.isEnd || Function.isLastFunctionEnd != 0) {
            return
        }
        Function.addCommand("#" + ctx.text)
        temp = MCBool()
        //变量进栈
        Function.addCommand("scoreboard players set " + temp!!.identifier + " " + SbObject.MCS_boolean + " = " + 1)
        Function.currFunction!!.isEnd = true
        Function.isLastFunctionEnd = 1
        if (ctx.BREAK() != null) {
            Function.isBreak = true
        }
    }

    /**
     * 离开任意代码块。主要用于break语句和continue语句的匿名函数出栈判定。
     * @param ctx the parse tree
     */
    @Override
    override fun exitBlock(ctx: BlockContext) {
        if (!Function.currFunction!!.isEnd && Function.isLastFunctionEnd == 2) {
            if (ctx.parent is IfBlockContext) {
                //如果是if语句，出栈
                Function.currFunction = Function.currFunction!!.parent.get(0)
                Function.isLastFunctionEnd = 1
            }
            if (ctx.parent is ForBlockContext
                || ctx.parent is WhileBlockContext
                || ctx.parent is DoWhileBlockContext
            ) {
                //是循环语句，出栈的同时重置isLastFunctionEnd标志
                Function.currFunction = Function.currFunction!!.parent.get(0)
                Function.isLastFunctionEnd = 0
            }
        }
    }

    /**
     * 进入类体。
     * @param ctx the parse tree
     */
    @Override
    override fun enterClassBody(ctx: ClassBodyContext) {
        //获取类的对象
        val parent: ClassDeclarationContext = ctx.parent as ClassDeclarationContext
        val identifier: String = parent.className(0).text
        //设置作用域
        Class.currClass = Project.global.cache.classes[identifier]
        Function.currFunction = Class.currClass!!.classPreInit
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */
    @Override
    override fun exitClassBody(ctx: ClassBodyContext?) {
        Class.currClass = null
        Function.currFunction = Project.global.globalInit
    }

    /**
     * 类成员的声明
     * @param ctx the parse tree
     */
    @Override
    override fun exitClassMemberDeclaration(ctx: ClassMemberDeclarationContext) {
        val memberContext: ClassMemberContext = ctx.classMember()
        if (memberContext.classFunctionDeclaration() != null) {
            //函数声明由函数的listener处理
            return
        }
    }

    companion object {
        /**
         * 判断这个语句是否在循环语句中。包括嵌套形式。
         * @param ctx 需要判断的语句
         * @return 是否在嵌套中
         */
        private fun inLoopStatement(ctx: RuleContext): Boolean {
            if (ctx is ForStatementContext) {
                return true
            }
            if (ctx is DoWhileStatementContext) {
                return true
            }
            if (ctx is WhileStatementContext) {
                return true
            }
            return if (ctx.parent != null) {
                inLoopStatement(ctx.parent)
            } else false
        }
    }
}