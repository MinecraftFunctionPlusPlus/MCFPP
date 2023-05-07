package top.mcfpp.lib

import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.Var

class NativeConstructor(cls: Class) : Constructor(cls), Native {
    @Override
    override fun invoke(args: ArrayList<Var>, cls: ClassPointer) {
        TODO()
    }
}