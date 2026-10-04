package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedGenericTemplate

class MCFPPGenericDataTemplateType(
    template: DataTemplate,
    val genericVar: ArrayList<out MCFPPValue<*>>,
    parentType: ArrayList<out MCFPPType>
): MCFPPDataTemplateType(template, parentType) {

    override val typeId: TypeId = TypeId.Specialized(
        TypeId.Declaration("template", template.namespace, template.identifier),
        genericVar.map { requireNotNull(top.mcfpp.analysis.ValueSnapshot.of(it)) { "Generic arguments require a complete immutable value" } }
    )

    override val typeName: String
        get() = "${super.typeName}[${genericVar.joinToString("_") {it.value.toString()}}]"

    override fun tryResolve() {
        (template as? UnsolvedGenericTemplate)?.resolve()
    }
}