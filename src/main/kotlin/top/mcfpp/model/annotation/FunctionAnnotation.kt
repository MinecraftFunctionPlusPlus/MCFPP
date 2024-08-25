package top.mcfpp.model.annotation

import top.mcfpp.model.Class
import top.mcfpp.model.DataTemplate
import top.mcfpp.util.LogProcessor

abstract class FunctionAnnotation(identifier: String, namespace: String) : Annotation(identifier,namespace) {
    final override fun forClass(clazz: Class) {
        throw Exception("Cannot use function annotation on class")
    }

    final override fun forDataObject(data: DataTemplate) {
        LogProcessor.error("Cannot use class annotation on data template")
    }
}