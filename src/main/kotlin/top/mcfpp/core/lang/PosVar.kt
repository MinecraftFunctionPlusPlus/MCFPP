package top.mcfpp.core.lang

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueFacts
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.command.Command
import top.mcfpp.model.Member
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** A coordinate access never owns a second mutable copy of its dimensions. */
class Pos3Var : Var<Pos3Var> {
    override var type: MCFPPType = MCFPPBaseType.Pos3
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(other: Pos3Var) : super(other)
    override fun clone() = Pos3Var(this)
    var x: PosDimension get() = dimension("x", 0); set(v) { writeDimension(0, v) }
    var y: PosDimension get() = dimension("y", 1); set(v) { writeDimension(1, v) }
    var z: PosDimension get() = dimension("z", 2); set(v) { writeDimension(2, v) }
    var value: ArrayList<PosDimension>
        get() = arrayListOf(x, y, z)
        set(v) {
            require(v.size == 3)
            StorageAccess.initializeLiteral(this, CompilerValue.Sequence(v.map { StorageAccess.snapshot(it)
                ?: error("Coordinate dimension has no complete value") }))
        }
    private fun dimension(name: String, index: Int): PosDimension {
        val root = StorageAccess.ensure(this)
        return (StorageAccess.adapter(MCFPPPrivateType.MCFPPCoordinateDimension, name,
            root.copy(place = root.place.index(index), path = root.path.intIndex(index))) as PosDimension)
            .apply { parent = this@Pos3Var }
    }
    private fun writeDimension(index: Int, v: PosDimension) {
        val root = StorageAccess.ensure(this)
        val snapshot = StorageAccess.snapshot(v) ?: error("Coordinate dimension has no complete value")
        root.data.write(root.place.index(index), ValueFacts(TypeKnowledge.Exact(v.type.typeId), ValueKnowledge.Constant(snapshot)))
    }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = when (key) {
        "x" -> x to true; "y" -> y to true; "z" -> z to true; else -> null to true
    }
    override fun replaceMemberVar(v: Var<*>) { when (v.identifier) {
        "x" -> x = v as PosDimension; "y" -> y = v as PosDimension; "z" -> z = v as PosDimension
    } }
    override fun doAssignedBy(b: Var<*>): Pos3Var { StorageAccess.write(this, b); return this }
    override fun getTempVar(): Pos3Var = StorageAccess.capture(this) as Pos3Var
    override fun toCommandPart(): Command = Command.buildAll(x, y, z)
}

class Pos2Var : Var<Pos2Var> {
    override var type: MCFPPType = MCFPPBaseType.Pos2
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(other: Pos2Var) : super(other)
    override fun clone() = Pos2Var(this)
    var x: PosDimension get() = dimension("x", 0); set(v) { writeDimension(0, v) }
    var z: PosDimension get() = dimension("z", 1); set(v) { writeDimension(1, v) }
    var value: ArrayList<PosDimension>
        get() = arrayListOf(x, z)
        set(v) {
            require(v.size == 2)
            StorageAccess.initializeLiteral(this, CompilerValue.Sequence(v.map { StorageAccess.snapshot(it)
                ?: error("Coordinate dimension has no complete value") }))
        }
    private fun dimension(name: String, index: Int): PosDimension {
        val root = StorageAccess.ensure(this)
        return (StorageAccess.adapter(MCFPPPrivateType.MCFPPCoordinateDimension, name,
            root.copy(place = root.place.index(index), path = root.path.intIndex(index))) as PosDimension)
            .apply { parent = this@Pos2Var }
    }
    private fun writeDimension(index: Int, v: PosDimension) {
        val root = StorageAccess.ensure(this)
        val snapshot = StorageAccess.snapshot(v) ?: error("Coordinate dimension has no complete value")
        root.data.write(root.place.index(index), ValueFacts(TypeKnowledge.Exact(v.type.typeId), ValueKnowledge.Constant(snapshot)))
    }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = when (key) {
        "x" -> x to true; "z" -> z to true; else -> null to true
    }
    override fun replaceMemberVar(v: Var<*>) { when (v.identifier) { "x" -> x = v as PosDimension; "z" -> z = v as PosDimension } }
    override fun doAssignedBy(b: Var<*>): Pos2Var { StorageAccess.write(this, b); return this }
    override fun getTempVar(): Pos2Var = StorageAccess.capture(this) as Pos2Var
    override fun toCommandPart(): Command = Command.buildAll(x, z)
}

class PosDimension : Var<PosDimension> {
    override var type: MCFPPType = MCFPPPrivateType.MCFPPCoordinateDimension
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(prefix: String, number: Number, identifier: String = TempPool.getVarIdentify()) : this(identifier) {
        require(prefix in setOf("", "~", "^"))
        val numeric = when (number) {
            is Float -> CompilerValue.FloatBits(number.toRawBits())
            is Double -> CompilerValue.DoubleBits(number.toRawBits())
            else -> CompilerValue.Integral(number.toLong())
        }
        StorageAccess.initializeLiteral(this, CompilerValue.Sequence(listOf(CompilerValue.Text(prefix), numeric)))
    }
    constructor(other: PosDimension) : super(other)
    override fun clone() = PosDimension(this)
    private fun parts(): List<CompilerValue>? = ((StorageAccess.snapshot(this) as? CompilerValue.Typed)
        ?.payload as? CompilerValue.Sequence)?.elements
    val prefix: String get() = (parts()?.getOrNull(0) as? CompilerValue.Text)?.value ?: error("Uninitialized coordinate dimension")
    val number: Number get() = when (val v = parts()?.getOrNull(1)) {
        is CompilerValue.Integral -> v.value
        is CompilerValue.FloatBits -> Float.fromBits(v.bits)
        is CompilerValue.DoubleBits -> Double.fromBits(v.bits)
        else -> error("Uninitialized coordinate dimension")
    }
    val value: Pair<String, Number> get() = prefix to number
    override fun doAssignedBy(b: Var<*>): PosDimension { StorageAccess.write(this, b); return this }
    override fun getTempVar(): PosDimension = StorageAccess.capture(this) as PosDimension
    override fun toCommandPart(): Command {
        if (parts() == null) { LogProcessor.error("Coordinate dimension requires a complete value"); return Command() }
        return Command(prefix).apply { if (prefix.isEmpty() || number.toDouble() != 0.0) build(number.toString(), false) }
    }
}
