package top.mcfpp.core.lang.obj

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.Member
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.EnumMember
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** The declaration descriptor carries no mutable member value. */
class EnumVar : Var<EnumVar> {
    val enum: Enum
    constructor(enum: Enum, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.enum = enum
        type = enum.getType()
    }
    constructor(other: EnumVar) : super(other) { enum = other.enum }
    constructor(enum: Enum, member: EnumMember, identifier: String = TempPool.getVarIdentify()) : this(enum, identifier) {
        initialize(member)
    }
    constructor(enum: Enum, ordinal: Int, identifier: String = TempPool.getVarIdentify()) : this(enum, identifier) {
        val member = enum.getMember(ordinal)
        if (member == null) { LogProcessor.error("Enum member not found: $ordinal"); isError = true }
        else initialize(member)
    }
    private fun initialize(member: EnumMember) {
        StorageAccess.initializeLiteral(this, CompilerValue.Typed(type.typeId, CompilerValue.Record(mapOf(
            "ordinal" to CompilerValue.Integral(member.value.toLong()),
            "data" to CompilerValue.Nbt(NbtEncoding.snbt(member.data))
        ))))
    }
    val value: EnumMember
        get() {
            val record = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Record
            val ordinal = (record?.fields?.get("ordinal") as? CompilerValue.Integral)?.value
            return ordinal?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.let { enum.getMember(it.toInt()) }
                ?: error("Enum member has no complete compile-time value")
        }
    override fun doAssignedBy(b: Var<*>): EnumVar { StorageAccess.write(this, b); return this }
    override fun clone() = EnumVar(this)
    override fun getTempVar() = StorageAccess.capture(this) as EnumVar
    override fun storeToStack() { StorageAccess.materialize(this) }
    override fun getFromStack() { StorageAccess.invalidateReads(listOf(this)) }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = when (key) {
        "value" -> asIntVar() to true
        "identifier" -> if (StorageAccess.snapshot(this) != null)
            StorageAccess.literal(MCFPPBaseType.String, CompilerValue.Text(value.identifier), key) to true
        else { LogProcessor.error("Enum identifier has no runtime producer"); null to true }
        "data" -> asNBTVar() to true
        else -> null to true
    }
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                  accessModifier: Member.AccessModifier): Pair<Function, Boolean> = UnknownFunction(key) to true
    fun asIntVar(): MCInt {
        if (StorageAccess.snapshot(this) != null) return MCInt(value.value)
        LogProcessor.error("Enum ordinal has no runtime producer in the data-only enum encoding")
        return MCInt().apply { isError = true }
    }
    fun asNBTVar(): NBTBasedData {
        val record = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Record
        val data = record?.fields?.get("data")
        if (data != null) return StorageAccess.literal(MCFPPNBTType.NBT, data) as NBTBasedData
        return StorageAccess.view(this, MCFPPNBTType.NBT) as NBTBasedData
    }
    override fun toNBTVar() = asNBTVar()
}
