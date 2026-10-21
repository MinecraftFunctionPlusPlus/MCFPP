package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.TypeDataTemplateConstructor

data class TemplateConstructorInfo(
    val normalParams: List<FunctionParamInfo>,
    val context: CurlBlockContext?,
    val declarationEnvironment: DeclarationEnvironmentInfo?,
    val accessModifier: top.mcfpp.model.Member.AccessModifier = top.mcfpp.model.Member.AccessModifier.PUBLIC
): ModelInfo<DataTemplateConstructor> {
    override fun get(): DataTemplateConstructor = get(DataTemplateInfo.currTemplate!!)

    internal fun get(owner: DataTemplate): DataTemplateConstructor {
        val constructor = if (owner is TypeDataTemplate && context == null)
            TypeDataTemplateConstructor(owner)
        else DataTemplateConstructor(owner, null)
        constructor.declarationFile = null
        constructor.declarationEnvironment = declarationEnvironment
        constructor.accessModifier = accessModifier
        val previous = AbstractFunctionInfo.currFunction
        try {
            AbstractFunctionInfo.currFunction = constructor
            normalParams.forEach { constructor.normalParams.add(it.get()) }
        } finally { AbstractFunctionInfo.currFunction = previous }
        constructor.ast = context
        constructor.buildParamVar()
        return constructor
    }

    companion object {
        fun from(constructor: DataTemplateConstructor): TemplateConstructorInfo {
            return TemplateConstructorInfo(
                constructor.normalParams.map { FunctionParamInfo.from(it) },
                constructor.ast,
                DeclarationEnvironmentInfo.from(constructor.declarationFile) ?: constructor.declarationEnvironment,
                constructor.accessModifier
            )
        }
    }
}
