package top.mcfpp.model.function

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Native
import top.mcfpp.model.compound.Class

class NativeClassConstructor(cls: Class) : ClassConstructor(cls), Native {
    @Override
    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        TODO()
    }
}