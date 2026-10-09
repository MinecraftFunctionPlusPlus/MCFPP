package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.compound.DataTemplate

class MCFPPTypeDataTemplateType(
    template: DataTemplate
): MCFPPDataTemplateType(template, arrayListOf(MCFPPBaseType.Any)){

    override val instanceData: DataTemplate
        get() {
            tryResolve()
            return template
        }

    val typeAs: MCFPPType get() {
        tryResolve()
        return (template as TypeDataTemplate).typeAs
    }

    override val nbtType get() = typeAs.nbtType
    override val hasRuntimeRepresentation get() = typeAs.hasRuntimeRepresentation
    override fun defaultValue(): Any? = typeAs.defaultValue()

    override fun defaultValueVar(): Var<*> {
        return buildUnConcrete(top.mcfpp.util.TempPool.getVarIdentify())
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        tryResolve()
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier).apply {
            delegateVar = typeAs.build(identifier, value).also { it.parent = this }
        }
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        tryResolve()
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }
}
