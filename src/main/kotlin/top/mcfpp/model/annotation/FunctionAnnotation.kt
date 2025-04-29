package top.mcfpp.model.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.util.LogProcessor

abstract class FunctionAnnotation(identifier: String, namespace: String) : Annotation(identifier,namespace) {
    final override fun forClass(clazz: Class) {
        throw Exception("Cannot use function annotation on template")
    }

    final override fun forDataTemplate(data: DataTemplate) {
        LogProcessor.error("Cannot use function annotation on template")
    }

    final override fun forField(field: Var<*>) {
        LogProcessor.error("Cannot use function annotation on field")
    }
}