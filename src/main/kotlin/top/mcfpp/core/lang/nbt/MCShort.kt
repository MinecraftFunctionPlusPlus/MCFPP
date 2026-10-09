package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.TempPool

open class MCShort : MCInt {
    override var type: MCFPPType
        get() = MCFPPNBTType.Short
        set(value) { require(value == MCFPPNBTType.Short) }
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(container: FieldContainer, identifier: String = TempPool.getVarIdentify()) : super(container, identifier)
    constructor(source: MCShort) : super(source)
    constructor(source: MCInt) : super(source)
    constructor(value: Short, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Integral(value.toLong()))
    }
    constructor(container: FieldContainer, value: Short, identifier: String = TempPool.getVarIdentify()) : this(container, identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Integral(value.toLong()))
    }
    override fun clone(): MCShort = MCShort(this)
    override fun getTempVar(): MCShort = StorageAccess.capture(this) as MCShort
}
