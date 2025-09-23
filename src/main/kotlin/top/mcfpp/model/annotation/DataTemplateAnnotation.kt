package top.mcfpp.model.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

abstract class DataTemplateAnnotation(identifier: String, namespace: String) : Annotation(identifier, namespace) {
    final override fun forFunction(function: Function) {
        LogProcessor.error("Cannot use data annotation on function")
    }

    final override fun forField(field: Var<*>) {
        LogProcessor.error("Cannot use data annotation on field")
    }
}