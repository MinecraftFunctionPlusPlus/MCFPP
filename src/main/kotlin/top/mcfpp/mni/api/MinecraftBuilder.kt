package top.mcfpp.mni.api

import java.util.function.BiConsumer
import java.util.function.Consumer

/** Builds Minecraft operations as generated MCFPP code. JVM callbacks run once while the compiler constructs the code. */
interface MinecraftBuilder : CodeBuilder {
    /** Describes the entities that [query] selects when the generated code runs. */
    fun entities(query: EntityQuery): EntitySet

    /** Builds code that runs as each selected entity. [body] receives that entity within its child scope. */
    fun executeAs(targets: EntitySet, body: BiConsumer<MinecraftBuilder, EntityHandle>)
    /** Builds code with the supplied execution position. */
    fun at(position: PositionHandle, body: Consumer<MinecraftBuilder>)
    /** Builds code with the supplied rotation. */
    fun rotated(rotation: RotationHandle, body: Consumer<MinecraftBuilder>)

    /** Returns a writable score for [entity] in an existing scoreboard [objective]. */
    fun score(entity: EntityHandle, objective: String): Ref<IntT>
    /** Returns a writable score for a fixed [holder] in an existing scoreboard [objective]. */
    fun score(holder: ScoreHolder, objective: String): Ref<IntT>
    /** Describes absolute world coordinates. Coordinates must be finite. */
    fun position(x: Double, y: Double, z: Double): PositionHandle
    /** Describes an entity's position for use when the generated code runs. */
    fun position(entity: EntityHandle): PositionHandle
    /** Describes the execution position at this point in the generated code. */
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
    /** Describes absolute yaw and pitch in degrees. Angles must be finite. */
    fun rotation(yaw: Double, pitch: Double): RotationHandle
    /** Describes the rotation of [entity]. */
    fun rotation(entity: EntityHandle): RotationHandle
    /** Describes the execution rotation at this point in the generated code. */
    fun currentRotation(): RotationHandle
    /** Describes the block at [position]. */
    fun blockAt(position: PositionHandle): BlockHandle
    /** Creates a runtime test of a block's identifier or tag and its selected states. */
    fun blockMatches(block: BlockHandle, predicate: BlockPredicate): Expr<BoolT>
}

/** A set of entities selected when generated code runs. Use [MinecraftBuilder.executeAs] to visit each entity. */
sealed interface EntitySet : Code
/** One entity supplied inside an [MinecraftBuilder.executeAs] callback. Use it in that callback's scope. */
sealed interface EntityHandle : Code
/** A position description used by [MinecraftBuilder.at] and [MinecraftBuilder.blockAt]. */
sealed interface PositionHandle : Code
/** A rotation description used by [MinecraftBuilder.rotated] and [MinecraftBuilder.localOffset]. */
sealed interface RotationHandle : Code
/** A block location used by [MinecraftBuilder.blockMatches]. */
sealed interface BlockHandle : Code

/** The starting set for an entity query. */
enum class EntitySelection {
    /** All entities, corresponding to `@e`. */
    ALL_ENTITIES,
    /** All players, corresponding to `@a`. */
    ALL_PLAYERS
}
/** The order used before applying an entity query's limit. */
enum class EntitySort {
    /** Closest entities first. */
    NEAREST,
    /** Most distant entities first. */
    FURTHEST,
    /** Random entity order. */
    RANDOM,
    /** The game's available entity order. */
    ARBITRARY
}

/**
 * An immutable description of an entity selector. Each filter method returns a new query.
 * @property selection Starting set of entities.
 * @property entityType Required namespaced entity type, or null for any type.
 * @property limit Maximum result count, or null for the selector's default.
 * @property sort Result order, or null for the selector's default.
 */
class EntityQuery private constructor(
    val selection: EntitySelection,
    val entityType: String?,
    tags: List<String>,
    val limit: Int?,
    val sort: EntitySort?
) {
    /** Tags that every selected entity must carry. */
    val tags: List<String> = immutableList(tags)

    /** Returns a query restricted to a namespaced entity type, such as `minecraft:pig`. */
    fun withType(resource: String): EntityQuery {
        requireMinecraftResource(resource)
        return EntityQuery(selection, resource, tags, limit, sort)
    }
    /** Returns a query with one more required entity tag. */
    fun withTag(tag: String): EntityQuery {
        require(tag.matches(Regex("[A-Za-z0-9_.:+-]+"))) { "Invalid entity tag" }
        return EntityQuery(selection, entityType, tags + tag, limit, sort)
    }
    /** Returns a query limited to the positive [count]. */
    fun withLimit(count: Int): EntityQuery {
        require(count > 0) { "An entity limit must be positive" }
        return EntityQuery(selection, entityType, tags, count, sort)
    }
    /** Returns a query with the selected result order. */
    fun sorted(order: EntitySort): EntityQuery = EntityQuery(selection, entityType, tags, limit, order)

    companion object {
        /** Creates an unfiltered query starting from `@e`. */
        @JvmStatic fun allEntities(): EntityQuery = EntityQuery(EntitySelection.ALL_ENTITIES, null, emptyList(), null, null)
        /** Creates an unfiltered query starting from `@a`. */
        @JvmStatic fun allPlayers(): EntityQuery = EntityQuery(EntitySelection.ALL_PLAYERS, null, emptyList(), null, null)
    }
}

/** A fixed scoreboard name, such as a fake player. [name] stays the same when the executor changes. */
class ScoreHolder private constructor(val name: String) {
    companion object {
        /** Creates a holder with 1–40 characters from letters, digits, `#`, `_`, `.`, `+`, and `-`. */
        @JvmStatic fun named(name: String): ScoreHolder {
            require(name.length in 1..40 && name.matches(Regex("[#A-Za-z0-9_.+-]+"))) { "Invalid fixed score holder" }
            return ScoreHolder(name)
        }
    }
}

/**
 * A block test with optional block states. Each filter method returns a new predicate.
 * @property resource Namespaced block or block tag identifier.
 * @property isTag True for a block tag test.
 */
class BlockPredicate private constructor(val resource: String, val isTag: Boolean, states: Map<String, String>) {
    /** Required state values, indexed by state name. */
    val states: Map<String, String> = immutableMap(states.toSortedMap())
    /** Returns a predicate with the selected state value. State names and values use lowercase letters, digits, or underscores. */
    fun withState(name: String, value: String): BlockPredicate {
        require(name.matches(Regex("[a-z0-9_]+")) && value.matches(Regex("[a-z0-9_]+"))) { "Invalid block state" }
        return BlockPredicate(resource, isTag, states + (name to value))
    }
    companion object {
        /** Creates a test for a namespaced block identifier, such as `minecraft:stone`. */
        @JvmStatic fun block(resource: String): BlockPredicate {
            requireMinecraftResource(resource)
            return BlockPredicate(resource, false, emptyMap())
        }
        /** Creates a test for a namespaced block tag identifier, supplied as `minecraft:logs`. */
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
