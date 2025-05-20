package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.model.CanSelectMember
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

class Pos3Var: ConcreteVar<Pos3Var, ArrayList<PosDimension>> {

    var x: PosDimension
    var y: PosDimension
    var z: PosDimension

    override var type: MCFPPType = MCFPPBaseType.Pos3

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier, arrayListOf()){
        this.x = PosDimension("", 0, identifier)
        this.y = PosDimension("", 0, identifier)
        this.z = PosDimension("", 0, identifier)
        value = arrayListOf(x, y, z)
    }

    constructor(b: Pos3Var) : super(b){
        x = PosDimension(b.x)
        y = PosDimension(b.y)
        z = PosDimension(b.z)
        value = arrayListOf(x, y, z)
    }

    override fun clone(): Pos3Var {
        return Pos3Var(this)
    }

    override fun doAssignedBy(b: Var<*>): Pos3Var {
        return when (b) {
            is Pos3Var -> {
                x = x.assignedBy(b.x)
                y = y.assignedBy(b.y)
                z = z.assignedBy(b.z)
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
        return Command.buildAll(x,y,z)
    }

    override fun replaceMemberVar(v: Var<*>) {
        v as PosDimension
        when(v.identifier){
            "x" -> {
                x = v
                value[0] = v
            }
            "y" -> {
                y = v
                value[1] = v
            }
            "z" -> {
                z = v
                value[2] = v
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if(!super.equals(other)) return false
        return x == (other as Pos3Var).x && y == other.y && z == other.z
    }
}

class Pos2Var: ConcreteVar<Pos2Var, ArrayList<PosDimension>> {

    override var type: MCFPPType = MCFPPBaseType.Pos2

    var x: PosDimension
    var z: PosDimension

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier, arrayListOf()){
        x = PosDimension("",0, identifier)
        z = PosDimension("",0, identifier)
        value = arrayListOf(x, z)
    }

    constructor(b: Pos2Var) : super(b){
        x = PosDimension(b.x)
        z = PosDimension(b.z)
        value = arrayListOf(x, z)
    }

    override fun clone(): Pos2Var {
        return Pos2Var(this)
    }

    override fun doAssignedBy(b: Var<*>): Pos2Var {
        return when (b) {
            is Pos2Var -> {
                x = x.assignedBy(b.x)
                z = z.assignedBy(b.z)
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
        return Command.buildAll(x, z)
    }

    override fun replaceMemberVar(v: Var<*>) {
        v as PosDimension
        when(v.identifier){
            "x" -> {
                x = v
                value[0] = v
            }
            "z" -> {
                z = v
                value[1] = v
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if(!super.equals(other)) return false
        return x == (other as Pos2Var).x && z == other.z
    }
}

open class PosDimension: ConcreteVar<PosDimension, Pair<String, Number>> {

    val prefix get() = value.first

    val number get() = value.second

    override var type: MCFPPType = MCFPPPrivateType.MCFPPCoordinateDimension

    @Suppress("SuspiciousVarProperty")
    override var parent: CanSelectMember? = null
        get() = super.parent

    /**
     * 创建一个int类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        prefix: String,
        number: Number,
        identifier: String = TempPool.getVarIdentify()
    ) : super(identifier, prefix to number) {
        this.identifier = identifier
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: PosDimension) : super(b){
        value = b.value
    }

    override fun doAssignedBy(b: Var<*>): PosDimension {
        return when (b) {
            is PosDimension -> {
                value = b.value
                return this
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

    override fun clone(): PosDimension {
        return PosDimension(this)
    }

    override fun getTempVar(): PosDimension {
        return PosDimension(value.first, value.second, TempPool.getVarIdentify())
    }

    /**
     * 返回此坐标维度作为命令部分的表示。可能为宏函数，需要[Command.buildMacroFunction]转换
     */
    override fun toCommandPart(): Command{
        val c = Command(prefix)
        if(number != 0.toDouble()){
            c.build(number.toString(), false)
        }
        return c
    }

    override fun equals(other: Any?): Boolean {
        if(other !is PosDimension) return false
        if(this === other) return true
        return value == other.value
    }

}