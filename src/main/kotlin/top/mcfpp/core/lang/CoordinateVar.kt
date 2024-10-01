package top.mcfpp.core.lang

import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPStdPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

class Coordinate3Var: Var<Coordinate3Var> {

    var x: CoordinateDimension
    var y: CoordinateDimension
    var z: CoordinateDimension

    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString(),
    ) : super(identifier){
        this.x = CoordinateDimension("", curr, identifier)
        this.y = CoordinateDimension("", curr, identifier)
        this.z = CoordinateDimension("", curr, identifier)
    }

    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        x = CoordinateDimension("", identifier)
        y = CoordinateDimension("", identifier)
        z = CoordinateDimension("", identifier)
    }

    constructor(b: Coordinate3Var) : super(b){
        x = CoordinateDimension(b.x)
        y = CoordinateDimension(b.y)
        z = CoordinateDimension(b.z)
    }

    override fun clone(): Coordinate3Var {
        return Coordinate3Var(this)
    }

    override fun doAssignedBy(b: Var<*>): Coordinate3Var {
        return when (b) {
            is Coordinate3Var -> {
                x.assignedBy(b.x)
                y.assignedBy(b.y)
                z.assignedBy(b.z)
                this
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun getTempVar(): Coordinate3Var {
        return Coordinate3Var().assignedBy(this)
    }

    override fun storeToStack() {
        x.storeToStack()
        y.storeToStack()
        z.storeToStack()
    }

    override fun getFromStack() {
        x.getFromStack()
        y.getFromStack()
        z.getFromStack()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return when(key){
            "x" -> x to true
            "y" -> y to true
            "z" -> z to true
            else -> null to true
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction(key) to true
    }
    fun toCommandPart(): Command{
        val c = Command("")
        c.build(x.toCommandPart(), false)
        c.build(y.toCommandPart())
        c.build(z.toCommandPart())
        return c
    }

    companion object {
        val data = CompoundData("coordinate3", "mcfpp").apply {
            extends(MCAny.data)
        }
    }
}

class Coordinate2Var: Var<Coordinate2Var> {

    var x: CoordinateDimension
    var z: CoordinateDimension

    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : super(identifier){
        x = CoordinateDimension("", curr, identifier)
        z = CoordinateDimension("", curr, identifier)
    }

    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier){
        x = CoordinateDimension("", identifier)
        z = CoordinateDimension("", identifier)
    }

    constructor(b: Coordinate2Var) : super(b){
        x = CoordinateDimension(b.x)
        z = CoordinateDimension(b.z)
    }

    override fun clone(): Coordinate2Var {
        return Coordinate2Var(this)
    }

    override fun doAssignedBy(b: Var<*>): Coordinate2Var {
        return when (b) {
            is Coordinate2Var -> {
                x.assignedBy(b.x)
                z.assignedBy(b.z)
                this
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun getTempVar(): Coordinate2Var {
        return Coordinate2Var().assignedBy(this)
    }

    override fun storeToStack() {
        x.storeToStack()
        z.storeToStack()
    }

    override fun getFromStack() {
        x.getFromStack()
        z.getFromStack()
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return when(key) {
            "x" -> x to true
            "z" -> z to true
            else -> null to true
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction(key) to true
    }

    fun toCommandPart(): Command{
        val c = Command("")
        c.buildMacro(x, false)
        c.buildMacro(z, false)
        return c
    }

    companion object {
        val data = CompoundData("coordinate2", "mcfpp").apply {
            extends(MCAny.data)
        }
    }
}

open class CoordinateDimension: MCNumber<Number> {

    var prefix: MCString

    var number: MCNumber<*>? = null

    override var type: MCFPPType = MCFPPStdPrivateType.MCFPPCoordinateDimension

    override var parent: CanSelectMember? = null
        get() = super.parent
        set(value) {
            field = value
            number?.parent = value
        }

    /**
     * 创建一个int类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        prefix: String,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier
        this.prefix = MCStringConcrete(curr, StringTag(prefix), identifier)
    }

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(prefix: String, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        this.prefix = MCStringConcrete(StringTag(prefix), identifier)
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: CoordinateDimension) : super(b){
        this.prefix = b.prefix.clone() as MCString
        this.number = b.number?.clone()
    }

    override fun assignCommand(a: MCNumber<*>): MCNumber<Number> {
        this.prefix = this.prefix.assignedBy((a as CoordinateDimension).prefix) as MCString
        val aNum = a.number
        if(number == null && aNum == null) {
            //Do nothing
        }else if(number == null){
            number = aNum?.clone()
            number!!.identifier = this.identifier
            number!!.parent = this.parent
            number!!.nbtPath = this.nbtPath
        }else if(aNum == null){
            number = null
        }else{
            number!!.assignCommand(aNum)
        }
        return this
    }

    override fun doAssignedBy(b: Var<*>): MCNumber<Number> {
        return when (b) {
            is CoordinateDimension -> {
                assignCommand(b)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): MCNumber<Number> {
        return CoordinateDimension(this)
    }

    override fun getTempVar(): MCNumber<Number> {
        return CoordinateDimension(UUID.randomUUID().toString())
    }

    override fun storeToStack() {
        prefix.storeToStack()
        if(number != null){
            number!!.storeToStack()
        }
    }

    override fun getFromStack() {
        prefix.getFromStack()
        if(number != null){
            number!!.getFromStack()
        }
    }

    /**
     * 返回此坐标维度作为命令部分的表示。可能为宏函数，需要[Command.buildMacroFunction]转换
     */
    open fun toCommandPart(): Command{
        val c = if(prefix is MCStringConcrete){
            Command((prefix as MCStringConcrete).value.value)
        }else{
            Command("").buildMacro(prefix, false)
        }
        if(number != null){
            c.buildMacro(number!!, false)
        }
        return c
    }

}

class CoordinateDimensionConcrete: CoordinateDimension, MCFPPValue<Number>{

    override var value: Number = Double.NaN

    constructor(
        prefix: String,
        curr: FieldContainer,
        value: Number,
        identifier: String = UUID.randomUUID().toString()
    ) : super(prefix, curr, identifier) {
        this.value = value
        number = if(value is Int) {
            MCIntConcrete(curr, value.toInt(), identifier)
        }else{
            MCFloatConcrete(curr, value.toFloat(), identifier)
        }
    }

    constructor(prefix: String, value: Number, identifier: String = UUID.randomUUID().toString()) : super(prefix, identifier) {
        this.value = value
        number = if(value is Int) {
            MCIntConcrete(value.toInt(), identifier)
        }else{
            MCFloatConcrete(value.toFloat(), identifier)
        }
    }

    constructor(coo: CoordinateDimension, value: Number) : super(coo){
        this.value = value
        number = if(coo.number is MCInt) {
            MCIntConcrete(coo.number as MCInt, value.toInt())
        }else{
            MCFloatConcrete(coo.number as MCFloat, value.toFloat())
        }
    }

    constructor(coo: CoordinateDimensionConcrete) : super(coo){
        this.value = coo.value
    }

    override fun clone(): MCNumber<Number> {
        return CoordinateDimensionConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        if(value is Int){
            (number as MCIntConcrete).toDynamic(false)
        }else{
            (number as MCFloatConcrete).toDynamic(false)
        }
        val qwq = CoordinateDimension(this)
        if(replace) replacedBy(qwq)
        return qwq
    }

}