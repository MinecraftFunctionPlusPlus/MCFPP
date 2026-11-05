package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate

/** Restricts an MCFPP template to use as a type, with instance construction reported as a compiler error. */
open class NoInstance private constructor() : DataTemplateAnnotation("NoInstance", "mcfpp.lang") {
    override fun forDataTemplate(data: DataTemplate) {}

    companion object {
        /** Restores this marker from a published library annotation record. */
        @JvmStatic fun restorePortable(): NoInstance = NoInstance()
    }
}
