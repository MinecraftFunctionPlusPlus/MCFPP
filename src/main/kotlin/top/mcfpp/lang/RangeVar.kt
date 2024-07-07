package top.mcfpp.lang

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.util.*
import kotlin.experimental.and

open class RangeVar: Var<Int> {

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
        right = MCFloat(identifier + "_right")
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

    override fun assign(b: Var<*>): RangeVar {
        when(b){
            is RangeVar -> {
                this.point = b.point
                if(point and 2 != 0.toByte()) left.assign(b.left)
                if(point and 1 != 0.toByte()) right.assign(b.right)
            }
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
            }
        }
        return this
    }

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            is MCFPPBaseType.Range -> {
                return this
            }
            else -> {
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                return UnknownVar(identifier)
            }
        }
    }

    override fun clone(): RangeVar {
        return RangeVar(this)
    }

    override fun getTempVar(): Var<*> {
        if (isTemp) return this
        val re = RangeVar()
        re.isTemp = true
        return re.assign(this)
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
        if(point and 2 != 0.toByte()) command.buildMacro(left.identifier, false)
        command.build("..")
        if(point and 1 != 0.toByte()) command.buildMacro(right.identifier, false)
        return command
    }
}

class RangeVarConcrete: MCFPPValue<Pair<Float?, Float?>>, RangeVar{

    override var value: Pair<Float?, Float?>

    /**
     * 创建一个固定的int
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
        value.first?.let { left.assign(MCFloatConcrete(identifier + "_left", it)) }
        value.second?.let { right.assign(MCFloatConcrete(identifier + "_right", it)) }
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
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
        value.first?.let { left.assign(MCFloatConcrete(identifier + "_left", it)) }
        value.second?.let { right.assign(MCFloatConcrete(identifier + "_right", it)) }
    }

    constructor(range: RangeVar, value: Pair<Float?, Float?>) : super(range){
        this.value = value
        if(value.second == null && value.first == null) {
            LogProcessor.error("Range should have at least one side")
        }
        if(value.first != null && value.second != null && value.second!! < value.first!!){
            LogProcessor.error("Left value should be smaller than right value")
        }
        value.first?.let { left.assign(MCFloatConcrete(identifier + "_left", it)) }
        value.second?.let { right.assign(MCFloatConcrete(identifier + "_right", it)) }
    }

    constructor(range: RangeVarConcrete) : super(range){
        this.value = range.value
        value.first?.let { left.assign(MCFloatConcrete(identifier + "_left", it)) }
        value.second?.let { right.assign(MCFloatConcrete(identifier + "_right", it)) }
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        value.first?.let { (left as MCFloatConcrete).toDynamic(false) }
        value.second?.let { (right as MCFloatConcrete).toDynamic(false) }
        return RangeVar(this)
    }

    override fun getTempVar(): Var<*> {
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
        if(value.second != null) command.buildMacro(value.second!!.toString(), false)
        return command
    }

}