package top.mcfpp.mni.api

import java.util.function.BiConsumer
import java.util.function.Consumer

/** Structured Minecraft operations. Host callbacks are consumed while constructing the tree. */
interface MinecraftBuilder : CodeBuilder {
    fun entities(query: EntityQuery): EntitySet

    /** Changes the executor only. The callback's self belongs to its child scope/context. */
    fun executeAs(targets: EntitySet, body: BiConsumer<MinecraftBuilder, EntityHandle>)
    fun at(position: PositionHandle, body: Consumer<MinecraftBuilder>)
    fun rotated(rotation: RotationHandle, body: Consumer<MinecraftBuilder>)

    fun score(entity: EntityHandle, objective: String): Ref<IntT>
    fun score(holder: ScoreHolder, objective: String): Ref<IntT>
    fun position(x: Double, y: Double, z: Double): PositionHandle
    fun position(entity: EntityHandle): PositionHandle
    fun currentPosition(): PositionHandle
    /** Adds world-axis offsets, independently of the current rotation. */
    fun offset(base: PositionHandle, x: Double, y: Double, z: Double): PositionHandle
    /** Adds explicit left/up/forward offsets using the supplied rotation. */
    fun localOffset(
        base: PositionHandle,
        rotation: RotationHandle,
        left: Double,
        up: Double,
        forward: Double
    ): PositionHandle
    fun rotation(yaw: Double, pitch: Double): RotationHandle
    fun rotation(entity: EntityHandle): RotationHandle
    fun currentRotation(): RotationHandle
    fun blockAt(position: PositionHandle): BlockHandle
    fun blockMatches(block: BlockHandle, predicate: BlockPredicate): Expr<BoolT>
}

/** A runtime collection is intentionally distinct from the callback's single entity. */
sealed interface EntitySet : Code
sealed interface EntityHandle : Code
sealed interface PositionHandle : Code
sealed interface RotationHandle : Code
sealed interface BlockHandle : Code

enum class EntitySelection { ALL_ENTITIES, ALL_PLAYERS }
enum class EntitySort { NEAREST, FURTHEST, RANDOM, ARBITRARY }

/** A closed selector description; it never contains a command or selector fragment. */
class EntityQuery private constructor(
    val selection: EntitySelection,
    val entityType: String?,
    tags: List<String>,
    val limit: Int?,
    val sort: EntitySort?
) {
    val tags: List<String> = immutableList(tags)

    fun withType(resource: String): EntityQuery {
        requireMinecraftResource(resource)
        return EntityQuery(selection, resource, tags, limit, sort)
    }
    fun withTag(tag: String): EntityQuery {
        require(tag.matches(Regex("[A-Za-z0-9_.:+-]+"))) { "Invalid entity tag" }
        return EntityQuery(selection, entityType, tags + tag, limit, sort)
    }
    fun withLimit(count: Int): EntityQuery {
        require(count > 0) { "An entity limit must be positive" }
        return EntityQuery(selection, entityType, tags, count, sort)
    }
    fun sorted(order: EntitySort): EntityQuery = EntityQuery(selection, entityType, tags, limit, order)

    companion object {
        @JvmStatic fun allEntities(): EntityQuery = EntityQuery(EntitySelection.ALL_ENTITIES, null, emptyList(), null, null)
        @JvmStatic fun allPlayers(): EntityQuery = EntityQuery(EntitySelection.ALL_PLAYERS, null, emptyList(), null, null)
    }
}

/** Fixed scoreboard holders have no implicit @s dependency. */
class ScoreHolder private constructor(val name: String) {
    companion object {
        @JvmStatic fun named(name: String): ScoreHolder {
            require(name.length in 1..40 && name.matches(Regex("[#A-Za-z0-9_.+-]+"))) { "Invalid fixed score holder" }
            return ScoreHolder(name)
        }
    }
}

/** A block or block-tag test with separately validated state names/values. */
class BlockPredicate private constructor(val resource: String, val isTag: Boolean, states: Map<String, String>) {
    val states: Map<String, String> = immutableMap(states.toSortedMap())
    fun withState(name: String, value: String): BlockPredicate {
        require(name.matches(Regex("[a-z0-9_]+")) && value.matches(Regex("[a-z0-9_]+"))) { "Invalid block state" }
        return BlockPredicate(resource, isTag, states + (name to value))
    }
    companion object {
        @JvmStatic fun block(resource: String): BlockPredicate {
            requireMinecraftResource(resource)
            return BlockPredicate(resource, false, emptyMap())
        }
        @JvmStatic fun tag(resource: String): BlockPredicate {
            requireMinecraftResource(resource)
            return BlockPredicate(resource, true, emptyMap())
        }
    }
}

internal fun requireMinecraftResource(resource: String) {
    require(resource.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "Invalid Minecraft resource identifier" }
}

internal fun requireMinecraftObjective(objective: String) {
    require(objective.length in 1..16 && objective.matches(Regex("[A-Za-z0-9_.+-]+"))) { "Invalid score objective" }
}

internal fun requireMinecraftSession(metadata: CodeMetadata, vararg dependencies: Code) {
    dependencies.forEach {
        require((it as OwnedCode).metadata.sessionId == metadata.sessionId) { "Minecraft handle belongs to another session" }
    }
}

/** Each component names the context that last established that component. */
internal data class ExecutionContextFrame(
    val id: ExecutionContextId,
    val executor: ExecutionContextId,
    val position: ExecutionContextId,
    val rotation: ExecutionContextId,
    val dimension: ExecutionContextId = position
) {
    init {
        require(listOf(executor, position, rotation, dimension).all { it.session == id.session }) { "Execution context components belong to another session" }
    }
    fun withExecutor(next: ExecutionContextId) = ExecutionContextFrame(next, next, position, rotation, dimension)
    fun withPosition(next: ExecutionContextId) = ExecutionContextFrame(next, executor, next, rotation, dimension)
    fun withRotation(next: ExecutionContextId) = ExecutionContextFrame(next, executor, position, next, dimension)
    fun withDimension(next: ExecutionContextId) = ExecutionContextFrame(next, executor, position, rotation, next)
    /** execute at entity establishes all spatial components, unlike positioned coordinates. */
    fun atEntity(next: ExecutionContextId) = ExecutionContextFrame(next, executor, next, next, next)
    companion object {
        fun root(id: ExecutionContextId) = ExecutionContextFrame(id, id, id, id, id)
    }
}

internal class ImmutableEntitySet(override val metadata: CodeMetadata, val query: EntityQuery) :
    EntitySet,
    OwnedCode

/** The executor identity is stable when another context changes only its position or rotation. */
internal class ImmutableEntityHandle(override val metadata: CodeMetadata, val executor: ExecutionContextId) :
    EntityHandle,
    OwnedCode {
    init {
        require(metadata.executionContextId != null && executor.session == metadata.sessionId) { "An entity requires an owned execution context" }
    }
}

internal sealed interface PositionNode {
    data class Absolute(val x: Double, val y: Double, val z: Double) : PositionNode {
        init {
            require(listOf(x, y, z).all { it.isFinite() }) { "Position coordinates must be finite" }
        }
    }
    data class Current(val context: ExecutionContextId) : PositionNode
    data class Entity(val entity: EntityHandle) : PositionNode
    data class Offset(val base: PositionHandle, val x: Double, val y: Double, val z: Double) : PositionNode {
        init {
            require(listOf(x, y, z).all { it.isFinite() }) { "Position offsets must be finite" }
        }
    }
    data class LocalOffset(
        val base: PositionHandle,
        val rotation: RotationHandle,
        val left: Double,
        val up: Double,
        val forward: Double
    ) : PositionNode {
        init {
            require(listOf(left, up, forward).all { it.isFinite() }) { "Local offsets must be finite" }
        }
    }
}

internal class ImmutablePositionHandle(override val metadata: CodeMetadata, val node: PositionNode) :
    PositionHandle,
    OwnedCode {
    init {
        when (node) {
            is PositionNode.Absolute -> Unit
            is PositionNode.Current -> require(node.context.session == metadata.sessionId) { "Position context belongs to another session" }
            is PositionNode.Entity -> requireMinecraftSession(metadata, node.entity)
            is PositionNode.Offset -> requireMinecraftSession(metadata, node.base)
            is PositionNode.LocalOffset -> requireMinecraftSession(metadata, node.base, node.rotation)
        }
    }
}

internal sealed interface RotationNode {
    data class Absolute(val yaw: Double, val pitch: Double) : RotationNode {
        init {
            require(yaw.isFinite() && pitch.isFinite()) { "Rotation angles must be finite" }
        }
    }
    data class Current(val context: ExecutionContextId) : RotationNode
    data class Entity(val entity: EntityHandle) : RotationNode
}

internal class ImmutableRotationHandle(override val metadata: CodeMetadata, val node: RotationNode) :
    RotationHandle,
    OwnedCode {
    init {
        when (node) {
            is RotationNode.Absolute -> Unit
            is RotationNode.Current -> require(node.context.session == metadata.sessionId) { "Rotation context belongs to another session" }
            is RotationNode.Entity -> requireMinecraftSession(metadata, node.entity)
        }
    }
}

internal class ImmutableBlockHandle(override val metadata: CodeMetadata, val position: PositionHandle) :
    BlockHandle,
    OwnedCode {
    init {
        requireMinecraftSession(metadata, position)
    }
}

internal sealed interface MinecraftScoreTarget {
    data class Fixed(val holder: ScoreHolder) : MinecraftScoreTarget
    data class Entity(val entity: EntityHandle) : MinecraftScoreTarget
}

/** A score is an address, with one cached immutable read, rather than a copied runtime value. */
internal class ImmutableMinecraftScoreRef(
    override val metadata: CodeMetadata,
    private val intType: TypeRef<IntT>,
    val target: MinecraftScoreTarget,
    val objective: String
) : Ref<IntT>,
    OwnedCode {
    init {
        require(intType is ImmutableTypeRef<*> && intType.kind == TypeKind.INT) { "A score requires the actual int type" }
        requireMinecraftObjective(objective)
        if (target is MinecraftScoreTarget.Entity) requireMinecraftSession(metadata, target.entity)
    }
    private val read: Expr<IntT> = ImmutableExpr(metadata, intType, ExprNode.ScoreRead(target, objective))
    override fun type(): TypeRef<IntT> = intType
    override fun load(): Expr<IntT> = read
}
