package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.compound.DataTemplate

data class TemplateConstructorInfo(
    val normalParams: List<FunctionParamInfo>,
    val context: CurlBlockContext?,
    val declarationEnvironment: DeclarationEnvironmentInfo?
): ModelInfo<DataTemplateConstructor> {
    override fun get(): DataTemplateConstructor = get(DataTemplateInfo.currTemplate!!)

    internal fun get(owner: DataTemplate): DataTemplateConstructor {
        val constructor = DataTemplateConstructor(owner, null)
        constructor.declarationFile = null
        constructor.declarationEnvironment = declarationEnvironment
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
                constructor.ast,
                DeclarationEnvironmentInfo.from(constructor.declarationFile) ?: constructor.declarationEnvironment
            )
        }
    }
}
