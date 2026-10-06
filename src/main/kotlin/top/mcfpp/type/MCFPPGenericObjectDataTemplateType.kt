package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompiledGenericObjectDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.Project

class MCFPPGenericObjectDataTemplateType(
    template: DataTemplate,
    val genericVar: ArrayList<MCFPPValue<*>>,
    parentType: ArrayList<out MCFPPType>,
    override val typeId: TypeId.Specialized
): MCFPPObjectDataTemplateType(template, parentType) {

    override val typeName: String
        get() = "${super.typeName}[${genericVar.joinToString("_") {it.value.toString()}}]"

    override fun tryResolve() {
        if (Project.compileStage == Project.CompileStage.READ_LIB) return
        val declaration = typeId.constructor as? TypeId.Declaration ?: return
        if (declaration.kind != "object") return
        val prototype = GlobalScope.getUnsolvedImportNamespace(declaration.namespace)?.scope
            ?.getObject(declaration.name) as? GenericObjectDataTemplate ?: return
        if (prototype.namespace != declaration.namespace || prototype.identifier != declaration.name) return
        val compiled = template as? CompiledGenericObjectDataTemplate
        if (compiled?.originTemplate !== prototype) {
            val canonical = MCFPPType.resolveSpecialization(typeId) ?: return
            template = canonical
            genericVar.clear()
            genericVar.addAll(canonical.args)
        }
        parentType = ArrayList(template.parent.filterIsInstance<DataTemplate>().map { it.getType() })
    }
}
