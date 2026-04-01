package top.mcfpp.io.info

import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.type.MCFPPType

data class FunctionParamInfo(
    var identifier: String,
    var type: MCFPPType,
    var isStatic: Boolean = false,
    var hasDefault: Boolean = false,
    var isReadOnly: Boolean = false,
    var defaultVar: Var<*>? = null
): ModelInfo<FunctionParam>{
    override fun get(): FunctionParam {
        return FunctionParam(type, identifier, AbstractFunctionInfo.currFunction!!, isStatic, hasDefault, isReadOnly).apply {
            this.defaultVar = this@FunctionParamInfo.defaultVar
        }
    }

    companion object {
        fun from(param: FunctionParam): FunctionParamInfo {
            return FunctionParamInfo(
                param.identifier,
                param.type,
                param.isStatic,
                param.hasDefault,
                param.isReadOnly,
                param.defaultVar
            )
        }
    }

}
data class DataTemplateParamInfo(
    var identifier: String,
    var type: MCFPPType
): ModelInfo<DataTemplateParam> {
    override fun get(): DataTemplateParam {
        return DataTemplateParam(type.typeName ,identifier, type)
    }

    companion object {
        fun from(param: DataTemplateParam): DataTemplateParamInfo {
            return DataTemplateParamInfo(
                param.identifier,
                param.type!!
            )
        }
    }
}