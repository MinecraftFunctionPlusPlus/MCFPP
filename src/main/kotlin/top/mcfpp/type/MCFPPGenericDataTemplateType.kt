package top.mcfpp.type

import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.Project

class MCFPPGenericDataTemplateType @JvmOverloads constructor(
    template: DataTemplate,
    parentType: ArrayList<out MCFPPType>,
    override val typeId: TypeId.Specialized,
    @Transient private val sessionResolver: (() -> CompiledGenericDataTemplate?)? = null,
    @Transient private val sessionGuard: (() -> Unit)? = null
): MCFPPDataTemplateType(template, parentType) {

    constructor(other: MCFPPGenericDataTemplateType) : this(
        other.rawTemplate(), ArrayList(other.parentType), other.typeId, other.sessionResolver, other.sessionGuard
    )

    /** Read existing checkpoint state without invoking the template getter's lazy resolution. */
    private fun rawTemplate(): DataTemplate = checkpointPrivateState().elements.single()

    override val typeName: String
        get() {
            sessionGuard?.invoke()
            return "${super.typeName}[${typeId.arguments.joinToString("_")}]"
        }

    override fun tryResolve() {
        sessionGuard?.invoke()
        // Includes must all restore their declaration imports before specialization.
        if (Project.compileStage == Project.CompileStage.READ_LIB) return
        sessionResolver?.let { resolve ->
            if (rawTemplate() is CompiledGenericDataTemplate) return
            val canonical = resolve() ?: return
            require(canonical.inheritanceIdentity == typeId) { "Session specialization differs from the descriptor identity" }
            template = canonical
            parentType = canonical.getType().parentType
            return
        }
        if (template is CompiledGenericDataTemplate) return
        val boundPrototype = template as? GenericDataTemplate
        if (boundPrototype?.sessionIdentity != null && boundPrototype.nominalIdentity == typeId.constructor) {
            template = MCFPPType.resolveSpecialization(typeId, boundPrototype) ?: return
            parentType = template.getType().parentType
            return
        }
        val declaration = typeId.constructor as? TypeId.Declaration ?: return
        val prototype = when (declaration.kind) {
            "template" -> GlobalScope.getCanonicalTemplate(declaration.namespace, declaration.name)
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
