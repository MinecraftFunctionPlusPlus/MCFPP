package top.mcfpp.type

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedGenericTemplate

class MCFPPGenericInterfaceType(
    itf: DataTemplate,
    parentType: ArrayList<out MCFPPType>,
    override val typeId: TypeId.Specialized
): MCFPPDataTemplateType(itf, parentType) {

    override val typeName: String
        get() = "${super.typeName}[${typeId.arguments.joinToString("_")}]"

    override fun tryResolve() {
        (template as? UnsolvedGenericTemplate)?.resolve()
    }
}
