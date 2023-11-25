package top.mcfpp.lang

import top.mcfpp.lib.Class
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Member
import top.mcfpp.util.Utils
import java.util.UUID

/**
 * ClassType是类的类型指针。它是一种特殊的指针，能够指向这个类的静态成员。
 *
 * @see Class 类的核心实现
 * @see ClassObject 类的实例。指针的目标
 * @see ClassPointer 类的指针
 */
class ClassType: CompoundDataType{

    /**
     * 获取这个类的实例的指针实体在mcfunction中拥有的tag
     */
    val tag: String
        get() = dataType.namespace + "_class_" + dataType.identifier + "_type"

    /**
     * 新建一个指向这个类的类型指针
     */
    constructor(cls: Class):super(cls)

    /**
     * 复制一个类的类型指针
     */
    constructor(clsType: ClassType):super(clsType)

    @get:Override
    override val type: String
        get() = "ClassType@${dataType.identifier}"
}