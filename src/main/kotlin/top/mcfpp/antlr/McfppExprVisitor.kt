package top.mcfpp.antlr

import net.querz.nbt.io.SNBTUtil
import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.*
import top.mcfpp.lang.*
import top.mcfpp.lib.*
import top.mcfpp.lib.Function
import top.mcfpp.util.StringHelper
import top.mcfpp.util.Utils
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

/**
 * 获取表达式结果用的visitor。解析并计算一个形如a+b*c的表达式。
 */
class McfppExprVisitor : mcfppBaseVisitor<Var?>() {

    private val tempVarCommandCache = HashMap<Var, String>()

    var processVarCache : ArrayList<Var> = ArrayList()

    private var currSelector : CanSelectMember? = null

    /**
     * 计算一个复杂表达式
     * @param ctx the parse tree
     * @return 表达式的结果
     */
    @Override
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var? {
        Project.ctx = ctx
        val l = Function.currFunction
        val f = NoStackFunction("expression_${UUID.randomUUID()}",Function.currFunction)
        Function.currFunction = f
        return if(ctx.primary() != null){
            val q = visit(ctx.primary())
            Function.currFunction = l
            l.commands.addAll(f.commands)
            q
        }else{
            val q = visit(ctx.conditionalOrExpression())
            Function.currFunction = l
            if(q !is ReturnedMCBool){
                l.commands.addAll(f.commands)
            }else{
                //注册函数
                if(!GlobalField.localNamespaces.containsKey(f.namespace))
                    GlobalField.localNamespaces[f.namespace] = NamespaceField()
                GlobalField.localNamespaces[f.namespace]!!.addFunction(f,false)
            }
            q
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
        if(ctx.conditionalAndExpression().size != 1){
            val list = ArrayList<ReturnedMCBool>()
            val l = Function.currFunction
            var isConcrete = true
            var result = false
            for (i in 0 until ctx.conditionalAndExpression().size) {
                val temp = NoStackFunction("bool_${UUID.randomUUID()}",Function.currFunction)
                //注册函数
                if(!GlobalField.localNamespaces.containsKey(temp.namespace))
                    GlobalField.localNamespaces[temp.namespace] = NamespaceField()
                GlobalField.localNamespaces[temp.namespace]!!.addFunction(temp,false)
                Function.currFunction = temp
                val b: Var? = visit(ctx.conditionalAndExpression(i))
                Function.currFunction = l
                if (b !is MCBool) {
                    Project.error("The operator \"&&\" cannot be used with ${b!!.type}")
                    throw IllegalArgumentException("")
                }
                if(b.isConcrete && isConcrete){
                    result = result || b.value
                }else{
                    isConcrete = false
                }
            }
            if(isConcrete){
                return MCBool(result)
            }
            for (v in list){
                Function.addCommand("execute if function ${v.parentFunction.namespaceID} run return 1")
            }
            Function.addCommand("return 0")
            return ReturnedMCBool(Function.currFunction)
        }else{
            return visit(ctx.conditionalAndExpression(0))
        }
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
        if(ctx.equalityExpression().size != 1){
            val list = ArrayList<ReturnedMCBool>()
            val l = Function.currFunction
            var isConcrete = true
            var result = true
            for (i in 0 until ctx.equalityExpression().size) {
                val temp = NoStackFunction("bool_${UUID.randomUUID()}",Function.currFunction)
                //注册函数
                if(!GlobalField.localNamespaces.containsKey(temp.namespace))
                    GlobalField.localNamespaces[temp.namespace] = NamespaceField()
                GlobalField.localNamespaces[temp.namespace]!!.addFunction(temp,false)
                Function.currFunction = temp
                val b: Var? = visit(ctx.equalityExpression(i))
                Function.currFunction = l
                if (b !is MCBool) {
                    Project.error("The operator \"&&\" cannot be used with ${b!!.type}")
                    throw IllegalArgumentException("")
                }
                if(b.isConcrete && isConcrete){
                    result = result && b.value
                }else{
                    isConcrete = false
                }
            }
            if(isConcrete){
                return MCBool(result)
            }
            val sb = StringBuilder("execute ")
            for (v in list){
                sb.append("if function ${v.parentFunction.namespaceID} ")
            }
            sb.append("run return 1")
            Function.addCommand(sb.toString())
            Function.addCommand("return 0")
            return ReturnedMCBool(Function.currFunction)
        }else{
            return visit(ctx.equalityExpression(0))
        }
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
        if (ctx.relationalExpression().size != 1) {
            val b: Var? = visit(ctx.relationalExpression(1))
            if(!re!!.isTemp){
                re = re.getTempVar()
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
                Project.error("The operator \"${ctx.relationalOp()}\" cannot be used between ${re!!.type} and ${b!!.type}")
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
        processVarCache.add(visitAdditiveExpressionRe!!)
        for (i in 1 until ctx.multiplicativeExpression().size) {
            var b: Var? = visit(ctx.multiplicativeExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(visitAdditiveExpressionRe!! != MCFloat.ssObj){
                visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.getTempVar()
            }
            visitAdditiveExpressionRe = if (Objects.equals(ctx.op.text, "+")) {
                visitAdditiveExpressionRe!!.plus(b!!)
            } else if (Objects.equals(ctx.op.text, "-")) {
                visitAdditiveExpressionRe!!.minus(b!!)
            } else {
                null
            }
            if(visitAdditiveExpressionRe == null){
                Project.error("The operator \"${ctx.op.text}\" cannot be used between ${visitAdditiveExpressionRe!!.type} and ${b!!.type}.")
                Utils.stopCompile(IllegalArgumentException(""))
                exitProcess(1)
            }
            processVarCache[processVarCache.size - 1] = visitAdditiveExpressionRe!!
        }
        processVarCache.remove(visitAdditiveExpressionRe!!)
        return visitAdditiveExpressionRe
    }

    private var visitMultiplicativeExpressionRe : Var? = null

    /**
     * 计算一个乘除法表达式，例如a * b
     * @param ctx the parse tree
     * @return 表达式的值
     */
    //乘法
    @Override
    override fun visitMultiplicativeExpression(ctx: mcfppParser.MultiplicativeExpressionContext): Var? {
        Project.ctx = ctx
        visitMultiplicativeExpressionRe = visit(ctx.unaryExpression(0))
        processVarCache.add(visitMultiplicativeExpressionRe!!)
        for (i in 1 until ctx.unaryExpression().size) {
            var b: Var? = visit(ctx.unaryExpression(i))
            if(b is MCFloat) b = b.toTempEntity()
            if(visitMultiplicativeExpressionRe != MCFloat.ssObj){
                visitMultiplicativeExpressionRe = visitMultiplicativeExpressionRe!!.getTempVar()
            }
            visitMultiplicativeExpressionRe = when(ctx.op.text){
                "*" -> {
                    visitAdditiveExpressionRe!!.multiple(b!!)
                }
                "/" -> {
                    visitAdditiveExpressionRe!!.divide(b!!)
                }
                "%" -> {
                    visitAdditiveExpressionRe!!.modular(b!!)
                }
                else -> null
            }
            if(visitMultiplicativeExpressionRe == null){
                Project.error("The operator \"${ctx.op.text}\" cannot be used between ${visitMultiplicativeExpressionRe!!.type} and ${b!!.type}.")
                Utils.stopCompile(IllegalArgumentException(""))
                exitProcess(1)
            }
            processVarCache[processVarCache.size - 1] = visitMultiplicativeExpressionRe!!
        }
        processVarCache.remove(visitMultiplicativeExpressionRe!!)
        return visitMultiplicativeExpressionRe
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
                    a = a.getTempVar()
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
                val clsstr = ctx.type().text.split(":")
                val qwq: Class? = if(clsstr.size == 2) {
                    GlobalField.getClass(clsstr[0], clsstr[1])
                }else{
                    GlobalField.getClass(null, clsstr[0])
                }
                if (qwq == null) {
                    Project.error("Undefined class:" + ctx.type().className().text)
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
        for (selector in ctx.selector()){
            visit(selector)
        }
        return currSelector as? Var
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
            //常量
            val valueContext: mcfppParser.ValueContext = ctx.value()
            if (valueContext.INT() != null) {
                return MCInt(Integer.parseInt(valueContext.INT().text))
            } else if (valueContext.STRING() != null) {
                val r: String = valueContext.STRING().text
                return MCString(r.substring(1, r.length - 1))
            } else if (valueContext.FLOAT() != null){
                return MCFloat(valueContext.FLOAT()!!.text.toFloat())
            } else if (valueContext.BOOL() != null){
                return MCBool(valueContext.BOOL()!!.text.toBoolean())
            } else if (valueContext.nbtValue() != null){
                return NBT(SNBTUtil.fromSNBT(valueContext.nbtValue().text))
            }
        } else if(ctx.constructorCall() != null){
            return visit(ctx.constructorCall())
        } else{
            //this或者super
            val re: Var? = Function.currFunction.field.getVar(ctx.text)
            if (re == null) {
                Project.error("${ctx.text} can only be used in member functions.")
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
        if (ctx.Identifier() != null && ctx.arguments() == null) {
            //没有数组选取
            val qwq: String = ctx.Identifier().text
            var re = if(currSelector == null){
                val re: Var? = Function.currFunction.field.getVar(qwq)
                if (re == null) {
                    Project.error("Undefined variable:$qwq")
                    throw Exception()
                }
                re
            }else{
                //获取成员
                val re  = currSelector!!.getMemberVar(qwq, currSelector!!.getAccess(Function.currFunction))
                if (re.first == null) {
                    Project.error("Undefined field: $qwq")
                }
                if (!re.second){
                    Project.error("Cannot access member $qwq")
                }
                re.first
            }
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                return re
            } else {
                if(re is Indexable<*>){
                    for (value in ctx.identifierSuffix()) {
                        val index = visit(value.conditionalExpression())!!
                        re = (re as Indexable<*>).getByIndex(index)
                    }
                }else{
                    throw IllegalArgumentException("Cannot index ${re!!.type}")
                }
                return re
            }
        } else if (ctx.expression() != null) {
            // '(' expression ')'
            return McfppExprVisitor().visit(ctx.expression())
        } else {
            //是函数调用，将已经计算好的中间量存储到栈中
            for (v in processVarCache){
                v.storeToStack()
            }
            //函数的调用
            Function.addCommand("#" + ctx.text)
            //参数获取
            val args: ArrayList<Var> = ArrayList()
            val exprVisitor = McfppExprVisitor()
            if(ctx.arguments().expressionList() != null){
                for (expr in ctx.arguments().expressionList().expression()) {
                    val arg = exprVisitor.visit(expr)!!
                    args.add(arg)
                }
            }
            val p = StringHelper.splitNamespaceID(ctx.namespaceID().text)
            val func = if(currSelector == null){
                McfppFuncVisitor().getFunction(p.first,p.second,FunctionParam.getVarTypes(args))
            }else{
                if(p.first != null){
                    Project.warn("Invalid namespace usage ${p.first} in function call ")
                }
                McfppFuncVisitor().getFunction(currSelector!!,p.second,FunctionParam.getVarTypes(args))
            }
            if (func == null) {
                Project.error("Function " + ctx.text + " not defined")
                throw FunctionNotDefineException()
            }
            func.invoke(args,currSelector)
            for (v in processVarCache){
                v.getFromStack()
            }
            //函数树
            Function.currFunction.child.add(func)
            func.parent.add(Function.currFunction)
            return func.returnVar
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
        constructor.invoke(args, callerClassP = obj.initPointer)
        return obj.initPointer
    }
}