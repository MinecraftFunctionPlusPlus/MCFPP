package top.mcfpp.model.function

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.TypeDataTemplate

/** The internal wrapper constructor has no reflective native dispatch. */
class TypeDataTemplateConstructor(data: TypeDataTemplate) : DataTemplateConstructor(data, null) {
    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        val result = caller as TypeDataTemplateObject
        TypeDataTemplate.defaultConstructor(normalArgs.values.single(), result)
        return result
    }
}
