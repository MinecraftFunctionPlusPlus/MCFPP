package top.mcfpp.model.compound

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.type.MCFPPGenericObjectDataTemplateType
import top.mcfpp.type.MCFPPObjectDataTemplateType
import top.mcfpp.type.MCFPPType

open class GenericObjectDataTemplate(
    ctx: mcfppParser.TemplateBodyContext,
    identifier: String,
    namespace: String = Project.currNamespace
) : GenericDataTemplate(ctx, identifier, namespace), ObjectCompoundData {
    init { companionObject = this }

    override val namespaceID: String
        get() = "$namespace:${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}"

    override val prefix: String
        get() = "${namespace}_object_template_${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_"

    override fun createCompiledTemplate(identifier: String, args: List<MCFPPValue<*>>,
                                        argumentValues: List<CompilerValue>): CompiledGenericDataTemplate =
        CompiledGenericObjectDataTemplate(identifier, namespace, this, args, argumentValues)

    override fun getType(): MCFPPObjectDataTemplateType =
        MCFPPObjectDataTemplateType(this, ArrayList(parent.filterIsInstance<DataTemplate>().map { it.getType() }))

    fun isSelf(identifier: String, readOnlyParam: List<MCFPPType>): Boolean {
        if (this.identifier == identifier) {
            if (readOnlyParam.size != readOnlyParams.size) return false
            for (i in readOnlyParam.indices) {
                if (readOnlyParam[i].typeName != readOnlyParams[i].typeIdentifier) return false
            }
            return true
        }
        return false
    }
}

class CompiledGenericObjectDataTemplate(
    identifier: String,
    namespace: String = Project.currNamespace,
    originClass: GenericObjectDataTemplate,
    args: List<MCFPPValue<*>>,
    argumentValues: List<CompilerValue>
) : CompiledGenericDataTemplate(identifier, namespace, originClass, args, argumentValues), ObjectCompoundData {
    init { companionObject = this }

    override fun getType(): MCFPPGenericObjectDataTemplateType =
        MCFPPGenericObjectDataTemplateType(this, ArrayList(args),
            ArrayList(parent.filterIsInstance<DataTemplate>().map { it.getType() }), identity)
}
