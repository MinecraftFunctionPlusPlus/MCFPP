package top.mcfpp.model.function

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPImVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.ObjectVar
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.Utils.addFirst
import top.mcfpp.util.Utils.subMap
import java.util.*

open class DataTemplateConstructor(val data: DataTemplate, ctx: CurlBlockContext?): Function(
    "_init_" + data.identifier.lowercase(Locale.getDefault()) + "_" + data.constructors.size,
    data,
    ctx
) {

    lateinit var file: MCFPPFile

    fun addParamsFromContext(ctx: mcfppParser.NormalParamsContext) {
        val n = ctx.parameterList()?:return
        for (param in n.parameter()) {
            val (p,v) = parseParam(param)
            normalParams.add(p)
            scope.putVar(p.identifier, v)
        }
    }

    fun isSelf(d: DataTemplate, normalParams: List<MCFPPType>) : Boolean{
        if (this.data == d && this.normalParams.size == normalParams.size) {
            if (this.normalParams.size == 0) {
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
            return hasFoundFunc
        }else{
            return false
        }
    }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        if(caller != null){
            //not an object
            scope.putVar("this", caller as DataTemplateObject, true)
            normalArgs.addFirst("this", caller)
            if(ast == null) return caller
            super.invoke(normalArgs, caller)
            return caller
        }else{
            //init an object
            return super.invoke(normalArgs, null)
        }
    }

    override fun compile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>> {
        return if(args.containsKey("this")){
            //第一个参数是this，需要去除
            superCompile(args.subMap(1, args.size))
        }else{
            superCompile(args)
        }
    }

    private fun superCompile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>>{
        //函数参数已知条件下的编译
        val values = args.values.map { if (it is MCFPPValue<*>) it.value else null }
        val argList = args.values.toList()
        compiledFunctions[values]?.let { return it to LinkedHashMap(args.filter { e -> e.value !is MCFPPValue<*> }) }
        val cf = Function(this)
        //替换变量
        for (i in values.indices) {
            if (values[i] != null) {
                cf.scope.putVar(
                    normalParams[i].identifier,
                    cf.scope.getVar(normalParams[i].identifier)!!.assignedBy(argList[i]),
                    true
                )
            }
        }
        //去除确定的参数
        val params = ArrayList<FunctionParam>(normalParams)
        for (i in argList.indices) {
            if (argList[i] is MCFPPValue<*>) {
                params.remove(normalParams[i])
            }
        }
        cf.normalParams = params
        cf.commands.clear()
        cf.identifier = this.identifier + "_" + compiledFunctions.size
        compiledFunctions[values] = cf
        cf.ast = null
        cf.runInFunction {
            if(data is ObjectDataTemplate){
                val qwq = ObjectVar(data.getType())
                //初始化
                for ((k, v) in data.preInit) {
                    val init = MCFPPExprVisitor().visitExpression(v)
                    qwq.getMemberVar(k, Member.AccessModifier.PRIVATE).first!!.assignedBy(init)
                }
            }else{
                //初始化
                for ((k, v) in data.preInit) {
                    val init = MCFPPExprVisitor().visitExpression(v)
                    DataTemplate.getField(args["this"] as DataTemplateObject, k)!!.assignedBy(init)
                }
            }
            MCFPPImVisitor().visitCurlBlock(ast!!)
        }
        return cf to args.filter { it !is MCFPPValue<*> } as LinkedHashMap
    }
}

