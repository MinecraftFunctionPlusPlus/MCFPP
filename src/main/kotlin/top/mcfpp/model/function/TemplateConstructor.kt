package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.IntTemplateBase
import top.mcfpp.lang.Var
import top.mcfpp.model.Template
import java.util.*

class TemplateConstructor(target: Template) : Function(
    "_init_" + target.identifier.lowercase(Locale.getDefault()) + "_" + target.constructors.size,
    target,
    false
) {

    @Override
    @InsertCommand
    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, struct: IntTemplateBase) {
        addCommand("data modify storage mcfpp:system " + Project.config.defaultNamespace + ".stack_frame prepend value {}")
        //传入this参数
        val thisPoint = field.getVar("this")!! as ClassPointer
        TODO()
    }

}