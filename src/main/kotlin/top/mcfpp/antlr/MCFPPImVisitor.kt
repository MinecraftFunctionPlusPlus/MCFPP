package top.mcfpp.antlr

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.RuleContextExtension.children
import top.mcfpp.antlr.mcfppParser.BlockContext
import top.mcfpp.antlr.mcfppParser.CompileTimeFuncDeclarationContext
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.JavaVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ExecuteBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.iterator.ConcreteIterator
import top.mcfpp.core.lang.obj.DataTemplateObjectConcrete
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.Execute
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Generic
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.MCFPPFuncGetter
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

open class MCFPPImVisitor: mcfppParserBaseVisitor<Any?>() {

    var isInTopStatement = false

    override fun visitTopStatement(ctx: mcfppParser.TopStatementContext): Any? = withCompilationContext(ctx) {
        if(ctx.statement().size == 0) return null
        MCFPPFile.currFile!!.topFunction.runInFunction {
            //注册函数
            GlobalScope.localNamespaces[Project.currNamespace]!!.scope.addFunction(Function.currFunction, force = false)
            isInTopStatement = true
            super.visitTopStatement(ctx)
            isInTopStatement = false
            //变量移动到文件作用域
            MCFPPFile.currFile!!.field.vars.putAll(Function.currFunction.scope.vars)
        }
        return null
    }

    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        if(ctx.parent is CompileTimeFuncDeclarationContext) return null
        enterFunctionDeclaration(ctx)
        super.visitFunctionDeclaration(ctx)
        exitFunctionDeclaration()
        return null
    }

    private fun enterFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext) {
        val f: Function
        //获取函数对象
        val types = ctx.functionDeclarationPart().functionParams()?.let { FunctionParam.parseReadonlyAndNormalParamTypes(it) }
        //获取缓存中的对象
        f = GlobalScope.getFunction(Project.currNamespace, ctx.functionDeclarationPart().Identifier().text, types?.first?.map { it.build("") }?:ArrayList(), types?.second?.map { it.build("") }?:ArrayList())
        Function.currFunction = f
    }

    private fun exitFunctionDeclaration() {
        //函数是否有返回值
        if(Function.currFunction !is Generic<*> && Function.currFunction.returnType !=  MCFPPPrivateType.Void && !Function.currFunction.hasReturnStatement){
            LogProcessor.error("Function should return a value: " + Function.currFunction.namespaceID)
        }
        //释放指针
        Function.currFunction = Function.nullFunction
    }

    override fun visitCurlBlock(ctx: mcfppParser.CurlBlockContext): Any? = withCompilationContext(ctx) {
        if(ctx.parent is CompileTimeFuncDeclarationContext) return null
        if(Function.currFunction !is Generic<*>){
            ctx.statement().forEach {
                if(Function.currFunction.hasReturnStatement || Function.currFunction.isEnded){
                    return@forEach
                }
                visitStatement(it)
            }
        }
        return null
    }

    //泛型函数编译使用的入口
    fun visitCurlBlock(ctx: mcfppParser.CurlBlockContext, function: Function){
        val lastFunction = Function.currFunction
        Function.currFunction = function
        ctx.statement().forEach {
            if(Function.currFunction.hasReturnStatement){
                return@forEach
            }
            visitStatement(it)
        }
        Function.currFunction = lastFunction
    }

    /**
     * 变量声明
     * @param ctx the parse tree
     */
    @InsertCommand
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext):Any? = withCompilationContext(ctx) {
        val fieldModifier = ctx.fieldModifier()?.text
        var type = ctx.type()?.let {
            MCFPPType.parseFromContextNotNull(it, Function.currFunction.scope)
        }
        var init: Var<*>? = null
        if (ctx.expression() != null) {
            Function.addComment(ctx.text)
            init = MCFPPExprVisitor(
                if(type is MCFPPEnumType) type else null
            ).visitExpression(ctx.expression())
        }
        //类型推断
        if(type == null && init == null){
            LogProcessor.error("Variable ${ctx.Identifier().text} must have a type or an initializer")
            return null to null
        }else if(type == null){
            type = init!!.type
        }
        val `var` = if(fieldModifier == "import"){
            val qwq = type.buildUnConcrete(ctx.Identifier().text, Function.currFunction)
            qwq.hasAssigned = true
            qwq
        }else{
            type.build(ctx.Identifier().text, Function.currFunction)
        }
        if(isInTopStatement){
            `var`.nbtPath = NBTPath.getFileScopePath(`var`)
        }else{
            `var`.nbtPath = NBTPath.getNormalStackPath(`var`)
        }
        //一定是函数变量
        if (Function.currField.containVar(ctx.Identifier().text)) {
            LogProcessor.error("Duplicate defined variable:" + ctx.Identifier().text)
        }
        if(init != null){
            //变量赋值
            Function.currField.putVar(`var`.identifier, `var`.assignedBy(init), true)
        }else{
            Function.currField.putVar(`var`.identifier, `var`, true)
        }
        when(fieldModifier){
            "const" -> {
                if(!`var`.hasAssigned){
                    LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                }
                `var`.isConst = true
            }
            "dynamic" -> {
                if(`var` is MCFPPValue<*>){
                    `var`.toDynamic(true)
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
    override fun visitStatementExpression(ctx: mcfppParser.StatementExpressionContext):Any? = withCompilationContext(ctx) {
        Function.addComment("expression: " + ctx.text)
        if(ctx.varWithSelector() != null){
            val left: Var<*> = MCFPPExprVisitor().visitVarWithSelector(ctx.varWithSelector())
            if (left.isConst) {
                LogProcessor.error("Cannot assign a constant repeatedly: " + left.identifier)
                return null
            }
            val type = left.type
            val right: Var<*> = MCFPPExprVisitor(
                if(type is MCFPPEnumType) type else null
            ).visitExpression(ctx.expression())
            if(right !is MCFPPValue<*> && left.parent is DataTemplateObjectConcrete){
                left.parent = (left.parent as DataTemplateObjectConcrete).toDynamic(true)
            }
            left.replacedBy(left.assignedBy(right))
        }else{
            MCFPPExprVisitor().visitExpression(ctx.expression())
        }
        Function.addComment("expression end: " + ctx.text)
        return null
    }

    override fun visitExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext): Any? = withCompilationContext(ctx) {
        //是扩展函数
        enterExtensionFunctionDeclaration(ctx)
        super.visitExtensionFunctionDeclaration(ctx)
        exitExtensionFunctionDeclaration()

        return null
    }

    fun enterExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext) {
        val f: Function
        val data = MCFPPType.parseFromContext(ctx.type(), MCFPPFile.currFile!!.field)?.instanceData?: return
        //解析参数
        val types = FunctionParam.parseReadonlyAndNormalParamTypes(ctx.functionParams())
        val field = data.scope
        //获取缓存中的对象
        f = field.getFunction(ctx.Identifier().text, types.first.map { it.build("") }, types.second.map { it.build("") })

        Function.currFunction = f
    }

    fun exitExtensionFunctionDeclaration() {
        //函数是否有返回值
        if (Function.currFunction.returnType != MCFPPPrivateType.Void && !Function.currFunction.hasReturnStatement) {
            LogProcessor.error("A 'return' expression required in function: " + Function.currFunction.namespaceID)
        }
        //释放指针
        Function.currFunction = Function.nullFunction
    }

//region 逻辑语句
    
    @InsertCommand
    override fun visitReturnStatement(ctx: mcfppParser.ReturnStatementContext):Any? = withCompilationContext(ctx) {
        Function.addComment(ctx.text)
        if (ctx.expression() != null) {
            val ret: Var<*> = MCFPPExprVisitor().visitExpression(ctx.expression())
            Function.currBaseFunction.assignReturnVar(ret)
        }
        Function.currBaseFunction.hasReturnStatement = true
        Function.addCommand("return 1")
        return null
    }

    override fun visitBlock(ctx: mcfppParser.BlockContext): Any? = withCompilationContext(ctx) {
        if(ctx.curlBlock() != null) {
            ctx.curlBlock().statement().forEach {
                if(Function.currFunction.hasReturnStatement){
                    return@forEach
                }
                visitStatement(it)
            }
        }else{
            ctx.children().forEach {
                if(Function.currFunction.hasReturnStatement){
                    return null
                }
                visitStatement(it as mcfppParser.StatementContext)
            }
        }

        return null
    }

    private enum class ConditionType {
        /**
         * 上一个分支必然不执行
         */
        ALWAYS_FALSE,

        /**
         * 上一个分支必然执行
         */
        ALWAYS_TRUE,

        /**
         * 正常
         */
        NORMAL
    }

    private var breakIf = ConditionType.NORMAL
    override fun visitIfStatement(ctx: mcfppParser.IfStatementContext): Any? = withCompilationContext(ctx) {
        //进入if函数
        breakIf = ConditionType.NORMAL
        Function.addComment("if start")
        //获取if语句以后的所有语句
        val index = (ctx.parent.parent as ParserRuleContext).children.indexOf(ctx.parent)
        val list =  (ctx.parent.parent as ParserRuleContext).children.subList(index + 1, ctx.parent.parent.childCount)
        list.forEach {
            if(it is TerminalNode){
                ctx.block().addChild(it)
            }else{
                ctx.block().addChild(it as RuleContext)
            }
        }
        list.forEach { l -> ctx.elseIfStatement().forEach {
            if(l is TerminalNode){
                it.block().addChild(l)
            }else{
                it.block().addChild(l as RuleContext)
            }
        } }
        do {
            //if分支
            val (c,f) = enterIfBranch(ctx)
            if(c){
                //此分支会被编译，注册函数
                if(breakIf != ConditionType.ALWAYS_TRUE) {
                    //并不是必然编译的，所以需要注册函数，让分支内的内容在if_branch函数中执行。如果是必然执行的，那么直接内联即可
                    if (!GlobalScope.localNamespaces.containsKey(f.namespace)) {
                        GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                    }
                    GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f, false)
                    //同时，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
                    Function.currFunction.scope.forEachVar {
                        if(it is MCFPPValue<*>) it.toDynamic(true)
                    }
                    Function.currFunction = f
                }
                visitBlock(ctx.block())
                //由于原来的调用if的函数已经被return命令返回，需要if_branch函数帮助清理它的栈
                if(breakIf != ConditionType.ALWAYS_TRUE) {
                    Function.addCommand(Commands.stackOut())
                    Function.currFunction = Function.currFunction.parent[0]
                }
                Function.addComment("if branch end")
            }
            //上一条分支必然执行，后面的分支都必定不会执行，不需要编译了
            if(breakIf == ConditionType.ALWAYS_TRUE) break
            //else if分支
            ctx.elseIfStatement().forEach {
                val (c2, f2) = enterElseIfBranch(it)
                if(c2){
                    //这条else if分支会被编译
                    if(breakIf != ConditionType.ALWAYS_TRUE) {
                        //如果这里breakIf是1，只能是这一条分支必然会被执行而上一条分支不可能执行（参见enterElseIfBranch中的逻辑
                        //而不为1的时候，说明它只能是0，因为如果是-1的话c2也会是false而不能执行到这里
                        //所以让我们注册函数
                        if (!GlobalScope.localNamespaces.containsKey(f2.namespace))
                            GlobalScope.localNamespaces[f2.namespace] = Namespace(f2.namespace)
                        GlobalScope.localNamespaces[f2.namespace]!!.scope.addFunction(f2, false)
                        //同时，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
                        Function.currFunction.scope.forEachVar {
                            if(it is MCFPPValue<*>) it.toDynamic(true)
                        }
                        Function.currFunction = f2
                    }
                    visitBlock(it.block())
                    //由于原来的调用if的函数已经被return命令返回，需要if_branch函数帮助清理它的栈
                    if(breakIf != ConditionType.ALWAYS_TRUE) {  //这里同理
                        Function.addCommand(Commands.stackOut())
                        Function.currFunction = Function.currFunction.parent[0]
                    }
                    Function.addComment("else-if branch end")
                }
                //后续的分支也不需要继续编译了
                if(breakIf == ConditionType.ALWAYS_TRUE) return@forEach
            }
            //不需要继续编译else语句了
            if(breakIf == ConditionType.ALWAYS_TRUE) break
            //else语句
            Function.addComment("else branch start")
            if(breakIf != ConditionType.ALWAYS_FALSE){
                //注册函数
                val f3 = NoStackFunction(TempPool.getFunctionIdentify("else_branch"), Function.currFunction)
                if (!GlobalScope.localNamespaces.containsKey(f3.namespace))
                    GlobalScope.localNamespaces[f3.namespace] = Namespace(f3.namespace)
                GlobalScope.localNamespaces[f3.namespace]!!.scope.addFunction(f3, false)
                Function.addCommand(Command("return run").build(Commands.function(f3)))
                //同样的，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
                Function.currFunction.scope.forEachVar {
                    if(it is MCFPPValue<*>) it.toDynamic(true)
                }
                Function.currFunction = f3
            }
            if(ctx.elseStatement() != null){
                visitBlock(ctx.elseStatement().block())
            }
            if(!Function.currFunction.hasReturnStatement){
                list.forEach { visit(it) }
            }
            if(breakIf != ConditionType.ALWAYS_FALSE){
                Function.addCommand(Commands.stackOut())
                Function.currFunction = Function.currFunction.parent[0]
            }
            Function.addComment("else branch end")
        }while (false)
        Function.addComment("if end")
        //if以后的语句已经被全部打包到if分支里面，所以if语句之后的statement没有意义
        Function.currFunction.isEnded = true
        return null
    }

    /**
     * 检查条件，返回是否会编译这条if分支
     */
    private fun enterIfBranch(ctx: mcfppParser.IfStatementContext): Pair<Boolean, NoStackFunction>{
        Function.addComment("if branch start")
        //匿名函数的定义
        val f = NoStackFunction(TempPool.getFunctionIdentify("if_branch"), Function.currFunction)
        val expr = ctx.bucketExpression().expression()
        if(expr == null){
            LogProcessor.error("The condition of if statement is null.")
            return false to f
        }
        when(val exp = MCFPPExprVisitor().visit(expr)){
            is ScoreBoolConcrete -> {
                if (exp.value) {
                    //函数调用的命令
                    //给子函数开栈
                    Function.addCommand("function " + f.namespaceID)
                    //LogProcessor.warn("The condition is always true. ")
                    breakIf = ConditionType.ALWAYS_TRUE
                } else {
                    Function.addComment("function " + f.namespaceID)
                    //LogProcessor.warn("The condition is always false. ")
                    breakIf = ConditionType.ALWAYS_FALSE
                }
                return exp.value to f
            }

            is ExecuteBool -> {
                //注册函数
                if(!GlobalScope.localNamespaces.containsKey(f.namespace))
                    GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
                //给子函数开栈
                Function.addCommand(
                    Command("execute").build(exp.toCommandPart()).build("run return run").build(Commands.function(f))
                )
            }

            is BaseBool -> {
                //注册函数
                if(!GlobalScope.localNamespaces.containsKey(f.namespace))
                    GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
                Function.addCommand(
                    Command("execute if").build(exp.toCommandPart()).build("run return run").build(Commands.function(f))
                )
            }

            else -> {
                LogProcessor.error("The condition must be a boolean expression.")
                Function.addComment("[error/The condition must be a boolean expression]function " + f.namespaceID)
                breakIf = ConditionType.ALWAYS_FALSE
                return false to f
            }
        }
        return true to f
    }

    /**
     * 检查条件，返回是否会编译这条else-if分支
     */
    private fun enterElseIfBranch(ctx: mcfppParser.ElseIfStatementContext): Pair<Boolean, NoStackFunction>{
        Function.addComment("else-if branch start")
        //匿名函数的定义
        val f = NoStackFunction(TempPool.getFunctionIdentify("else_if_branch"), Function.currFunction)
        val expr = ctx.bucketExpression().expression()
        if(expr == null){
            LogProcessor.error("The condition of if statement is null.")
            return false to f
        }
        when(val exp = MCFPPExprVisitor().visit(expr)){
            is ScoreBoolConcrete -> {
                if (exp.value) {
                    //函数调用的命令
                    //给子函数开栈
                    Function.addCommand("function " + f.namespaceID)
                    //LogProcessor.warn("The condition is always true. ")
                    breakIf = if(breakIf == ConditionType.NORMAL) ConditionType.NORMAL else ConditionType.ALWAYS_TRUE
                } else {
                    Function.addComment("function " + f.namespaceID)
                    //LogProcessor.warn("The condition is always false. ")
                    breakIf = if(breakIf == ConditionType.NORMAL) ConditionType.NORMAL else ConditionType.ALWAYS_FALSE
                }
                return exp.value to f
            }

            is ExecuteBool -> {
                //注册函数
                if(!GlobalScope.localNamespaces.containsKey(f.namespace))
                    GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
                //给子函数开栈
                Function.addCommand(
                    Command("execute").build(exp.toCommandPart()).build("run return run").build(Commands.function(f))
                )
                breakIf = ConditionType.NORMAL
            }

            is BaseBool -> {
                //注册函数
                if(!GlobalScope.localNamespaces.containsKey(f.namespace))
                    GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
                Function.addCommand(
                    Command("execute if").build(exp.toCommandPart()).build("run return run").build(Commands.function(f))
                )
                breakIf = ConditionType.NORMAL
            }

            else -> {
                LogProcessor.error("The condition must be a boolean expression.")
                Function.addComment("[error/The condition must be a boolean expression]function " + f.namespaceID)
                breakIf = if(breakIf == ConditionType.NORMAL) ConditionType.NORMAL else ConditionType.ALWAYS_FALSE
                return false to f
            }
        }
        return true to f
    }

    override fun visitWhileStatement(ctx: mcfppParser.WhileStatementContext): Any? = withCompilationContext(ctx) {
        enterWhileStatement()
        visitWhileBlock(ctx.block())
        exitWhileStatement()
        return null
    }

    @InsertCommand
    fun enterWhileStatement() {
        //进入if函数
        Function.addComment("while start")
        //外while函数。这个函数中包含了while循环的逻辑
        val whileFunction = InternalFunction("_while_", Function.currFunction)
        Function.addCommand(Commands.stackIn())
        Function.addCommand(Commands.function(whileFunction))
        Function.addCommand(Commands.stackOut())
        //同时，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
        Function.currFunction.scope.forEachVar {
            if(it is MCFPPValue<*>) it.toDynamic(true)
        }
        Function.currFunction = whileFunction
        if(!GlobalScope.localNamespaces.containsKey(whileFunction.namespace))
            GlobalScope.localNamespaces[whileFunction.namespace] = Namespace(whileFunction.namespace)
        GlobalScope.localNamespaces[whileFunction.namespace]!!.scope.addFunction(whileFunction,false)
    }

    
    @InsertCommand
    fun exitWhileStatement() {
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addComment("while end")
    }


    fun visitWhileBlock(ctx: mcfppParser.BlockContext): Any? = withCompilationContext(ctx) {
        enterWhileBlock(ctx)
        visitBlock(ctx)
        exitWhileBlock()
        return null
    }

    /**
     * 进入while语句块
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun enterWhileBlock(ctx: mcfppParser.BlockContext) {
        //入栈
        Function.addCommand(Commands.stackIn())
        Function.addComment("while start")
        val parent: mcfppParser.WhileStatementContext = ctx.parent as mcfppParser.WhileStatementContext
        //while语句块编译的目标函数，即内while函数
        val f: Function = InternalFunction("_while_block_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalScope.localNamespaces.containsKey(f.namespace))
            GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
        GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
        //循环条件判断，此时目标函数是外while函数。条件表达式在外while中计算。
        val expr = parent.bucketExpression().expression()
        if(expr == null){
            LogProcessor.error("The condition of while statement is null.")
            return
        }
        when(val exp = MCFPPExprVisitor().visit(expr)){
            is ScoreBoolConcrete -> {
                if(exp.value){
                    //内while函数的返回值表示循环是否被阻断，使用execute if判断。若成立，则继续运行外while函数
                    Function.addCommand("execute " +
                            "if function " + f.namespaceID + " " +
                            "run function " + Function.currFunction.namespaceID)
                }else{
                    Function.addComment("function " + f.namespaceID)
                }
            }

            is ExecuteBool -> {
                Function.addCommand(Command("execute")
                    .build(exp.toCommandPart())
                    .build("if function " + f.namespaceID)
                    .build("run function " + Function.currFunction.namespaceID))
            }

            is BaseBool -> {
                Function.addCommand(Command("execute")
                    .build("if").build(exp.toCommandPart())
                    .build("if function " + f.namespaceID)
                    .build("run function " + Function.currFunction.namespaceID)
                )
            }

            else -> {
                LogProcessor.error("The condition must be a boolean expression.")
                Function.addComment("[error/The condition must be a boolean expression]function " + f.namespaceID)
            }
        }
        Function.currFunction = f //后续块中的命令解析到递归的函数中

    }

    @InsertCommand
    fun exitWhileBlock() {
        Function.addCommand("return 1")
        Function.currFunction = Function.currFunction.parent[0]
        Function.addComment("while loop end")
        Function.currFunction.scope.forEachVar {
            if(!it.trackLost){
                it.trackLost = true
                if(it is MCFPPValue<*>) it.toDynamic(true)
            }
        }
    }

    override fun visitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext): Any? = withCompilationContext(ctx) {
        enterDoWhileStatement()
        visitDoWhileBlock(ctx.block())
        exitDoWhileStatement()
        return null
    }

    private lateinit var doWhileFunction: InternalFunction
    @InsertCommand
    fun enterDoWhileStatement() {
        //进入do-while函数
        Function.addComment("do-while start")
        doWhileFunction = InternalFunction("_dowhile_", Function.currFunction)
        //这里不能急着调用循环函数，因为循环体必定会执行一次。参见enterDoWhileBlock的代码
        //Function.addCommand(Commands.stackIn())
        //Function.addCommand(Commands.function(doWhileFunction))
        //Function.addCommand(Commands.stackOut())
        //同时，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
        Function.currFunction.scope.forEachVar {
            if(it is MCFPPValue<*>) it.toDynamic(true)
        }
        Function.currFunction = doWhileFunction
        if(!GlobalScope.localNamespaces.containsKey(doWhileFunction.namespace))
            GlobalScope.localNamespaces[doWhileFunction.namespace] = Namespace(doWhileFunction.namespace)
        GlobalScope.localNamespaces[doWhileFunction.namespace]!!.scope.addFunction(doWhileFunction,false)
    }

    @InsertCommand
    fun exitDoWhileStatement() {
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addComment("do-while end")
    }


    fun visitDoWhileBlock(ctx: mcfppParser.BlockContext): Any? = withCompilationContext(ctx) {
        enterDoWhileBlock(ctx)
        visitBlock(ctx)
        exitDoWhileBlock()
        return null
    }

    /**
     * 进入do-while语句块，开始匿名函数调用
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun enterDoWhileBlock(ctx: mcfppParser.BlockContext) {
        Function.addComment("do while start")
        //匿名函数的定义
        val f: Function = InternalFunction("_dowhile_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalScope.localNamespaces.containsKey(f.namespace)) {
            GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
        }
        GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
        //给子函数开栈
        Function.currFunction.parent[0].commands.addAll(
            arrayOf(
                //必然调用一次循环体内的函数
                Commands.stackIn(),
                Commands.function(f),
                Commands.stackOut(),
                //然后才开始调用do-while外函数。此时就是先判断再执行，转换为了while语句
                Commands.stackIn(),
                Commands.function(doWhileFunction),
                Commands.stackOut()
            )
        )
        //递归调用
        val parent = ctx.parent as mcfppParser.DoWhileStatementContext
        val expr = parent.bucketExpression().expression()
        if(expr == null){
            LogProcessor.error("The condition of do-while statement is null.")
            return
        }
        when(val exp = MCFPPExprVisitor().visit(expr)){
            is ScoreBoolConcrete -> {
                if(exp.value){
                    Function.addCommand("execute " +
                            "if function " + f.namespaceID + " " +
                            "run function " + Function.currFunction.namespaceID)
                    LogProcessor.warn("The condition is always true. ")
                }else{
                    Function.addComment("function " + f.namespaceID)
                    LogProcessor.warn("The condition is always false. ")
                }
            }

            is ExecuteBool -> {
                Function.addCommand(Command("execute")
                    .build(exp.toCommandPart())
                    .build("if function " + f.namespaceID)
                    .build("run function " + Function.currFunction.namespaceID))
            }

            is BaseBool -> {
                Function.addCommand(Command("execute")
                    .build("if").build(exp.toCommandPart())
                    .build("if function " + f.namespaceID)
                    .build("run function " + Function.currFunction.namespaceID)
                )
            }

            else -> {
                LogProcessor.error("The condition must be a boolean expression.")
                Function.addComment("[error/The condition must be a boolean expression]function " + f.namespaceID)
            }
        }
        Function.currFunction = f //后续块中的命令解析到递归的函数中
    }

    
    @InsertCommand
    fun exitDoWhileBlock() {
        //返回1
        Function.addCommand("return 1")
        Function.currFunction = Function.currFunction.parent[0]
        Function.addComment("do while end")
        Function.currFunction.scope.forEachVar {
            if(it.trackLost){
                it.trackLost = false
                if(it is MCFPPValue<*>) it.toDynamic(true)
            }
        }
    }

//endregion

    /**
     * 使用原版命令
     */
    @InsertCommand
    override fun visitOrgCommand(ctx: mcfppParser.OrgCommandContext):Any? = withCompilationContext(ctx) {
        val command = Command()
        for (content in ctx.orgCommandContent()){
            if(content.OrgCommandText() != null){
                command.build(content.OrgCommandText().text)
            }else{
                val exp = MCFPPExprVisitor().visitExpression(content.orgCommandExpression().expression())
                command.build(exp.toCommandPart(), false)
            }
        }
        Function.addCommand(command)
        return null
    }

    /**
     * 进入任意语句，检查此函数是否还能继续添加语句
     * @param ctx the parse tree
     */

    @InsertCommand
    override fun visitStatement(ctx: mcfppParser.StatementContext): Any? = withCompilationContext(ctx) {
        if(Function.currFunction.isEnded){
            return null
        }
        if (Function.currFunction.isReturned) {
            LogProcessor.warn("Unreachable code: " + ctx.text)
            return null
        }
        super.visitStatement(ctx)
        return null
    }

    private var temp: ScoreBool? = null
    
    @InsertCommand
    override fun visitControlStatement(ctx: mcfppParser.ControlStatementContext):Any? = withCompilationContext(ctx) {
        if (!inLoopStatement(ctx)) {
            LogProcessor.error("'continue' or 'break' can only be used in loop statements.")
            return null
        }
        Function.addComment(ctx.text)
        //return语句
        if(ctx.BREAK() != null){
            //break，完全跳出while
            Function.addCommand("return 0")
        }else{
            //continue，跳过当次
            Function.addCommand("return 1")
        }
        Function.currFunction.isReturned = true
        return null
    }

    //进入execute语句
    override fun visitExecuteStatement(ctx: mcfppParser.ExecuteStatementContext): Any? = withCompilationContext(ctx) {
        val exec = Execute()
        for (context in ctx.executeContext()){
            var arg: Execute.WriteOnlyVar? = null
            for (exp in context.executeExpression().`var`()){
                if(arg == null){
                    val qwq = exec.data.scope.getVar(exp.text) as Execute.WriteOnlyVar?
                    if(qwq == null){
                        LogProcessor.error("Cannot find argument: ${exp.text}")
                        continue
                    }
                    arg = qwq
                }else{
                    arg = arg.getData().scope.getVar(exp.text) as Execute.WriteOnlyVar
                }
            }
            val value = MCFPPExprVisitor().visitExpression(context.expression())
            arg?.assignedBy(value)
        }
        val execFunction = NoStackFunction(TempPool.getFunctionIdentify("execute"), Function.currFunction)
        GlobalScope.localNamespaces[execFunction.namespace]!!.scope.addFunction(execFunction, false)
        val l = Function.currFunction
        Function.currFunction = execFunction
        super.visitExecuteStatement(ctx)
        Function.currFunction = l
        Function.addCommand(exec.run(execFunction))
        return null
    }

    override fun visitForeachStatement(ctx: mcfppParser.ForeachStatementContext): Any? {
        val id = ctx.Identifier().text
        val qwq = MCFPPExprVisitor().visitExpression(ctx.expression())
        // try to get iterator
        val func = MCFPPFuncGetter.getFunction(qwq, "iterator", emptyList(), arrayListOf())
        if(func is UnknownFunction){
            LogProcessor.error("Not iterable")
            return null
        }
        func.invoke(arrayListOf(), qwq)
        val iterator = (func.returnVar as JavaVar).value
        if(iterator is ConcreteIterator<*>){
            visitConcreteForeach(id, iterator, ctx.block())
        }else{
            //同时，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
            Function.currFunction.scope.forEachVar {
                if(it is MCFPPValue<*>) it.toDynamic(true)
            }
            TODO()
        }
        return null
    }

    fun visitConcreteForeach(id: String, iterator: ConcreteIterator<*>, ctx: BlockContext){
        for (v in iterator){
            val i = v.type.buildUnConcrete(id).assignedBy(v)
            Function.addCommands(Commands.internalFunction(Function.currFunction){
                Function.currFunction.scope.putVar(id, i)
                visitBlock(ctx)
            })
        }
    }

    //region template

    /**
     * 进入类体。
     * @param ctx the parse tree
     */
    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext): Any? = withCompilationContext(ctx) {
        //什么都不做哦
        return null
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext): Any? = withCompilationContext(ctx) {
        //什么都不做哦
        return null
    }

    //endregion

    companion object {
        /**
         * 判断这个语句是否在循环语句中。包括嵌套形式。
         * @param ctx 需要判断的语句
         * @return 是否在嵌套中
         */
        fun inLoopStatement(ctx: RuleContext): Boolean {
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