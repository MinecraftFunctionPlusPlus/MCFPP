package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.model.compound.TypeDataTemplate

class MCFPPTypeDataTemplateType(
    template: TypeDataTemplate
): MCFPPDataTemplateType(template, arrayListOf(MCFPPBaseType.Any)){

    override fun defaultValueVar(): Var<*> {
        return (template as TypeDataTemplate).typeAs.defaultValueVar()
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }

    override fun buildUnConcrete(identifier: String): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }
}