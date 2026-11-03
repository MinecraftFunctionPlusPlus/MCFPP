package top.mcfpp.core.lang.context

import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.lib.EntityFacts
import top.mcfpp.model.Member
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeRelations
import top.mcfpp.util.LogProcessor

/** Implemented by context lowering, which may preserve an ancestor binding before use. */
interface ContextCommandEncoder {
    fun executor(value: BoundExecutor): Command
    fun position(value: BoundPosition): Command

    /**
     * Resolve all operands together at the original call context. Implementations may emit
     * preparations there and wrap the result in an execution prefix; ordinary entity and
     * relative-coordinate operands must retain that original evaluation context.
     */
    fun operation(operands: List<ContextCommandOperand>, changesExecutor: Boolean = false, build: (List<Command>) -> Command): Command {
        if (changesExecutor) throw ContextAccessException("This command encoder cannot preserve operands across an executor change")
        return build(operands.map { operand -> when (operand) {
            is ContextCommandOperand.Executor -> executor(operand.value)
            is ContextCommandOperand.Position -> position(operand.value)
            is ContextCommandOperand.Entity -> operand.value.toEntityTargetCommand()
            is ContextCommandOperand.Coordinates -> operand.value.toCommandPart()
        } })
    }
}

sealed interface ContextCommandOperand {
    data class Executor(val value: BoundExecutor) : ContextCommandOperand
    data class Position(val value: BoundPosition) : ContextCommandOperand
    data class Entity(val value: EntityTargetAccess) : ContextCommandOperand
    data class Coordinates(val value: Var<*>) : ContextCommandOperand
}

/** Direct inheritance needs no materialization. Changed components require a richer encoder. */
class DirectContextCommandEncoder(private val current: () -> ExecutionContext) : ContextCommandEncoder {
    override fun executor(value: BoundExecutor): Command {
        value.requireDirectUseIn(current())
        return Command("@s")
    }

    override fun position(value: BoundPosition): Command {
        value.requireDirectUseIn(current())
        return Command("~ ~ ~")
    }
}

/** Marker consumed by controlled callback borrowing/escape analysis, including erased paths. */
interface ScopedContextValue {
    val contextScope: ContextScope?
    fun requireUsableIn(scope: ContextScope)
}

/** Adapters have no NBT payload. Copies retain a borrow rather than constructing a snapshot. */
abstract class ScopedContextVar<Self : ScopedContextVar<Self>>(
    identifier: String,
    private val declaredType: MCFPPType
) : Var<Self>(identifier), ScopedContextValue {
    final override var type: MCFPPType
        get() = declaredType
        set(value) {
            if (value != declaredType) throw ContextAccessException("Cannot erase a scoped context value to $value")
        }

    init {
        isConst = true
        isFinal = true
    }

    protected fun requireBoundScope(): ContextScope =
        contextScope?.also { it.requireActive() }
            ?: throw ContextAccessException("Context declaration '$identifier' has no active source binding")

    /** Local aliases and synchronous arguments can retain this exact binding. */
    fun borrow(scope: ContextScope, identifier: String = this.identifier): Self {
        requireUsableIn(scope)
        return clone().also { it.identifier = identifier }
    }

    final override fun canImplicitCast(type: MCFPPType): Boolean = this.type == type ||
        this.type is MCFPPExecutionContextType && type is MCFPPExecutionContextType &&
        TypeRelations.isSubtype(this.type, type)

    final override fun canExplicitCast(type: MCFPPType): Boolean = canImplicitCast(type)

    override fun implicitCast(type: MCFPPType): Var<*> = if (canImplicitCast(type)) this else rejectCast(type)
    final override fun explicitCast(type: MCFPPType): Var<*> = implicitCast(type)

    private fun rejectCast(type: MCFPPType): Var<*> {
        LogProcessor.error("Scoped context value '${this.type}' cannot be erased or stored as '$type'")
        return buildCastErrorVar(type)
    }

    final override fun doAssignedBy(b: Var<*>): Self =
        throw ContextAccessException("Context bindings are read-only; create a local borrow instead")

    final override fun getTempVar(): Self {
        requireBoundScope()
        val captured = top.mcfpp.analysis.StorageAccess.capture(this)
        @Suppress("UNCHECKED_CAST")
        return if (captured is ScopedContextVar<*>) captured as Self else clone().apply { isError = true }
    }

    final override fun storeToStack() {
        throw ContextAccessException("Scoped context values cannot escape into runtime storage")
    }

    final override fun getFromStack() {
        throw ContextAccessException("Scoped context values cannot be restored from runtime storage")
    }

    final override fun toNBTVar(): NBTBasedData {
        LogProcessor.error("Scoped context values have no NBT representation")
        return NBTBasedData(identifier).apply { isError = true }
    }

    override fun replaceMemberVar(v: Var<*>) {
        LogProcessor.error("Execution context members are read-only")
    }
}

class ExecutionContextVar(
    identifier: String,
    val context: ExecutionContext?,
    declaredType: MCFPPExecutionContextType = context?.let(ContextTypes::of) ?: ContextTypes.ExecutionContext,
    val encoder: ContextCommandEncoder? = null
) : ScopedContextVar<ExecutionContextVar>(identifier, declaredType) {
    init {
        require(context == null || TypeRelations.isSubtype(ContextTypes.of(context), declaredType)) {
            "Context binding does not match its declared context type"
        }
    }

    override val contextScope: ContextScope? get() = context?.scope
    override fun requireUsableIn(scope: ContextScope) = requireContext().requireUsableIn(scope)
    fun requireContext(): ExecutionContext = context?.also { it.scope.requireActive() }
        ?: throw ContextAccessException("Context declaration '$identifier' has no active source binding")

    fun bind(context: ExecutionContext, encoder: ContextCommandEncoder? = this.encoder): ExecutionContextVar =
        ExecutionContextVar(identifier, context, type as MCFPPExecutionContextType, encoder)

    override fun clone(): ExecutionContextVar =
        ExecutionContextVar(identifier, context, type as MCFPPExecutionContextType, encoder).also { it.setAs(this) }

    override fun implicitCast(type: MCFPPType): Var<*> =
        if (type is MCFPPExecutionContextType && canImplicitCast(type))
            ExecutionContextVar(identifier, context, type, encoder).also { it.setAs(this) }
        else super.implicitCast(type)

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val bound = requireContext()
        return when (key) {
            "executor" -> BoundExecutorVar(key, bound.executor, encoder).also { it.parent = this } to true
            "pos" -> BoundPositionVar(key, bound.pos, encoder).also { it.parent = this } to true
            else -> null to true
        }
    }

    override fun toCommandPart(): Command = throw ContextAccessException("ExecutionContext is not a command argument")
}

/** Implements the same entity-consumer contract as ordinary entity values. */
class BoundExecutorVar(
    identifier: String,
    val binding: BoundExecutor,
    val encoder: ContextCommandEncoder? = null
) : ScopedContextVar<BoundExecutorVar>(identifier, MCFPPEntityType.NormalSelector), EntityTargetAccess {
    override val contextScope: ContextScope get() = binding.scope
    override val entityFacts: EntityFacts get() = binding.facts
    override fun requireUsableIn(scope: ContextScope) = binding.requireUsableIn(scope)
    override fun clone(): BoundExecutorVar = BoundExecutorVar(identifier, binding, encoder).also { it.setAs(this) }

    override fun toEntityTargetCommand(): Command {
        requireBoundScope()
        return encoder?.executor(binding)
            ?: throw ContextAccessException("Bound executor requires context command lowering")
    }

    override fun toCommandPart(): Command = toEntityTargetCommand()

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> =
        top.mcfpp.core.lang.entity.EntityMembers.member(this, key, accessModifier)

    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                   accessModifier: Member.AccessModifier): Pair<top.mcfpp.model.function.Function, Boolean> =
        top.mcfpp.core.lang.entity.EntityMembers.data(this).getFunction(key, readOnlyArgs, normalArgs) to true
}

class BoundPositionVar(
    identifier: String,
    val binding: BoundPosition?,
    val encoder: ContextCommandEncoder? = null
) : ScopedContextVar<BoundPositionVar>(identifier, ContextTypes.Position) {
    override val contextScope: ContextScope? get() = binding?.scope
    override fun requireUsableIn(scope: ContextScope) = requireBinding().requireUsableIn(scope)
    fun requireBinding(): BoundPosition = binding?.also { it.scope.requireActive() }
        ?: throw ContextAccessException("Position declaration '$identifier' has no active source binding")
    fun bind(binding: BoundPosition, encoder: ContextCommandEncoder? = this.encoder): BoundPositionVar =
        BoundPositionVar(identifier, binding, encoder)
    override fun clone(): BoundPositionVar = BoundPositionVar(identifier, binding, encoder).also { it.setAs(this) }
    override fun toCommandPart(): Command = encoder?.position(requireBinding())
        ?: throw ContextAccessException("Bound position requires context command lowering")
}
