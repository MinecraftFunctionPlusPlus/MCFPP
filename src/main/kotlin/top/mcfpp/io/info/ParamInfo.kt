package top.mcfpp.io.info

import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.type.MCFPPType

data class FunctionParamInfo(
    var identifier: String,
    var type: MCFPPType,
    var isStatic: Boolean = false,
    var hasDefault: Boolean = false,
    var isReadOnly: Boolean = false,
    var defaultValue: top.mcfpp.analysis.CompilerValue? = null,
    var defaultContext: top.mcfpp.antlr.mcfppParser.ValueContext? = null,
    var defaultTypes: Map<top.mcfpp.type.TypeId, MCFPPType> = emptyMap()
): ModelInfo<FunctionParam>{
    override fun get(): FunctionParam {
        return FunctionParam(type, identifier, AbstractFunctionInfo.currFunction!!, isStatic, hasDefault, isReadOnly).apply {
            this.defaultValue = this@FunctionParamInfo.defaultValue
            this.defaultContext = this@FunctionParamInfo.defaultContext
            this.defaultTypes = this@FunctionParamInfo.defaultTypes
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
                param.defaultValue,
                param.defaultContext,
                param.defaultTypes
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
