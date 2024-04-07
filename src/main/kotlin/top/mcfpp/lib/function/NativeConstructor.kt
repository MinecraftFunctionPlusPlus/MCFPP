package top.mcfpp.lib.function

import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.Var
import top.mcfpp.lib.Class
import top.mcfpp.lib.Native

class NativeConstructor(cls: Class) : Constructor(cls), Native {
    @Override
    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        TODO()
    }
}