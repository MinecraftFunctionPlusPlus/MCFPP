package top.mcfpp.core.lang

import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class Pos3Var: Var<Pos3Var> {

    var x: PosDimension
    var y: PosDimension
    var z: PosDimension

    override var type: MCFPPType = MCFPPBaseType.Pos3

    constructor(
        curr: FieldContainer,
        identifier: String = TempPool.getVarIdentify(),
    ) : super(identifier){
        this.x = PosDimension("", curr, identifier)
        this.y = PosDimension("", curr, identifier)
        this.z = PosDimension("", curr, identifier)
    }

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        x = PosDimension("", identifier)
        y = PosDimension("", identifier)
        z = PosDimension("", identifier)
    }

    constructor(b: Pos3Var) : super(b){
        x = PosDimension(b.x)
        y = PosDimension(b.y)
        z = PosDimension(b.z)
    }

    override fun clone(): Pos3Var {
        return Pos3Var(this)
    }

    override fun doAssignedBy(b: Var<*>): Pos3Var {
        return when (b) {
            is Pos3Var -> {
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

    override fun getTempVar(): Pos3Var {
        return Pos3Var().assignedBy(this)
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
        }.apply { first?.parent = this@Pos3Var }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction(key) to true
    }
    override fun toCommandPart(): Command{
        val c = Command("")
        c.build(x.toCommandPart(), false)
        c.build(y.toCommandPart())
        c.build(z.toCommandPart())
        return c
    }

    override fun replaceMemberVar(v: Var<*>) {
        v as PosDimension
        when(v.identifier){
            "x" -> x = v
            "y" -> y = v
            "z" -> z = v
        }
    }

    companion object {
        val data = CompoundData("pos3", "mcfpp").apply {
            extends(MCAny.data)
        }
    }
}

class Pos2Var: Var<Pos2Var> {

    override var type: MCFPPType = MCFPPBaseType.Pos2

    var x: PosDimension
    var z: PosDimension

    constructor(
        curr: FieldContainer,
        identifier: String = TempPool.getVarIdentify()
    ) : super(identifier){
        x = PosDimension("", curr, identifier)
        z = PosDimension("", curr, identifier)
    }

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        x = PosDimension("", identifier)
        z = PosDimension("", identifier)
    }

    constructor(b: Pos2Var) : super(b){
        x = PosDimension(b.x)
        z = PosDimension(b.z)
    }

    override fun clone(): Pos2Var {
        return Pos2Var(this)
    }

    override fun doAssignedBy(b: Var<*>): Pos2Var {
        return when (b) {
            is Pos2Var -> {
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

    override fun getTempVar(): Pos2Var {
        return Pos2Var().assignedBy(this)
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
        }.apply { first?.parent = this@Pos2Var }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction(key) to true
    }

    override fun toCommandPart(): Command{
        val c = Command("")
        c.buildMacro(x, false)
        c.buildMacro(z, false)
        return c
    }

    override fun replaceMemberVar(v: Var<*>) {
        v as PosDimension
        when(v.identifier){
            "x" -> x = v
            "z" -> z = v
        }
    }

    companion object {
        val data = CompoundData("pos2", "mcfpp").apply {
            extends(MCAny.data)
        }
    }
}

open class PosDimension: MCNumber<Number> {

    var prefix: MCString

    var number: MCNumber<*>? = null

    override var type: MCFPPType = MCFPPPrivateType.MCFPPCoordinateDimension

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
        identifier: String = TempPool.getVarIdentify()
    ) : super(curr, identifier) {
        this.identifier = identifier
        this.prefix = MCStringConcrete(StringTag(prefix), identifier)
    }

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(prefix: String, identifier: String = TempPool.getVarIdentify()) : super(identifier){
        this.prefix = MCStringConcrete(StringTag(prefix), identifier)
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: PosDimension) : super(b){
        this.prefix = b.prefix.clone() as MCString
        this.number = b.number?.clone()
    }

    override fun assignCommand(a: MCNumber<*>): MCNumber<Number> {
        this.prefix = this.prefix.assignedBy((a as PosDimension).prefix) as MCString
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
            is PosDimension -> {
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
        return PosDimension(this)
    }

    override fun getTempVar(): MCNumber<Number> {
        return PosDimension(TempPool.getVarIdentify())
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
    override fun toCommandPart(): Command{
        val c = if(prefix is MCStringConcrete){
            Command((prefix as MCStringConcrete).value.value)
        }else{
            Command("").buildMacro(prefix, false)
        }
        if(number != null && !(number is MCIntConcrete && (number as MCIntConcrete).value == 0)){
            c.build(number!!.toCommandPart())
        }
        return c
    }

}

class CoordinateDimensionConcrete: PosDimension, MCFPPValue<Number>{

    override var value: Number = Double.NaN

    constructor(
        prefix: String,
        curr: FieldContainer,
        value: Number,
        identifier: String = TempPool.getVarIdentify()
    ) : super(prefix, curr, identifier) {
        this.value = value
        number = if(value is Int) {
            MCIntConcrete(curr, value.toInt(), identifier)
        }else{
            MCFloatConcrete(curr, value.toFloat(), identifier)
        }
    }

    constructor(prefix: String, value: Number, identifier: String = TempPool.getVarIdentify()) : super(prefix, identifier) {
        this.value = value
        number = if(value is Int) {
            MCIntConcrete(value.toInt(), identifier)
        }else{
            MCFloatConcrete(value.toFloat(), identifier)
        }
    }

    constructor(coo: PosDimension, value: Number) : super(coo){
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
        val qwq = PosDimension(this)
        if(replace) replacedBy(qwq)
        return qwq
    }

}