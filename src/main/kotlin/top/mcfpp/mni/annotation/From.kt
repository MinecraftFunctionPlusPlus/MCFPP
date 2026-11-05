package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.util.LogProcessor

/** Associates an MCFPP data template with a JVM provider. Macros use explicit source function declarations. */
open class From(className: String) : DataTemplateAnnotation("From", "mcfpp.lang") {
    /** Fully qualified JVM provider class name. */
    var className: String = className
        private set

    /** Restores the provider class name from a published library annotation record. */
    fun restorePortableState(value: String) { className = value }

    override fun forDataTemplate(data: DataTemplate) {
        try {
            data.injectedBy(Class.forName(className))
        } catch (failure: ClassNotFoundException) {
            LogProcessor.error("Class not found: $className")
        }
    }
}
