package top.mcfpp.core.lang.context

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.TypeId

/** Canonical source types; lexical identity is carried by a value, never by its type. */
class MCFPPExecutionContextType internal constructor(
    override val typeName: String,
    key: String,
    parent: MCFPPExecutionContextType? = null
) : MCFPPType(if (parent == null) arrayListOf() else arrayListOf(parent)) {
    override val typeId: TypeId = TypeId.Builtin(key)
    override val hasRuntimeRepresentation: Boolean get() = false

    override fun build(identifier: String, value: Any?): Var<*> = when (value) {
        null -> buildUnConcrete(identifier)
        is ExecutionContext -> ExecutionContextVar(identifier, value, this)
        else -> throw ContextAccessException("$typeName requires a bound execution context")
    }

    override fun buildUnConcrete(identifier: String): Var<*> = ExecutionContextVar(identifier, null, this)
}

class MCFPPBoundPositionType internal constructor() : MCFPPType() {
    override val typeName: String get() = "ContextPosition"
    override val typeId: TypeId = TypeId.Builtin("context.position")
    override val hasRuntimeRepresentation: Boolean get() = false
    override fun build(identifier: String, value: Any?): Var<*> = when (value) {
        null -> buildUnConcrete(identifier)
        is BoundPosition -> BoundPositionVar(identifier, value)
        else -> throw ContextAccessException("ContextPosition requires a bound context position")
    }
    override fun buildUnConcrete(identifier: String): Var<*> = BoundPositionVar(identifier, null)
}

object ContextTypes {
    val ExecutionContext = MCFPPExecutionContextType("ExecutionContext", "context.execution")
    val PosContext = MCFPPExecutionContextType("PosContext", "context.pos", ExecutionContext)
    val EntityContext = MCFPPExecutionContextType("EntityContext", "context.entity", ExecutionContext)
    val RelationContext = MCFPPExecutionContextType("RelationContext", "context.relation", EntityContext)
    val Position = MCFPPBoundPositionType()

    fun named(name: String): MCFPPType? = when (name) {
        "ExecutionContext" -> ExecutionContext
        "PosContext" -> PosContext
        "EntityContext" -> EntityContext
        "RelationContext" -> RelationContext
        "ContextPosition" -> Position
        else -> null
    }

    /** Declaration binding must not instantiate a context value to discover these types. */
    fun memberType(type: MCFPPType, name: String): MCFPPType? =
        if (type !is MCFPPExecutionContextType) null else when (name) {
            "executor" -> MCFPPEntityType.NormalSelector
            "pos" -> Position
            else -> null
        }

    fun of(context: ExecutionContext): MCFPPExecutionContextType = when (context) {
        is top.mcfpp.core.lang.context.RelationContext -> RelationContext
        is top.mcfpp.core.lang.context.EntityContext -> EntityContext
        is top.mcfpp.core.lang.context.PosContext -> PosContext
        else -> ExecutionContext
    }
}
