package top.mcfpp.lib

import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.ClassBase
import top.mcfpp.lang.Var

class NativeConstructor(cls: Class) : Constructor(cls), Native {
    @Override
    override fun invoke(args: ArrayList<Var>, cls: ClassBase?) {
        TODO()
    }
}