package top.mcfpp.type

import top.mcfpp.model.Class
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.ObjectClass
import top.mcfpp.util.LogProcessor
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var

class MCFPPObjectClassType(
    cls: ObjectClass,
    override var parentType: List<MCFPPType>
): MCFPPClassType(cls, parentType) {

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

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName}")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot build var from object: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot build var from type: $typeName")
        return UnknownVar(identifier)
    }
}