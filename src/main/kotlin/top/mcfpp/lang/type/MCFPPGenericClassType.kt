package top.mcfpp.lang.type

import top.mcfpp.lang.Var
import top.mcfpp.lib.Class

class MCFPPGenericClassType (
    cls: Class,
    parentType: List<MCFPPType>
) : MCFPPClassType(cls, parentType) {

    val genericVar : ArrayList<Var<*>> = ArrayList()

}