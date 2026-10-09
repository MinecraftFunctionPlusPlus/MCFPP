package top.mcfpp.core.lang

import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.lib.SbObject
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Int is a typed access. Constants and live score locations share the same Place protocol. */
open class MCInt : MCNumber<Int>, OnScoreboard {
    override var type: MCFPPType = MCFPPBaseType.Int
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : super(curr, identifier)
    constructor(other: MCInt) : super(other)
    constructor(value: Int, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Integral(value.toLong()))
    }
    constructor(curr: FieldContainer, value: Int, identifier: String = TempPool.getVarIdentify()) : this(curr, identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Integral(value.toLong()))
    }
    constructor(other: MCInt, value: Int) : this(value, other.identifier)
    constructor(other: EnumVar) : this(other.identifier) {
        val ordinal = ((StorageAccess.snapshot(other) as? CompilerValue.Typed)?.payload as? CompilerValue.Record)
            ?.fields?.get("ordinal") as? CompilerValue.Integral
        if (ordinal == null) { LogProcessor.error("Enum ordinal requires a complete enum value"); isError = true }
        else StorageAccess.initializeLiteral(this, ordinal)
    }
    val value: Int get() = ((StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Integral)
        ?.value?.toInt() ?: error("Int Place has no complete value")
    override fun setObj(sbObject: SbObject): MCInt { this.sbObject = sbObject; return this }
    override fun clone() = MCInt(this)
    override fun doAssignedBy(b: Var<*>): MCInt {
        if (b.type != type) { LogProcessor.error("Cannot assign ${b.type} to int"); isError = true; return this }
        StorageAccess.write(this, b)
        return this
    }
    override fun assignCommand(a: MCNumber<*>): MCInt = doAssignedBy(a)
    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun canExplicitCast(type: MCFPPType): Boolean = true
    override fun implicitCast(type: MCFPPType): Var<*> = if (this.type == MCFPPBaseType.Int && type == MCFPPBaseType.Float)
        top.mcfpp.backend.NumericConversions.promoteToFloat(this) else super.implicitCast(type)
    private fun arithmetic(other: Var<*>, operation: String): Var<*> {
        if (other !is MCInt || other.type != MCFPPBaseType.Int) { LogProcessor.error("Int arithmetic requires int operands"); return UnknownVar(identifier).apply { isError = true } }
        val a = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload
        val b = (StorageAccess.snapshot(other) as? CompilerValue.Typed)?.payload
        if (symbol?.forceRuntime != true && other.symbol?.forceRuntime != true && a != null && b != null) PrimitiveEvaluation.binary(operation, a, b)?.let { return StorageAccess.literal(type, it) }
        val left = StorageAccess.intRegister(this)
        val right = StorageAccess.intRegister(other)
        val result = MCInt().apply { isTemp = true; setObj(SbObject.MCFPP_TEMP) }
        val destination = StorageLayout.Scoreboard(result.name, result.sbObject.toString())
        Function.addCommand(Command("scoreboard players operation ${destination.player} ${destination.objective} = ${left.player} ${left.objective}"))
        Function.addCommand(Command("scoreboard players operation ${destination.player} ${destination.objective} $operation= ${right.player} ${right.objective}"))
        return StorageAccess.publishScore(result, destination)
    }
    override fun plus(a: Var<*>) = arithmetic(a, "+")
    override fun minus(a: Var<*>) = arithmetic(a, "-")
    override fun times(a: Var<*>) = arithmetic(a, "*")
    override fun div(a: Var<*>) = arithmetic(a, "/")
    override fun rem(a: Var<*>) = arithmetic(a, "%")
    private fun comparison(other: Var<*>, operation: String): Var<*> {
        if (other !is MCInt || other.type != MCFPPBaseType.Int) { LogProcessor.error("Int comparison requires int operands"); return UnknownVar(identifier).apply { isError = true } }
        val a = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload
        val b = (StorageAccess.snapshot(other) as? CompilerValue.Typed)?.payload
        if (symbol?.forceRuntime != true && other.symbol?.forceRuntime != true && a != null && b != null) PrimitiveEvaluation.binary(operation, a, b)?.let { return StorageAccess.literal(MCFPPBaseType.Bool, it) }
        val left = StorageAccess.intRegister(this)
        val right = StorageAccess.intRegister(other)
        val result = top.mcfpp.core.lang.bool.ScoreBool()
        Function.addCommand("execute store success score ${result.name} ${result.boolObject} ${if (operation == "!=") "unless" else "if"} score ${left.player} ${left.objective} ${if (operation == "==" || operation == "!=") "=" else operation} ${right.player} ${right.objective}")
        return StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
    }
    override fun isBigger(a: Var<*>) = comparison(a, ">")
    override fun isSmaller(a: Var<*>) = comparison(a, "<")
    override fun isSmallerOrEqual(a: Var<*>) = comparison(a, "<=")
    override fun isBiggerOrEqual(a: Var<*>) = comparison(a, ">=")
    override fun isEqual(a: Var<*>) = comparison(a, "==")
    override fun isNotEqual(a: Var<*>) = comparison(a, "!=")
    override fun inRange(a: Var<*>): Var<*> {
        if (a !is RangeVar || !a.isIntRange()) { LogProcessor.error("Int membership requires an int range"); return UnknownVar(identifier).apply { isError = true } }
        val register = StorageAccess.intRegister(this)
        val result = top.mcfpp.core.lang.bool.ScoreBool()
        Function.addCommands(Command("execute store success score ${result.name} ${result.boolObject} if score ${register.player} ${register.objective} matches").build(a.toCommandPart()).buildMacroFunction())
        return StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
    }
    override fun getTempVar(): MCInt = StorageAccess.capture(this) as MCInt
    override fun storeToStack() { StorageAccess.materialize(this) }
    override fun getFromStack() {
        val score = StorageAccess.intRegister(this)
        name = score.player
    }
    override fun toNBTVar(): NBTBasedData = StorageAccess.view(this, MCFPPNBTType.NBT, diagnose = false) as NBTBasedData
}
