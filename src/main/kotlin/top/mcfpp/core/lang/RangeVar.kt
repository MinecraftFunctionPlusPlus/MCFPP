package top.mcfpp.core.lang

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueState
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.FieldContainer
import top.mcfpp.lib.NBTPath
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Optional numeric endpoints. Integers retain Int identity and never pass through Float. */
open class RangeVar : Var<RangeVar> {
    var prefix: FieldContainer? = null
    override var type: MCFPPType = MCFPPBaseType.Range

    // Bit 2 denotes the left endpoint, bit 1 the right endpoint.
    var point: Byte = 0
        get() {
            val binding = storageBinding ?: return field
            return listOf("left" to 2, "right" to 1).sumOf { (name, bit) ->
                if (binding.data.facts.read(binding.place.field(name))?.state == ValueState.INITIALIZED) bit else 0
            }.toByte()
        }
    private var leftValue: MCNumber<*>
    private var rightValue: MCNumber<*>
    var left: MCNumber<*>
        get() = endpoint("left", leftValue)
        set(value) { leftValue = value }
    var right: MCNumber<*>
        get() = endpoint("right", rightValue)
        set(value) { rightValue = value }

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        nbtPath = NBTPath.getNormalStackPath(this)
        leftValue = MCFloat(identifier + "_left")
        rightValue = MCFloat(identifier + "_right")
    }
    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(identifier) { prefix = curr }
    constructor(source: RangeVar) : super(source) {
        prefix = source.prefix
        point = source.point
        leftValue = source.leftValue.clone()
        rightValue = source.rightValue.clone()
    }

    private fun endpoint(name: String, fallback: MCNumber<*>): MCNumber<*> {
        val binding = storageBinding?.field(name) ?: return fallback
        val id = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
        val type = when (id) {
            MCFPPBaseType.Int.typeId -> MCFPPBaseType.Int
            MCFPPBaseType.Float.typeId -> MCFPPBaseType.Float
            else -> {
                LogProcessor.error("Range endpoint '$name' requires a proven int or float layout")
                return fallback.clone().apply { isError = true }
            }
        }
        return StorageAccess.read(StorageAccess.adapter(type, identifier + "_" + name, binding)) as MCNumber<*>
    }

    fun parts(): Map<String, MCNumber<*>> = buildMap {
        if (point.toInt() and 2 != 0) put("left", left)
        if (point.toInt() and 1 != 0) put("right", right)
    }

    override fun doAssignedBy(b: Var<*>): RangeVar {
        if (b !is RangeVar) { LogProcessor.error("Cannot assign ${b.type} to range"); return this }
        val copied = StorageAccess.copyCollection(RangeVar(this), b) as RangeVar
        if (isDynamic) copied.storageBinding!!.data.materialize()
        return copied
    }
    override fun clone() = RangeVar(this)
    override fun getTempVar(): RangeVar = if (isTemp) this else RangeVar().apply {
        isTemp = true
        nbtPath = NBTPath.temp.memberIndex(identifier)
    }.assignedBy(this)
    override fun storeToStack() { StorageAccess.ensure(this).data.materialize() }
    override fun getFromStack() = Unit
    override fun toNBTVar() = StorageAccess.view(this, MCFPPNBTType.NBT, diagnose = false) as NBTBasedData

    override fun toCommandPart(): Command {
        val loaded = StorageAccess.read(this)
        if (loaded is RangeVarConcrete) return loaded.toCommandPart()
        return Command("").apply {
            if (point.toInt() and 2 != 0) buildMacro(left, false)
            build("..", false)
            if (point.toInt() and 1 != 0) buildMacro(right, false)
        }
    }
    fun isIntRange() = parts().values.all { it is MCInt }

    companion object {
        fun fromBounds(left: MCNumber<*>?, right: MCNumber<*>?): RangeVar {
            if ((left == null || left is MCFPPValue<*>) && (right == null || right is MCFPPValue<*>))
                return RangeVarConcrete(((left as? MCFPPValue<*>)?.value as Number?) to ((right as? MCFPPValue<*>)?.value as Number?))
            return RangeVar().apply {
                point = ((if (left == null) 0 else 2) + (if (right == null) 0 else 1)).toByte()
                if (left != null) this.left = left.getTempVar() as MCNumber<*>
                if (right != null) this.right = right.getTempVar() as MCNumber<*>
            }
        }
    }
}

class RangeVarConcrete : RangeVar, MCFPPValue<Pair<Number?, Number?>> {
    override var value: Pair<Number?, Number?>

    constructor(value: Pair<Number?, Number?>, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
        initialize()
    }
    constructor(curr: FieldContainer, value: Pair<Number?, Number?>, identifier: String = TempPool.getVarIdentify()) : super(curr, identifier) {
        this.value = value
        initialize()
    }
    constructor(source: RangeVar, value: Pair<Number?, Number?>) : super(source) {
        this.value = value
        initialize()
    }
    constructor(source: RangeVarConcrete) : this(source, source.value)

    private fun initialize() {
        val (first, last) = value
        if (first == null && last == null) LogProcessor.error("Range should have at least one side")
        if (first != null && last != null && first.toDouble() > last.toDouble())
            LogProcessor.error("Left value should be smaller than right value")
        fun number(value: Number, suffix: String): MCNumber<*> = when (value) {
            is Int -> MCIntConcrete(value, identifier + suffix)
            is Float -> MCFloatConcrete(value, identifier + suffix)
            else -> error("Range endpoints must be language int or float")
        }
        point = ((if (first == null) 0 else 2) + (if (last == null) 0 else 1)).toByte()
        if (first != null) left = number(first, "_left")
        if (last != null) right = number(last, "_right")
    }
    override fun toDynamic(replace: Boolean): Var<*> {
        StorageAccess.ensure(this).data.materialize()
        val result = RangeVar(this).apply { isDynamic = true }
        if (replace) replacedBy(result)
        return result
    }
    override fun getTempVar(): RangeVarConcrete = if (isTemp) this else RangeVarConcrete(value).apply { isTemp = true }
    override fun clone() = RangeVarConcrete(this)
    override fun toCommandPart() = Command("${value.first ?: ""}..${value.second ?: ""}")
}
