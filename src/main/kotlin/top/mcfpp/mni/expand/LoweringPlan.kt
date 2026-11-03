package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/** The bridge supplies physical layout/flow services for this explicit compiler target. */
internal data class LoweringTarget(
    val function: FunctionKey,
    val executionContext: ExecutionContextId? = null,
    val domain: PlanDomainStamp? = null
)

/** Versions are supplied by declaration/layout preparation, never inferred from an arena/session. */
internal class PlanDomainStamp(
    typeDomains: Set<Long>,
    val layoutVersion: String? = null,
    val targetVersion: String? = null
) {
    val typeDomains: Set<Long> = immutableSet(typeDomains)
    val complete: Boolean get() = typeDomains.size == 1 && layoutVersion != null && targetVersion != null
    init {
        require(layoutVersion == null || layoutVersion.isNotBlank())
        require(targetVersion == null || targetVersion.isNotBlank())
    }
    override fun equals(other: Any?): Boolean = other is PlanDomainStamp &&
        typeDomains == other.typeDomains &&
        layoutVersion == other.layoutVersion &&
        targetVersion == other.targetVersion
    override fun hashCode(): Int = 31 * (31 * typeDomains.hashCode() + (layoutVersion?.hashCode() ?: 0)) +
        (targetVersion?.hashCode() ?: 0)
}

internal data class PlanType(
    val key: TypeKey,
    val name: String,
    val kind: TypeKind,
    val stableSourceId: String? = null,
    val scalarShape: ConstantShape? = null
) {
    companion object {
        fun from(type: TypeRef<*>): PlanType {
            val descriptor = type as ImmutableTypeRef<*>
            require(descriptor.graph?.owns(descriptor) != false) { "Noncanonical frozen type" }
            val scalar = descriptor.graph?.constantSchema(descriptor.key) as? ConstantSchema.Scalar
            return PlanType(descriptor.key, descriptor.name(), descriptor.kind, descriptor.stableSourceId, scalar?.shape)
        }
    }
}

/** Closed plan data: even nested type constants carry detached identity rather than a TypeRef. */
internal sealed interface PlanConstant {
    data class Integral(val value: Long) : PlanConstant
    data class FloatBits(val bits: Int) : PlanConstant
    data class DoubleBits(val bits: Long) : PlanConstant
    data class Bool(val value: Boolean) : PlanConstant
    data class Text(val value: String) : PlanConstant
    data class Nbt(val snbt: String) : PlanConstant
    data class TypeValue(val type: PlanType) : PlanConstant
    object NullValue : PlanConstant
    class Sequence(elements: List<PlanConstant>) : PlanConstant {
        val elements = immutableList(elements.map(::snapshot))
        override fun equals(other: Any?): Boolean = other is Sequence && elements == other.elements
        override fun hashCode(): Int = elements.hashCode()
    }
    class Record(fields: Map<String, PlanConstant>) : PlanConstant {
        val fields = immutableMap(fields.toSortedMap().mapValues { snapshot(it.value) })
        override fun equals(other: Any?): Boolean = other is Record && fields == other.fields
        override fun hashCode(): Int = fields.hashCode()
    }
    companion object {
        fun detach(value: ConstantValue): PlanConstant = when (value) {
            is ConstantValue.Integral -> Integral(value.value)
            is ConstantValue.FloatBits -> FloatBits(value.bits)
            is ConstantValue.DoubleBits -> DoubleBits(value.bits)
            is ConstantValue.Bool -> Bool(value.value)
            is ConstantValue.Text -> Text(value.value)
            is ConstantValue.Nbt -> Nbt(value.snbt)
            is ConstantValue.TypeValue -> TypeValue(PlanType.from(value.type))
            ConstantValue.NullValue -> NullValue
            is ConstantValue.Sequence -> Sequence(value.elements.map(::detach))
            is ConstantValue.Record -> Record(value.fields.mapValues { detach(it.value) })
        }
        private fun snapshot(value: PlanConstant): PlanConstant = when (value) {
            is Sequence -> Sequence(value.elements)
            is Record -> Record(value.fields)
            else -> value
        }
    }
}
internal data class PlanReadonlyArgument(val type: PlanType, val value: PlanConstant, val source: SourceSpan)
internal data class PlanTemp(val index: Int, val type: PlanType)
internal data class PlanBlockId(val index: Int)
internal data class PlanLocationId(val index: Int, val type: PlanType)
internal data class PlanSymbol(val index: Int, val binding: SymbolId)
internal data class PlanScope(val index: Int, val binding: ScopeId, val parent: ScopeId?)

/** Local plan indices are stable; binding is only an adapter lookup key. */
internal data class PlanContextId(val index: Int)
internal data class PlanContextRegionId(val index: Int)
internal data class PlanExecutionFrame(
    val id: PlanContextId,
    val binding: ExecutionContextId,
    val executor: PlanContextId,
    val position: PlanContextId,
    val rotation: PlanContextId,
    val scope: PlanScope,
    val dimension: PlanContextId = position
)
internal data class PlanEntity(val executor: PlanContextId, val scope: PlanScope, val site: PlanSite)
internal class PlanEntityQuery(
    val selection: EntitySelection,
    val entityType: String?,
    tags: List<String>,
    val limit: Int?,
    val sort: EntitySort?
) {
    val tags = immutableList(tags)
}
internal sealed interface PlanPositionNode {
    data class Absolute(val x: Double, val y: Double, val z: Double) : PlanPositionNode
    data class Current(val component: PlanContextId) : PlanPositionNode
    data class Entity(val entity: PlanEntity) : PlanPositionNode
    data class Offset(val base: PlanPosition, val x: Double, val y: Double, val z: Double) : PlanPositionNode
    data class LocalOffset(
        val base: PlanPosition,
        val rotation: PlanRotation,
        val left: Double,
        val up: Double,
        val forward: Double
    ) : PlanPositionNode
}
internal data class PlanPosition(val node: PlanPositionNode, val scope: PlanScope, val site: PlanSite)
internal sealed interface PlanRotationNode {
    data class Absolute(val yaw: Double, val pitch: Double) : PlanRotationNode
    data class Current(val component: PlanContextId) : PlanRotationNode
    data class Entity(val entity: PlanEntity) : PlanRotationNode
}
internal data class PlanRotation(val node: PlanRotationNode, val scope: PlanScope, val site: PlanSite)
internal data class PlanBlockPosition(val position: PlanPosition, val scope: PlanScope, val site: PlanSite)
internal class PlanBlockPredicate(val resource: String, val isTag: Boolean, states: Map<String, String>) {
    val states = immutableMap(states.toSortedMap())
}
internal sealed interface PlanScoreTarget {
    data class Fixed(val holder: String) : PlanScoreTarget
    data class Entity(val entity: PlanEntity) : PlanScoreTarget
}
/** The executor component belongs to the original address, independently of the ambient block. */
internal data class PlanScoreAddress(
    val target: PlanScoreTarget,
    val objective: String,
    val type: PlanType,
    val frame: PlanExecutionFrame?,
    val scope: PlanScope
)
internal sealed interface PlanContextChange {
    data class ExecuteAs(val targets: PlanEntityQuery, val self: PlanEntity) : PlanContextChange
    data class At(val position: PlanPosition) : PlanContextChange
    data class Rotated(val rotation: PlanRotation) : PlanContextChange
}
internal data class PlanContextRegion(
    val id: PlanContextRegionId,
    val parent: PlanExecutionFrame,
    val frame: PlanExecutionFrame,
    val scope: PlanScope,
    val change: PlanContextChange,
    val body: PlanBlockId,
    val after: PlanBlockId,
    val site: PlanSite
)

/** Inner-first regions abandoned by a nonlocal edge, including any active entity iterator. */
internal class PlanContextUnwind(regions: List<PlanContextRegionId> = emptyList()) {
    val regions = immutableList(regions)
}

/** Source identities are lookup keys for the bridge, never generated resource names. */
internal class PlanSite(val callSite: SourceSpan, val generatedFrom: SourceSpan?, frames: List<ExpansionOriginFrame>) {
    val frames = immutableList(frames)
}

internal data class PlanField(
    val key: FieldKey,
    val name: String,
    val type: PlanType,
    val writable: Boolean,
    val ownerType: PlanType? = null
)
internal sealed interface PlanLocationStep {
    data class Field(val field: PlanField) : PlanLocationStep
    data class Index(val value: Int) : PlanLocationStep
    data class Key(val value: String) : PlanLocationStep
    data class CapturedIndex(val symbol: PlanSymbol) : PlanLocationStep
    data class CapturedKey(val symbol: PlanSymbol) : PlanLocationStep
}

/** Captured path components are read by CaptureLocation at that operation's execution position. */
internal class PlanLocation(
    val root: PlanSymbol,
    steps: List<PlanLocationStep>,
    val type: PlanType,
    val executionContext: ExecutionContextId?,
    val writable: Boolean,
    val capturedBase: PlanLocationId? = null
) {
    val steps = immutableList(steps)
}

internal data class PlanParameter(val name: String, val type: PlanType, val mode: ParamMode)
internal enum class PlanCallKind { ORDINARY, LEGACY_NATIVE, HELPER }
internal class PlanFunction(
    val key: FunctionKey,
    val name: String,
    val kind: PlanCallKind,
    parameters: List<PlanParameter>,
    val resultType: PlanType,
    readonlyParameters: List<PlanParameter> = emptyList()
) {
    val parameters = immutableList(parameters)
    val readonlyParameters = immutableList(readonlyParameters)
}

internal sealed interface PlanArgument {
    data class Value(val value: PlanTemp) : PlanArgument
    /** An address, including frozen base/path/context; aliases retain their original storage. */
    data class Reference(val location: PlanLocationId) : PlanArgument
}

/** All operations execute once, in order, whenever their basic block is entered. */
internal sealed interface PlanOperation {
    val site: PlanSite
    data class Literal(val result: PlanTemp, val value: PlanConstant, override val site: PlanSite) : PlanOperation
    data class CaptureLocation(val result: PlanLocationId, val location: PlanLocation, override val site: PlanSite) : PlanOperation
    /** Captures the original score address. Reads of a missing score produce zero each time,
     * and writes/REF calls use this same address without copying back a temporary value. */
    data class CaptureScore(val result: PlanLocationId, val address: PlanScoreAddress, override val site: PlanSite) : PlanOperation
    data class Read(val result: PlanTemp, val location: PlanLocationId, override val site: PlanSite) : PlanOperation
    data class Write(val location: PlanLocationId, val value: PlanTemp, override val site: PlanSite) : PlanOperation
    data class DeclareLocal(val location: PlanLocation, val hint: String, val initialValue: PlanTemp, override val site: PlanSite) : PlanOperation
    data class Unary(val result: PlanTemp, val operator: UnaryOperator, val operand: PlanTemp, override val site: PlanSite) : PlanOperation
    /** AND/OR never occur here: their right operand is guarded by a Branch. */
    data class Binary(val result: PlanTemp, val operator: BinaryOperator, val left: PlanTemp, val right: PlanTemp, override val site: PlanSite) : PlanOperation
    data class Convert(val result: PlanTemp, val operand: PlanTemp, val kind: ConversionKind, override val site: PlanSite) : PlanOperation
    data class Field(val result: PlanTemp, val receiver: PlanTemp, val field: PlanField, override val site: PlanSite) : PlanOperation
    data class Index(val result: PlanTemp, val receiver: PlanTemp, val index: PlanTemp, override val site: PlanSite) : PlanOperation
    data class BlockMatches(
        val result: PlanTemp,
        val block: PlanBlockPosition,
        val predicate: PlanBlockPredicate,
        override val site: PlanSite
    ) : PlanOperation
    /** Mutable result registers implement region/short-circuit joins without rereading inputs. */
    data class Move(val result: PlanTemp, val value: PlanTemp, override val site: PlanSite) : PlanOperation
    /**
     * [receiver] is the original receiver address, captured before [arguments] by an earlier
     * CaptureLocation/CaptureScore operation. It is a reference, not a copied value, so a
     * member write still targets the caller's physical storage and execution context.
     * Null keeps global/static and existing receiverless plans compatible.
     */
    class Call(
        val result: PlanTemp?,
        val function: PlanFunction,
        arguments: List<PlanArgument>,
        override val site: PlanSite,
        readonlyArguments: List<PlanReadonlyArgument> = emptyList(),
        val receiver: PlanArgument.Reference? = null
    ) : PlanOperation {
        val arguments = immutableList(arguments)
        val readonlyArguments = immutableList(readonlyArguments)
    }
}

internal enum class PlanExitKind { BLOCK, EXPRESSION, RESULT, CALLBACK }
internal sealed interface PlanTerminator {
    val site: PlanSite
    data class Jump(
        val target: PlanBlockId,
        override val site: PlanSite,
        val unwind: PlanContextUnwind = PlanContextUnwind()
    ) : PlanTerminator
    data class Branch(
        val condition: PlanTemp,
        val ifTrue: PlanBlockId,
        val ifFalse: PlanBlockId,
        override val site: PlanSite,
        val ifTrueUnwind: PlanContextUnwind = PlanContextUnwind(),
        val ifFalseUnwind: PlanContextUnwind = PlanContextUnwind()
    ) : PlanTerminator
    /** Evaluate in the parent frame. As enumerates once and enters body for the first entity,
     * or after when empty. At/rotated enter their body exactly once. */
    data class ContextEnter(val region: PlanContextRegionId, override val site: PlanSite) : PlanTerminator
    /** Normal body completion: as selects its next entity or leaves; at/rotated leave once.
     * Each iteration restores the same parent position/rotation before establishing its self. */
    data class ContextNext(val region: PlanContextRegionId, override val site: PlanSite) : PlanTerminator
    /** CALLBACK exits exactly the validated root callback, never its caller. */
    data class Exit(
        val value: PlanTemp?,
        val kind: PlanExitKind,
        override val site: PlanSite,
        val unwind: PlanContextUnwind = PlanContextUnwind()
    ) : PlanTerminator
    /** An unreachable normal exit of a validator-proven non-returning region must not fall through. */
    data class Unreachable(val reason: String, override val site: PlanSite) : PlanTerminator
}

internal class PlanBasicBlock(
    val id: PlanBlockId,
    val scope: PlanScope,
    operations: List<PlanOperation>,
    val terminator: PlanTerminator,
    val executionFrame: PlanExecutionFrame? = null,
    contextRegions: List<PlanContextRegionId> = emptyList()
) {
    val operations = immutableList(operations)
    val contextRegions = immutableList(contextRegions)
}

/** Fully detached: no Code, Ref, FunctionRef, arena, builder or host callback survives lowering. */
internal class LoweringPlan internal constructor(
    val target: LoweringTarget,
    val entry: PlanBlockId,
    val resultType: PlanType?,
    blocks: List<PlanBasicBlock>,
    symbols: List<PlanSymbol>,
    scopes: List<PlanScope>,
    val domain: PlanDomainStamp = PlanDomainStamp(emptySet()),
    executionFrames: List<PlanExecutionFrame> = emptyList(),
    contextRegions: List<PlanContextRegion> = emptyList()
) {
    val blocks = immutableList(blocks)
    val symbols = immutableList(symbols)
    val scopes = immutableList(scopes)
    val executionFrames = immutableList(executionFrames)
    val contextRegions = immutableList(contextRegions)
}
