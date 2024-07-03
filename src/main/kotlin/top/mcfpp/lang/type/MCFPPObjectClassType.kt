package top.mcfpp.lang.type

import top.mcfpp.Project
import top.mcfpp.lang.Var
import top.mcfpp.model.Class
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.Function

class MCFPPObjectClassType(
    cls: ObjectClass,
    override var parentType: List<MCFPPType>
):MCFPPClassType(cls, parentType) {

    /**
     * 获取这个类的实例的指针实体在mcfunction中拥有的tag
     */
    override val tag: String
        get() {
            if(genericType.isNotEmpty()){
                return cls.namespace + "_object_class_" + cls.identifier + "_type[" + genericType.sortedBy { it.typeName }.joinToString("_") { it.typeName } + "]"
            }
            return cls.namespace + "_object_class_" + cls.identifier + "_type"
        }

    override val typeName: String
        get() = "object_class(${cls.namespace}:${cls.identifier})[${
            genericType.joinToString("_") { it.typeName }
        }]"
}