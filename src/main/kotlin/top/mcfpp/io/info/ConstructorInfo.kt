package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.model.function.DataTemplateConstructor

data class TemplateConstructorInfo(
    val normalParams: List<FunctionParamInfo>,
    val context: CurlBlockContext?
): ModelInfo<DataTemplateConstructor> {
    override fun get(): DataTemplateConstructor {
        val constructor = DataTemplateConstructor(DataTemplateInfo.currTemplate!!, null)
        normalParams.forEach {
            AbstractFunctionInfo.currFunction = constructor
            constructor.normalParams.add(it.get())
        }
        constructor.ast = context
        constructor.buildParamVar()
        return constructor
    }

    companion object {
        fun from(constructor: DataTemplateConstructor): TemplateConstructorInfo {
            return TemplateConstructorInfo(
                constructor.normalParams.map { FunctionParamInfo.from(it) },
                constructor.ast
            )
        }
    }
}