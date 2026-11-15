package top.mcfpp.core.lang.context

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.TypeId

/** Canonical source types; lexical identity is carried by a value, never by its type. */
class MCFPPExecutionContextType internal constructor(
    override val typeName: String,
    key: String,
    vararg parents: MCFPPExecutionContextType
) : MCFPPType(arrayListOf(*parents)) {
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
    val Context = MCFPPExecutionContextType("Context", "context.base")
    val DimensionContext = MCFPPExecutionContextType("DimensionContext", "context.dimension", Context)
    val PosContext = MCFPPExecutionContextType("PosContext", "context.pos", DimensionContext)
    val RotationContext = MCFPPExecutionContextType("RotationContext", "context.rotation", Context)
    val AnchorContext = MCFPPExecutionContextType("AnchorContext", "context.anchor", Context)
    val EntityContext = MCFPPExecutionContextType("EntityContext", "context.entity", Context)
    val ExecutionContext = MCFPPExecutionContextType("ExecutionContext", "context.execution", PosContext, RotationContext, AnchorContext)
    val EntityExecutionContext = MCFPPExecutionContextType("EntityExecutionContext", "context.entity_execution", ExecutionContext, EntityContext)
    val RelationContext = MCFPPExecutionContextType("RelationContext", "context.relation", EntityExecutionContext)
    val Position = MCFPPBoundPositionType()
    val Rotation = MCFPPBoundComponentType("ContextRotation", "rotation")
    val Dimension = MCFPPBoundComponentType("ContextDimension", "dimension")
    val Anchor = MCFPPBoundComponentType("ContextAnchor", "anchor")

    fun named(name: String): MCFPPType? = when (name) {
        "Context" -> Context
        "DimensionContext" -> DimensionContext
        "RotationContext" -> RotationContext
        "AnchorContext" -> AnchorContext
        "ExecutionContext" -> ExecutionContext
        "EntityExecutionContext" -> EntityExecutionContext
        "PosContext" -> PosContext
        "EntityContext" -> EntityContext
        "RelationContext" -> RelationContext
        "ContextPosition" -> Position
        "ContextRotation" -> Rotation
        "ContextDimension" -> Dimension
        "ContextAnchor" -> Anchor
        else -> null
    }

    /** Declaration binding must not instantiate a context value to discover these types. */
    fun memberType(type: MCFPPType, name: String): MCFPPType? =
        if (type !is MCFPPExecutionContextType) null else when {
            name == "executor" && supports(type, EntityContext) -> MCFPPEntityType.NormalSelector
            name == "pos" && supports(type, PosContext) -> Position
            name == "rotation" && supports(type, RotationContext) -> Rotation
            name == "dimension" && supports(type, DimensionContext) -> Dimension
            name == "anchor" && supports(type, AnchorContext) -> Anchor
            else -> null
        }

    fun supports(type: MCFPPType, capability: MCFPPExecutionContextType): Boolean =
        top.mcfpp.type.TypeRelations.isSubtype(type, capability)

    fun of(context: ExecutionContext): MCFPPExecutionContextType = when {
        context.relationBinding != null -> RelationContext
        context.executorPresence == ExecutorPresence.PRESENT -> EntityExecutionContext
        else -> ExecutionContext
    }
}

/** A scoped component view, never an ordinary resource or runtime value. */
class MCFPPBoundComponentType internal constructor(override val typeName: String, val component: String) : MCFPPType() {
    override val typeId: TypeId = TypeId.Builtin("context.bound.$component")
    override val hasRuntimeRepresentation: Boolean get() = false
    override fun build(identifier: String, value: Any?): Var<*> = BoundComponentVar(identifier, value as? ExecutionContext, this)
    override fun buildUnConcrete(identifier: String): Var<*> = BoundComponentVar(identifier, null, this)
}
