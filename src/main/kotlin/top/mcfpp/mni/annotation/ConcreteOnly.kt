package top.mcfpp.mni.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/** MCFPP declaration marker for concrete values, retained on data templates and fields. */
open class ConcreteOnly private constructor() : Annotation("ConcreteOnly", "mcfpp.lang") {
    override fun forDataTemplate(data: DataTemplate) {}
    override fun forField(field: Var<*>) {}
    override fun forFunction(function: Function) {
        LogProcessor.error("@${identifier}cannot be used on function")
    }

    companion object {
        /** Restores this marker from a published library annotation record. */
        @JvmStatic fun restorePortable(): ConcreteOnly = ConcreteOnly()
    }
}
