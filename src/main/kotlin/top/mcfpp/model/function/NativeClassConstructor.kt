package top.mcfpp.model.function

import top.mcfpp.model.CanSelectMember
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.Native

class NativeClassConstructor(cls: Class) : ClassConstructor(cls), Native {
    @Override
    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        TODO()
    }
}