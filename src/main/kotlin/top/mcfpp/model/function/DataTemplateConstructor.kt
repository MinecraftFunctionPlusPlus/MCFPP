package top.mcfpp.model.function

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.FunctionBodyContext
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.Utils.addFirst
import top.mcfpp.util.Utils.subMap
import java.util.*

open class DataTemplateConstructor(val data: DataTemplate, ctx: FunctionBodyContext?): Function(
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
            field.putVar(p.identifier, v)
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
        if(ast == null) return caller as DataTemplateObject
        field.putVar("this", caller as DataTemplateObject, true)
        normalArgs.addFirst("this", caller)
        //初始化
        for ((k, v) in data.preInit) {
            val init = MCFPPExprVisitor().visitExpression(v)
            val field = DataTemplate.getField(caller, k)!!
            field.replacedBy(field.assignedBy(init))
        }
        super.invoke(normalArgs, caller as CanSelectMember?)
        return caller
    }

    override fun compile(args: LinkedHashMap<String, Var<*>>): Pair<Function, LinkedHashMap<String, Var<*>>> {
        //第一个参数是this，需要去除
        return super.compile(args.subMap(1, args.size))
    }
}

