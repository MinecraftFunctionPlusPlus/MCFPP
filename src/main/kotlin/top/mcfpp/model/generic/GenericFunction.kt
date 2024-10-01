package top.mcfpp.model.generic

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.Class
import top.mcfpp.model.Interface
import top.mcfpp.model.DataTemplate
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.CanSelectMember
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.util.LogProcessor

class GenericFunction : Function, Generic<Function> {

    override lateinit var ctx: mcfppParser.FunctionBodyContext

    override var index: Int = 0

    override val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    override val compiledFunctions: HashMap<List<Any?>, Function> = HashMap()

    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(identifier, namespace, returnType, ctx)

    /**
     * 创建一个函数，并指定它所属的类。
     * @param identifier 函数的标识符
     */
    constructor(identifier: String, cls: Class, isStatic: Boolean, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(identifier, cls, isStatic, returnType, ctx)

    /**
     * 创建一个函数，并指定它所属的接口。接口的函数总是抽象并且公开的
     * @param identifier 函数的标识符
     */
    constructor(identifier: String, itf: Interface, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(identifier, itf, returnType, ctx)

    /**
     * 创建一个函数，并指定它所属的结构体。
     * @param name 函数的标识符
     */
    constructor(name: String, template: DataTemplate, isStatic: Boolean, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(name, template, isStatic, returnType, ctx)

    override fun invoke(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        val f = compile(readOnlyArgs)
        f.invoke(normalArgs, caller)
    }

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams().parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r.parameter()){
            val (p,v) = parseParam(param)
            readOnlyParams.add(p)
            if(v.hasAssigned && v !is MCFPPValue<*>){
                LogProcessor.error("ReadOnly params must have a concrete value")
                throw Exception()
            }
            field.putVar(p.identifier, v)
        }
        hasDefaultValue = false
        for (param in n.parameter()) {
            var (p,v) = parseParam(param)
            normalParams.add(p)
            if(v is MCFPPValue<*>) v = v.toDynamic(false)
            field.putVar(p.identifier, v)
        }
    }

    override fun compile(readOnlyArgs: ArrayList<Var<*>>): Function {
        if(compiledFunctions.containsKey(readOnlyArgs.map { (it as MCFPPValue<*>).value })){
            return compiledFunctions[readOnlyArgs.map { (it as MCFPPValue<*>).value }]!!
        }
        //创建新的函数
        val compiledFunction = when(ownerType){
            Companion.OwnerType.NONE -> {
                Function("${identifier}_${index}", namespace, returnType, ctx)
            }
            //interface是和Class一起处理的
            Companion.OwnerType.CLASS -> {
                if(owner is Class){
                    Function("${identifier}_${index}", owner as Class, isStatic, returnType, ctx)
                }else{
                    Function("${identifier}_${index}", owner as Interface, returnType, ctx)
                }
            }
            Companion.OwnerType.TEMPLATE -> {
                Function("${identifier}_${index}", owner as DataTemplate, isStatic, returnType, ctx)
            }
            Companion.OwnerType.BASIC -> {
                //拓展函数的编译在ExtensionGenericFunction中进行
                nullFunction
            }
        }
        //传递参数信息
        compiledFunction.normalParamTypeList.addAll(normalParamTypeList)
        compiledFunction.normalParams.addAll(normalParams)
        //传递参数变量
        for (i in normalParams.indices) {
            val r = field.getVar(normalParams[i].identifier)!!
            compiledFunction.field.putVar(normalParams[i].identifier, r, false)
        }
        for (i in readOnlyParams.indices) {
            var r = field.getVar(readOnlyParams[i].identifier)!!
            r = r.assignedBy(readOnlyArgs[i])
            if(r is MCFPPTypeVar){
                compiledFunction.field.putType(readOnlyParams[i].identifier, r.value)
            }
            compiledFunction.field.putVar(readOnlyParams[i].identifier, r, false)
        }
        index ++
        //编译这个函数
        MCFPPImVisitor().visitFunctionBody(ctx, compiledFunction)
        if(compiledFunction.returnType !=  MCFPPBaseType.Void && !compiledFunction.hasReturnStatement){
            LogProcessor.error("A 'return' expression required in function: " + compiledFunction.namespaceID)
        }
        //注册这个函数
        GlobalField.localNamespaces[namespace]!!.field.addFunction(compiledFunction, false)
        //传递函数的返回值
        this.returnVar = compiledFunction.returnVar
        return compiledFunction
    }

    override fun isSelf(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>): Boolean {
        if (this.identifier == key && this.normalParams.size == normalParams.size && this.readOnlyParams.size == readOnlyParams.size) {
            if (this.normalParams.size == 0 && this.readOnlyParams.size == 0) {
                return true
            }
            var hasFoundFunc = true
            //参数比对
            for (i in normalParams.indices) {
                if (!FunctionParam.isSubOf(normalParams[i],this.normalParams[i].type)) {
                    hasFoundFunc = false
                    break
                }
            }
            if(hasFoundFunc){
                for (i in readOnlyParams.indices) {
                    if (!FunctionParam.isSubOf(readOnlyParams[i],this.readOnlyParams[i].type)) {
                        hasFoundFunc = false
                        break
                    }
                }
            }
            return hasFoundFunc
        }else{
            return false
        }
    }

    override fun isSelfWithDefaultValue(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>): Boolean {
        if(key != this.identifier || normalParams.size > this.normalParams.size || readOnlyParams.size > this.normalParams.size) return false
        if (this.normalParams.size == 0 && this.readOnlyParams.size == 0) {
            return true
        }
        var hasFoundFunc = true
        //参数比对
        var index = 0
        while (index < normalParams.size) {
            if (!FunctionParam.isSubOf(normalParams[index],this.normalParams[index].type)) {
                hasFoundFunc = false
                break
            }
            index++
        }
        hasFoundFunc = hasFoundFunc && this.normalParams[index].hasDefault
        if(!hasFoundFunc) return false
        index = 0
        while (index < readOnlyParams.size) {
            if (!FunctionParam.isSubOf(readOnlyParams[index],this.readOnlyParams[index].type)) {
                hasFoundFunc = false
                break
            }
            index++
        }
        return hasFoundFunc && this.readOnlyParams[index].hasDefault
    }
}