package top.mcfpp.core.lang

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.backend.LegacyFloatComparison
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.lib.SbObject
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** A typed access whose value and four-component producer live exclusively in Place facts/layout. */
class MCFloat : MCNumber<Float> {
    var sign: MCInt
    var int0: MCInt
    var int1: MCInt
    var exp: MCInt
    override var type: MCFPPType = MCFPPBaseType.Float
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
    }
    constructor(curr: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        name = curr.prefix + identifier
        sign = MCInt(name).setObj(SbObject.MCS_float_sign) as MCInt
        int0 = MCInt(name).setObj(SbObject.MCS_float_int0) as MCInt
        int1 = MCInt(name).setObj(SbObject.MCS_float_int1) as MCInt
        exp = MCInt(name).setObj(SbObject.MCS_float_exp) as MCInt
    }
    constructor(other: MCFloat) : super(other) {
        sign = MCInt(other.sign); int0 = MCInt(other.int0); int1 = MCInt(other.int1); exp = MCInt(other.exp)
    }
    constructor(value: Float, identifier: String = TempPool.getVarIdentify()) : this(identifier) { initialize(value) }
    constructor(curr: FieldContainer, value: Float, identifier: String = TempPool.getVarIdentify()) : this(curr, identifier) { initialize(value) }
    constructor(other: MCFloat, value: Float) : this(other) { initialize(value) }
    private fun initialize(value: Float) {
        if (!value.isFinite()) { LogProcessor.error("Float input must be finite"); isError = true }
        else StorageAccess.initializeLiteral(this, CompilerValue.FloatBits(value.toRawBits()))
    }
    val value: Float get() = ((StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.FloatBits)
        ?.let { Float.fromBits(it.bits) } ?: error("Float has no complete compile-time value")
    override fun doAssignedBy(b: Var<*>): MCFloat { StorageAccess.write(this, b); return this }
    override fun assignCommand(a: MCNumber<*>): MCFloat { StorageAccess.write(this, a); return this }
    override fun clone() = MCFloat(this)
    override fun getTempVar(): Var<*> {
        val captured = StorageAccess.capture(this)
        return if (captured.isError) captured else StorageAccess.read(captured)
    }
    override fun storeToStack() { StorageAccess.materialize(this) }
    override fun getFromStack() { StorageAccess.invalidateReads(listOf(this)); if (!FloatProviders.enabled) registers() }
    override fun explicitCast(type: MCFPPType) = StorageAccess.view(this, type)
    override fun canExplicitCast(type: MCFPPType) = true
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = null to true

    private fun registers(): List<StorageLayout.Scoreboard>? {
        requireLegacyBackend()
        return StorageAccess.legacyFloatRegisters(this)
    }
    fun loadWork(): MCFloat? {
        val source = registers() ?: return null
        listOf("float_sign", "float_int0", "float_int1", "float_exp").zip(source).forEach { (target, score) ->
            Function.addCommand("scoreboard players operation $target int = ${score.player} ${score.objective}")
        }
        return StorageAccess.publishLegacyFloat(ssObj.physicalTemporary(), workComponents())
    }
    fun toTempEntity(): MCFloat {
        if (FloatProviders.enabled) return StorageAccess.capture(this) as MCFloat
        val source = registers() ?: return MCFloat().apply { isError = true }
        val target = listOf(SbObject.Math_float_sign, SbObject.Math_float_int0, SbObject.Math_float_int1, SbObject.Math_float_exp)
        target.zip(source).forEach { (objective, score) ->
            Function.addCommand("scoreboard players operation $tempFloatEntityUUID $objective = ${score.player} ${score.objective}")
        }
        return StorageAccess.publishLegacyFloat(tempFloat.physicalTemporary(), target.map { StorageLayout.Scoreboard(tempFloatEntityUUID, it.toString()) })
    }
    internal fun physicalTemporary() = MCFloat().also {
        it.sign = MCInt(sign); it.int0 = MCInt(int0); it.int1 = MCInt(int1); it.exp = MCInt(exp)
    }
    private fun arithmetic(other: MCFloat, operation: String, helper: String): Var<*> {
        if (isError || other.isError) return MCFloat().apply { isError = true }
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, other, operation)
        if (other.toTempEntity().isError || loadWork() == null) return MCFloat().apply { isError = true }
        Function.addCommand("execute as $tempFloatEntityUUID run function math.float:hpo/float/$helper")
        return captureWorkResult()
    }
    override fun plus(a: Var<*>) = arithmetic(a as MCFloat, "+", "_add")
    override fun minus(a: Var<*>) = arithmetic(a as MCFloat, "-", "_rmv")
    override fun times(a: Var<*>) = arithmetic(a as MCFloat, "*", "_mult")
    override fun div(a: Var<*>) = arithmetic(a as MCFloat, "/", "_div")
    override fun rem(a: Var<*>): Var<*> {
        if (isError || a.isError) return MCFloat().apply { isError = true }
        if (FloatProviders.enabled) return FloatProviders.arithmetic(this, a as MCFloat, "%")
        LogProcessor.error("Float remainder has no implementation for the legacy component backend")
        return MCFloat().apply { isError = true }
    }
    private fun compare(other: MCFloat, operation: String): Var<*> {
        if (isError || other.isError) return ScoreBool().apply { isError = true }
        if (FloatProviders.enabled) return FloatProviders.compare(this, other, operation)
        val left = registers() ?: return ScoreBool().apply { isError = true }
        val right = other.registers() ?: return ScoreBool().apply { isError = true }
        fun components(scores: List<StorageLayout.Scoreboard>) = LegacyFloatComparison.Components(
            "${scores[0].player} ${scores[0].objective}", "${scores[1].player} ${scores[1].objective}",
            "${scores[2].player} ${scores[2].objective}", "${scores[3].player} ${scores[3].objective}")
        val comparison = MCInt()
        val result = ScoreBool()
        LegacyFloatComparison.emit(components(left), components(right), operation,
            "${comparison.name} ${comparison.sbObject}", "${result.name} ${result.boolObject}", Function::addCommand)
        return StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
    }
    override fun isBigger(a: Var<*>) = compare(a as MCFloat, ">")
    override fun isSmaller(a: Var<*>) = compare(a as MCFloat, "<")
    override fun isSmallerOrEqual(a: Var<*>) = compare(a as MCFloat, "<=")
    override fun isBiggerOrEqual(a: Var<*>) = compare(a as MCFloat, ">=")
    override fun isEqual(a: Var<*>) = compare(a as MCFloat, "==")
    override fun isNotEqual(a: Var<*>) = compare(a as MCFloat, "!=")
    fun negate(): Var<*> {
        if (isError) return this
        if (FloatProviders.enabled) return FloatProviders.negate(this)
        if (loadWork() == null) return MCFloat().apply { isError = true }
        Function.addCommand("scoreboard players operation float_sign int *= -1 int")
        return captureWorkResult()
    }
    companion object {
        internal fun requireLegacyBackend() { if (!FloatProviders.enabled) Project.enableModulePackage("math.float", "stdlib") }
        const val tempFloatEntityUUID = "53aa19cc-a067-402b-8ba1-9328cc5fb6c1"
        const val tempFloatEntityUUIDNBT = "[I;1403656652,-1603846101,-1952345304,-866142527]"
        fun floatToMCFloat(float: Float): Array<Int> {
            require(float.isFinite()) { "Legacy float encoding requires a finite input" }
            if (float == 0f) return arrayOf(0, 0, 0, 0)
            val absolute = float.toDouble().absoluteValue
            var exponent = floor(log10(absolute)).toInt()
            var mantissa = round(absolute * 10.0.pow(7 - exponent)).toInt()
            if (mantissa == 100_000_000) { mantissa /= 10; exponent++ }
            return arrayOf(if (float < 0) -1 else 1, mantissa / 10000, mantissa % 10000, exponent + 1)
        }
        private fun workComponents() = listOf("float_sign", "float_int0", "float_int1", "float_exp").map { StorageLayout.Scoreboard(it, "int") }
        val ssObj = MCFloat().apply {
            sign = MCInt("float_sign").setObj(SbObject.Math_int) as MCInt
            int0 = MCInt("float_int0").setObj(SbObject.Math_int) as MCInt
            int1 = MCInt("float_int1").setObj(SbObject.Math_int) as MCInt
            exp = MCInt("float_exp").setObj(SbObject.Math_int) as MCInt
        }
        val tempFloat = MCFloat().apply {
            sign = MCInt(tempFloatEntityUUID).setObj(SbObject.Math_float_sign) as MCInt
            int0 = MCInt(tempFloatEntityUUID).setObj(SbObject.Math_float_int0) as MCInt
            int1 = MCInt(tempFloatEntityUUID).setObj(SbObject.Math_float_int1) as MCInt
            exp = MCInt(tempFloatEntityUUID).setObj(SbObject.Math_float_exp) as MCInt
        }
        fun captureWorkResult(identifier: String = TempPool.getVarIdentify()): MCFloat {
            val result = MCFloat(identifier)
            val components = listOf(result.sign, result.int0, result.int1, result.exp).map { StorageLayout.Scoreboard(it.name, it.sbObject.toString()) }
            components.zip(workComponents()).forEach { (target, source) ->
                Function.addCommand("scoreboard players operation ${target.player} ${target.objective} = ${source.player} ${source.objective}")
            }
            return StorageAccess.publishLegacyFloat(result, components)
        }
        fun ssObjToVar(identifier: String = TempPool.getVarIdentify()) = captureWorkResult(identifier)
    }
}
