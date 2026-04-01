package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedGenericObjectTemplate

class MCFPPGenericObjectDataTemplateType(
    template: DataTemplate,
    val genericVar: ArrayList<out MCFPPValue<*>>,
    parentType: ArrayList<out MCFPPType>
): MCFPPDataTemplateType(template, parentType) {

    override val typeName: String
        get() = "${super.typeName}[${genericVar.joinToString("_") {it.value.toString()}}]"

    override fun tryResolve() {
        (template as? UnsolvedGenericObjectTemplate)?.resolve()
    }
}