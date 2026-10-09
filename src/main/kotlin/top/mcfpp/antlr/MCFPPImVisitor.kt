package top.mcfpp.antlr

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData

import top.mcfpp.analysis.TypeUsage
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess

import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.Project.withCompilationContext
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.RuleContextExtension.children
import top.mcfpp.antlr.mcfppParser.BlockContext
import top.mcfpp.antlr.mcfppParser.CompileTimeFuncDeclarationContext
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCNumber
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.RangeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
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
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

open class MCFPPImVisitor: mcfppParserBaseVisitor<Any?>() {

    var isInTopStatement = false

    // Statements following a branch must run in each branch function, including nested branches.
    private var followingStatements: List<mcfppParser.StatementContext> = emptyList()

    private class LoopContext(
        val visible: List<Var<*>>,
        val entry: Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>
    ) {
        val backEdges = arrayListOf<Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>>()
        val breakExits = arrayListOf<Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>>()
    }
    private val loops = java.util.ArrayDeque<LoopContext>()

    private fun helper(name: String, parent: Function): NoStackFunction {
        var identifier: String
        do {
            identifier = TempPool.getFunctionIdentify(name)
        } while (listOf(GlobalScope.localNamespaces, GlobalScope.libNamespaces, GlobalScope.stdNamespaces)
                .any { it[Project.currNamespace]?.scope?.functions?.containsKey(identifier) == true })
        return NoStackFunction(identifier, parent).also {
            it.scope = top.mcfpp.model.scope.FunctionScope(parent.scope)
            parent.child.add(it)
            GlobalScope.localNamespaces.getOrPut(it.namespace) { Namespace(it.namespace) }.scope.addFunction(it, false)
        }
    }

    private fun emitControlCall(function: Function): top.mcfpp.analysis.StorageLayout.Scoreboard {
        val result = top.mcfpp.core.lang.MCInt(TempPool.getVarIdentify())
        val score = top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString())
        Function.addCommand(Command("execute store result score ${score.player} ${score.objective} run")
            .build(Commands.function(function)))
        StorageAccess.publishScore(result, score)
        return score
    }

    private fun propagateLoopReturn(score: top.mcfpp.analysis.StorageLayout.Scoreboard) {
        if (loops.isNotEmpty()) {
            Function.addCommand("execute if score ${score.player} ${score.objective} matches 2 run return 2")
        } else {
            val exit = helper("loop_return", Function.currFunction)
            exit.runInFunction {
                Function.currFunction.registerFrameExit()
                Function.addCommand("return 1")
            }
            Function.addCommand(Command("execute if score ${score.player} ${score.objective} matches 2 run")
                .build(Commands.function(exit)))
            Function.addCommand("execute if score ${score.player} ${score.objective} matches 2 run return 1")
        }
    }

    private fun finishBranchPath() {
        if (loops.isEmpty()) {
            Function.currBaseFunction.recordFallthroughExit()
            Function.currFunction.registerFrameExit()
            Function.addCommand("return 1")
        }
        else {
            loops.peekLast().backEdges.add(StorageAccess.flowSnapshot(loops.peekLast().visible))
            Function.addCommand("return 1")
        }
    }

    private fun emitContinuation(branch: Function, condition: Var<*>? = null) {
        fun prefix(value: Var<*>): Command? = when (value) {
            is BaseBool -> Command("execute if").build(value.toCommandPart())
            else -> { LogProcessor.error("A branch condition must be boolean"); null }
        }
        if (top.mcfpp.command.TargetCapabilities.forVersion(Project.config.version)?.functionReturnRun == true) {
            val command = if (condition == null) Command("return run")
                else prefix(condition)?.build("run return run") ?: return
            Function.addCommand(command.build(Commands.function(branch)))
            return
        }
        // Zero denotes an unselected branch; selected break/continue/return are 1/2/3.
        // The wrapper returns this tag after the nested call, so recursion cannot
        // overwrite the selection while the branch is running.
        val wrapper = helper("branch_exit", Function.currFunction)
        wrapper.runInFunction {
            val result = emitControlCall(branch)
            for (code in 0..2) Function.addCommand(
                "execute if score ${result.player} ${result.objective} matches $code run return ${code + 1}")
            Function.addCommand("return 2")
        }
        val result = if (condition == null) emitControlCall(wrapper) else {
            val value = StorageAccess.capture(condition)
            if (value.isError) return
            val result = top.mcfpp.core.lang.MCInt(TempPool.getVarIdentify())
            val score = top.mcfpp.analysis.StorageLayout.Scoreboard(result.name, result.sbObject.toString())
            Function.addCommand("scoreboard players set ${score.player} ${score.objective} 0")
            Function.addCommand((prefix(value) ?: return)
                .build("store result score ${score.player} ${score.objective} run").build(Commands.function(wrapper)))
            StorageAccess.publishScore(result, score)
            score
        }
        for (code in 1..3) Function.addCommand(
            "execute if score ${result.player} ${result.objective} matches $code run return ${code - 1}")
    }

    private fun makeRuntime(value: Var<*>) {
        if (top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(value))
            top.mcfpp.analysis.StorageAccess.materialize(value)
    }

    private fun visitStatements(
        statements: List<mcfppParser.StatementContext>,
        afterBlock: List<mcfppParser.StatementContext> = emptyList()
    ) {
        val previous = followingStatements
        try {
            statements.forEachIndexed { index, statement ->
                if (Function.currFunction.hasReturnStatement || Function.currFunction.isReturned || Function.currFunction.isEnded) return@forEachIndexed
                followingStatements = statements.drop(index + 1) + afterBlock
                visitStatement(statement)
            }
        } finally {
            followingStatements = previous
        }
    }

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
        // Declaration lookup is by the resolved declaration itself. Constructing fake
        // argument values here applies call conversions and cannot represent generic T.
        f = GlobalScope.localNamespaces[Project.currNamespace]!!.scope.functions[
            ctx.functionDeclarationPart().Identifier().text]?.firstOrNull { it.ast === ctx.curlBlock() }
            ?: UnknownFunction(ctx.functionDeclarationPart().Identifier().text)
        Function.currFunction = f
    }

    private fun exitFunctionDeclaration() {
        //函数是否有返回值
        if(Function.currFunction !is Generic<*> && Function.currFunction.returnType !=  MCFPPPrivateType.Void && !Function.currFunction.hasReturnStatement &&
            !SpecializationPolicy.needsStaticErasedBindings(Function.currFunction) && !Function.currFunction.needsActualBinding()){
            LogProcessor.error("Function should return a value: " + Function.currFunction.namespaceID)
        }
        //释放指针
        Function.currFunction = Function.nullFunction
    }

    override fun visitCurlBlock(ctx: mcfppParser.CurlBlockContext): Any? = withCompilationContext(ctx) {
        if(ctx.parent is CompileTimeFuncDeclarationContext) return null
        Function.currFunction.compileBody(context = ctx)
        return null
    }

    fun compileFunctionBody(ctx: mcfppParser.CurlBlockContext?, beforeBody: () -> Unit = {}) {
        if(Function.currFunction !is Generic<*>){
            val function = Function.currFunction
            if (function.needsActualBinding()) return
            if (!function.actualCallBody && SpecializationPolicy.needsStaticErasedBindings(function)) return
            if (function.bodyCompiled || function.bodyBeingCompiled) return
            function.bodyBeingCompiled = true
            try {
                beforeBody()
                val compiled = ctx != null && top.mcfpp.analysis.PrimitiveCompiler.tryCompile(ctx, function)
                if (!compiled) {
                    function.bindIncomingParameters()
                    if (ctx != null) visitStatements(ctx.statement())
                    if (!function.hasReturnStatement && !function.isEnded) function.registerFrameExit()
                }
            } finally {
                function.bodyBeingCompiled = false
                function.bodyCompiled = true
            }
        }
    }

    //泛型函数编译使用的入口
    fun visitCurlBlock(ctx: mcfppParser.CurlBlockContext, function: Function){
        val lastFunction = Function.currFunction
        Function.currFunction = function
        function.bindIncomingParameters()
        visitStatements(ctx.statement())
        Function.currFunction = lastFunction
    }

    /**
     * 变量声明
     * @param ctx the parse tree
     */
    @InsertCommand
    override fun visitFieldDeclaration(ctx: mcfppParser.FieldDeclarationContext):Any? = withCompilationContext(ctx) {
        val declarationErrors = Project.errorCount
        val fieldModifier = ctx.fieldModifier()?.text
        var type = ctx.type()?.let {
            MCFPPType.parseFromContextNotNull(it, Function.currFunction.scope)
        }
        if (type == null && ctx.expression() != null) {
            try {
                type = top.mcfpp.analysis.DeclarationBinding(Function.currFunction, emptyMap()).expression(ctx.expression()).type
            } catch (failure: top.mcfpp.analysis.DeclarationBinding.Failure) {
                LogProcessor.error(failure.message ?: "Cannot bind variable declaration")
                return null
            }
        }
        if (Project.errorCount != declarationErrors) return null
        var init: Var<*>? = null
        if (ctx.expression() != null) {
            Function.addComment(ctx.text)
            init = MCFPPExprVisitor(
                if(type is MCFPPEnumType) type else null
            ).visitExpression(ctx.expression(), if (ctx.type() != null) type else null)
            if (init.isError) return null
        }
        //类型推断
        if(type == null && init == null){
            LogProcessor.error("Variable ${ctx.Identifier().text} must have a type or an initializer")
            return null to null
        }else if(type == null){
            type = init!!.type
        }
        TypeUsage.ordinaryDiagnostic(type, init?.let(StorageAccess::snapshot))?.let {
            LogProcessor.error(it)
            return null
        }
        val `var` = type.buildUnConcrete(ctx.Identifier().text, Function.currFunction)
        if(isInTopStatement){
            `var`.nbtPath = NBTPath.getFileScopePath(`var`)
        }else{
            `var`.nbtPath = NBTPath.getNormalStackPath(`var`)
        }
        //一定是函数变量
        if (Function.currField.containVar(ctx.Identifier().text)) {
            LogProcessor.error("Duplicate defined variable:" + ctx.Identifier().text)
        }
        `var`.isConst = fieldModifier == "const"
        `var`.bindDeclaration(forceRuntime = fieldModifier == "dynamic")
        if (fieldModifier == "import") StorageAccess.bindIncomingParameter(`var`)
        val stored = if (fieldModifier == "dynamic" && init != null && !top.mcfpp.analysis.StorageAccess.hasRuntimeRepresentation(init)) {
            `var`.assignedBy(init)
        } else if (isViewInitializer(ctx.expression()) && init?.storageBinding?.view != null && init.type == type) {
            top.mcfpp.analysis.StorageAccess.adapter(type, `var`.identifier, init.storageBinding!!).apply {
                symbol = `var`.symbol
                isConst = `var`.isConst
            }
        } else if (init != null) `var`.assignedBy(init) else `var`
        Function.currField.putVar(`var`.identifier, stored, true)
        if (fieldModifier == "dynamic" && !stored.isError) makeRuntime(stored)
        return null
    }

    private fun isViewInitializer(expression: ParseTree?): Boolean = when (expression) {
        null -> false
        is mcfppParser.CastExpressionContext -> expression.type() != null ||
            isViewInitializer(expression.unaryExpression())
        is mcfppParser.BucketExpressionContext -> isViewInitializer(expression.expression())
        else -> expression.childCount == 1 && isViewInitializer(expression.getChild(0))
    }

    /**
     * 一个赋值的语句
     * @param ctx the parse tree
     */
    @InsertCommand
    override fun visitStatementExpression(ctx: mcfppParser.StatementExpressionContext):Any? = withCompilationContext(ctx) {
        Function.addComment("expression: " + ctx.text)
        if(ctx.varWithSelector() != null){
            val left: Var<*> = MCFPPExprVisitor().visitAssignableVarWithSelector(ctx.varWithSelector())
            if (left.isError || left is top.mcfpp.core.lang.UnknownVar) return null
            val type = left.type
            val assignment = ctx.assignmentOperator().text
            val current = if (assignment == "=") null else {
                val value = top.mcfpp.analysis.StorageAccess.read(if (left is PropertyVar) left.get() else left)
                top.mcfpp.analysis.StorageAccess.capture(value)
            }
            val right: Var<*> = MCFPPExprVisitor(
                if(type is MCFPPEnumType) type else null
            ).visitExpression(ctx.expression())
            val operator = assignment.dropLast(1)
            val assigned = when (current) {
                null -> right
                else -> top.mcfpp.analysis.StorageAccess.binary(current, right, operator)
            }
            if (assigned.isError) return null
            TypeUsage.ordinaryDiagnostic(assigned.type, StorageAccess.snapshot(assigned))?.let {
                LogProcessor.error(it)
                return null
            }
            left.replacedBy(left.assignedBy(assigned))
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
        val field = data.scope
        //获取缓存中的对象
        f = field.getFunctionCandidates(ctx.Identifier().text).firstOrNull { it.ast === ctx.curlBlock() }
            ?: UnknownFunction(ctx.Identifier().text)

        Function.currFunction = f
    }

    fun exitExtensionFunctionDeclaration() {
        //函数是否有返回值
        if (Function.currFunction !is Generic<*> && Function.currFunction.returnType != MCFPPPrivateType.Void &&
            !Function.currFunction.needsActualBinding() && !Function.currFunction.hasReturnStatement) {
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
            val diagnostic = TypeUsage.ordinaryDiagnostic(ret.type, StorageAccess.snapshot(ret))
            if (diagnostic != null) LogProcessor.error(diagnostic)
            else Function.currBaseFunction.assignReturnVar(ret)
        }
        // A return terminates this path, not the other branches of the declaration.
        Function.currFunction.hasReturnStatement = true
        if (loops.isEmpty()) {
            Function.currFunction.registerFrameExit()
            Function.addCommand("return 1")
        } else Function.addCommand("return 2")
        return null
    }

    override fun visitBlock(ctx: mcfppParser.BlockContext): Any? = withCompilationContext(ctx) {
        visitBlock(ctx, emptyList())
        return null
    }

    private fun visitBlock(ctx: mcfppParser.BlockContext, afterBlock: List<mcfppParser.StatementContext>) {
        val statements = ctx.curlBlock()?.statement() ?: ctx.children().map { it as mcfppParser.StatementContext }
        visitStatements(statements, afterBlock)
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
        val enclosingCondition = breakIf
        var before = top.mcfpp.analysis.StorageAccess.flowSnapshot(flowValues())
        val paths = arrayListOf<Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>>()
        fun restoreBranch() {
            top.mcfpp.analysis.StorageAccess.restoreFlow(before)
        }
        fun recordBranch() {
            if (Function.currFunction.hasReturnStatement || Function.currFunction.isReturned) return
            val state = top.mcfpp.analysis.StorageAccess.flowSnapshot(flowValues())
            paths.add(state)
        }
        try {
        //进入if函数
        breakIf = ConditionType.NORMAL
        Function.addComment("if start")
        val continuation = followingStatements
        val returningPaths = ArrayList<Boolean>()
        do {
            //if分支
            val (c,f) = enterIfBranch(ctx)
            before = top.mcfpp.analysis.StorageAccess.flowSnapshot(flowValues())
            if(c){
                restoreBranch()
                //此分支会被编译，注册函数
                if(breakIf != ConditionType.ALWAYS_TRUE) {
                    //并不是必然编译的，所以需要注册函数，让分支内的内容在if_branch函数中执行。如果是必然执行的，那么直接内联即可
                    if (!GlobalScope.localNamespaces.containsKey(f.namespace)) {
                        GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                    }
                    GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f, false)
                    Function.currFunction = f
                }
                visitBlock(ctx.block(), continuation)
                if (!Function.currFunction.hasReturnStatement && !Function.currFunction.isEnded) {
                    visitStatements(continuation)
                }
                returningPaths.add(Function.currFunction.hasReturnStatement)
                recordBranch()
                if (!Function.currFunction.hasReturnStatement && !Function.currFunction.isEnded) finishBranchPath()
                if(breakIf != ConditionType.ALWAYS_TRUE) {
                    Function.currFunction = Function.currFunction.parent[0]
                }
                Function.addComment("if branch end")
            }
            //上一条分支必然执行，后面的分支都必定不会执行，不需要编译了
            if(breakIf == ConditionType.ALWAYS_TRUE) break
            //else if分支
            ctx.elseIfStatement().forEach {
                restoreBranch()
                val (c2, f2) = enterElseIfBranch(it)
                before = top.mcfpp.analysis.StorageAccess.flowSnapshot(flowValues())
                if(c2){
                    //这条else if分支会被编译
                    if(breakIf != ConditionType.ALWAYS_TRUE) {
                        //如果这里breakIf是1，只能是这一条分支必然会被执行而上一条分支不可能执行（参见enterElseIfBranch中的逻辑
                        //而不为1的时候，说明它只能是0，因为如果是-1的话c2也会是false而不能执行到这里
                        //所以让我们注册函数
                        if (!GlobalScope.localNamespaces.containsKey(f2.namespace))
                            GlobalScope.localNamespaces[f2.namespace] = Namespace(f2.namespace)
                        GlobalScope.localNamespaces[f2.namespace]!!.scope.addFunction(f2, false)
                        Function.currFunction = f2
                    }
                    visitBlock(it.block(), continuation)
                    if (!Function.currFunction.hasReturnStatement && !Function.currFunction.isEnded) {
                        visitStatements(continuation)
                    }
                    returningPaths.add(Function.currFunction.hasReturnStatement)
                    recordBranch()
                    if (!Function.currFunction.hasReturnStatement && !Function.currFunction.isEnded) finishBranchPath()
                    if(breakIf != ConditionType.ALWAYS_TRUE) {  //这里同理
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
            restoreBranch()
            Function.addComment("else branch start")
            if(breakIf != ConditionType.ALWAYS_FALSE){
                //注册函数
                val f3 = NoStackFunction(TempPool.getFunctionIdentify("else_branch"), Function.currFunction)
                if (!GlobalScope.localNamespaces.containsKey(f3.namespace))
                    GlobalScope.localNamespaces[f3.namespace] = Namespace(f3.namespace)
                GlobalScope.localNamespaces[f3.namespace]!!.scope.addFunction(f3, false)
                //同样的，外层定义域中的变量可能丢失跟踪，这里处理为强制全部丢失跟踪
                Function.currFunction.scope.forEachVar {
                    makeRuntime(it)
                }
                emitContinuation(f3)
                Function.currFunction = f3
            }
            if(ctx.elseStatement() != null){
                visitBlock(ctx.elseStatement().block(), continuation)
            }
            if(!Function.currFunction.hasReturnStatement && !Function.currFunction.isEnded){
                visitStatements(continuation)
            }
            returningPaths.add(Function.currFunction.hasReturnStatement)
            recordBranch()
            if (!Function.currFunction.hasReturnStatement && !Function.currFunction.isEnded) finishBranchPath()
            if(breakIf != ConditionType.ALWAYS_FALSE){
                Function.currFunction = Function.currFunction.parent[0]
            }
            Function.addComment("else branch end")
        }while (false)
        top.mcfpp.analysis.StorageAccess.restoreFlow(if (paths.isNotEmpty())
            top.mcfpp.analysis.StorageAccess.joinFlow(paths) else top.mcfpp.analysis.StorageAccess.exitFlow(before))
        Function.addComment("if end")
        //if以后的语句已经被全部打包到if分支里面，所以if语句之后的statement没有意义
        Function.currFunction.isEnded = true
        Function.currFunction.hasReturnStatement = returningPaths.isNotEmpty() && returningPaths.all { it }
        return null
        } finally {
            breakIf = enclosingCondition
        }
    }

    /**
     * 检查条件，返回是否会编译这条if分支
     */
    private fun flowValues(): List<Var<*>> {
        val values = StorageAccess.visibleValues(Function.currFunction.scope)
        val base = Function.currBaseFunction
        return if (base.returnType == MCFPPPrivateType.Void) values else values + base.returnVar
    }

    private fun knownCondition(value: Var<*>): Boolean? {
        if (value.symbol?.forceRuntime == true) return null
        return ((StorageAccess.snapshot(value) as? top.mcfpp.analysis.CompilerValue.Typed)?.payload as? top.mcfpp.analysis.CompilerValue.Bool)?.value
    }

    private fun enterIfBranch(ctx: mcfppParser.IfStatementContext): Pair<Boolean, NoStackFunction>{
        Function.addComment("if branch start")
        //匿名函数的定义
        val f = NoStackFunction(TempPool.getFunctionIdentify("if_branch"), Function.currFunction)
        val expr = ctx.bucketExpression().expression()
        if(expr == null){
            LogProcessor.error("The condition of if statement is null.")
            return false to f
        }
        val exp = MCFPPExprVisitor().visit(expr)
        knownCondition(exp)?.let { condition ->
                if (condition) {
                    // visitIfStatement inlines a branch that is known to run.
                    //LogProcessor.warn("The condition is always true. ")
                    breakIf = ConditionType.ALWAYS_TRUE
                } else {
                    Function.addComment("function " + f.namespaceID)
                    //LogProcessor.warn("The condition is always false. ")
                    breakIf = ConditionType.ALWAYS_FALSE
                }
                return condition to f
        }
        when(exp){
            is BaseBool -> {
                //注册函数
                if(!GlobalScope.localNamespaces.containsKey(f.namespace))
                    GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
                Function.currFunction.scope.forEachVar { makeRuntime(it) }
                StorageAccess.flush(StorageAccess.visibleValues(Function.currFunction.scope))
                emitContinuation(f, exp)
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
        val exp = MCFPPExprVisitor().visit(expr)
        knownCondition(exp)?.let { condition ->
                if (condition) {
                    // A runtime chain registers this branch; a static chain inlines it.
                    if (breakIf == ConditionType.NORMAL) {
                        Function.currFunction.scope.forEachVar { makeRuntime(it) }
                        emitContinuation(f)
                    }
                    //LogProcessor.warn("The condition is always true. ")
                    breakIf = if(breakIf == ConditionType.NORMAL) ConditionType.NORMAL else ConditionType.ALWAYS_TRUE
                } else {
                    Function.addComment("function " + f.namespaceID)
                    //LogProcessor.warn("The condition is always false. ")
                    breakIf = if(breakIf == ConditionType.NORMAL) ConditionType.NORMAL else ConditionType.ALWAYS_FALSE
                }
                return condition to f
        }
        when(exp){
            is BaseBool -> {
                //注册函数
                if(!GlobalScope.localNamespaces.containsKey(f.namespace))
                    GlobalScope.localNamespaces[f.namespace] = Namespace(f.namespace)
                GlobalScope.localNamespaces[f.namespace]!!.scope.addFunction(f,false)
                Function.currFunction.scope.forEachVar { makeRuntime(it) }
                emitContinuation(f, exp)
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
        compileLoop(ctx.block(), { MCFPPExprVisitor().visitExpression(ctx.bucketExpression().expression()) }, false)
        return null
    }

    private fun bindIterationVariable(name: String, element: Var<*>, body: Function) {
        val variable = element.type.buildUnConcrete(name, body)
        variable.bindDeclaration(name)
        StorageAccess.ensure(variable)
        StorageAccess.write(variable, element)
        body.scope.putVar(name, variable)
    }

    private fun compileLoop(block: BlockContext, condition: () -> Var<*>, bodyFirst: Boolean,
                            captured: List<Var<*>> = emptyList(), iteration: Pair<String, () -> Var<*>>? = null,
                            advance: (() -> Unit)? = null) {
        val caller = Function.currFunction
        val visible = flowValues() + captured
        visible.forEach { StorageAccess.ensure(it) }
        val entry = StorageAccess.flowSnapshot(visible)
        val loop = LoopContext(visible, entry)
        val header = helper("loop_header", caller)
        val body = helper("loop_body", header)
        val step = helper("loop_step", header)
        StorageAccess.restoreFlow(StorageAccess.widenLoop(entry))
        var known: Boolean? = null
        var naturalExit: Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>? = null
        fun compileHeader() = header.runInFunction {
            val value = condition()
            known = knownCondition(value)
            if (known != true) naturalExit = StorageAccess.flowSnapshot(visible)
            if (known != false) {
                if (known == null) {
                    val falseCondition = StorageAccess.unary(value, "!")
                    val command = when (falseCondition) {
                        is BaseBool -> Command("execute if").build(falseCondition.toCommandPart())
                        else -> { LogProcessor.error("The loop condition must be boolean"); null }
                    }
                    if (command != null) Function.addCommand(command.build("run return 0"))
                }
                val result = emitControlCall(step)
                Function.addCommand("execute if score ${result.player} ${result.objective} matches 2 run return 2")
            }
            Function.addCommand("return 0")
        }
        if (!bodyFirst) compileHeader()
        if (known != false || bodyFirst) {
            loops.addLast(loop)
            try {
                body.runInFunction {
                    iteration?.let { bindIterationVariable(it.first, it.second(), body) }
                    visitBlock(block)
                    if (!Function.currFunction.isEnded && !Function.currFunction.hasReturnStatement) finishBranchPath()
                }
            } finally { loops.removeLast() }
            if (bodyFirst) {
                if (loop.backEdges.isNotEmpty()) {
                    StorageAccess.restoreFlow(StorageAccess.joinFlow(loop.backEdges))
                    compileHeader()
                }
                else header.runInFunction { Function.addCommand("return 0") }
            }
            step.runInFunction {
                val result = emitControlCall(body)
                Function.addCommand("execute if score ${result.player} ${result.objective} matches 2 run return 2")
                Function.addCommand("execute if score ${result.player} ${result.objective} matches 0 run return 0")
                advance?.invoke()
                val next = emitControlCall(header)
                Function.addCommand("execute if score ${next.player} ${next.objective} matches 2 run return 2")
                Function.addCommand("return 0")
            }
        } else step.runInFunction { Function.addCommand("return 0") }
        StorageAccess.restoreFlow(StorageAccess.finishLoop(entry,
            if (known == false) emptyList() else loop.backEdges,
            loop.breakExits,
            maySkip = !bodyFirst || known != true,
            naturalExits = listOfNotNull(naturalExit)))
        val result = emitControlCall(if (bodyFirst) step else header)
        propagateLoopReturn(result)
    }

    override fun visitDoWhileStatement(ctx: mcfppParser.DoWhileStatementContext): Any? = withCompilationContext(ctx) {
        compileLoop(ctx.block(), { MCFPPExprVisitor().visitExpression(ctx.bucketExpression().expression()) }, true)
        return null
    }

//endregion

    /**
     * 使用原版命令
     */
    @InsertCommand
    override fun visitOrgCommand(ctx: mcfppParser.OrgCommandContext):Any? = withCompilationContext(ctx) {
        val observed = top.mcfpp.analysis.StorageAccess.visibleValues(Function.currField)
        top.mcfpp.analysis.StorageAccess.flush(observed)
        val command = Command()
        for (content in ctx.orgCommandContent()){
            if(content.OrgCommandText() != null){
                command.build(content.OrgCommandText().text)
            }else{
                val exp = MCFPPExprVisitor().visitExpression(content.orgCommandExpression().expression())
                command.build(exp.toCommandPart(), false)
            }
        }
        Function.addCommands(command.buildMacroFunction())
        val literalCommand = ctx.orgCommandContent().takeIf { contents ->
            contents.all { it.OrgCommandText() != null }
        }?.joinToString("") { it.OrgCommandText().text }
        val effect = literalCommand?.let(top.mcfpp.analysis.EffectAnalysis::rawCommandEffect)
            ?: top.mcfpp.analysis.Effect.Unknown
        top.mcfpp.analysis.EffectAnalysis.recordEffect(effect)
        if (effect == top.mcfpp.analysis.Effect.Unknown) {
            top.mcfpp.analysis.StorageAccess.barrier(observed)
        }
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
        if (loops.isEmpty()) {
            LogProcessor.error("'continue' or 'break' can only be used in loop statements.")
            return null
        }
        Function.addComment(ctx.text)
        val loop = loops.peekLast()
        val state = StorageAccess.flowSnapshot(loop.visible)
        //return语句
        if(ctx.BREAK() != null){
            loop.breakExits.add(state)
            //break，完全跳出while
            Function.addCommand("return 0")
        }else{
            loop.backEdges.add(state)
            //continue，跳过当次
            Function.addCommand("return 1")
        }
        Function.currFunction.isReturned = true
        Function.currFunction.isEnded = true
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
        val iterable = MCFPPExprVisitor().visitExpression(ctx.expression())
        if (iterable is RangeVar) {
            val bounds = StorageAccess.rangeIterationBounds(iterable) ?: return null
            val first = StorageAccess.capture(bounds.first)
            val last = StorageAccess.capture(bounds.second)
            if (first.isError || last.isError) return null
            val index = MCFPPBaseType.Int.buildUnConcrete(id, Function.currFunction)
            StorageAccess.ensure(index)
            StorageAccess.write(index, first)
            compileLoop(ctx.block(), { StorageAccess.binary(index, last, "<=") }, false,
                captured = listOf(index, last), iteration = id to { StorageAccess.capture(index) }, advance = {
                    val atEnd = StorageAccess.binary(index, last, ">=")
                    val command = when (atEnd) {
                        is BaseBool -> Command("execute if").build(atEnd.toCommandPart())
                        else -> null
                    }
                    if (command != null) Function.addCommand(command.build("run return 0"))
                    StorageAccess.write(index, StorageAccess.binary(index,
                        StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(1)), "+"))
                })
            return null
        }
        val captured = StorageAccess.capture(iterable)
        if (captured.isError) return null
        StorageAccess.closedIterationElements(captured)?.let { elements ->
            val caller = Function.currFunction
            val visible = flowValues() + captured
            visible.forEach { StorageAccess.ensure(it) }
            val entry = StorageAccess.flowSnapshot(visible)
            val exits = arrayListOf<Map<top.mcfpp.analysis.StoredData, top.mcfpp.analysis.FlowFacts>>()
            val sequence = helper("foreach_sequence", caller)
            var incoming = listOf(entry)
            for (element in elements) {
                if (incoming.isEmpty()) break
                StorageAccess.restoreFlow(StorageAccess.joinFlow(incoming))
                val loop = LoopContext(visible, StorageAccess.flowSnapshot(visible))
                val body = helper("foreach_element", sequence)
                loops.addLast(loop)
                try {
                    body.runInFunction {
                        bindIterationVariable(id, element, body)
                        visitBlock(ctx.block())
                        if (!Function.currFunction.isEnded && !Function.currFunction.hasReturnStatement) finishBranchPath()
                    }
                } finally { loops.removeLast() }
                exits.addAll(loop.breakExits)
                incoming = loop.backEdges
                sequence.runInFunction {
                    val result = emitControlCall(body)
                    Function.addCommand("execute if score ${result.player} ${result.objective} matches 2 run return 2")
                    Function.addCommand("execute if score ${result.player} ${result.objective} matches 0 run return 0")
                }
            }
            exits.addAll(incoming)
            StorageAccess.restoreFlow(if (exits.isEmpty()) StorageAccess.exitFlow(entry) else StorageAccess.joinFlow(exits))
            sequence.runInFunction { Function.addCommand("return 0") }
            propagateLoopReturn(emitControlCall(sequence))
            return null
        }
        val length = StorageAccess.iterationLength(captured) ?: return null
        val index = top.mcfpp.core.lang.MCInt(TempPool.getVarIdentify())
        StorageAccess.ensure(index)
        StorageAccess.write(index, StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(0)))
        compileLoop(ctx.block(), { StorageAccess.binary(index, length, "<") }, false,
            captured = listOf(index, length, captured), iteration = id to {
                StorageAccess.capture(StorageAccess.iterationElement(captured, index))
            }, advance = {
                StorageAccess.write(index, StorageAccess.binary(index,
                    StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(1)), "+"))
            })
        return null
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
