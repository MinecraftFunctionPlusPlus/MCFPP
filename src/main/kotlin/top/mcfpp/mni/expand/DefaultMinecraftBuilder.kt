package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.function.BiConsumer
import java.util.function.Consumer

/** A scoped adapter over the same builder and arena; no host callback is stored in the tree. */
internal class DefaultMinecraftBuilder(private val builder: DefaultCodeBuilder) :
    MinecraftBuilder,
    CodeBuilder by builder {
    private val arena get() = builder.arena
    private fun metadata() = builder.minecraftMetadata()

    override fun minecraft(): MinecraftBuilder {
        metadata()
        return this
    }

    override fun entities(query: EntityQuery): EntitySet = arena.entitySet(metadata(), query)

    override fun executeAs(targets: EntitySet, body: BiConsumer<MinecraftBuilder, EntityHandle>) {
        // Validate dependencies before invoking any host callback, even for an empty body.
        metadata()
        requireVisible(targets)
        lateinit var self: ImmutableEntityHandle
        builder.minecraftChild(arena::executorContext, { child, frame ->
            self = arena.entityHandle(child.minecraftMetadata(), frame)
            body.accept(DefaultMinecraftBuilder(child), self)
        }, { block, parent, frame -> StmtNode.ExecuteAs(targets, self, block, parent, frame) }, false)
    }

    private fun requireVisible(code: Code) {
        val owner = (code as OwnedCode).metadata
        if (owner.sessionId != arena.sessionId || !arena.isVisible(owner.scopeId, builder.scope))
            throw CodeModelException(code.source(), "Minecraft handle is outside this expansion scope")
    }

    override fun at(position: PositionHandle, body: Consumer<MinecraftBuilder>) {
        metadata()
        requireVisible(position)
        builder.minecraftChild(
            arena::positionContext,
            { child, _ -> body.accept(DefaultMinecraftBuilder(child)) },
            { block, parent, frame -> StmtNode.At(position, block, parent, frame) },
            true
        )
    }

    override fun rotated(rotation: RotationHandle, body: Consumer<MinecraftBuilder>) {
        metadata()
        requireVisible(rotation)
        builder.minecraftChild(
            arena::rotationContext,
            { child, _ -> body.accept(DefaultMinecraftBuilder(child)) },
            { block, parent, frame -> StmtNode.Rotated(rotation, block, parent, frame) },
            true
        )
    }

    override fun score(entity: EntityHandle, objective: String): Ref<IntT> = arena.scoreRef(metadata(), builder.minecraftIntType(), MinecraftScoreTarget.Entity(entity), objective)

    override fun score(holder: ScoreHolder, objective: String): Ref<IntT> = arena.scoreRef(metadata(), builder.minecraftIntType(), MinecraftScoreTarget.Fixed(holder), objective)

    override fun position(x: Double, y: Double, z: Double): PositionHandle = arena.position(metadata(), PositionNode.Absolute(x, y, z))

    override fun position(entity: EntityHandle): PositionHandle = arena.position(metadata(), PositionNode.Entity(entity))

    override fun currentPosition(): PositionHandle {
        val frame = builder.contextFrame()
        return arena.position(metadata(), PositionNode.Current(frame.position))
    }

    override fun offset(base: PositionHandle, x: Double, y: Double, z: Double): PositionHandle = arena.position(metadata(), PositionNode.Offset(base, x, y, z))

    override fun localOffset(
        base: PositionHandle,
        rotation: RotationHandle,
        left: Double,
        up: Double,
        forward: Double
    ): PositionHandle = arena.position(metadata(), PositionNode.LocalOffset(base, rotation, left, up, forward))

    override fun rotation(yaw: Double, pitch: Double): RotationHandle = arena.rotation(metadata(), RotationNode.Absolute(yaw, pitch))
    override fun rotation(entity: EntityHandle): RotationHandle = arena.rotation(metadata(), RotationNode.Entity(entity))

    override fun currentRotation(): RotationHandle {
        val frame = builder.contextFrame()
        return arena.rotation(metadata(), RotationNode.Current(frame.rotation))
    }

    override fun blockAt(position: PositionHandle): BlockHandle = arena.blockHandle(metadata(), position)
    override fun blockMatches(block: BlockHandle, predicate: BlockPredicate): Expr<BoolT> = arena.expr(metadata(), builder.minecraftBoolType(), ExprNode.BlockMatches(block, predicate))
}
