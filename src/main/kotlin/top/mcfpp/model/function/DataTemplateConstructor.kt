package top.mcfpp.model.function

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.DataTemplateObject
import top.mcfpp.model.CompoundData
import top.mcfpp.model.DataTemplate
import top.mcfpp.type.MCFPPType
import java.util.*
class DataTemplateConstructor(val data: DataTemplate): Function("_init_" + data.identifier.lowercase(Locale.getDefault()) + "_" + data.constructors.size, data, false, context = null) {
    init {
        val thisObj = DataTemplateObject(data, "this")
        thisObj.identifier = "this"
        field.putVar("this",thisObj)
    }

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
}

