package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.Var
import java.util.*
import kotlin.collections.ArrayList

class StructConstructor: Function {

    val target: Struct

    constructor(target: Struct):super("_init_" + target.identifier.lowercase(Locale.getDefault()) + "_" + target.constructors.size, target, false){
        this.target = target
    }

    @Override
    @InsertCommand
    override fun invoke(args: ArrayList<Var>, struct: Struct) {
        addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        //传入this参数
        val thisPoint = field.getVar("this")!! as ClassPointer
    }

}