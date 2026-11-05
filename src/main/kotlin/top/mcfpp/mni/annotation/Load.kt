package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.FunctionAnnotation
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag

/** Adds the annotated MCFPP function to minecraft:load so it runs when the data pack loads. */
open class Load : FunctionAnnotation("Load", "mcfpp.lang") {
    override fun forFunction(function: Function) { function.addTag(FunctionTag.LOAD) }
}
