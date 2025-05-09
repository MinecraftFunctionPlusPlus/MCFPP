package top.mcfpp.type

import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.UnsolvedGenericClass
import top.mcfpp.util.TempPool

class MCFPPGenericClassType (
    cls: Class,
    val genericVar : ArrayList<out MCFPPValue<*>>,
    parentType: ArrayList<out MCFPPType>
) : MCFPPClassType(cls, parentType) {

    override val typeName: String
        get() = "class(${cls.namespace}:${cls.identifier})[${
            genericVar.joinToString("_") { it.value.toString() }
        }]"

    override fun tryResolve() {
        if(cls is UnsolvedGenericClass){
            cls = (cls as UnsolvedGenericClass).resolve()
        }
    }

    override fun defaultValue(): Var<*> {
        return ClassPointer(cls, "default")
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> = ClassPointer(cls, identifier)
    override fun build(identifier: String): Var<*> = ClassPointer(cls, identifier)
    override fun build(identifier: String, clazz: Class): Var<*> = ClassPointer(this.cls, identifier)
    override fun build(value: Any): Var<*> = ClassPointer(this.cls, TempPool.getVarIdentify())
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = ClassPointer(cls, identifier)
    override fun buildUnConcrete(identifier: String): Var<*> = ClassPointer(cls, identifier)
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = ClassPointer(this.cls, identifier)

}