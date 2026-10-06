package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.Project

class MCFPPGenericDataTemplateType(
    template: DataTemplate,
    val genericVar: ArrayList<MCFPPValue<*>>,
    parentType: ArrayList<out MCFPPType>,
    override val typeId: TypeId.Specialized
): MCFPPDataTemplateType(template, parentType) {

    override val typeName: String
        get() = "${super.typeName}[${genericVar.joinToString("_") {it.value.toString()}}]"

    override fun tryResolve() {
        // Includes must all restore their declaration imports before specialization.
        if (Project.compileStage == Project.CompileStage.READ_LIB) return
        val declaration = typeId.constructor as? TypeId.Declaration ?: return
        if (declaration.kind != "template") return
        val prototype = GlobalScope.getUnsolvedImportNamespace(declaration.namespace)?.scope
            ?.getTemplate(declaration.name) as? GenericDataTemplate ?: return
        if (prototype.namespace != declaration.namespace || prototype.identifier != declaration.name || prototype.isInterface) return
        val compiled = template as? CompiledGenericDataTemplate
        if (compiled?.originTemplate !== prototype) {
            val canonical = MCFPPType.resolveSpecialization(typeId) ?: return
            template = canonical
            genericVar.clear()
            genericVar.addAll(canonical.args)
        }
        parentType = template.getType().parentType
    }
}
