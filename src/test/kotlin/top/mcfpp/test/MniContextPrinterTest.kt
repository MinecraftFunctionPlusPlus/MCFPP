package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CodeArena
import top.mcfpp.mni.expand.CodePrinter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MniContextPrinterTest {
    private val source = SourceSpan("context-printer.mcfpp", 2, 8, 1, 3, 1, 9)
    private val intKey = TypeKey(990, 1)
    private val boolKey = TypeKey(990, 2)
    private val graph = FrozenTypeGraph.freeze(
        listOf(
            FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT),
            FrozenTypeRecord(boolKey, "bool", "builtin:bool", TypeKind.BOOL)
        )
    )

    @Test fun printsEveryMinecraftShapeWithStableIdsAndIndependentContextComponents() {
        val first = render(81)
        val second = render(810)
        assertEquals(first, second)
        listOf(
            "executeAs entities all_players", "self=entity c0", "executor=c0 position=c1 rotation=c1",
            "at position current c1 {executor=c0 position=c2 rotation=c1}",
            "rotated rotation current c1 {executor=c0 position=c2 rotation=c3}",
            "position(1.0, 2.0, 3.0)", "position entity(entity c0)", "position offset(", "position localOffset(",
            "rotation(90.0, -20.0)", "rotation entity(entity c0)",
            "blockMatches blockAt(", "tag \"minecraft:logs\"[\"axis\"=\"y\"]",
            "score holder \"#global\" \"points\"", "score entity c0 \"health\"",
            "scoreRead entity c0 \"health\""
        ).forEach { expected -> assertTrue(first.contains(expected), "$expected missing from:\n$first") }
        assertTrue(!first.contains("@"), first)
        assertTrue(!first.contains("Immutable"), first)
    }

    private fun render(sessionValue: Long): String {
        val session = SessionId(sessionValue)
        val scope = ScopeId(session, 0)
        val origin = Origin(source)
        val arena = CodeArena(session, scope) { _, _ -> }
        @Suppress("UNCHECKED_CAST")
        val intType = graph.type(intKey) as TypeRef<IntT>
        @Suppress("UNCHECKED_CAST")
        val boolType = graph.type(boolKey) as TypeRef<BoolT>
        val root = arena.rootContext(scope)
        fun metadata(atScope: ScopeId, frame: ExecutionContextFrame) = arena.metadata(atScope, origin, frame.id)
        val rootMeta = metadata(scope, root)
        val targets = arena.entitySet(
            rootMeta,
            EntityQuery.allPlayers().withType("minecraft:player")
                .withTag("team.red").withLimit(2).sorted(EntitySort.NEAREST)
        )
        val fixed = arena.scoreRef(rootMeta, intType, MinecraftScoreTarget.Fixed(ScoreHolder.named("#global")), "points")

        val asScope = arena.childScope(scope)
        val asFrame = arena.executorContext(root, asScope)
        val asMeta = metadata(asScope, asFrame)
        val self = arena.entityHandle(asMeta, asFrame)
        val atScope = arena.childScope(asScope)
        val atFrame = arena.positionContext(asFrame, atScope)
        val atMeta = metadata(atScope, atFrame)
        val atPosition = arena.position(asMeta, PositionNode.Current(root.position))
        val rotatedScope = arena.childScope(atScope)
        val rotatedFrame = arena.rotationContext(atFrame, rotatedScope)
        val rotatedMeta = metadata(rotatedScope, rotatedFrame)
        val atRotation = arena.rotation(atMeta, RotationNode.Current(atFrame.rotation))

        val absolute = arena.position(rotatedMeta, PositionNode.Absolute(1.0, 2.0, 3.0))
        val currentPosition = arena.position(rotatedMeta, PositionNode.Current(atFrame.position))
        val entityPosition = arena.position(rotatedMeta, PositionNode.Entity(self))
        val offset = arena.position(rotatedMeta, PositionNode.Offset(currentPosition, 0.0, 1.0, 2.0))
        val absoluteRotation = arena.rotation(rotatedMeta, RotationNode.Absolute(90.0, -20.0))
        val currentRotation = arena.rotation(rotatedMeta, RotationNode.Current(rotatedFrame.rotation))
        val entityRotation = arena.rotation(rotatedMeta, RotationNode.Entity(self))
        val localOffset = arena.position(
            rotatedMeta,
            PositionNode.LocalOffset(entityPosition, entityRotation, 1.0, 2.0, 3.0)
        )
        val block = arena.blockHandle(rotatedMeta, localOffset)
        val predicate = BlockPredicate.tag("minecraft:logs").withState("axis", "y")
        val matches = arena.expr(rotatedMeta, boolType, ExprNode.BlockMatches(block, predicate))
        val score = arena.scoreRef(rotatedMeta, intType, MinecraftScoreTarget.Entity(self), "health")
        val body = arena.block(
            rotatedMeta,
            listOf(
                arena.stmt(rotatedMeta, StmtNode.Eval(matches)),
                arena.stmt(rotatedMeta, StmtNode.Assign(score, score.load())),
                arena.stmt(rotatedMeta, StmtNode.Assign(fixed, fixed.load()))
            )
        )
        val rotated = arena.stmt(atMeta, StmtNode.Rotated(atRotation, body, atFrame, rotatedFrame))
        val at = arena.stmt(asMeta, StmtNode.At(atPosition, arena.block(atMeta, listOf(rotated)), asFrame, atFrame))
        val execute = arena.stmt(
            rootMeta,
            StmtNode.ExecuteAs(
                targets,
                self,
                arena.block(asMeta, listOf(at)),
                root,
                asFrame
            )
        )
        val output = buildString {
            append(CodePrinter().print(arena.block(rootMeta, listOf(execute))))
            append('\n').append(CodePrinter().print(absolute))
            append('\n').append(CodePrinter().print(absoluteRotation))
            append('\n').append(CodePrinter().print(currentPosition))
            append('\n').append(CodePrinter().print(currentRotation))
            append('\n').append(CodePrinter().print(offset))
            append('\n').append(CodePrinter().print(entityPosition))
            append('\n').append(CodePrinter().print(entityRotation))
            append('\n').append(CodePrinter().print(fixed))
            append('\n').append(CodePrinter().print(score.load()))
        }
        arena.close()
        return output
    }
}
