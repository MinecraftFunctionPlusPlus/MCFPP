package top.mcfpp.mni.annotation

import top.mcfpp.model.annotation.DataTemplateAnnotation
import top.mcfpp.model.compound.DataTemplate

/** Stores a JVM class association for To metadata. Applying this annotation currently raises [UnsupportedOperationException]. */
open class To(className: String) : DataTemplateAnnotation("To", "mcfpp.lang") {
    /** Fully qualified associated JVM class name. */
    var className: String = className
        private set

    /** Restores the class name from a published library annotation record. */
    fun restorePortableState(value: String) { className = value }

    override fun forDataTemplate(data: DataTemplate) { throw UnsupportedOperationException("Not implemented yet") }
}
