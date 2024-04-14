package top.mcfpp.antlr

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.lang.*
import top.mcfpp.lib.*
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.field.GlobalField
import top.mcfpp.lib.function.ExtensionFunction
import top.mcfpp.lib.function.FunctionParam
import top.mcfpp.lib.function.UnknownFunction
import top.mcfpp.lib.generic.Generic
import top.mcfpp.lib.generic.GenericClass
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper
import java.util.*
import kotlin.collections.ArrayList

class McfppLeftExprVisitor : mcfppParserBaseVisitor<Var<*>?>(){
    private var currSelector : CanSelectMember? = null

    /**
     * 计算一个基本的表达式。可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitBasicExpression(ctx: mcfppParser.BasicExpressionContext): Var<*>? {
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
    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var<*>? {
        Project.ctx = ctx
        currSelector = if(ctx.primary() != null){
            visit(ctx.primary())
        }else{
            if(ctx.type().className() != null){
                //ClassName
                val clsstr = StringHelper.splitNamespaceID(ctx.type().text)
                val qwq: Class? = GlobalField.getClass(clsstr.first, clsstr.second)
                if (qwq == null) {
                    LogProcessor.error("Undefined class:" + ctx.type().className().text)
                    return UnknownVar("${ctx.type().className().text}_type_" + UUID.randomUUID())
                }
                qwq.getType()
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
    override fun visitSelector(ctx: mcfppParser.SelectorContext?): Var<*>? {
        currSelector = visit(ctx!!.`var`())!!.getTempVar()
        return null
    }

    /**
     * 一个初级表达式，可能是一个变量，也可能是一个数值
     * @param ctx the parse tree
     * @return 表达式的值
     */
    @Override
    override fun visitPrimary(ctx: mcfppParser.PrimaryContext): Var<*>? {
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
        } else {
            //this或者super
            val s = if(ctx.SUPER() != null){
                "super"
            }else{
                "this"
            }
            val re: Var<*>? = Function.field.getVar(s)
            if (re == null) {
                LogProcessor.error("$s can only be used in member functions.")
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
    override fun visitVar(ctx: mcfppParser.VarContext): Var<*>? {
        Project.ctx = ctx
        return if (ctx.Identifier() != null && ctx.arguments() == null) {
            // Identifier identifierSuffix*
            if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
                //没有数组选取
                val qwq: String = ctx.Identifier().text
                if(currSelector == null){
                    val re: Var<*>? = Function.field.getVar(qwq)
                    if (re == null) {
                        LogProcessor.error("Undefined variable:$qwq")
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
                    }else if(Function.currFunction !is ExtensionFunction && Function.currFunction.ownerType == Function.Companion.OwnerType.TEMPLATE){
                        Function.currFunction.parentStruct()!!.getAccess(cpd)
                    }else{
                        Member.AccessModifier.PUBLIC
                    }
                    val re  = currSelector!!.getMemberVar(qwq,am)
                    if (re.first == null) {
                        LogProcessor.error("Undefined variable:$qwq")
                    }
                    if (!re.second){
                        LogProcessor.error("Cannot access member $qwq")
                    }
                    re.first
                }
            } else {
                TODO("数组调用")
            }
        } else if (ctx.expression() != null) {
            // '(' expression ')'
            visit(ctx.expression())
        } else {
            //函数的调用
            Function.addCommand("#" + ctx.text)
            //参数获取
            val normalArgs: ArrayList<Var<*>> = ArrayList()
            val readOnlyArgs: ArrayList<Var<*>> = ArrayList()
            val exprVisitor = McfppExprVisitor()
            for (expr in ctx.arguments().readOnlyArgs()?.expressionList()?.expression()?: emptyList()) {
                val arg = exprVisitor.visit(expr)!!
                readOnlyArgs.add(arg)
            }
            for (expr in ctx.arguments().normalArgs().expressionList()?.expression()?: emptyList()) {
                val arg = exprVisitor.visit(expr)!!
                normalArgs.add(arg)
            }
            //获取函数
            val p = StringHelper.splitNamespaceID(ctx.namespaceID().text)
            val func = if(currSelector == null){
                GlobalField.getFunction(p.first, p.second, FunctionParam.getArgTypeNames(readOnlyArgs), FunctionParam.getArgTypeNames(normalArgs))
            }else{
                if(p.first != null){
                    LogProcessor.warn("Invalid namespace usage ${p.first} in function call ")
                }
                McfppFuncManager().getFunction(currSelector!!,p.second,
                    FunctionParam.getArgTypeNames(readOnlyArgs),
                    FunctionParam.getArgTypeNames(normalArgs))
            }
            //调用函数
            return if (func is UnknownFunction) {
                //可能是构造函数
                var cls: Class? = GlobalField.getClass(p.first, p.second)
                if (cls == null) {
                    LogProcessor.error("Function " + ctx.text + " not defined")
                    Function.addCommand("[Failed to Compile]${ctx.text}")
                    func.invoke(normalArgs,currSelector)
                    return func.returnVar
                }
                if(cls is GenericClass){
                    //实例化泛型函数
                    cls = cls.compile(readOnlyArgs)
                }
                //获取对象
                val ptr = cls.newInstance()
                //调用构造函数
                val constructor = cls.getConstructor(FunctionParam.getArgTypeNames(normalArgs))
                if (constructor == null) {
                    LogProcessor.error("No constructor like: " + FunctionParam.getArgTypeNames(normalArgs) + " defined in class " + ctx.namespaceID().text)
                    Function.addCommand("[Failed to compile]${ctx.text}")
                }else{
                    constructor.invoke(normalArgs, callerClassP = ptr)
                }
                return ptr
            }else{
                if(func is Generic<*>){
                    func.invoke(readOnlyArgs, normalArgs, currSelector)
                }else{
                    func.invoke(normalArgs,currSelector)
                }
                //函数树
                Function.currFunction.child.add(func)
                func.parent.add(Function.currFunction)
                func.returnVar
            }
        }
    }
}