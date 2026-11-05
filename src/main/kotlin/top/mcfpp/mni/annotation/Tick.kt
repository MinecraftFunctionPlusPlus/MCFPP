package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.FunctionAnnotation
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionTag

/** Adds the annotated MCFPP function to minecraft:tick so it runs each game tick. */
open class Tick : FunctionAnnotation("Tick", "mcfpp.lang") {
    override fun forFunction(function: Function) { function.addTag(FunctionTag.TICK) }
}
