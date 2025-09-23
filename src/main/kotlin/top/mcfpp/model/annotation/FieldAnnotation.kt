package top.mcfpp.model.annotation

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor


abstract class FieldAnnotation(identifier: String, namespace: String) : Annotation(identifier, namespace) {
    override fun forFunction(function: Function) {
        LogProcessor.error("Cannot use field annotation on function")
    }

    override fun forDataTemplate(data: DataTemplate) {
        LogProcessor.error("Cannot use field annotation on template")
    }
}