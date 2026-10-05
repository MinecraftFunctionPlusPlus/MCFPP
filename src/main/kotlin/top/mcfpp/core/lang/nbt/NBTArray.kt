package top.mcfpp.core.lang.nbt

import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCObject
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.property.Property
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Commands
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

abstract class NBTArray: NBTBasedData {

    abstract val arrayType: MCFPPType

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    constructor(b: NBTArray) : super(b)

    override fun doAssignedBy(b: Var<*>): NBTArray {
        if (b !is NBTArray || b.type != type) {
            LogProcessor.error("Cannot assign '${b.type}' to '$type'")
            return this
        }
        val target = type.buildUnConcrete(identifier).setAs(this) as NBTArray
        if (b.storageBinding != null) return StorageAccess.copyCollection(target, b) as NBTArray
        if (b is MCFPPValue<*>) return type.build(identifier, (b.value as Tag<*>).copy()).setAs(this) as NBTArray
        Function.addCommand(Commands.dataSetFrom(target.nbtPath, b.nbtPath))
        return target
    }

    protected fun dynamicArray(replace: Boolean): NBTArray {
        StorageAccess.materialize(this)
        val result = StorageAccess.adapter(type, identifier, StorageAccess.ensure(this)).apply { setAs(this) } as NBTArray
        if (replace) {
            if (parentTemplate() != null) (parent as DataTemplateObject).instanceField.putVar(identifier, result, true)
            else Function.currFunction.scope.putVar(identifier, result, true)
        }
        return result
    }

    /** A precise array element has the language type of its NBT format, independent of host subclasses. */
    internal fun constantElements(): List<Var<*>>? = when (this) {
        is NBTByteArrayConcrete -> value.value.map { MCByteConcrete(it) }
        is NBTIntArrayConcrete -> value.value.map { MCIntConcrete(it) }
        is NBTLongArrayConcrete -> value.value.map { MCLongConcrete(LongTag(it)) }
        else -> null
    }

    override fun getByIndex(index: Var<*>): PropertyVar{
        val actual = if (index is MCAny && index !is MCObject) index.semanticValue() else index
        if(actual is MCInt && actual.type.typeId == MCFPPBaseType.Int.typeId){
            val v = StorageAccess.element(this, actual, arrayType)
            return PropertyVar(Property.buildSimpleProperty(v), v,this)
        }else{
            LogProcessor.error("Index must be a int")
            val re = UnknownVar("error_${identifier}_index_${index.identifier}")
            return PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }
}
