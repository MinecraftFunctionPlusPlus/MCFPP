package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.exception.TODOException
import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.Function
import top.alumopper.mcfpp.lib.*

/**
 * 一个类的实例的指针.它指向了一个类的对象
 */
class ClassPointer : Var, CanSelectMember {
    /**
     * 指针的类型
     */
    var clsType: Class

    /**
     * 指针的类的标识符
     */
    override var type: String

    /**
     * 指针指向的类的实例
     */
    var obj: ClassObject? = null

    /**
     * 指针的地址
     */
    var address: MCInt

    /**
     * 创建一个指针
     * @param type 指针的类型
     * @param container 容器，生成前缀用
     * @param identifier 标识符
     */
    constructor(type: Class, container: CacheContainer, identifier: String) {
        clsType = type
        this.type = clsType.identifier
        key = identifier
        this.identifier = container.prefix + identifier
        address =
            MCInt(Function.currFunction!!.namespace + "_class_" + type.identifier + "_pointer_" + identifier).setObj(
                type.addressSbObject
            ) as MCInt
    }


    constructor(classPointer: ClassPointer) : super(classPointer) {
        clsType = classPointer.clsType
        type = classPointer.type
        obj = classPointer.obj
        address = classPointer.address
    }

    /**
     * 将b中的值赋值给此变量。一个指针只能指向它的类型的类或者其子类的实例
     * @param b 变量的对象
     * @throws VariableConverseException 如果隐式转换失败
     */
    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        if (b is ClassObject) {
            if (!b.clsType.canCastTo(clsType)) {
                throw VariableConverseException()
            }
            if (obj != null) {
                //原实体中的实例减少一个指针
                Function.addCommand(
                    "execute " +
                            "as @e[tag=" + obj!!.clsType.namespace + "_class_" + obj!!.clsType.identifier + "_pointer] " +
                            "if score @s " + obj!!.address!!.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                            "run data remove entity @s data.pointers[0]"
                )
            }
            obj = b
            //地址储存
            address.assign(b.address)
            //实例中的指针列表
            Function.addCommand(
                "execute " +
                        "as @e[tag=" + obj!!.clsType.namespace + "_class_" + obj!!.clsType.identifier + "_pointer] " +
                        "if score @s " + b.address!!.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                        "run data modify entity @s data.pointers append value 0"
            )
        }
        if (b is ClassPointer) {
            if (!b.clsType.canCastTo(clsType)) {
                throw VariableConverseException()
            }
            if (obj != null) {
                //原实体中的实例减少一个指针
                Function.addCommand(
                    "execute " +
                            "as @e[tag=" + obj!!.clsType.namespace + "_class_" + obj!!.clsType.identifier + "_pointer] " +
                            "if score @s " + obj!!.address!!.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                            "run data remove entity @s data.pointers[0]"
                )
            }
            obj = b.obj
            //地址储存
            address.assign(b.address)
            //实例中的指针列表
            Function.addCommand(
                "execute " +
                        "as @e[tag=" + obj!!.clsType.namespace + "_class_" + obj!!.clsType.identifier + "_pointer] " +
                        "if score @s " + b.address.`object`.name + " = " + address.identifier + " " + address.`object`.name + " " +
                        "run data modify entity @s data.pointers append value 0"
            )
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Any {
        throw TODOException("")
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