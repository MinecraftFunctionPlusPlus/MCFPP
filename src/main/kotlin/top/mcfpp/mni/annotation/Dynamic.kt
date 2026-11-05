package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.FunctionAnnotation
import top.mcfpp.model.function.Function

/** Clears the function's source body from its compiler model when the annotation runs. */
open class Dynamic : FunctionAnnotation("Dynamic", "mcfpp.lang") {
    override fun forFunction(function: Function) {
        function.ast = null
    }
}
