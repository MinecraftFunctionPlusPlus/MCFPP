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
    var defaultVar: Var<*>? = null,
    var defaultContext: top.mcfpp.antlr.mcfppParser.ValueContext? = null
): ModelInfo<FunctionParam>{
    override fun get(): FunctionParam {
        return FunctionParam(type, identifier, AbstractFunctionInfo.currFunction!!, isStatic, hasDefault, isReadOnly).apply {
            this.defaultVar = this@FunctionParamInfo.defaultVar
            this.defaultContext = this@FunctionParamInfo.defaultContext
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
                param.defaultVar,
                param.defaultContext
            )
        }
    }

}
data class DataTemplateParamInfo(
    var identifier: String,
    var type: MCFPPType,
    val typeIdentifier: String,
    val variance: top.mcfpp.model.compound.DeclarationVariance = top.mcfpp.model.compound.DeclarationVariance.INVARIANT
): ModelInfo<DataTemplateParam> {
    override fun get(): DataTemplateParam {
        return DataTemplateParam(typeIdentifier, identifier, type, variance)
    }

    companion object {
        fun from(param: DataTemplateParam): DataTemplateParamInfo {
            return DataTemplateParamInfo(
                param.identifier,
                param.type!!,
                param.typeIdentifier,
                param.variance
            )
        }
    }
}
