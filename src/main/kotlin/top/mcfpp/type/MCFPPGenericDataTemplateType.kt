package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.core.lang.Var
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.util.LogProcessor

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
        val declaration = typeId.constructor as TypeId.Declaration
        val prototype = GlobalScope.getUnsolvedImportNamespace(declaration.namespace)?.scope
            ?.getTemplate(declaration.name) as? GenericDataTemplate ?: return
        val compiled = template as? CompiledGenericDataTemplate
        if (compiled?.originTemplate !== prototype) {
            if (typeId.arguments.size != prototype.readOnlyParams.size) {
                LogProcessor.error("Readonly argument count does not match template '${prototype.identifier}'")
                return
            }
            val types = MCFPPType.builtinTypesById().toMutableMap()
            val arguments = ArrayList<Var<*>>()
            for ((parameter, snapshot) in prototype.readOnlyParams.zip(typeId.arguments)) {
                val type = parameter.type!!
                types[type.typeId] = type
                var payload = snapshot
                while (payload is CompilerValue.Typed) payload = payload.payload
                if (payload is CompilerValue.TypeValue) {
                    MCFPPType.resolveTypeId(payload.id)?.let { types[payload.id] = it }
                }
                val value = StorageAccess.restore(type, snapshot, parameter.identifier, types)
                if (value == null) {
                    LogProcessor.error("Cannot restore frozen readonly argument '${parameter.identifier}' of ${declaration.name}")
                    return
                }
                arguments.add(value)
            }
            val canonical = prototype.compile(arguments) ?: return
            template = canonical
            genericVar.clear()
            genericVar.addAll(canonical.args)
        }
        parentType = template.getType().parentType
    }
}
