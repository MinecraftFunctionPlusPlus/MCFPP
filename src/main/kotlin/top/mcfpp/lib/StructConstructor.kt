package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.StructBase
import top.mcfpp.lang.Var
import java.util.*
import kotlin.collections.ArrayList

class StructConstructor(target: Struct) : Function(
    "_init_" + target.identifier.lowercase(Locale.getDefault()) + "_" + target.constructors.size,
    target,
    false
) {

    @Override
    @InsertCommand
    override fun invoke(args: ArrayList<Var>, struct: StructBase) {
        addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        //传入this参数
        val thisPoint = field.getVar("this")!! as ClassPointer
    }

}