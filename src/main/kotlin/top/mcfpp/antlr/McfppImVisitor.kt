package top.mcfpp.antlr

import org.antlr.v4.runtime.RuleContext
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.mcfppParser.CompileTimeFuncDeclarationContext
import top.mcfpp.command.CommandList
import top.mcfpp.command.Commands
import top.mcfpp.exception.*
import top.mcfpp.lang.*
import top.mcfpp.lib.*
import top.mcfpp.lib.Annotation
import top.mcfpp.lib.Function
import top.mcfpp.util.LogProcessor

open class McfppImVisitor: mcfppParserBaseVisitor<Any?>() {

    override fun visitFunctionBody(ctx: mcfppParser.FunctionBodyContext): Any? {
        if(ctx.parent is CompileTimeFuncDeclarationContext) return null
        enterFunctionBody(ctx)
        super.visitFunctionBody(ctx)
        exitFunctionBody(ctx)
        return null
    }

    /**
     * 进入一个函数体
     * @param ctx the parse tree
     */
    fun enterFunctionBody(ctx: mcfppParser.FunctionBodyContext) {
        Project.ctx = ctx
        var f: Function
        //获取函数对象
        if (ctx.parent.parent !is mcfppParser.ClassMemberContext && ctx.parent.parent !is mcfppParser.TemplateDeclarationContext && ctx.parent !is mcfppParser.ExtensionFunctionDeclarationContext) {
            //不是类成员和结构体成员
            //创建函数对象
            val parent = ctx.parent as mcfppParser.FunctionDeclarationContext
            f = Function(parent.Identifier().text)
            //解析参数
            if (parent.parameterList() != null) {
                f.addParams((ctx.parent as mcfppParser.FunctionDeclarationContext).parameterList())
            }
            //获取缓存中的对象
            f = GlobalField.getFunction(f.namespace, f.identifier, f.paramTypeList)!!
        } else if (ctx.parent is mcfppParser.ConstructorDeclarationContext) {
            //是构造函数
            //创建构造函数对象并解析参数
            val temp = Function("temp")
            if ((ctx.parent as mcfppParser.ConstructorDeclarationContext).parameterList() != null) {
                temp.addParams((ctx.parent as mcfppParser.ConstructorDeclarationContext).parameterList())
            }
            //获取缓存中的对象
            f = if(ctx.parent.parent is mcfppParser.ClassMemberContext){
                Class.currClass!!.getConstructor(FunctionParam.toStringList(temp.params))!!
            }else{
                Template.currTemplate!!.getConstructor(FunctionParam.toStringList(temp.params))!!
            }
        } else if(ctx.parent.parent is mcfppParser.ClassMemberContext){
            //是类的成员函数
            //创建函数对象并解析参数
            val qwq = ctx.parent as mcfppParser.ClassFunctionDeclarationContext
            f = Function(qwq.Identifier().text, Class.currClass!!, false)
            if (qwq.parameterList() != null) {
                f.addParams(qwq.parameterList())
            }
            //获取缓存中的对象
            val fun1 = Class.currClass!!.field.getFunction(f.identifier, f.paramTypeList)
            f = (fun1 ?: Class.currClass!!.staticField.getFunction(f.identifier, f.paramTypeList))!!
        } else if(ctx.parent is mcfppParser.ExtensionFunctionDeclarationContext){
            //是扩展函数
            val qwq = ctx.parent as mcfppParser.ExtensionFunctionDeclarationContext
            //获取被拓展的类
            //获取被拓展的类
            val data : CompoundData = if(qwq.type().className() == null){
                when(qwq.type().text){
                    "int" -> MCInt.data
                    else -> {
                        throw Exception("Cannot add extension function to ${qwq.type().text}")
                    }
                }
            }else{
                val clsStr = qwq.type().className().text.split(":")
                val id : String
                val nsp : String?
                if(clsStr.size == 1){
                    id = clsStr[0]
                    nsp = null
                }else{
                    id = clsStr[1]
                    nsp = clsStr[0]
                }
                val owo: Class? = GlobalField.getClass(nsp, id)
                if (owo == null) {
                    val pwp = GlobalField.getStruct(nsp, id)
                    if(pwp == null){
                        LogProcessor.error("Undefined class or struct:" + qwq.type().className().text)
                        f = UnknownFunction(qwq.Identifier().text)
                        Function.currFunction = f
                        return
                    }else{
                        pwp
                    }
                }else{
                    owo
                }
            }
            //创建函数对象并解析参数
            f = Function(qwq.Identifier().text)
            if (qwq.parameterList() != null) {
                f.addParams(qwq.parameterList())
            }
            val field = if(f.isStatic) data.staticField else data.field
            //获取缓存中的对象
            f = field.getFunction(qwq.Identifier().text,f.paramTypeList)!!
        } else{
            //是结构体成员
            //创建函数对象并解析参数
            val qwq = ctx.parent as mcfppParser.TemplateFunctionDeclarationContext
            f = Function(qwq.Identifier().text, Template.currTemplate!!, false)
            if (qwq.parameterList() != null) {
                f.addParams(qwq.parameterList())
            }
            //获取缓存中的对象
            val fun1 = Template.currTemplate!!.field.getFunction(f.identifier, f.paramTypeList)
            f = (fun1 ?: Template.currTemplate!!.staticField.getFunction(f.identifier, f.paramTypeList))!!
        }
        Function.currFunction = f
        //对函数进行注解处理
        if(Class.currClass == null && Template.currTemplate == null){
            for (a in annoInGlobal){
                a.forFunction(f)
            }
            annoInGlobal.clear()
        }else{
            for (a in annoInCompound){
                a.forFunction(f)
            }
            annoInCompound.clear()
        }
    }

    /**
     * 离开一个函数体
     * @param ctx the parse tree
     */
    
    fun exitFunctionBody(ctx: mcfppParser.FunctionBodyContext) {
        Project.ctx = ctx
        //函数是否有返回值
        if(Function.currFunction.returnType != "void" && !Function.currFunction.hasReturnStatement){
            LogProcessor.error("A 'return' expression required in function: " + Function.currFunction.namespaceID)
        }
        if (Class.currClass == null) {
            //不在类中
            Function.currFunction = Function.defaultFunction
        } else {
            Function.currFunction = Class.currClass!!.classPreInit
        }
    }

    /**
     * 进入命名空间声明的时候
     * @param ctx the parse tree
     */
    
    override fun visitNamespaceDeclaration(ctx: mcfppParser.NamespaceDeclarationContext):Any? {
        Project.ctx = ctx
        Project.currNamespace = ctx.Identifier(0).text
        if(ctx.Identifier().size > 1){
            for (n in ctx.Identifier().subList(1,ctx.Identifier().size-1)){
                Project.currNamespace += ".$n"
            }
        }
        Function.defaultFunction.namespace = Project.currNamespace
        return null
    }

    /**
     * 变量声明
     * @param ctx the parse tree
     */
    
    @InsertCommand
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext):Any? {
        Project.ctx = ctx
        //变量生成
        val fieldModifier = ctx.fieldModifier()?.text
        if (ctx.parent is mcfppParser.ClassMemberContext) {
            return null
        }
        if(ctx.VAR() != null){
            //自动判断类型
            val init: Var = McfppExprVisitor().visit(ctx.expression())!!
            val `var` = Var.build(ctx.Identifier().text, init.type, Function.currFunction)
            //变量注册
            //一定是函数变量
            if (!Function.field.putVar(ctx.Identifier().text, `var`, true)) {
                LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
            }
            try {
                if(`var` is MCInt && init is MCInt && !init.isConcrete){
                    Function.currFunction.commands.replaceThenAnalyze(init.name to `var`.name, init.`object`.name to `var`.`object`.name)
                    `var`.assignCommand(init,Function.currFunction.commands.last().toString())
                }else{
                    `var`.assign(init)
                }
            } catch (e: VariableConverseException) {
                LogProcessor.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
                throw VariableConverseException()
            }
            when(fieldModifier){
                "const" -> {
                    if(!`var`.hasAssigned){
                        LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                    }
                    `var`.isConst = true
                }
                "dynamic" -> {
                    if(`var`.isConcrete){
                        `var`.toDynamic()
                    }
                }
                "import" -> {
                    `var`.isImport = true
                }
            }
        }else{
            for (c in ctx.fieldDeclarationExpression()){
                //函数变量，生成
                val `var` = Var.build(c.Identifier().text, ctx.type().text, Function.currFunction)
                //变量注册
                //一定是函数变量
                if (!Function.field.putVar(c.Identifier().text, `var`,true)) {
                    LogProcessor.error("Duplicate defined variable name:" + c.Identifier().text)
                }
                Function.addCommand("#field: " + ctx.type().text + " " + c.Identifier().text + if (c.expression() != null) " = " + c.expression().text else "")
                //变量初始化
                if (c.expression() != null) {
                    val init: Var = McfppExprVisitor().visit(c.expression())!!
                    try {
                        if(`var` is MCInt && init is MCInt && !init.isConcrete){
                            Function.currFunction.commands.replaceThenAnalyze(init.name to `var`.name, init.`object`.name to `var`.`object`.name)
                            `var`.assignCommand(init,Function.currFunction.commands.last().toString())
                        }else{
                            `var`.assign(init)
                        }
                    } catch (e: VariableConverseException) {
                        LogProcessor.error("Cannot convert " + init.javaClass + " to " + `var`.javaClass)
                        throw VariableConverseException()
                    }
                }
                when(fieldModifier){
                    "const" -> {
                        if(!`var`.hasAssigned){
                            LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                        }
                        `var`.isConst = true
                    }
                    "dynamic" -> {
                        if(`var`.isConcrete){
                            `var`.toDynamic()
                        }
                    }
                    "import" -> {
                        `var`.isImport = true
                    }
                }
            }
        }
        return null
    }

    /**
     * 一个赋值的语句
     * @param ctx the parse tree
     */
    
    @InsertCommand
    override fun visitStatementExpression(ctx: mcfppParser.StatementExpressionContext):Any? {
        Project.ctx = ctx
        Function.addCommand("#expression: " + ctx.text)
        val right: Var = McfppExprVisitor().visit(ctx.expression())!!
        if(ctx.basicExpression() != null){
            val left: Var = McfppLeftExprVisitor().visit(ctx.basicExpression())!!
            if (left.isConst) {
                LogProcessor.error("Cannot assign a constant repeatedly: " + left.identifier)
                return null
            }
            try {
                if(left is MCInt && right is MCInt){
                    Function.currFunction.commands.replaceThenAnalyze(right.name to left.name, right.`object`.name to left.`object`.name)
                    left.assignCommand(right,Function.currFunction.commands.last().toString())
                }else{
                    left.assign(right)
                }
            } catch (e: VariableConverseException) {
                LogProcessor.error("Cannot convert " + right.javaClass + " to " + left.javaClass)
                throw VariableConverseException()
            }
        }else{
            //TODO 只有一个表达式的计算
        }
        Function.addCommand("#expression end: " + ctx.text)
        return null
    }

    val annoInGlobal = ArrayList<Annotation>()
    val annoInCompound = ArrayList<Annotation>()

    /**
     * 注解
     *
     * @param ctx
     */
    //TODO: Func
    override fun visitClassAnnotation(ctx: mcfppParser.ClassAnnotationContext): Any? {
        Project.ctx = ctx
        val anno = GlobalField.annotations[ctx.id.text]
        if(anno == null){
            LogProcessor.error("Annotation ${ctx.id.text} not found")
            return null
        }
        //参数解析
        val params = ArrayList<Var>()
        if(ctx.arguments()?.expressionList() != null){
            for (e in ctx.arguments().expressionList().expression()){
                params.add(McfppExprVisitor().visit(e)!!)
            }
        }
        if(Class.currClass == null && Template.currTemplate == null){
            //在全局
            annoInGlobal.add(Annotation.newInstance(anno,params))
        }else{
            annoInCompound.add(Annotation.newInstance(anno,params))
        }
        return null
    }

    /**
     * 自加或自减语句
     * TODO
     * @param ctx the parse tree
     */
    /*
    * override fun exitSelfAddOrMinusStatement(ctx: mcfppParser.SelfAddOrMinusStatementContext) {
    *    Project.ctx = ctx
    *    Function.addCommand("#" + ctx.text)
    *    val re: Var? = Function.field.getVar(ctx.selfAddOrMinusExpression().Identifier().text)
    *    if (re == null) {
    *        LogProcessor.error("Undefined variable:" + ctx.selfAddOrMinusExpression().Identifier().text)
    *        throw VariableNotDefineException()
    *    }
    *    if (ctx.selfAddOrMinusExpression().op.text.equals("++")) {
    *        if (re is MCInt) {
    *            if (re.isConcrete) {
    *                re.value = re.value!! + 1
    *            } else {
    *                Function.addCommand(Commands.SbPlayerAdd(re, 1))
    *            }
    *        }
    *    } else {
    *        if (re is MCInt) {
    *            if (re.isConcrete) {
    *                re.value = re.value!! - 1
    *            } else {
    *                Function.addCommand(Commands.SbPlayerRemove(re, 1))
    *            }
    *        }
    *    }
    * }
    */

//region 逻辑语句
    
    @InsertCommand
    override fun visitReturnStatement(ctx: mcfppParser.ReturnStatementContext):Any? {
        Project.ctx = ctx
        Function.addCommand("#" + ctx!!.text)
        if (ctx.expression() != null) {
            val ret: Var = McfppExprVisitor().visit(ctx.expression())!!
            Function.currBaseFunction.returnVar(ret)
        }
        if(Function.currFunction !is InternalFunction)
            Function.currFunction.hasReturnStatement = true
        Function.addCommand("return 1")
        return null
    }

    override fun visitIfStatement(ctx: mcfppParser.IfStatementContext): Any? {
        enterIfStatement(ctx)
        super.visitIfStatement(ctx)
        exitIfStatement(ctx)
        return null
    }

    /**
     * 进入if语句
     * Enter if statement
     *
     * @param ctx
     */
    
    @InsertCommand
    fun enterIfStatement(ctx: mcfppParser.IfStatementContext) {
        //进入if函数
        Project.ctx = ctx
        Function.addCommand("#" + "if start")
        val ifFunction = InternalFunction("_if_", Function.currFunction)
        ifFunction.invoke(ArrayList(),null)
        Function.currFunction = ifFunction
        if(!GlobalField.localNamespaces.containsKey(ifFunction.namespace))
            GlobalField.localNamespaces[ifFunction.namespace] = NamespaceField()
        GlobalField.localNamespaces[ifFunction.namespace]!!.addFunction(ifFunction,false)
    }

    /**
     * 离开if语句
     * Exit if statement
     *
     * @param ctx
     */
    
    @InsertCommand
    fun exitIfStatement(ctx: mcfppParser.IfStatementContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("#" + "if end")
    }

    override fun visitIfBlock(ctx: mcfppParser.IfBlockContext): Any? {
        enterIfBlock(ctx)
        super.visitIfBlock(ctx)
        exitIfBlock(ctx)
        return null
    }

    /**
     * 进入if分支的语句块
     * @param ctx the parse tree
     */

    @InsertCommand
    fun enterIfBlock(ctx: mcfppParser.IfBlockContext) {
        Project.ctx = ctx
        val parent = ctx.parent
        Function.addCommand("#if branch start")
        //匿名函数的定义
        val f = InternalFunction("_if_branch_", Function.currFunction)
        //注册函数
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f,false)
        if (parent is mcfppParser.IfStatementContext || parent is mcfppParser.ElseIfStatementContext) {
            //第一个if
            parent as mcfppParser.IfStatementContext
            val exp = McfppExprVisitor().visit(parent.expression())
            if(exp !is MCBool){
                throw TypeCastException()
            }
            if (exp.isConcrete && exp.value) {
                //函数调用的命令
                //给子函数开栈
                Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
                Function.addCommand("function " + f.namespaceID)
                Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
                Function.addCommand("return 1")
                LogProcessor.warn("The condition is always true. ")
            } else if (exp.isConcrete) {
                Function.addCommand("#function " + f.namespaceID)
                Function.addCommand("return 1")
                LogProcessor.warn("The condition is always false. ")
            } else {
                exp as ReturnedMCBool
                val exp1 = MCBool()
                exp1.assign(exp)
                //给子函数开栈
                Function.addCommand(
                    "execute " +
                            "if score " + exp1.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}"
                )
                Function.addCommand(
                    "execute " +
                            "if score " + exp1.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run function " + f.namespaceID
                )
                Function.addCommand(
                    "execute " +
                            "if score " + exp1.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]"
                )
                //由于下一个函数被直接中断，因此需要自己把自己的栈去掉
                Function.addCommand(
                    "execute " +
                            "if score " + exp1.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]"
                )
                Function.addCommand(
                    "execute " +
                            "if score " + exp1.name + " " + SbObject.MCS_boolean + " matches 1 " +
                            "run return 1"
                )
            }
        }
        else {
            //else语句
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand("function " + f.namespaceID)
            Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        }
        Function.currFunction = f
    }

    /**
     * 离开if语句块
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun exitIfBlock(ctx: mcfppParser.IfBlockContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#if branch end")
    }

    override fun visitWhileStatement(ctx: mcfppParser.WhileStatementContext): Any? {
        enterWhileStatement(ctx)
        super.visitWhileStatement(ctx)
        exitWhileStatement(ctx)
        return null
    }

    @InsertCommand
    fun enterWhileStatement(ctx: mcfppParser.WhileStatementContext) {
        //进入if函数
        Project.ctx = ctx
        Function.addCommand("#while start")
        val whileFunction = InternalFunction("_while_", Function.currFunction)
        Function.addCommand("function " + whileFunction.namespaceID)
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = whileFunction
        if(!GlobalField.localNamespaces.containsKey(whileFunction.namespace))
            GlobalField.localNamespaces[whileFunction.namespace] = NamespaceField()
        GlobalField.localNamespaces[whileFunction.namespace]!!.addFunction(whileFunction,false)
    }

    
    @InsertCommand
    fun exitWhileStatement(ctx: mcfppParser.WhileStatementContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("#while end")
    }


    override fun visitWhileBlock(ctx: mcfppParser.WhileBlockContext): Any? {
        enterWhileBlock(ctx)
        super.visitWhileBlock(ctx)
        exitWhileBlock(ctx)
        return null
    }

    /**
     * 进入while语句块
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun enterWhileBlock(ctx: mcfppParser.WhileBlockContext) {
        Project.ctx = ctx
        //入栈
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand("#" + "while start")
        val parent: mcfppParser.WhileStatementContext = ctx.parent as mcfppParser.WhileStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
        //匿名函数的定义
        val f: Function = InternalFunction("_while_block_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f,false)
        //条件判断
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
            LogProcessor.warn("The condition is always true. ")
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("#function " + f.namespaceID)
            LogProcessor.warn("The condition is always false. ")
        } else {
            exp as ReturnedMCBool
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            //函数返回1才会继续执行(continue或者正常循环完毕)，返回0则不继续循环(break)
            Function.addCommand(
                "execute " +
                        "if function " + exp.parentFunction.namespaceID + " " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
        }
        Function.currFunction = f //后续块中的命令解析到递归的函数中

    }

    /**
     * 离开while语句块
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun exitWhileBlock(ctx: mcfppParser.WhileBlockContext) {
        Project.ctx = ctx
        //调用完毕，将子函数的栈销毁
        //由于在同一个命令中完成了两个函数的调用，因此需要在子函数内部进行子函数栈的销毁工作
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //这里取出while函数的栈
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.addCommand("return 1")
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#while loop end")
    }

    override fun visitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext): Any? {
        enterDoWhileStatement(ctx)
        super.visitDoWhileStatement(ctx)
        exitDoWhileStatement(ctx)
        return null
    }

    @InsertCommand
    fun enterDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext) {
        //进入do-while函数
        Project.ctx = ctx
        Function.addCommand("#do-while start")
        val doWhileFunction = InternalFunction("_dowhile_", Function.currFunction)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand("function " + doWhileFunction.namespaceID)
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = doWhileFunction
        if(!GlobalField.localNamespaces.containsKey(doWhileFunction.namespace))
            GlobalField.localNamespaces[doWhileFunction.namespace] = NamespaceField()
        GlobalField.localNamespaces[doWhileFunction.namespace]!!.addFunction(doWhileFunction,false)
    }



    /**
     * 离开do-while语句
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun exitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addCommand("#do-while end")
    }


    override fun visitDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext): Any? {
        enterDoWhileBlock(ctx)
        super.visitDoWhileBlock(ctx)
        exitDoWhileBlock(ctx)
        return null
    }

    /**
     * 进入do-while语句块，开始匿名函数调用
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun enterDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext) {
        Project.ctx = ctx
        Function.addCommand("#do while start")
        //匿名函数的定义
        val f: Function = InternalFunction("_dowhile_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace)) GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f,false)
        //给子函数开栈
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand(
            "execute " +
                    "unless function " + f.namespaceID + " " +
                    "run return 1"
        )
        val parent = ctx!!.parent as mcfppParser.DoWhileStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.expression()) as MCBool
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //递归调用
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
            LogProcessor.warn("The condition is always true. ")
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("#" + Commands.function(Function.currFunction))
            LogProcessor.warn("The condition is always false. ")
        } else {
            exp as ReturnedMCBool
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function ${exp.parentFunction.namespaceID} " +
                        "run " + Commands.function(Function.currFunction)
            )
        }
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    
    @InsertCommand
    fun exitDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext) {
        Project.ctx = ctx
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //调用完毕，将子函数的栈销毁
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#do while end")
    }


    override fun visitForStatement(ctx: mcfppParser.ForStatementContext): Any? {
        enterForStatement(ctx)
        super.visitForStatement(ctx)
        exitForStatement(ctx)
        return null
    }

    /**
     * 整个for语句本身额外有一个栈，无条件调用函数
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun enterForStatement(ctx: mcfppParser.ForStatementContext) {
        Project.ctx = ctx
        Function.addCommand("#for start")
        val forFunc: Function = InternalFunction("_for_", Function.currFunction)
        forFunc.parent.add(Function.currFunction)
        if(!GlobalField.localNamespaces.containsKey(forFunc.namespace))
            GlobalField.localNamespaces[forFunc.namespace] = NamespaceField()
        GlobalField.localNamespaces[forFunc.namespace]!!.addFunction(forFunc,false)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand(Commands.function(forFunc))
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = forFunc
    }

    
    @InsertCommand
    fun exitForStatement(ctx: mcfppParser.ForStatementContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        Function.addCommand("#for end")
    }

    override fun visitForInit(ctx: mcfppParser.ForInitContext): Any? {
        enterForInit(ctx)
        super.visitForInit(ctx)
        exitForInit(ctx)
        return null
    }
    
    @InsertCommand
    fun enterForInit(ctx: mcfppParser.ForInitContext) {
        Project.ctx = ctx
        Function.addCommand("#for init start")
    }

    
    @InsertCommand
    fun exitForInit(ctx: mcfppParser.ForInitContext) {
        Project.ctx = ctx
        Function.addCommand("#for init end")
        //进入for循环主体
        Function.addCommand("#for loop start")
        val forLoopFunc: Function = InternalFunction("_for_loop_", Function.currFunction)
        forLoopFunc.parent.add(Function.currFunction)
        if(!GlobalField.localNamespaces.containsKey(forLoopFunc.namespace))
            GlobalField.localNamespaces[forLoopFunc.namespace] = NamespaceField()
        GlobalField.localNamespaces[forLoopFunc.namespace]!!.addFunction(forLoopFunc,false)
        Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        Function.addCommand(Commands.function(forLoopFunc))

    }


    /**
     * 进入for update语句块。
     * 由于在编译过程中，编译器会首先编译for语句的for control部分，也就是for后面的括号，这就意味着forUpdate语句将会先forBlock
     * 被写入到命令函数中。因此我们需要将forUpdate语句中的命令临时放在一个列表内部，然后在forBlock调用完毕后加上它的命令
     *
     * @param ctx the parse tree
     */

    override fun visitForUpdate(ctx: mcfppParser.ForUpdateContext): Any? {
        enterForUpdate(ctx)
        super.visitForUpdate(ctx)
        exitForUpdate(ctx)
        return null
    }

    fun enterForUpdate(ctx: mcfppParser.ForUpdateContext) {
        Project.ctx = ctx
        forInitCommands = Function.currFunction.commands
        Function.currFunction.commands = forUpdateCommands
    }

    //暂存
    private var forInitCommands = CommandList()
    private var forUpdateCommands = CommandList()

    /**
     * 离开for update。暂存for update缓存，恢复主缓存，准备forblock编译
     * @param ctx the parse tree
     */
    
    fun exitForUpdate(ctx: mcfppParser.ForUpdateContext) {
        Project.ctx = ctx
        Function.currFunction.commands = forInitCommands
    }

    override fun visitForBlock(ctx: mcfppParser.ForBlockContext): Any? {
        enterForBlock(ctx)
        super.visitForBlock(ctx)
        exitForBlock(ctx)
        return null
    }

    /**
     * 进入for block语句。此时当前函数为父函数
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun enterForBlock(ctx: mcfppParser.ForBlockContext) {
        Project.ctx = ctx
        val parent: mcfppParser.ForStatementContext = ctx.parent as mcfppParser.ForStatementContext
        val exp: MCBool = McfppExprVisitor().visit(parent.forControl().expression()) as MCBool
        //匿名函数的定义。这里才是正式的for函数哦喵
        val f: Function = InternalFunction("_forblock_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = NamespaceField()
        GlobalField.localNamespaces[f.namespace]!!.addFunction(f,false)
        //条件循环判断
        if (exp.isConcrete && exp.value) {
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            Function.addCommand(
                "execute " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
            LogProcessor.warn("The condition is always true. ")
        } else if (exp.isConcrete) {
            //给子函数开栈
            Function.addCommand("#function " + f.namespaceID)
            LogProcessor.warn("The condition is always false. ")
        } else {
            exp as ReturnedMCBool
            //给子函数开栈
            Function.addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
            //函数返回1才会继续执行(continue或者正常循环完毕)，返回0则不继续循环(break)
            Function.addCommand(
                "execute " +
                        "if function ${exp.parentFunction.namespaceID} " +
                        "if function " + f.namespaceID + " " +
                        "run function " + Function.currFunction.namespaceID
            )
        }
        //调用完毕，将子函数的栈销毁。这条命令仍然是在for函数中的。
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    /**
     * 离开for block语句。此时当前函数仍然是for的函数
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun exitForBlock(ctx: mcfppParser.ForBlockContext) {
        Project.ctx = ctx
        //for update的命令压入
        Function.currFunction.commands.addAll(forUpdateCommands)
        forUpdateCommands.clear()
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        //继续销毁forloop函数的栈
        Function.addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        Function.currFunction = Function.currFunction.parent[0]
    }
//endregion

    
    @InsertCommand
    override fun visitOrgCommand(ctx: mcfppParser.OrgCommandContext):Any? {
        Project.ctx = ctx
        Function.addCommand(ctx.text.substring(1))
        return null
    }

    /**
     * 进入任意语句，检查此函数是否还能继续添加语句
     * @param ctx the parse tree
     */

    @InsertCommand
    override fun visitStatement(ctx: mcfppParser.StatementContext): Any? {
        Project.ctx = ctx
        if (Function.currFunction.isEnd) {
            LogProcessor.warn("Unreachable code: " + ctx.text)
        }
        super.visitStatement(ctx)
        return null
    }

    private var temp: MCBool? = null
    
    @InsertCommand
    override fun visitControlStatement(ctx: mcfppParser.ControlStatementContext):Any? {
        Project.ctx = ctx
        if (!inLoopStatement(ctx)) {
            LogProcessor.error("'continue' or 'break' can only be used in loop statements.")
            return null
        }
        Function.addCommand("#" + ctx.text)
        //return语句
        if(ctx.BREAK() != null){
            Function.addCommand("return 0")
        }else{
            Function.addCommand("return 1")
        }
        Function.currFunction.isEnd = true
        return null
    }

    //region class

    override fun visitClassBody(ctx: mcfppParser.ClassBodyContext): Any? {
        enterClassBody(ctx)
        super.visitClassBody(ctx)
        exitClassBody(ctx)
        return null
    }


    /**
     * 进入类体。
     * @param ctx the parse tree
     */
    
    fun enterClassBody(ctx: mcfppParser.ClassBodyContext) {
        Project.ctx = ctx
        //获取类的对象
        val parent: mcfppParser.ClassDeclarationContext = ctx.parent as mcfppParser.ClassDeclarationContext
        val identifier: String = parent.classWithoutNamespace().text
        //设置作用域
        Class.currClass = GlobalField.getClass(Project.currNamespace, identifier)
        Function.currFunction = Class.currClass!!.classPreInit
        //注解
        for (a in annoInGlobal){
            a.forClass(Class.currClass!!)
        }
        annoInGlobal.clear()
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */
    
    fun exitClassBody(ctx: mcfppParser.ClassBodyContext) {
        Project.ctx = ctx
        Class.currClass = null
        Function.currFunction = Function.nullFunction
    }

    /**
     * 类成员的声明
     * @param ctx the parse tree
     */
    
    override fun visitClassMemberDeclaration(ctx: mcfppParser.ClassMemberDeclarationContext):Any? {
        Project.ctx = ctx
        val memberContext: mcfppParser.ClassMemberContext = ctx.classMember()?:return null
        if (memberContext.classFunctionDeclaration() != null) {
            //函数声明由函数的listener处理
            return null
        }
        return null
    }

    //endregion

    //region struct

    /**
     * 进入类体。
     * @param ctx the parse tree
     */

    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext): Any? {
        enterTemplateBody(ctx)
        super.visitTemplateBody(ctx)
        exitTemplateBody(ctx)
        return null
    }
    
     fun enterTemplateBody(ctx: mcfppParser.TemplateBodyContext) {
        Project.ctx = ctx
        //获取类的对象
        val parent = ctx.parent as mcfppParser.TemplateDeclarationContext
        val identifier: String = parent.classWithoutNamespace().text
        //设置作用域
        Template.currTemplate = GlobalField.getStruct(Project.currNamespace, identifier)
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */
    
    fun exitTemplateBody(ctx: mcfppParser.TemplateBodyContext) {
        Project.ctx = ctx
        Template.currTemplate = null
    }

    //endregion

    companion object {
        /**
         * 判断这个语句是否在循环语句中。包括嵌套形式。
         * @param ctx 需要判断的语句
         * @return 是否在嵌套中
         */
        private fun inLoopStatement(ctx: RuleContext): Boolean {
            if (ctx is mcfppParser.ForStatementContext) {
                return true
            }
            if (ctx is mcfppParser.DoWhileStatementContext) {
                return true
            }
            if (ctx is mcfppParser.WhileStatementContext) {
                return true
            }
            return if (ctx.parent != null) {
                inLoopStatement(ctx.parent)
            } else false
        }
    }
}