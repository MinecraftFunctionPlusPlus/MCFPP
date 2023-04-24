package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.lib.Function
import top.alumopper.mcfpp.lib.Class

/**
 * 实现此接口即代表这个Java类所代表的类型能够从这个类型的变量中搜寻此变量所代表的mcfpp类的成员。
 */
interface CanSelectMember {
    fun getVarMember(key: String?): Var?
    fun getFunctionMember(key: String?, params: List<String?>?): Function?
    fun Class(): Class?
}