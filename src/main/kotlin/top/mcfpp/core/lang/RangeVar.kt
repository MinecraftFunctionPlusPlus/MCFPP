package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*
import kotlin.experimental.and

open class RangeVar: Var<RangeVar> {

    //01 10 11 00(不合法)
    //1表示有，0表示没有
    var point: Byte = 0b00

    var left: MCNumber<*>
    var right: MCNumber<*>

    /**
     * 创建一个range类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个range值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        left = MCFloat(identifier + "_left")
        left.nbtPath = this.nbtPath.memberIndex("left")
        right = MCFloat(identifier + "_right")
        right.nbtPath = this.nbtPath.memberIndex("right")
    }

    /**
     * 复制一个range
     * @param b 被复制的range值
     */
    constructor(b: RangeVar) : super(b){
        left = MCFloat(b.identifier + "_left")
        right = MCFloat(b.identifier + "_right")
        point = b.point
    }

    override fun doAssignedBy(b: Var<*>): RangeVar {
        when (b) {
            is RangeVar -> {
                this.point = b.point
                if (point and 2 != 0.toByte()) left.assignedBy(b.left)
                if (point and 1 != 0.toByte()) right.assignedBy(b.right)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): RangeVar {
        return RangeVar(this)
    }

    override fun getTempVar(): RangeVar {
        if (isTemp) return this
        val re = RangeVar()
        re.isTemp = true
        return re.assignedBy(this)
    }

    override fun storeToStack() {
        if(point and 2 != 0.toByte()) left.storeToStack()
        if(point and 1 != 0.toByte()) right.storeToStack()
    }

    override fun getFromStack() {
        if(point and 2 != 0.toByte()) left.getFromStack()
        if(point and 1 != 0.toByte()) right.getFromStack()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun toNBTVar(): NBTBasedData {
        val n = NBTBasedData()
        n.name = name
        n.identifier = identifier
        n.isStatic = isStatic
        n.accessModifier = accessModifier
        n.isTemp = isTemp
        n.stackIndex = stackIndex
        n.isConst = isConst
        n.nbtPath = nbtPath
        return n
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    open fun toCommandPart() : Command{
        val command = Command("")
        if(point and 2 != 0.toByte()) command.buildMacro(left, false)
        command.build("..")
        if(point and 1 != 0.toByte()) command.buildMacro(right, false)
        return command
    }

    companion object {
        val data = CompoundData("range","mcfpp")

        init {
            data.extends(MCAny.data)
        }

    }

}

class RangeVarConcrete: MCFPPValue<Pair<Float?, Float?>>, RangeVar{

    override var value: Pair<Float?, Float?>

    /**
     * 创建一个固定的range
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Pair<Float?, Float?>,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
        if(value.second == null && value.first == null) {
            LogProcessor.error("Range should have at least one side")
        }
        if(value.first != null && value.second != null && value.second!! < value.first!!){
            LogProcessor.error("Left value should be smaller than right value")
        }
        value.first?.let { left.assignedBy(MCFloatConcrete(it, identifier + "_left")) }
        value.second?.let { right.assignedBy(MCFloatConcrete(it, identifier + "_right")) }
    }

    /**
     * 创建一个固定的range。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Pair<Float?, Float?>, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
        if(value.second == null && value.first == null) {
            LogProcessor.error("Range should have at least one side")
        }
        if(value.first != null && value.second != null && value.second!! < value.first!!){
            LogProcessor.error("Left value should be smaller than right value")
        }
        value.first?.let { left.assignedBy(MCFloatConcrete(it, identifier + "_left")) }
        value.second?.let { right.assignedBy(MCFloatConcrete(it, identifier + "_right")) }
    }

    constructor(range: RangeVar, value: Pair<Float?, Float?>) : super(range){
        this.value = value
        if(value.second == null && value.first == null) {
            LogProcessor.error("Range should have at least one side")
        }
        if(value.first != null && value.second != null && value.second!! < value.first!!){
            LogProcessor.error("Left value should be smaller than right value")
        }
        value.first?.let { left.assignedBy(MCFloatConcrete(it, identifier + "_left")) }
        value.second?.let { right.assignedBy(MCFloatConcrete(it, identifier + "_right")) }
    }

    constructor(range: RangeVarConcrete) : super(range){
        this.value = range.value
        value.first?.let { left.assignedBy(MCFloatConcrete(it, identifier + "_left")) }
        value.second?.let { right.assignedBy(MCFloatConcrete(it, identifier + "_right")) }
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        value.first?.let { (left as MCFloatConcrete).toDynamic(false) }
        value.second?.let { (right as MCFloatConcrete).toDynamic(false) }
        return RangeVar(this)
    }

    override fun getTempVar(): RangeVarConcrete {
        if (isTemp) return this
        return RangeVarConcrete(value)
    }

    override fun clone(): RangeVarConcrete {
        return RangeVarConcrete(this)
    }

    override fun toCommandPart() : Command{
        val command = Command("")
        if(value.first != null) command.build(value.first!!.toString(), false)
        command.build("..")
        if(value.second != null) command.build(value.second!!.toString(), false)
        return command
    }

}