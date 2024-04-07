package top.mcfpp.lib.function.generic

import top.mcfpp.Project
import top.mcfpp.antlr.McfppImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.Class
import top.mcfpp.lib.CompoundData
import top.mcfpp.lib.field.GlobalField
import top.mcfpp.lib.function.ExtensionFunction
import top.mcfpp.lib.function.FunctionParam

class GenericExtensionFunction: ExtensionFunction, Generic<ExtensionFunction> {

    override lateinit var ctx: mcfppParser.FunctionBodyContext

    override var index: Int = 0

    override val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    override val compiledFunctions: HashMap<ArrayList<Var<*>>, ExtensionFunction> = HashMap()

    /**
     * 创建一个函数
     * @param name 函数的标识符
     */
    constructor(name: String, owner: CompoundData, namespace: String = Project.currNamespace, returnType: MCFPPType = MCFPPBaseType.Void, ctx: mcfppParser.FunctionBodyContext):super(name, owner, namespace, returnType){
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
        for (param in r.parameter()){
            val param1 = FunctionParam(
                param.type().text,
                param.Identifier().text,
                this,
                param.STATIC() != null
            )
            readOnlyParams.add(param1)
        }
        for (param in n.parameter()) {
            val param1 = FunctionParam(
                param.type().text,
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

    override fun compile(readOnlyArgs: ArrayList<Var<*>>): ExtensionFunction {
        if(compiledFunctions.containsKey(readOnlyArgs)){
            return compiledFunctions[readOnlyArgs]!!
        }
        //创建新的函数
        val compiledFunction : ExtensionFunction = when(ownerType){
            Companion.OwnerType.CLASS -> {
                ExtensionFunction("${identifier}_${index}", owner as Class, namespace, returnType)
            }
            //TODO Template的拓展方法好像还没做欸
            Companion.OwnerType.TEMPLATE -> {
                TODO()
            }
            Companion.OwnerType.BASIC -> {
                ExtensionFunction("${identifier}_${index}", owner as CompoundData, namespace, returnType)
            }
            else -> {
                //拓展函数必定有成员
                throw Exception()
            }
        }
        //传递普通参数
        for (i in normalParams.indices) {
            val r = field.getVar(normalParams[i].identifier)!!
            compiledFunction.field.putVar(normalParams[i].identifier, r, false)
        }
        //传递只读参数
        for (i in readOnlyArgs.indices) {
            val r = field.getVar(readOnlyParams[i].identifier)
            r!!.assign(readOnlyArgs[i])
            compiledFunction.field.putVar(readOnlyParams[i].identifier, r, false)
        }
        index ++
        //编译这个函数
        currFunction = compiledFunction
        val visitor = McfppImVisitor()
        visitor.visit(ctx)
        currFunction = nullFunction
        //注册这个函数
        GlobalField.localNamespaces[namespace]!!.addFunction(compiledFunction, false)
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