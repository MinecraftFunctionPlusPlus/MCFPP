package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.annotation.NoInstance
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor

/**
 * 模板类型
 * @see DataTemplate
 */
open class MCFPPDataTemplateType(
    template: DataTemplate,
    parentType: ArrayList<out MCFPPType>
) : MCFPPType(parentType) {

    @Transient private var resolvingTemplate = false

    private var templateSlot: DataTemplate = template

    var template: DataTemplate
        get() {
            if (this is MCFPPGenericDataTemplateType &&
                templateSlot !is top.mcfpp.model.compound.CompiledGenericDataTemplate && !resolvingTemplate) {
                resolvingTemplate = true
                try {
                    tryResolve()
                } finally {
                    resolvingTemplate = false
                }
            }
            return templateSlot
        }
        set(value) { templateSlot = value }

    private class RawState(val target: DataTemplate, val resolving: Boolean)
    private fun rawState(): RawState {
        check(javaClass in checkpointClasses) { "Template type checkpoint cannot capture ${javaClass.name}" }
        return RawState(templateSlot, resolvingTemplate)
    }

    /** Pure raw relationships: even a generic type does not invoke its template getter. */
    internal fun rawPreparationFacts(): RawPreparationFact = rawState().let {
        RawPreparationFact.row(RawPreparationFact.scalar("templateType"), RawPreparationFact.identity(this),
            RawPreparationFact.identity(it.target), RawPreparationFact.scalar(it.resolving))
    }

    /** Raw private relationships only. Elements are opaque roots for the declaration deep journal. */
    internal class PrivateStateCheckpoint internal constructor(
        elements: List<DataTemplate>, private val restoreState: () -> Unit,
        private val recapture: () -> PrivateStateCheckpoint
    ) {
        val elements: List<DataTemplate> = java.util.Collections.unmodifiableList(ArrayList(elements))
        fun restore() = restoreState()
        fun captureCurrent(): PrivateStateCheckpoint = recapture()
    }

    internal fun checkpointPrivateState(): PrivateStateCheckpoint = checkpointPrivateState(emptyList())

    private fun checkpointPrivateState(retained: List<DataTemplate>): PrivateStateCheckpoint {
        // Neither the public template getter nor a target's type/identity/getType is consulted.
        val raw = rawState()
        val target = raw.target
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        val elements = (retained + target).filter { seen.add(it) }
        return PrivateStateCheckpoint(elements, {
            templateSlot = target
            resolvingTemplate = raw.resolving
        }, { checkpointPrivateState(elements) })
    }

    override val typeId: TypeId get() = TypeId.Declaration("template", template.namespace, template.identifier)

    override val hasRuntimeRepresentation: Boolean
        get() = runtimeFields(hashSetOf())

    internal val instanceFields: List<Var<*>>
        get() {
            val readonly = (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)
                ?.originTemplate?.readOnlyParams?.map { it.identifier }.orEmpty()
            return template.scope.allVars.filterNot { it.isStatic || it.identifier in readonly }
        }

    private fun runtimeFields(visiting: MutableSet<TypeId>): Boolean {
        if (!visiting.add(typeId)) return true
        return try {
            instanceFields.all { field ->
                val fieldType = field.type
                runtimeFieldType(fieldType, visiting)
            }
        } finally {
            visiting.remove(typeId)
        }
    }

    private fun runtimeFieldType(type: MCFPPType, visiting: MutableSet<TypeId>): Boolean = when (type) {
        is MCFPPTypeDataTemplateType -> runtimeFieldType(type.typeAs, visiting)
        is MCFPPDataTemplateType -> type.runtimeFields(visiting)
        is MCFPPListType -> type.generic[0] === MCFPPPrivateType.Wildcard || type.generic.all { runtimeFieldType(it, visiting) }
        is MCFPPImmutableListType -> type.generic.all { runtimeFieldType(it, visiting) }
        is MCFPPDictType -> type.generic.all { runtimeFieldType(it, visiting) }
        is MCFPPMapType -> type.generic.all { runtimeFieldType(it, visiting) }
        is MCFPPUnionType -> type.types.all { runtimeFieldType(it, visiting) }
        else -> type.hasRuntimeRepresentation
    }

    override val instanceData: DataTemplate
        get() {
            tryResolve()
            return template
        }

    override val objectData: CompoundData
        get() = template.companionObject?: CompoundData(template.identifier, template.namespaceID)

    override fun replaceMemberVar(v: Var<*>) {
        val companion = template.companionObject
        if (companion == null) super.replaceMemberVar(v)
        else companion.scope.putVar(v.identifier, v, true)
    }

    override val typeName: String
        get() = "template(${template.namespace}:${template.identifier})"

    override val simpleName: String
        get() = template.identifier

    override fun tryResolve() {
        if(template is UnsolvedTemplate){
            template = (template as UnsolvedTemplate).resolve()
        }
    }

    override fun defaultValue(): Any? = null

    @Suppress("UNCHECKED_CAST")
    override fun build(identifier: String, value: Any?): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return top.mcfpp.analysis.StorageAccess.literal(this, value as top.mcfpp.analysis.CompilerValue, identifier)
        }
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        if (template.annotations.any { it is NoInstance }){
            LogProcessor.error("Template ${template.namespaceID} is not allowed to be instantiated.")
            return UnknownVar(identifier)
        }else{
            return DataTemplateObject(template, identifier)
        }
    }

    companion object{
        private val checkpointClasses = setOf<Class<*>>(
            MCFPPDataTemplateType::class.java, MCFPPGenericDataTemplateType::class.java,
            MCFPPObjectDataTemplateType::class.java,
            MCFPPGenericObjectDataTemplateType::class.java, MCFPPTypeDataTemplateType::class.java
        )
        val regex = Regex("^template\\((.+):(.+)\\)$")
    }

}
