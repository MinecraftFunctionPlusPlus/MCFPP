package top.mcfpp.core.lang

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPVectorType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Fixed-dimensional typed access; components belong to its parent's Place. */
class VectorVar : Var<VectorVar>, Indexable {
    val dimension: Int
    constructor(dimension: Int, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        require(dimension > 0) { "Vector dimension must be positive" }
        this.dimension = dimension
        type = MCFPPVectorType(dimension)
    }
    constructor(dimension: Int, container: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(dimension, identifier)
    constructor(values: Array<Int>, identifier: String = TempPool.getVarIdentify()) : this(values.size, identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(values.map {
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(it.toLong()))
        }))
    }
    constructor(values: Array<Int>, container: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(values, identifier)
    constructor(source: VectorVar) : super(source) { dimension = source.dimension }
    override fun doAssignedBy(source: Var<*>): VectorVar = StorageAccess.write(this, source) as VectorVar
    override fun clone(): VectorVar = VectorVar(this)
    override fun getTempVar(): VectorVar = StorageAccess.capture(this) as VectorVar
    override fun storeToStack() = StorageAccess.materialize(this)
    override fun getFromStack() { StorageAccess.read(this) }
    override fun getByIndex(index: Var<*>): PropertyVar {
        var closed = StorageAccess.snapshot(index)
        while (closed is CompilerValue.Typed) closed = closed.payload
        val offset = (closed as? CompilerValue.Integral)?.value
        val element = if (index.type == MCFPPBaseType.Int && offset != null && offset in 0L until dimension.toLong()) {
            StorageAccess.element(this, index, MCFPPBaseType.Int)
        } else {
            LogProcessor.error("Vector index requires a complete int within its declared dimension")
            UnknownVar("${identifier}_index").apply { isError = true }
        }
        return PropertyVar(Property.buildSimpleProperty(element), element, this)
    }
    override fun toNBTVar(): NBTBasedData = StorageAccess.view(this, MCFPPNBTType.NBT) as NBTBasedData
}
