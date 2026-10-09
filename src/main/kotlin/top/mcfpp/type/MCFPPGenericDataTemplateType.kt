package top.mcfpp.type

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.Project

class MCFPPGenericDataTemplateType(
    template: DataTemplate,
    parentType: ArrayList<out MCFPPType>,
    override val typeId: TypeId.Specialized
): MCFPPDataTemplateType(template, parentType) {

    override val typeName: String
        get() = "${super.typeName}[${typeId.arguments.joinToString("_")}]"

    override fun tryResolve() {
        // Includes must all restore their declaration imports before specialization.
        if (Project.compileStage == Project.CompileStage.READ_LIB) return
        if (template is CompiledGenericDataTemplate) return
        val declaration = typeId.constructor as? TypeId.Declaration ?: return
        val prototype = when (declaration.kind) {
            "template" -> GlobalScope.getCanonicalTemplate(declaration.namespace, declaration.name)
            "interface" -> GlobalScope.getCanonicalTemplate(declaration.namespace, declaration.name, true)
            else -> null
        } as? GenericDataTemplate ?: return
        if (prototype.getType().typeId != declaration) return
        val compiled = template as? CompiledGenericDataTemplate
        if (compiled?.originTemplate !== prototype) {
            val canonical = MCFPPType.resolveSpecialization(typeId) ?: return
            template = canonical
        }
        parentType = template.getType().parentType
    }
}
