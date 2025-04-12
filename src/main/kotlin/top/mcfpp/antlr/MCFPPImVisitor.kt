package top.mcfpp.antlr

import org.antlr.v4.runtime.RuleContext
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.RuleContextExtension.children
import top.mcfpp.antlr.mcfppParser.CompileTimeFuncDeclarationContext
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.DataTemplateObjectConcrete
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ExecuteBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.Execute
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Namespace
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam.Companion.typeToStringList
import top.mcfpp.model.generic.Generic
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.FunctionMutator
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPGenericClassType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class MCFPPImVisitor: mcfppParserBaseVisitor<Any?>() {

    override fun visitTopStatement(ctx: mcfppParser.TopStatementContext): Any? {
        if(ctx.statement().size == 0) return null
        Function.currFunction = MCFPPFile.currFile!!.topFunction
        //注册函数
        GlobalField.localNamespaces[Project.currNamespace]!!.field.addFunction(Function.currFunction, force = false)
        super.visitTopStatement(ctx)
        Function.currFunction = Function.nullFunction
        return null
    }

    override fun visitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext): Any? {
        if(ctx.parent is CompileTimeFuncDeclarationContext) return null
        enterFunctionDeclaration(ctx)
        super.visitFunctionDeclaration(ctx)
        exitFunctionDeclaration(ctx)
        return null
    }

    private fun enterFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext){
        Project.ctx = ctx
        val f: Function
        //获取函数对象
        val types = ctx.functionParams()?.let { FunctionParam.parseReadonlyAndNormalParamTypes(it) }
        //获取缓存中的对象
        f = GlobalField.getFunction(Project.currNamespace, ctx.Identifier().text, types?.first?.map { it.build("") }?:ArrayList(), types?.second?.map { it.build("") }?:ArrayList())
        Function.currFunction = f
    }

    private fun exitFunctionDeclaration(ctx: mcfppParser.FunctionDeclarationContext){
        Project.ctx = ctx
        //函数是否有返回值
        if(Function.currFunction !is Generic<*> && Function.currFunction.returnType !=  MCFPPBaseType.Void && !Function.currFunction.hasReturnStatement){
            LogProcessor.error("Function should return a value: " + Function.currFunction.namespaceID)
        }
        //释放指针
        Function.currFunction.disposeClassPtr()
        Function.currFunction = Function.nullFunction
        if (Class.currClass == null) {
            //不在类中
            Function.currFunction = Function.nullFunction
        } else {
            Function.currFunction = Class.currClass!!.classPreInit
        }
    }

    override fun visitFunctionBody(ctx: mcfppParser.FunctionBodyContext): Any? {
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
    fun visitFunctionBody(ctx: mcfppParser.FunctionBodyContext, function: Function){
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
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext):Any? {
        Project.ctx = ctx
        //变量生成
        val fieldModifier = ctx.fieldModifier()?.text
        if(ctx.VAR() != null){
            //自动判断类型
            val init: Var<*> = MCFPPExprVisitor().visitExpression(ctx.expression())
            var `var` = if(fieldModifier == "import"){
                val qwq = init.type.buildUnConcrete(ctx.Identifier().text, Function.currFunction)
                qwq.hasAssigned = true
                qwq
            }else{
                init.type.build(ctx.Identifier().text, Function.currFunction)
            }
            `var`.nbtPath = NBTPath.getNormalStackPath(`var`)
            //变量赋值
            `var` = `var`.assignedBy(init)
            //一定是函数变量
            if (!Function.currField.putVar(ctx.Identifier().text, `var`, false)) {
                LogProcessor.error("Duplicate defined variable name:" + ctx.Identifier().text)
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
        }else{
            //获取类型
            val type = MCFPPType.parseFromContext(ctx.type(), Function.currFunction.field)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                MCFPPBaseType.Any
            }
            for (c in ctx.fieldDeclarationExpression()){
                //函数变量，生成
                var `var` = if(fieldModifier == "import"){
                    val qwq = type.buildUnConcrete(c.Identifier().text, Function.currFunction)
                    qwq.hasAssigned = true
                    qwq
                }else{
                    type.build(c.Identifier().text, Function.currFunction)
                }
                //变量注册
                //一定是函数变量
                if (Function.currField.containVar(c.Identifier().text)) {
                    LogProcessor.error("Duplicate defined variable name:" + c.Identifier().text)
                }
                Function.addComment("field: " + ctx.type().text + " " + c.Identifier().text + if (c.expression() != null) " = " + c.expression().text else "")
                `var`.nbtPath = NBTPath.getNormalStackPath(`var`)
                //变量初始化
                if (c.expression() != null) {
                    val init: Var<*> = MCFPPExprVisitor(if(type is MCFPPGenericClassType) type else null, if(type is MCFPPEnumType) type else null).visitExpression(c.expression())
                    `var` = `var`.assignedBy(init)
                }
                when(fieldModifier){
                    "const" -> {
                        if(!`var`.hasAssigned){
                            LogProcessor.error("The const field ${`var`.identifier} must be initialized.")
                        }
                        `var`.isConst = true
                    }
                    "dynamic" -> {
                        if(`var` is MCFPPValue<*> && `var`.hasAssigned){
                            `var` = `var`.toDynamic(false)
                        }else if(`var` is MCFPPValue<*>){
                            `var` = type.buildUnConcrete(c.Identifier().text, Function.currFunction)
                        }
                    }
                }
                Function.currField.putVar(`var`.identifier, `var`, true)
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
        Function.addComment("expression: " + ctx.text)
        if(ctx.varWithSelector() != null){
            val left: Var<*> = McfppLeftExprVisitor().visitVarWithSelector(ctx.varWithSelector())
            if (left.isConst) {
                LogProcessor.error("Cannot assign a constant repeatedly: " + left.identifier)
                return null
            }
            val type = left.type
            val right: Var<*> = MCFPPExprVisitor(if(type is MCFPPGenericClassType) type else null, if(type is MCFPPEnumType) type else null).visitExpression(ctx.expression())
            try {
                if(right !is MCFPPValue<*> && left.parent is DataTemplateObjectConcrete){
                    left.parent = (left.parent as DataTemplateObjectConcrete).toDynamic(true)
                }
                left.replacedBy(left.assignedBy(right))
            } catch (e: VariableConverseException) {
                LogProcessor.error("Cannot convert " + right.javaClass + " to " + left.javaClass)
                throw e
            }
        }else{
            MCFPPExprVisitor().visitExpression(ctx.expression())
        }
        Function.addComment("expression end: " + ctx.text)
        return null
    }

    override fun visitExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext): Any? {
        //是扩展函数
        enterExtensionFunctionDeclaration(ctx)
        super.visitExtensionFunctionDeclaration(ctx)
        exitExtensionFunctionDeclaration(ctx)

        return null
    }

    fun enterExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext) {
        val f: Function
        val data: CompoundData = if (ctx.type().typeWithoutExcl().className() == null) {
            when (ctx.type().text) {
                "int" -> MCInt.data
                else -> {
                    throw Exception("Cannot add extension function to ${ctx.type().text}")
                }
            }
        } else {
            val clsStr = ctx.type().typeWithoutExcl().className().text.split(":")
            val id: String
            val nsp: String?
            if (clsStr.size == 1) {
                id = clsStr[0]
                nsp = null
            } else {
                id = clsStr[1]
                nsp = clsStr[0]
            }
            val owo: Class? = GlobalField.getClass(nsp, id)
            if (owo == null) {
                val pwp = GlobalField.getTemplate(nsp, id)
                if (pwp == null) {
                    LogProcessor.error("Undefined class or struct:" + ctx.type().typeWithoutExcl().className().text)
                    f = UnknownFunction(ctx.Identifier().text)
                    Function.currFunction = f
                    return
                } else {
                    pwp
                }
            } else {
                owo
            }
        }
        //解析参数
        val types = FunctionParam.parseReadonlyAndNormalParamTypes(ctx.functionParams())
        val field = data.field
        //获取缓存中的对象
        f = field.getFunction(ctx.Identifier().text, types.first.map { it.build("") }, types.second.map { it.build("") })

        Function.currFunction = f
    }

    fun exitExtensionFunctionDeclaration(ctx: mcfppParser.ExtensionFunctionDeclarationContext) {
        Project.ctx = ctx
        //函数是否有返回值
        if (Function.currFunction.returnType != MCFPPBaseType.Void && !Function.currFunction.hasReturnStatement) {
            LogProcessor.error("A 'return' expression required in function: " + Function.currFunction.namespaceID)
        }
        //释放指针
        Function.currFunction.disposeClassPtr()
        Function.currFunction = Function.nullFunction
    }

//region 逻辑语句
    
    @InsertCommand
    override fun visitReturnStatement(ctx: mcfppParser.ReturnStatementContext):Any? {
        Project.ctx = ctx
        Function.addComment(ctx.text)
        if (ctx.expression() != null) {
            val ret: Var<*> = MCFPPExprVisitor().visitExpression(ctx.expression())
            Function.currBaseFunction.assignReturnVar(ret)
        }
        Function.currBaseFunction.hasReturnStatement = true
        Function.addCommand("return 1")
        return null
    }

    override fun visitBlock(ctx: mcfppParser.BlockContext): Any? {
        Project.ctx = ctx
        ctx.statement().forEach {
            if(Function.currFunction.hasReturnStatement){
                return@forEach
            }
            visitStatement(it)
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
    @Suppress("UNCHECKED_CAST")
    override fun visitIfStatement(ctx: mcfppParser.IfStatementContext): Any? {
        //进入if函数
        Project.ctx = ctx
        breakIf = ConditionType.NORMAL
        Function.addComment("if start")
        //获取if语句以后的所有语句
        val index = ctx.parent.parent.children().indexOf(ctx.parent)
        val list =  ctx.parent.parent.children().subList(index + 1, ctx.parent.parent.children().size) as List<mcfppParser.StatementContext>
        list.forEach { ctx.ifBlock().block().addChild(it) }
        list.forEach { l -> ctx.elseIfStatement().forEach { it.block().addChild(l) } }
        do {
            //if分支
            val (c,f) = enterIfBranch(ctx)
            if(c){
                //此分支会被编译，注册函数
                if(breakIf != ConditionType.ALWAYS_TRUE) {
                    //并不是必然编译的，所以需要注册函数，让分支内的内容在if_branch函数中执行。如果是必然执行的，那么直接内联即可
                    if (!GlobalField.localNamespaces.containsKey(f.namespace)) {
                        GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
                    }
                    GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f, false)
                    Function.currFunction = f
                }
                visitIfBlock(ctx.ifBlock())
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
                        if (!GlobalField.localNamespaces.containsKey(f2.namespace))
                            GlobalField.localNamespaces[f2.namespace] = Namespace(f2.namespace)
                        GlobalField.localNamespaces[f2.namespace]!!.field.addFunction(f2, false)
                        Function.currFunction = f2
                    }
                    visitBlock(it.block())
                    Project.ctx = ctx
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
                if (!GlobalField.localNamespaces.containsKey(f3.namespace))
                    GlobalField.localNamespaces[f3.namespace] = Namespace(f3.namespace)
                GlobalField.localNamespaces[f3.namespace]!!.field.addFunction(f3, false)
                Function.addCommand(Command("return run").build(Commands.function(f3)))
                Function.currFunction = f3
            }
            if(ctx.elseStatement() != null){
                visitBlock(ctx.elseStatement().block())
            }
            if(!Function.currFunction.hasReturnStatement){
                list.forEach { visitStatement(it) }
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
        when(val exp = MCFPPExprVisitor().visitExpression(ctx.expression())){
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
                if(!GlobalField.localNamespaces.containsKey(f.namespace))
                    GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f,false)
                //给子函数开栈
                Function.addCommand(
                    Command("execute").build(exp.toCommandPart()).build("run return run").build(Commands.function(f))
                )
            }

            is BaseBool -> {
                //注册函数
                if(!GlobalField.localNamespaces.containsKey(f.namespace))
                    GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f,false)
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
        when(val exp = MCFPPExprVisitor().visitExpression(ctx.expression())){
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
                if(!GlobalField.localNamespaces.containsKey(f.namespace))
                    GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f,false)
                //给子函数开栈
                Function.addCommand(
                    Command("execute").build(exp.toCommandPart()).build("run return run").build(Commands.function(f))
                )
                breakIf = ConditionType.NORMAL
            }

            is BaseBool -> {
                //注册函数
                if(!GlobalField.localNamespaces.containsKey(f.namespace))
                    GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f,false)
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
        Function.addComment("while start")
        //外while函数。这个函数中包含了while循环的逻辑
        val whileFunction = InternalFunction("_while_", Function.currFunction)
        Function.addCommand(Commands.stackIn())
        Function.addCommand(Commands.function(whileFunction))
        Function.addCommand(Commands.stackOut())
        Function.currFunction = whileFunction
        if(!GlobalField.localNamespaces.containsKey(whileFunction.namespace))
            GlobalField.localNamespaces[whileFunction.namespace] = Namespace(whileFunction.namespace)
        GlobalField.localNamespaces[whileFunction.namespace]!!.field.addFunction(whileFunction,false)
    }

    
    @InsertCommand
    fun exitWhileStatement(ctx: mcfppParser.WhileStatementContext) {
        Project.ctx = ctx
        Function.currFunction = Function.currFunction.parent[0]
        //调用完毕，将子函数的栈销毁
        Function.addComment("while end")
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
        Function.addCommand(Commands.stackIn())
        Function.addComment("while start")
        val parent: mcfppParser.WhileStatementContext = ctx.parent as mcfppParser.WhileStatementContext
        //while语句块编译的目标函数，即内while函数
        val f: Function = InternalFunction("_while_block_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace))
            GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
        GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f,false)
        //循环条件判断，此时目标函数是外while函数。条件表达式在外while中计算。
        when(val exp = MCFPPExprVisitor().visitExpression(parent.expression())){
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

    /**
     * 离开while语句块
     * @param ctx the parse tree
     */
    
    @InsertCommand
    fun exitWhileBlock(ctx: mcfppParser.WhileBlockContext) {
        Project.ctx = ctx
        Function.currFunction.disposeClassPtr()
        Function.addCommand("return 1")
        Function.currFunction = Function.currFunction.parent[0]
        Function.addComment("while loop end")
        Function.currFunction.field.forEachVar {
            if(!it.trackLost){
                it.trackLost = true
                if(it is MCFPPValue<*>) it.toDynamic(true)
            }
        }
    }

    override fun visitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext): Any? {
        enterDoWhileStatement(ctx)
        super.visitDoWhileStatement(ctx)
        exitDoWhileStatement(ctx)
        return null
    }

    private lateinit var doWhileFunction: InternalFunction
    @InsertCommand
    fun enterDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext) {
        //进入do-while函数
        Project.ctx = ctx
        Function.addComment("do-while start")
        doWhileFunction = InternalFunction("_dowhile_", Function.currFunction)
        //这里不能急着调用循环函数，因为循环体必定会执行一次。参见enterDoWhileBlock的代码
        //Function.addCommand(Commands.stackIn())
        //Function.addCommand(Commands.function(doWhileFunction))
        //Function.addCommand(Commands.stackOut())
        Function.currFunction = doWhileFunction
        if(!GlobalField.localNamespaces.containsKey(doWhileFunction.namespace))
            GlobalField.localNamespaces[doWhileFunction.namespace] = Namespace(doWhileFunction.namespace)
        GlobalField.localNamespaces[doWhileFunction.namespace]!!.field.addFunction(doWhileFunction,false)
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
        Function.addComment("do-while end")
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
        Function.addComment("do while start")
        //匿名函数的定义
        val f: Function = InternalFunction("_dowhile_", Function.currFunction)
        f.child.add(f)
        f.parent.add(f)
        if(!GlobalField.localNamespaces.containsKey(f.namespace)) {
            GlobalField.localNamespaces[f.namespace] = Namespace(f.namespace)
        }
        GlobalField.localNamespaces[f.namespace]!!.field.addFunction(f,false)
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
        when(val exp = MCFPPExprVisitor().visitExpression(parent.expression())){
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
    fun exitDoWhileBlock(ctx: mcfppParser.DoWhileBlockContext) {
        Project.ctx = ctx
        Function.currFunction.disposeClassPtr()
        //返回1
        Function.addCommand("return 1")
        Function.currFunction = Function.currFunction.parent[0]
        Function.addComment("do while end")
        Function.currFunction.field.forEachVar {
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
    override fun visitOrgCommand(ctx: mcfppParser.OrgCommandContext):Any? {
        Project.ctx = ctx
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
    override fun visitStatement(ctx: mcfppParser.StatementContext): Any? {
        Project.ctx = ctx
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
    override fun visitControlStatement(ctx: mcfppParser.ControlStatementContext):Any? {
        Project.ctx = ctx
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
    override fun visitExecuteStatement(ctx: mcfppParser.ExecuteStatementContext): Any? {
        val exec = Execute()
        for (context in ctx.executeContext()){
            var arg: Execute.WriteOnlyVar? = null
            for (exp in context.executeExpression().`var`()){
                if(arg == null){
                    val qwq = exec.data.field.getVar(exp.text) as Execute.WriteOnlyVar?
                    if(qwq == null){
                        LogProcessor.error("Cannot find argument: ${exp.text}")
                        continue
                    }
                    arg = qwq
                }else{
                    arg = arg.getData().field.getVar(exp.text) as Execute.WriteOnlyVar
                }
            }
            val value = MCFPPExprVisitor().visitExpression(context.expression())
            arg?.assignedBy(value)
        }
        val execFunction = NoStackFunction(TempPool.getFunctionIdentify("execute"), Function.currFunction)
        GlobalField.localNamespaces[execFunction.namespace]!!.field.addFunction(execFunction, false)
        val l = Function.currFunction
        Function.currFunction = execFunction
        super.visitExecuteStatement(ctx)
        Function.currFunction = l
        Function.addCommand(exec.run(execFunction))
        return null
    }

    //region class
    override fun visitClassDeclaration(ctx: mcfppParser.ClassDeclarationContext): Any? {
        if(ctx.readOnlyParams() == null){
            super.visitClassDeclaration(ctx)
        }
        return null
    }

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
    private fun enterClassBody(ctx: mcfppParser.ClassBodyContext) {
        Project.ctx = ctx
        //获取类的对象
        val parent = ctx.parent
        if(parent is mcfppParser.ClassDeclarationContext){
            val identifier = parent.classWithoutNamespace().text
            Class.currClass = GlobalField.getClass(Project.currNamespace, identifier)
        }else{
            parent as mcfppParser.ObjectClassDeclarationContext
            val identifier = parent.classWithoutNamespace().text
            Class.currClass = GlobalField.getObject(Project.currNamespace, identifier) as ObjectClass
        }
        //设置作用域
        Function.currFunction = Class.currClass!!.classPreInit
    }

    /**
     * 离开类体。将缓存重新指向全局
     * @param ctx the parse tree
     */

    private fun exitClassBody(ctx: mcfppParser.ClassBodyContext) {
        Project.ctx = ctx
        Class.currClass = null
        Function.currFunction = Function.nullFunction
    }

    override fun visitClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext): Any? {
        //是类的成员函数
        enterClassFunctionDeclaration(ctx)
        super.visitClassFunctionDeclaration(ctx)
        exitClassFunctionDeclaration(ctx)
        return null
    }

    private fun enterClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext) {
        Project.ctx = ctx
        //解析参数
        val types = FunctionParam.parseReadonlyAndNormalParamTypes(ctx.functionParams())
        //获取缓存中的对象
        val f = Class.currClass!!.field.getFunction(ctx.Identifier().text, types.first.map { it.build("") }, types.second.map { it.build("") })
        Function.currFunction = f
    }

    private fun exitClassFunctionDeclaration(ctx: mcfppParser.ClassFunctionDeclarationContext) {
        Project.ctx = ctx
        Function.currFunction = Class.currClass!!.classPreInit
    }

    override fun visitClassConstructorDeclaration(ctx: mcfppParser.ClassConstructorDeclarationContext): Any? {
        //是构造函数
        enterClassConstructorDeclaration(ctx)
        super.visitClassConstructorDeclaration(ctx)
        exitClassConstructorDeclaration(ctx)
        return null
    }

    private fun enterClassConstructorDeclaration(ctx: mcfppParser.ClassConstructorDeclarationContext) {
        Project.ctx = ctx
        val types = FunctionParam.parseNormalParamTypes(ctx.normalParams())
        val c = Class.currClass!!.getConstructorByString(types.typeToStringList())!!
        Function.currFunction = c
    }

    private fun exitClassConstructorDeclaration(ctx: mcfppParser.ClassConstructorDeclarationContext) {
        Project.ctx = ctx
        Function.currFunction = Class.currClass!!.classPreInit
    }

    private lateinit var currProperty: Property
    override fun visitClassFieldDeclaration(ctx: mcfppParser.ClassFieldDeclarationContext): Any? {
        val id = ctx.fieldDeclarationExpression().Identifier().text
        currProperty = Class.currClass!!.field.getProperty(id)!!
        return super.visitClassFieldDeclaration(ctx)
    }

    override fun visitGetter(ctx: mcfppParser.GetterContext): Any? {
        if(ctx.functionBody() != null){
            Function.currFunction = (currProperty.accessor as FunctionAccessor).function
        }
        return super.visitGetter(ctx)
    }

    override fun visitSetter(ctx: mcfppParser.SetterContext?): Any? {
        if(ctx!!.functionBody() != null){
            Function.currFunction = (currProperty.mutator as FunctionMutator).function
        }
        return super.visitSetter(ctx)
    }
    //endregion

    //region template

    /**
     * 进入类体。
     * @param ctx the parse tree
     */

    override fun visitTemplateBody(ctx: mcfppParser.TemplateBodyContext): Any? {
        //什么都不做哦
        return null
    }

    override fun visitObjectTemplateDeclaration(ctx: mcfppParser.ObjectTemplateDeclarationContext?): Any? {
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