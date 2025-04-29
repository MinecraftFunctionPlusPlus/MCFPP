package top.mcfpp.model.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

abstract class ClassAnnotation(identifier: String, namespace: String) : Annotation(identifier,namespace) {
    final override fun forFunction(function: Function) {
        LogProcessor.error("Cannot use class annotation on function")
    }

    final override fun forDataTemplate(data: DataTemplate) {
        throw Exception("Cannot use class annotation on template")
    }

    final override fun forField(field: Var<*>) {
        LogProcessor.error("Cannot use class annotation on field")
    }
}