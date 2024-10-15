package top.mcfpp.model.generic

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.CanSelectMember
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.util.LogProcessor

class GenericExtensionFunction: ExtensionFunction, Generic<ExtensionFunction> {

    override lateinit var ctx: mcfppParser.FunctionBodyContext

    override var index: Int = 0

    override val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    override val compiledFunctions: HashMap<List<Any?>, Function> = HashMap()

    /**
     * 创建一个函数
     * @param name 函数的标识符
     */
    constructor(name: String, owner: CompoundData, namespace: String = Project.currNamespace, ctx: mcfppParser.FunctionBodyContext):super(name, owner, namespace, ctx)
    override fun invoke(readOnlyArgs: ArrayList<Var<*>>, normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?): Var<*> {
        val f = compile(readOnlyArgs)
        return f.invoke(normalArgs, caller)
    }

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams().parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r.parameter()){
            val (p,v) = parseParam(param)
            readOnlyParams.add(p)
            if(v !is MCFPPValue<*>){
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

    override fun compile(readOnlyArgs: ArrayList<Var<*>>): ExtensionFunction {
        if(compiledFunctions.containsKey(readOnlyArgs)){
            return (compiledFunctions[readOnlyArgs.map { (it as MCFPPValue<*>).value }] as ExtensionFunction?)!!
        }
        //创建新的函数
        val compiledFunction : ExtensionFunction = when(ownerType){
            Companion.OwnerType.CLASS -> {
                ExtensionFunction("${identifier}_${index}", owner as Class, namespace, ctx)
            }
            //TODO Template的拓展方法好像还没做欸
            Companion.OwnerType.TEMPLATE -> {
                TODO()
            }
            Companion.OwnerType.BASIC -> {
                ExtensionFunction("${identifier}_${index}", owner as CompoundData, namespace, ctx)
            }
            else -> {
                //拓展函数必定有成员
                throw Exception()
            }
        }
        compiledFunction.returnType = returnType
        //传递普通参数
        for (i in normalParams.indices) {
            val r = field.getVar(normalParams[i].identifier)!!
            compiledFunction.field.putVar(normalParams[i].identifier, r, false)
        }
        //传递只读参数
        for (i in readOnlyArgs.indices) {
            val r = field.getVar(readOnlyParams[i].identifier)
            compiledFunction.field.putVar(readOnlyParams[i].identifier, r!!.assignedBy(readOnlyArgs[i]), false)
        }
        index ++
        //编译这个函数
        currFunction = compiledFunction
        val visitor = MCFPPImVisitor()
        visitor.visit(ctx)
        currFunction = nullFunction
        //注册这个函数
        GlobalField.localNamespaces[namespace]!!.field.addFunction(compiledFunction, false)
        return compiledFunction
    }

    override fun isSelf(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean {
        if (this.identifier == key && this.normalParams.size == normalArgs.size && this.readOnlyParams.size == readOnlyArgs.size) {
            if (this.normalParams.size == 0 && this.readOnlyParams.size == 0) {
                return true
            }
            var hasFoundFunc = true
            //参数比对
            for (i in normalArgs.indices) {
                if (!field.getVar(normalParams[i].identifier)!!.canAssignedBy(normalArgs[i])) {
                    hasFoundFunc = false
                    break
                }
            }
            if(hasFoundFunc){
                for (i in readOnlyArgs.indices) {
                    if (!field.getVar(readOnlyParams[i].identifier)!!.canAssignedBy(readOnlyArgs[i])) {
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


    override fun isSelfWithDefaultValue(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean {
        if(key != this.identifier || normalArgs.size > this.normalParams.size || readOnlyArgs.size > this.normalParams.size) return false
        if (this.normalParams.size == 0 && this.readOnlyParams.size == 0) {
            return true
        }
        var hasFoundFunc = true
        //参数比对
        var index = 0
        while (index < normalArgs.size) {
            if (!field.getVar(normalParams[index].identifier)!!.canAssignedBy(normalArgs[index])) {
                hasFoundFunc = false
                break
            }
            index++
        }
        hasFoundFunc = hasFoundFunc && this.normalParams[index].hasDefault
        if(!hasFoundFunc) return false
        index = 0
        while (index < readOnlyArgs.size) {
            if (!field.getVar(readOnlyParams[index].identifier)!!.canAssignedBy(readOnlyArgs[index])) {
                hasFoundFunc = false
                break
            }
            index++
        }
        return hasFoundFunc && this.readOnlyParams[index].hasDefault
    }

}