package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.Interface
import top.mcfpp.model.compound.UnsolvedInterface
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.util.LogProcessor

open class MCFPPInterfaceType(
    var i: Interface,
    parentType: ArrayList<out MCFPPType>
): MCFPPType(parentType) {

    override val nbtType: java.lang.Class<out Tag<*>>
        get() = IntArrayTag::class.java

    override val objectData: CompoundData = CompoundData(i.identifier)

    override val typeName: String
        get() = "class(${i.namespace}:${i.identifier})"

    override val simpleName: String
        get() = i.identifier

    open fun getGenericClassType(compiledClass: Class) : MCFPPClassType {
        val t = MCFPPClassType(compiledClass, parentType)
        return t
    }

    override fun tryResolve() {
        if(i is UnsolvedInterface){
            i = (i as UnsolvedInterface).resolve()
        }
    }

    override fun build(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar(identifier)
    }
    override fun build(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar(identifier)
    }
    override fun build(value: Any): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar()
    }
    override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar(identifier)
    }
    override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> {
        LogProcessor.error("Cannot instantiate interface: $typeName")
        return UnknownVar(identifier)
    }

    override fun toString(): String {
        return typeName
    }

    companion object{
        val regex = Regex("^class\\((.+):(.+)\\)$")
    }
}