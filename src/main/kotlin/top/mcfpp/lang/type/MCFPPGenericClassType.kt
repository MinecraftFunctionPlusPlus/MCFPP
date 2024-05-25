package top.mcfpp.lang.type

import top.mcfpp.lang.Var
import top.mcfpp.model.Class

class MCFPPGenericClassType(
    cls: Class,
    parentType: List<MCFPPType>
) : MCFPPClassType(cls, parentType) {

    val genericVar: ArrayList<Var<*>> = ArrayList()

}