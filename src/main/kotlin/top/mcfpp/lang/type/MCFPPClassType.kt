package top.mcfpp.lang.type

import top.mcfpp.lib.Class

/**
 * 用于标识由mcfpp class定义出来的类
 * 这里还得找出父类是哪些
 */
class MCFPPClassType(
    var cls:Class,
    override var parentType: List<MCFPPType>
):MCFPPType {
    override var typeName: String = cls.identifier

}