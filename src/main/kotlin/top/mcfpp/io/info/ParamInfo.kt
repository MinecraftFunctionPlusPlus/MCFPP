package top.mcfpp.io.info

import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.generic.ClassParam
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

data class ClassParamInfo(
    var identifier: String,
    var type: MCFPPType
): ModelInfo<ClassParam> {
    override fun get(): ClassParam {
        return ClassParam(type.typeName ,identifier, type)
    }

    companion object {
        fun from(param: ClassParam): ClassParamInfo{
            return ClassParamInfo(
                param.identifier,
                param.type!!
            )
        }
    }
}