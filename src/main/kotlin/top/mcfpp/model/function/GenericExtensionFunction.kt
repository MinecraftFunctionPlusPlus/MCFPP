package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.util.LogProcessor

class GenericExtensionFunction: ExtensionFunction, Generic<ExtensionFunction> {

    override val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    override val compiledFunctions: HashMap<top.mcfpp.analysis.SpecializationKey, Function> = HashMap()

    /**
     * 创建一个函数
     * @param name 函数的标识符
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(name: String, owner: CompoundData, namespace: String = Project.currNamespace, ctx: mcfppParser.CurlBlockContext):super(name, owner, namespace, ctx)

    override fun invoke(readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, caller: CanSelectMember?): Var<*> {
        return invoke(mapReadonlyArgs(readOnlyArgs), mapNormalArgs(normalArgs), caller)
    }

    private fun invoke(readOnlyArgs: LinkedHashMap<String, Var<*>>, normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        return compile(completeDefaultValue((readOnlyArgs + normalArgs) as LinkedHashMap)).let {(k, v) -> k.invoke(v, caller)}
    }

    /**
     * 补全缺省参数
     */
    override fun completeDefaultValue(args: LinkedHashMap<String, Var<*>>): LinkedHashMap<String, Var<*>>{
        val completedArgs = LinkedHashMap<String, Var<*>>()
        for (p in readOnlyParams){
            completedArgs[p.identifier] = args[p.identifier]?:p.defaultVar!!
        }
        for (p in normalParams){
            completedArgs[p.identifier] = args[p.identifier]?:p.defaultVar!!
        }
        return completedArgs
    }

    override fun paramCount(): Int {
        return normalParams.size + readOnlyParams.size
    }
    
    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams().parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r.parameter()){
            val (p,v) = parseParam(param, isReadOnly = true)
            readOnlyParams.add(p)
            if(v !is MCFPPValue<*>){
                LogProcessor.error("ReadOnly params must have a concrete value")
                throw Exception()
            }
            scope.putVar(p.identifier, v)
            if (p.type == top.mcfpp.type.MCFPPConcreteType.Type)
                scope.putType(p.identifier, top.mcfpp.type.MCFPPGenericParamType(p.identifier, arrayListOf()))
        }
        hasDefaultValue = false
        for (param in n.parameter()) {
            var (p,v) = parseParam(param)
            normalParams.add(p)
            if(v is MCFPPValue<*>) v = v.toDynamic(false)
            scope.putVar(p.identifier, v)
        }
    }

    override fun compile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>> =
        SpecializationPolicy.compileGeneric(this, readOnlyParams, args)

    override fun isSelf(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean =
        ParameterMatcher.accepts(this, key, readOnlyArgs, normalArgs, false)

    override fun isSelfWithDefaultValue(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean =
        ParameterMatcher.accepts(this, key, readOnlyArgs, normalArgs, true)
}
