package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.exception.TODOException
import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.Function
import top.alumopper.mcfpp.lib.Class

/**
 * ClassType代表了一种类，它仅仅用于编译过程中。通过此类可以获取它代表的类的静态成员。
 */
class ClassType: ClassObject, CanSelectMember {

    constructor(cls: Class) : super(cls)

    @get:Override
    override var isStatic: Boolean
        get() = true
        set(isStatic) {
            super.isStatic = isStatic
        }

    @Override
    override fun clone(): Any {
        throw TODOException("")
    }

    @get:Override
    override var type: String
        get() = "ClassType"
        set(type) {
            super.type = type
        }

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun getVarMember(key: String?): Var? {
        return null
    }

    @Override
    override fun getFunctionMember(key: String?, params: List<String?>?): Function? {
        return null
    }
}