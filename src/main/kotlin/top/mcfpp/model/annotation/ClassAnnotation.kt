package top.mcfpp.model.annotation

import top.mcfpp.model.Class
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

abstract class ClassAnnotation(identifier: String, namespace: String) : Annotation(identifier,namespace) {
    final override fun forFunction(function: Function) {
        LogProcessor.error("Cannot use class annotation on function")
    }

    final override fun forClass(clazz: Class) {
        throw Exception("Cannot use function annotation on class")
    }
}