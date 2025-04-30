package top.mcfpp.type

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.nbt.tags.Tag

class MCFPPTypeDataTemplateType(
    template: TypeDataTemplate
): MCFPPDataTemplateType(template, arrayListOf(MCFPPBaseType.Any)){

    override fun defaultValue(): Tag<*> {
        return (template as TypeDataTemplate).typeAs.defaultValue()
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }

    override fun build(identifier: String): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }

    override fun build(identifier: String, clazz: Class): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }
    override fun build(value: Any): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, value)
    }
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }
    override fun buildUnConcrete(identifier: String): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        return TypeDataTemplateObject(template as TypeDataTemplate, identifier)
    }

}