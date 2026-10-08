package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.util.LogProcessor

class GenericFunction : Function, Generic<Function> {

    override val readOnlyParams: ArrayList<FunctionParam> = ArrayList()

    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, ctx: mcfppParser.CurlBlockContext) : super(identifier, namespace, ctx)

    /**
     * 创建一个函数，并指定它所属的结构体。
     * @param name 函数的标识符
     */
    constructor(name: String, template: DataTemplate, ctx: mcfppParser.CurlBlockContext) : super(
        name,
        template,
        ctx
    )

    override fun invoke(readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, caller: CanSelectMember?): Var<*> {
        return invoke(mapReadonlyArgs(readOnlyArgs), mapNormalArgs(normalArgs), caller)
    }

    private fun invoke(readOnlyArgs: LinkedHashMap<String, Var<*>>, normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        val completed = completeDefaultValue((readOnlyArgs + normalArgs) as LinkedHashMap, caller)
        val (compiled, arguments) = compile(completed)
        return compiled.invoke(arguments, caller)
    }

    /**
     * 补全缺省参数
     */
    override fun completeDefaultValue(args: LinkedHashMap<String, Var<*>>, receiver: CanSelectMember?,
                                      readonlyBindings: Map<String, Var<*>>): LinkedHashMap<String, Var<*>>{
        val completedReadonly = LinkedHashMap<String, Var<*>>(readonlyBindings)
        for (p in readOnlyParams){
            completedReadonly[p.identifier] = args[p.identifier] ?: readonlyDefault(p, completedReadonly)
                ?: top.mcfpp.core.lang.UnknownVar(p.identifier)
        }
        val normalArgs = LinkedHashMap(args.filterKeys { name -> normalParams.any { it.identifier == name } })
        val completedArgs = LinkedHashMap(completedReadonly)
        completedArgs.putAll(super.completeDefaultValue(normalArgs, receiver, completedReadonly))
        return completedArgs
    }

    override fun addParamsFromContext(ctx: mcfppParser.FunctionParamsContext) {
        val r = ctx.readOnlyParams().parameterList()
        val n = ctx.normalParams().parameterList()
        if(r == null && n == null) return
        for (param in r.parameter()){
            val (p,v) = parseParam(param, isReadOnly = true)
            readOnlyParams.add(p)
            if(v.hasAssigned && v !is MCFPPValue<*>){
                LogProcessor.error("ReadOnly params must have a concrete value")
                throw Exception()
            }
            scope.putVar(p.identifier, v)
            if (p.type == top.mcfpp.type.MCFPPConcreteType.Type)
                scope.putType(p.identifier, top.mcfpp.type.MCFPPGenericParamType(p.identifier, arrayListOf()))
        }
        hasDefaultValue = false
        for (param in n?.parameter()?: emptyList()) {
            var (p,v) = parseParam(param)
            normalParams.add(p)
            if(v is MCFPPValue<*> && p.type.hasRuntimeRepresentation) v = v.toDynamic(false)
            scope.putVar(p.identifier, v)
        }
    }

    override fun paramCount(): Int {
        return normalParams.size + readOnlyParams.size
    }

    override fun buildParamVar() {
        for (param in readOnlyParams){
            scope.putVar(param.identifier, param.buildVar())
        }
        for (param in normalParams){
            if(param.hasDefault){
                scope.putVar(param.identifier, param.buildVar())
            }
        }
    }

    override fun compile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>> =
        SpecializationPolicy.compileGeneric(this, readOnlyParams, args)

    override fun isSelf(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean =
        ParameterMatcher.accepts(this, key, readOnlyArgs, normalArgs, false)

    override fun isSelfWithDefaultValue(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean =
        ParameterMatcher.accepts(this, key, readOnlyArgs, normalArgs, true)
}
