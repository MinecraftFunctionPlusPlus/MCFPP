package top.mcfpp.model.function

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.FunctionBodyContext
import top.mcfpp.core.lang.DataTemplateObject
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPType
import java.util.*

class DataTemplateConstructor(val data: DataTemplate, ctx: FunctionBodyContext?): Function(
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

    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?): Var<*> {
        if(ast == null) return caller as DataTemplateObject
        field.putVar("this", caller as DataTemplateObject, true)
        normalArgs.add(0, caller)
        //初始化
        for ((k, v) in data.preInit) {
            val init = MCFPPExprVisitor().visitExpression(v)
            val field = DataTemplate.getField(caller, k)!!
            field.replacedBy(field.assignedBy(init))
        }
        super.invoke(normalArgs, caller as CanSelectMember?)
        return caller
    }

    override fun compile(args: List<Var<*>>): Function {
        //第一个参数是this，需要去除
        return super.compile(args.subList(1, args.size))
    }
}

