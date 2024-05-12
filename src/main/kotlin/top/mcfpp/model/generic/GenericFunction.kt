package top.mcfpp.model.generic

import top.mcfpp.Project
import top.mcfpp.antlr.McfppImVisitor
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.Class
import top.mcfpp.model.Interface
import top.mcfpp.model.Template
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.MCFPPTypeVar
import top.mcfpp.lang.Var
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.util.LogProcessor

class GenericFunction : Function, Generic<Function> {

    override lateinit var ctx: mcfppParser.FunctionBodyContext

    override var index: Int = 0

    override val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    override val compiledFunctions: HashMap<ArrayList<Var<*>>, Function> = HashMap()

    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(identifier, namespace, returnType){
        this.ctx = ctx
    }

    /**
     * 创建一个函数，并指定它所属的类。
     * @param identifier 函数的标识符
     */
    constructor(identifier: String, cls: Class, isStatic: Boolean, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(identifier, cls, isStatic, returnType) {
        this.ctx = ctx
    }

    /**
     * 创建一个函数，并指定它所属的接口。接口的函数总是抽象并且公开的
     * @param identifier 函数的标识符
     */
    constructor(identifier: String, itf: Interface, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(identifier, itf, returnType) {
        this.ctx = ctx
    }

    /**
     * 创建一个函数，并指定它所属的结构体。
     * @param name 函数的标识符
     */
    constructor(name: String, template: Template, isStatic: Boolean, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext) : super(name, template, isStatic, returnType){
        this.ctx = ctx
    }

    override fun invoke(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        val f = compile(readOnlyArgs)
        f.invoke(normalArgs, caller)
    }

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams().parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r?.parameter()?:ArrayList()){
            val param1 = FunctionParam(
                MCFPPType.parseFromContext(param.type(), this.field),
                param.Identifier().text,
                this,
                param.STATIC() != null
            )
            readOnlyParams.add(param1)
        }
        for (param in n?.parameter()?:ArrayList()) {
            val param1 = FunctionParam(
                MCFPPType.parseFromContext(param.type(), this.field),
                param.Identifier().text,
                this,
                param.STATIC() != null
            )
            normalParams.add(param1)
        }
        parseParams()
    }

    /**
     * 解析函数的参数
     */
    override fun parseParams(){
        for (p in readOnlyParams){
            val r = Var.build("_param_" + p.identifier, MCFPPType.parseFromIdentifier(p.typeIdentifier, field), this)
            r.isConcrete = true
            field.putVar(p.identifier, r)
        }
        for (p in normalParams){
            field.putVar(p.identifier, Var.build("_param_" + p.identifier, MCFPPType.parseFromIdentifier(p.typeIdentifier, field), this))
        }
    }

    override fun compile(readOnlyArgs: ArrayList<Var<*>>): Function {
        if(compiledFunctions.containsKey(readOnlyArgs)){
            return compiledFunctions[readOnlyArgs]!!
        }
        //创建新的函数
        val compiledFunction = when(ownerType){
            Companion.OwnerType.NONE -> {
                Function("${identifier}_${index}", namespace, returnType)
            }
            //interface是和Class一起处理的
            Companion.OwnerType.CLASS -> {
                if(owner is Class){
                    Function("${identifier}_${index}", owner as Class, isStatic, returnType)
                }else{
                    Function("${identifier}_${index}", owner as Interface, returnType)
                }
            }
            Companion.OwnerType.TEMPLATE -> {
                Function("${identifier}_${index}", owner as Template, isStatic, returnType)
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
            val r = field.getVar(readOnlyParams[i].identifier)!!
            r.assign(readOnlyArgs[i])
            if(r is MCFPPTypeVar){
                compiledFunction.field.putType(readOnlyParams[i].identifier, r.javaValue!!)
            }
            compiledFunction.field.putVar(readOnlyParams[i].identifier, r, false)
        }
        index ++
        //编译这个函数
        McfppImVisitor().visitFunctionBody(ctx, compiledFunction)
        if(compiledFunction.returnType !=  MCFPPBaseType.Void && !compiledFunction.hasReturnStatement){
            LogProcessor.error("A 'return' expression required in function: " + compiledFunction.namespaceID)
        }
        //注册这个函数
        GlobalField.localNamespaces[namespace]!!.addFunction(compiledFunction, false)
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
}