package top.mcfpp.io.info

import top.mcfpp.model.function.ClassConstructor
import top.mcfpp.model.function.DataTemplateConstructor

data class ClassConstructorInfo(
    val normalParams: List<FunctionParamInfo>
): ModelInfo<ClassConstructor> {
    override fun get(): ClassConstructor {
        val constructor = ClassConstructor(AbstractClassInfo.currClass!!)
        normalParams.forEach {
            AbstractFunctionInfo.currFunction = constructor
            constructor.normalParams.add(it.get())
        }
        return constructor
    }

    companion object {
        fun from(constructor: ClassConstructor): ClassConstructorInfo {
            return ClassConstructorInfo(constructor.normalParams.map { FunctionParamInfo.from(it) })
        }
    }
}

data class TemplateConstructorInfo(
    val normalParams: List<FunctionParamInfo>,
): ModelInfo<DataTemplateConstructor> {
    override fun get(): DataTemplateConstructor {
        val constructor = DataTemplateConstructor(DataTemplateInfo.currTemplate!!, null)
        normalParams.forEach {
            AbstractFunctionInfo.currFunction = constructor
            constructor.normalParams.add(it.get())
        }
        return constructor
    }

    companion object {
        fun from(constructor: DataTemplateConstructor): TemplateConstructorInfo {
            return TemplateConstructorInfo(constructor.normalParams.map { FunctionParamInfo.from(it) })
        }
    }
}