package top.mcfpp.core.lang

import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.FieldContainer
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Optional endpoint presence and values live exclusively in this range's Place. */
open class RangeVar : Var<RangeVar> {
    var prefix: FieldContainer? = null
    override var type: MCFPPType = MCFPPBaseType.Range
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(identifier) { prefix = curr }
    constructor(other: RangeVar) : super(other) { prefix = other.prefix }
    constructor(value: Pair<Number?, Number?>, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        if (value.first == null && value.second == null) LogProcessor.error("Range should have at least one side")
        if (value.first != null && value.second != null && value.first!!.toDouble() > value.second!!.toDouble())
            LogProcessor.error("Left value should be smaller than right value")
        val fields = linkedMapOf<String, CompilerValue>()
        fun number(v: Number): CompilerValue = when (v) {
            is Int -> CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(v.toLong()))
            is Float -> CompilerValue.Typed(MCFPPBaseType.Float.typeId, CompilerValue.FloatBits(v.toRawBits()))
            else -> error("Range endpoints must be int or float")
        }
        value.first?.let { fields["left"] = number(it) }
        value.second?.let { fields["right"] = number(it) }
        StorageAccess.initializeLiteral(this, CompilerValue.Record(fields))
    }
    constructor(curr: FieldContainer, value: Pair<Number?, Number?>, identifier: String = TempPool.getVarIdentify()) : this(value, identifier) { prefix = curr }
    constructor(other: RangeVar, value: Pair<Number?, Number?>) : this(value, other.identifier) { prefix = other.prefix }
    val point: Byte get() {
        val root = storageBinding ?: return 0
        return listOf("left" to 2, "right" to 1).sumOf { (name, bit) ->
            if (root.data.facts.read(root.place.field(name))?.state == ValueState.INITIALIZED) bit else 0
        }.toByte()
    }
    val left: MCNumber<*> get() = endpoint("left")
    val right: MCNumber<*> get() = endpoint("right")
    private fun endpoint(name: String): MCNumber<*> {
        val binding = StorageAccess.ensure(this).field(name)
        val id = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
        val endpointType = when (id) {
            MCFPPBaseType.Int.typeId -> MCFPPBaseType.Int
            MCFPPBaseType.Float.typeId -> MCFPPBaseType.Float
            else -> error("Range endpoint '$name' has no proven numeric layout")
        }
        return StorageAccess.read(StorageAccess.adapter(endpointType, identifier + "_" + name, binding)) as MCNumber<*>
    }
    fun parts(): Map<String, MCNumber<*>> = buildMap {
        if (point.toInt() and 2 != 0) put("left", left)
        if (point.toInt() and 1 != 0) put("right", right)
    }
    val value: Pair<Number?, Number?> get() {
        val fields = ((StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Record)?.fields
        fun number(name: String): Number? = when (val v = (fields?.get(name) as? CompilerValue.Typed)?.payload) {
            is CompilerValue.Integral -> v.value.toInt()
            is CompilerValue.FloatBits -> Float.fromBits(v.bits)
            else -> null
        }
        return number("left") to number("right")
    }
    override fun clone() = RangeVar(this)
    override fun doAssignedBy(b: Var<*>): RangeVar { StorageAccess.write(this, b); return this }
    override fun getTempVar(): RangeVar = StorageAccess.capture(this) as RangeVar
    override fun storeToStack() { StorageAccess.ensure(this).data.materialize() }
    override fun getFromStack() = Unit
    override fun toNBTVar() = StorageAccess.view(this, MCFPPNBTType.NBT, diagnose = false) as NBTBasedData
    override fun toCommandPart(): Command {
        if (StorageAccess.snapshot(this) != null) return Command("${value.first ?: ""}..${value.second ?: ""}")
        return Command("").apply {
            if (point.toInt() and 2 != 0) buildMacro(left, false)
            build("..", false)
            if (point.toInt() and 1 != 0) buildMacro(right, false)
        }
    }
    fun isIntRange() = parts().values.all { it.type == MCFPPBaseType.Int }
    companion object {
        fun fromBounds(left: MCNumber<*>?, right: MCNumber<*>?): RangeVar {
            if (left == null && right == null) LogProcessor.error("Range should have at least one side")
            val parts = linkedMapOf<String, Var<*>>()
            left?.let { parts["left"] = StorageAccess.capture(it) }
            right?.let { parts["right"] = StorageAccess.capture(it) }
            return StorageAccess.dictionaryLiteral(MCFPPBaseType.Range, parts) as RangeVar
        }
    }
}
