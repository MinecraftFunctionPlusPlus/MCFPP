package top.mcfpp.io.info

import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.property.Property

/** Preserve annotation graph aliases while keeping restored declarations independent of metadata. */
data class DeclarationAnnotationsInfo(
    val declared: List<Annotation> = emptyList(),
    val pending: List<Annotation> = emptyList()
) {
    fun restore(template: DataTemplate) {
        val copied = Property.copyAnnotations(declared + pending)
        val annotations = copied.take(declared.size)
        val deferred = copied.drop(declared.size)
        template.annotations.addAll(annotations)
        template.pendingDeclarationAnnotations.addAll(deferred)
    }

    companion object {
        fun from(template: DataTemplate): DeclarationAnnotationsInfo {
            val copied = Property.copyAnnotations(template.annotations + template.pendingDeclarationAnnotations)
            return DeclarationAnnotationsInfo(copied.take(template.annotations.size), copied.drop(template.annotations.size))
        }
    }
}
