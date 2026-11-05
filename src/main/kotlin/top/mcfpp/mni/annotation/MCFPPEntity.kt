package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate

/** Marks an entity data template and applies [DataOnly] to its fields. */
open class MCFPPEntity private constructor(restoring: Boolean) : DataTemplateAnnotation("MCFPPEntity", "mcfpp.lang") {
    /** Creates an entity annotation with the DataOnly parent annotation. */
    constructor() : this(false)

    init { if (!restoring) extends(DataOnly()) }

    override fun forDataTemplate(data: DataTemplate) {}

    companion object {
        /** Restores this annotation while the library reader restores its parent annotation separately. */
        @JvmStatic fun restorePortable(): MCFPPEntity = MCFPPEntity(true)
    }
}
