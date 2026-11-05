package top.mcfpp.mni.api

import java.util.Collections

/** Generated MCFPP code with a source location for compiler messages. */
sealed interface Code {
    /** Returns the source location associated with this code. */
    fun source(): SourceSpan
}
/** An expression that produces an MCFPP value of type [T] when the generated code runs. */
sealed interface Expr<T> : Code {
    /** Returns the MCFPP type of the expression result. */
    fun type(): TypeRef<T>
}
/** A generated statement, such as an assignment or a loop. */
sealed interface Stmt : Code
/** An ordered group of generated statements. Create one with [Quote.block]. */
sealed interface Block : Code {
    /** Returns the statements in execution order. */
    fun statements(): List<Stmt>
}
/** A writable local variable created by [CodeBuilder.local]. Each declaration receives its own generated name. */
sealed interface Local<T> : Ref<T> {
    /** Returns the suggested name used when the compiler names this variable. */
    fun hint(): String
}

internal fun <T> immutableList(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
internal fun <T> immutableSet(values: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
internal fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))

internal data class SessionId(val value: Long)
internal data class ScopeId(val session: SessionId, val value: Long)
internal data class SymbolId(val session: SessionId, val value: Long)
internal data class ExecutionContextId(val session: SessionId, val value: Long)
internal data class RegionId(val session: SessionId, val value: Long)
internal data class AddressId(val session: SessionId, val value: Long)
internal data class CallbackId(val universe: Long, val declaration: String)
internal data class ExpansionOriginFrame(val macroName: String, val callSite: SourceSpan)

internal class Origin(
    val callSite: SourceSpan,
    val generatedFrom: SourceSpan? = null,
    expansionFrames: List<ExpansionOriginFrame> = emptyList()
) {
    val expansionFrames: List<ExpansionOriginFrame> = immutableList(expansionFrames)
}

internal data class CodeMetadata(
    val sessionId: SessionId,
    val scopeId: ScopeId,
    val origin: Origin,
    val executionContextId: ExecutionContextId? = null
) {
    init {
        require(scopeId.session == sessionId) { "Scope belongs to another expansion session" }
        require(executionContextId == null || executionContextId.session == sessionId) { "Execution context belongs to another expansion session" }
    }
}

internal sealed interface OwnedCode : Code {
    val metadata: CodeMetadata
    override fun source(): SourceSpan = metadata.origin.generatedFrom ?: metadata.origin.callSite
}

internal sealed interface LocationStep {
    data class Field(val field: FieldRef<*>) : LocationStep
    data class Index(val value: Int) : LocationStep
    data class Key(val value: String) : LocationStep
    data class CapturedIndex(val symbol: SymbolId) : LocationStep
    data class CapturedKey(val symbol: SymbolId) : LocationStep
}

/** Root symbols are mapped to physical storage by the compiler bridge, never by macro authors. */
internal class NodeLocation(
    val root: SymbolId,
    steps: List<LocationStep> = emptyList(),
    val executionContextId: ExecutionContextId? = null,
    val writable: Boolean = true,
    val capturedBase: AddressId? = null
) {
    val steps: List<LocationStep> = immutableList(steps)
    init {
        require(executionContextId == null || executionContextId.session == root.session) { "Location context belongs to another session" }
        require(capturedBase == null || capturedBase.session == root.session) { "Captured address belongs to another session" }
        require(
            this.steps.all {
                when (it) {
                    is LocationStep.CapturedIndex -> it.symbol.session == root.session
                    is LocationStep.CapturedKey -> it.symbol.session == root.session
                    else -> true
                }
            }
        ) { "Captured location components belong to another session" }
    }
    override fun equals(other: Any?): Boolean = other is NodeLocation &&
        root == other.root &&
        steps == other.steps &&
        executionContextId == other.executionContextId &&
        capturedBase == other.capturedBase
    override fun hashCode(): Int = 31 * (
        31 * (31 * root.hashCode() + steps.hashCode()) +
            (executionContextId?.hashCode() ?: 0)
        ) + (capturedBase?.hashCode() ?: 0)
}

internal data class TypedConstant(val type: TypeRef<*>, val value: ConstantValue, val source: SourceSpan)

internal enum class UnaryOperator { NEGATE, NOT }
internal enum class BinaryOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO, EQ, NE, LT, LE, GT, GE, AND, OR }
internal enum class ConversionKind { IMPLICIT, EXPLICIT }

/** Payloads contain immutable handles, never builders, executable host callbacks or parser nodes. */
internal sealed interface CodeNode
internal sealed interface ExprNode : CodeNode {
    data class ScoreRead(val target: MinecraftScoreTarget, val objective: String) : ExprNode {
        init {
            requireMinecraftObjective(objective)
        }
    }
    data class BlockMatches(val block: BlockHandle, val predicate: BlockPredicate) : ExprNode
    data class Literal(val value: ConstantValue) : ExprNode
    data class Read(val location: NodeLocation) : ExprNode
    data class Unary(val operator: UnaryOperator, val operand: Expr<*>) : ExprNode
    data class Binary(val operator: BinaryOperator, val left: Expr<*>, val right: Expr<*>) : ExprNode
    data class Convert(val value: Expr<*>, val target: TypeRef<*>, val kind: ConversionKind) : ExprNode
    data class Field(val receiver: Expr<*>, val field: FieldRef<*>) : ExprNode
    data class Index(val receiver: Expr<*>, val index: Expr<*>) : ExprNode
    /**
     * [receiver] is the actual member/constructor receiver, kept as an original writable
     * reference so its physical location, type and execution context survive lowering.
     * It is captured once before the normal arguments; null keeps global/static and all
     * existing receiverless construction source-compatible.
     */
    class Call(
        val function: FunctionRef<*>,
        arguments: List<CallArgument>,
        readonlyArguments: List<TypedConstant> = emptyList(),
        val receiver: Ref<*>? = null
    ) : ExprNode {
        val arguments: List<CallArgument> = immutableList(arguments)
        val readonlyArguments: List<TypedConstant> = immutableList(readonlyArguments)
    }
    class MacroCall(val function: FunctionRef<*>, arguments: List<CallArgument>) : ExprNode {
        val arguments: List<CallArgument> = immutableList(arguments)
    }
    data class ExprBlock(val block: Block, val resultType: TypeRef<*>, val region: RegionId) : ExprNode
    class InlineCall(val callback: InlineFn<*>, arguments: List<InlineArgument>) : ExprNode {
        val arguments: List<InlineArgument> = immutableList(arguments)
    }
}

internal sealed interface StmtNode : CodeNode {
    data class ExecuteAs(
        val targets: EntitySet,
        val self: EntityHandle,
        val body: Block,
        val parentContext: ExecutionContextFrame,
        val context: ExecutionContextFrame
    ) : StmtNode {
        init {
            require(context.id != parentContext.id) { "executeAs requires a fresh context identity" }
            require(context == parentContext.withExecutor(context.id)) { "executeAs must change only the executor" }
            requireContextBody(body, context)
            val metadata = (body as OwnedCode).metadata
            requireMinecraftSession(metadata, targets, self)
            require(
                (self as ImmutableEntityHandle).executor == context.executor &&
                    self.metadata.executionContextId == context.id &&
                    self.metadata.scopeId == metadata.scopeId
            ) {
                "executeAs self must belong to its child scope and executor"
            }
        }
    }
    data class At(
        val position: PositionHandle,
        val body: Block,
        val parentContext: ExecutionContextFrame,
        val context: ExecutionContextFrame
    ) : StmtNode {
        init {
            require(context.id != parentContext.id) { "at requires a fresh context identity" }
            require(context == parentContext.withPosition(context.id)) { "at must change only the position" }
            requireContextBody(body, context)
            requireMinecraftSession((body as OwnedCode).metadata, position)
        }
    }
    data class Rotated(
        val rotation: RotationHandle,
        val body: Block,
        val parentContext: ExecutionContextFrame,
        val context: ExecutionContextFrame
    ) : StmtNode {
        init {
            require(context.id != parentContext.id) { "rotated requires a fresh context identity" }
            require(context == parentContext.withRotation(context.id)) { "rotated must change only the rotation" }
            requireContextBody(body, context)
            requireMinecraftSession((body as OwnedCode).metadata, rotation)
        }
    }
    data class CaptureAddress(val address: AddressId, val location: NodeLocation, val type: TypeRef<*>) : StmtNode {
        init {
            require(address.session == location.root.session) { "Captured address belongs to another session" }
        }
    }
    data class LocalDecl(val local: Local<*>, val initialValue: Expr<*>) : StmtNode
    data class Assign(val destination: Ref<*>, val expression: Expr<*>) : StmtNode
    data class Eval(val expression: Expr<*>) : StmtNode
    data class If(val condition: Expr<*>, val thenBlock: Block, val elseBlock: Block?) : StmtNode
    data class While(val condition: Expr<*>, val body: Block, val region: RegionId) : StmtNode
    data class ScopeBlock(val block: Block) : StmtNode
    data class CallbackReturn(val region: RegionId, val value: Expr<*>?) : StmtNode
    data class Yield(val region: RegionId, val value: Expr<*>) : StmtNode
    data class Break(val region: RegionId) : StmtNode
    data class Continue(val region: RegionId) : StmtNode
}

private fun requireContextBody(body: Block, context: ExecutionContextFrame) {
    require((body as OwnedCode).metadata.executionContextId == context.id) { "Context body belongs to another execution context" }
}

internal class BlockNode(statements: List<Stmt>) : CodeNode {
    val statements: List<Stmt> = immutableList(statements)
}

internal class ImmutableExpr<T>(
    override val metadata: CodeMetadata,
    private val expressionType: TypeRef<T>,
    val node: ExprNode
) : Expr<T>,
    OwnedCode {
    override fun type(): TypeRef<T> = expressionType
}

internal class ImmutableStmt(override val metadata: CodeMetadata, val node: StmtNode) :
    Stmt,
    OwnedCode

internal class ImmutableBlock(override val metadata: CodeMetadata, statements: List<Stmt>) :
    Block,
    OwnedCode {
    val node = BlockNode(statements)
    override fun statements(): List<Stmt> = node.statements
}

internal class ImmutableLocal<T>(
    override val metadata: CodeMetadata,
    private val localType: TypeRef<T>,
    val symbol: SymbolId,
    private val nameHint: String
) : Local<T>,
    OwnedCode {
    // A local owns a snapshot in the plan workspace. Its address is independent of the
    // executor used to initialize it; any scoped payload retains its separate borrow evidence.
    val location = NodeLocation(symbol)
    private val read: Expr<T> = ImmutableExpr(metadata, localType, ExprNode.Read(location))
    init {
        require(symbol.session == metadata.sessionId) { "Local belongs to another expansion session" }
    }
    override fun type(): TypeRef<T> = localType
    override fun hint(): String = nameHint
    override fun load(): Expr<T> = read
}
