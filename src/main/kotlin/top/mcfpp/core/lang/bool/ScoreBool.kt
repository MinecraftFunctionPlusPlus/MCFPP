package top.mcfpp.core.lang.bool

import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.lib.SbObject
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.function.Function
import top.mcfpp.util.TempPool

open class ScoreBool : BaseBool, OnScoreboard {
    override var isDataOnly = false
    override var name: String
    var boolObject: SbObject = SbObject.MCFPP_boolean
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier) { name = identifier }
    constructor(container: FieldContainer, identifier: String = TempPool.getVarIdentify()) : this(identifier) { name = container.prefix + identifier }
    constructor(source: ScoreBool) : super(source) { name = source.name; boolObject = source.boolObject; isDataOnly = source.isDataOnly }
    constructor(value: Boolean, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Bool(value))
    }
    constructor(container: FieldContainer, value: Boolean, identifier: String = TempPool.getVarIdentify()) : this(container, identifier) {
        StorageAccess.initializeLiteral(this, CompilerValue.Bool(value))
    }
    constructor(source: ScoreBool, value: Boolean) : this(value, source.identifier)
    val value: Boolean get() = ((StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Bool)
        ?.value ?: error("Boolean has no complete immutable value")
    override fun setObj(sbObject: SbObject): ScoreBool { boolObject = sbObject; return this }
    override fun doAssignedBy(source: Var<*>): ScoreBool = StorageAccess.write(this, source) as ScoreBool
    fun assignCommand(source: ScoreBool): ScoreBool = doAssignedBy(source)
    override fun clone() = ScoreBool(this)
    override fun getTempVar(): ScoreBool = StorageAccess.capture(this) as ScoreBool
    override fun storeToStack() = StorageAccess.materialize(this)
    override fun getFromStack() { StorageAccess.booleanRegister(this) }
    override fun toCommandPart(): Command {
        val score = StorageAccess.booleanRegister(this)
        return Command("score ${score.player} ${score.objective} matches 1")
    }
    override fun toScoreBool(replace: Boolean): ScoreBool = StorageAccess.read(this) as ScoreBool
    override fun toNBTVar(): top.mcfpp.core.lang.nbt.NBTBasedData =
        StorageAccess.view(this, top.mcfpp.type.MCFPPNBTType.NBT, diagnose = false) as top.mcfpp.core.lang.nbt.NBTBasedData
    fun asIntVar(): MCInt {
        val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Bool
        if (closed != null && symbol?.forceRuntime != true) return MCInt(if (closed.value) 1 else 0)
        val source = StorageAccess.booleanRegister(this)
        val result = MCInt().apply { setObj(SbObject.MCFPP_TEMP) }
        val score = StorageLayout.Scoreboard(result.name, result.sbObject.toString())
        Function.addCommand("scoreboard players operation ${score.player} ${score.objective} = ${source.player} ${source.objective}")
        return StorageAccess.publishScore(result, score)
    }
    private fun produce(condition: Command): ScoreBool {
        val result = ScoreBool().apply { boolObject = SbObject.MCFPP_TEMP }
        val score = StorageLayout.Scoreboard(result.name, result.boolObject.toString())
        Function.addCommand("scoreboard players set ${score.player} ${score.objective} 0")
        Function.addCommand(Command("execute").build(condition).build("run scoreboard players set ${score.player} ${score.objective} 1"))
        return StorageAccess.publishBoolean(result, score)
    }
    override fun negation(): Var<*> {
        val closed = (StorageAccess.snapshot(this) as? CompilerValue.Typed)?.payload as? CompilerValue.Bool
        return if (closed != null && symbol?.forceRuntime != true) ScoreBool(!closed.value) else produce(Command("unless").build(toCommandPart()))
    }
    override fun isEqual(a: Var<*>): Var<*> {
        val other = StorageAccess.read(a)
        if (other.isError) return other
        if (other !is ScoreBool) return super.isEqual(a)
        val leftScore = StorageAccess.booleanRegister(this)
        val rightScore = StorageAccess.booleanRegister(other)
        return produce(Command("if score ${leftScore.player} ${leftScore.objective} = ${rightScore.player} ${rightScore.objective}"))
    }
    override fun isNotEqual(a: Var<*>): Var<*> {
        val equal = isEqual(a)
        return if (equal.isError) equal else (equal as ScoreBool).negation()
    }
    override fun and(a: Var<*>): Var<*> {
        val other = (StorageAccess.read(a) as? BaseBool)?.toScoreBool(false) ?: return UnknownVar(identifier)
        return produce(Command("if").build(toCommandPart()).build("if").build(other.toCommandPart()))
    }
    override fun or(a: Var<*>): Var<*> {
        val other = (StorageAccess.read(a) as? BaseBool)?.toScoreBool(false) ?: return UnknownVar(identifier)
        val leftFalse = negation() as ScoreBool
        val rightFalse = other.negation() as ScoreBool
        return (leftFalse.and(rightFalse) as ScoreBool).negation()
    }
}
