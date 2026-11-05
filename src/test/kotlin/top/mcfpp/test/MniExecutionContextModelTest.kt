package top.mcfpp.test

import top.mcfpp.mni.api.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test

class MniExecutionContextModelTest {
    private val session = SessionId(101)
    private val rootId = ExecutionContextId(session, 1)
    private val root = ExecutionContextFrame.root(rootId)
    private val source = SourceSpan("context.mcfpp", 2, 8, 1, 3, 1, 9)
    private val intType = ImmutableTypeRef<IntT>(TypeKey(71, 1), "int", kind = TypeKind.INT)
    private fun meta(scope: Long = 1, context: ExecutionContextId = rootId) = CodeMetadata(context.session, ScopeId(context.session, scope), Origin(source), context)
    private fun body(context: ExecutionContextFrame, scope: Long = 2) = ImmutableBlock(meta(scope, context.id), emptyList())

    @Test fun entityQueriesArePersistentStructuredValuesWithImmutableTags() {
        val original = EntityQuery.allEntities().withType("minecraft:pig").withTag("first")
        val filtered = original.withTag("second").withLimit(3).sorted(EntitySort.NEAREST)
        assertEquals(EntitySelection.ALL_ENTITIES, filtered.selection)
        assertEquals("minecraft:pig", filtered.entityType)
        assertEquals(listOf("first"), original.tags)
        assertEquals(listOf("first", "second"), filtered.tags)
        assertEquals(3, filtered.limit)
        assertEquals(EntitySort.NEAREST, filtered.sort)
        assertFailsWith<UnsupportedOperationException> { (filtered.tags as MutableList<String>).clear() }
        assertEquals(EntitySelection.ALL_PLAYERS, EntityQuery.allPlayers().selection)
    }

    @Test fun blockPredicatesKeepResourceKindAndPersistentImmutableState() {
        val original = BlockPredicate.block("minecraft:oak_log").withState("axis", "x")
        val changed = original.withState("axis", "y")
        assertFalse(original.isTag)
        assertEquals("minecraft:oak_log", original.resource)
        assertEquals(mapOf("axis" to "x"), original.states)
        assertEquals(mapOf("axis" to "y"), changed.states)
        assertFailsWith<UnsupportedOperationException> { (changed.states as MutableMap<String, String>).clear() }
        assertTrue(BlockPredicate.tag("minecraft:logs").isTag)
    }

    @Test fun descriptorsRejectSelectorAndCommandFragmentsAtTheirOwnBoundary() {
        for (text in listOf("", "@s", "a b", "x\nkill @a", "a;run"))
            assertFailsWith<IllegalArgumentException>(text) { ScoreHolder.named(text) }
        assertEquals("#ray_hits", ScoreHolder.named("#ray_hits").name)
        for (text in listOf("stone", "minecraft:stone] run kill @a", "a:b\nc:d")) {
            assertFailsWith<IllegalArgumentException>(text) { BlockPredicate.block(text) }
            assertFailsWith<IllegalArgumentException>(text) { EntityQuery.allEntities().withType(text) }
        }
        assertFailsWith<IllegalArgumentException> { EntityQuery.allEntities().withTag("x] run kill @a") }
        assertFailsWith<IllegalArgumentException> { EntityQuery.allEntities().withLimit(0) }
        assertFailsWith<IllegalArgumentException> { BlockPredicate.block("minecraft:stone").withState("x]", "y") }
        assertFailsWith<IllegalArgumentException> { BlockPredicate.block("minecraft:stone").withState("axis", "y] run") }
    }

    @Test fun positionsAndRotationsRejectNonFiniteInputsWithoutDroppingTheirDependencies() {
        val position = ImmutablePositionHandle(meta(), PositionNode.Absolute(1.0, 2.0, 3.0))
        val rotation = ImmutableRotationHandle(meta(), RotationNode.Absolute(90.0, -15.0))
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { PositionNode.Absolute(bad, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { PositionNode.Offset(position, 0.0, bad, 0.0) }
            assertFailsWith<IllegalArgumentException> { PositionNode.LocalOffset(position, rotation, 0.0, 0.0, bad) }
            assertFailsWith<IllegalArgumentException> { RotationNode.Absolute(bad, 0.0) }
            assertFailsWith<IllegalArgumentException> { RotationNode.Absolute(0.0, bad) }
        }
        val world = ImmutablePositionHandle(meta(), PositionNode.Offset(position, 0.0, 0.0, 0.5))
        val local = ImmutablePositionHandle(meta(), PositionNode.LocalOffset(position, rotation, 0.0, 0.0, 0.5))
        assertSame(position, assertIs<PositionNode.Offset>(world.node).base)
        assertSame(rotation, assertIs<PositionNode.LocalOffset>(local.node).rotation)
        assertSame(position, assertIs<PositionNode.LocalOffset>(local.node).base)
    }

    @Test fun handlesRetainTheirSourceScopeContextAndExplicitEntityDependencies() {
        val context = root.withExecutor(ExecutionContextId(session, 2))
        val self = ImmutableEntityHandle(meta(2, context.id), context.executor)
        val position = ImmutablePositionHandle(meta(2, context.id), PositionNode.Entity(self))
        val rotation = ImmutableRotationHandle(meta(2, context.id), RotationNode.Entity(self))
        val block = ImmutableBlockHandle(meta(2, context.id), position)
        assertEquals(source, self.source())
        assertEquals(ScopeId(session, 2), self.metadata.scopeId)
        assertEquals(context.id, self.metadata.executionContextId)
        assertSame(self, assertIs<PositionNode.Entity>(position.node).entity)
        assertSame(self, assertIs<RotationNode.Entity>(rotation.node).entity)
        assertSame(position, block.position)
        assertSame(block, ExprNode.BlockMatches(block, BlockPredicate.tag("minecraft:logs")).block)
    }

    @Test fun crossSessionHandleGraphsAreRejectedWhileBeingAssembled() {
        val foreignId = ExecutionContextId(SessionId(202), 1)
        val foreignEntity = ImmutableEntityHandle(meta(context = foreignId), foreignId)
        val foreignPosition = ImmutablePositionHandle(meta(context = foreignId), PositionNode.Current(foreignId))
        val foreignRotation = ImmutableRotationHandle(meta(context = foreignId), RotationNode.Current(foreignId))
        val position = ImmutablePositionHandle(meta(), PositionNode.Current(rootId))
        assertFailsWith<IllegalArgumentException> { ImmutablePositionHandle(meta(), PositionNode.Entity(foreignEntity)) }
        assertFailsWith<IllegalArgumentException> { ImmutableRotationHandle(meta(), RotationNode.Entity(foreignEntity)) }
        assertFailsWith<IllegalArgumentException> { ImmutableBlockHandle(meta(), foreignPosition) }
        assertFailsWith<IllegalArgumentException> { ImmutablePositionHandle(meta(), PositionNode.Current(foreignId)) }
        assertFailsWith<IllegalArgumentException> { ImmutableRotationHandle(meta(), RotationNode.Current(foreignId)) }
        assertFailsWith<IllegalArgumentException> { ImmutablePositionHandle(meta(), PositionNode.Offset(foreignPosition, 0.0, 0.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { ImmutablePositionHandle(meta(), PositionNode.LocalOffset(position, foreignRotation, 0.0, 0.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { root.withExecutor(foreignId) }
        assertFailsWith<IllegalArgumentException> { ImmutableMinecraftScoreRef(meta(), intType, MinecraftScoreTarget.Entity(foreignEntity), "hp") }
    }

    @Test fun scoreReferencesCacheOneReadAndPreserveFixedOrEntityAddressIdentity() {
        val holder = ScoreHolder.named("#hits")
        val fixedTarget = MinecraftScoreTarget.Fixed(holder)
        val fixed = ImmutableMinecraftScoreRef(meta(), intType, fixedTarget, "ray_hits")
        assertSame(fixed.load(), fixed.load())
        assertSame(intType, fixed.type())
        val read = assertIs<ExprNode.ScoreRead>(assertIs<ImmutableExpr<*>>(fixed.load()).node)
        assertSame(fixedTarget, read.target)
        assertSame(holder, assertIs<MinecraftScoreTarget.Fixed>(read.target).holder)
        val self = ImmutableEntityHandle(meta(), rootId)
        val entity = ImmutableMinecraftScoreRef(meta(), intType, MinecraftScoreTarget.Entity(self), "hp")
        assertSame(self, assertIs<MinecraftScoreTarget.Entity>(entity.target).entity)
        assertEquals("hp", assertIs<ExprNode.ScoreRead>(assertIs<ImmutableExpr<*>>(entity.load()).node).objective)
    }

    @Test fun scoreReferencesCheckActualIntTypesAndObjectiveSyntaxDespiteGenericErasure() {
        val boolType = ImmutableTypeRef<BoolT>(TypeKey(71, 2), "bool", kind = TypeKind.BOOL)
        @Suppress("UNCHECKED_CAST")
        val lie = boolType as TypeRef<IntT>
        val target = MinecraftScoreTarget.Fixed(ScoreHolder.named("#fixed"))
        assertFailsWith<IllegalArgumentException> { ImmutableMinecraftScoreRef(meta(), lie, target, "hp") }
        for (objective in listOf("", "hp run", "x\ny", "12345678901234567", "@s")) {
            assertFailsWith<IllegalArgumentException>(objective) { ImmutableMinecraftScoreRef(meta(), intType, target, objective) }
            assertFailsWith<IllegalArgumentException>(objective) { ExprNode.ScoreRead(target, objective) }
        }
    }

    @Test fun executeAsDoesNotImplicitlyMoveOrRotateAndItsSelfBelongsToItsBody() {
        val child = root.withExecutor(ExecutionContextId(session, 2))
        val targets = ImmutableEntitySet(meta(), EntityQuery.allPlayers())
        val self = ImmutableEntityHandle(meta(2, child.id), child.executor)
        val statement = StmtNode.ExecuteAs(targets, self, body(child), root, child)
        assertEquals(root.position, statement.context.position)
        assertEquals(root.rotation, statement.context.rotation)
        assertEquals(child.id, statement.context.executor)
        assertSame(targets, statement.targets)
        assertSame(self, statement.self)
        assertEquals(ScopeId(session, 2), (statement.body as OwnedCode).metadata.scopeId)
    }

    @Test fun explicitPositionAndRotationTransitionsPreserveTheOtherContextComponents() {
        val asContext = root.withExecutor(ExecutionContextId(session, 2))
        val self = ImmutableEntityHandle(meta(2, asContext.id), asContext.executor)
        val position = ImmutablePositionHandle(meta(2, asContext.id), PositionNode.Entity(self))
        val atContext = asContext.withPosition(ExecutionContextId(session, 3))
        val at = StmtNode.At(position, body(atContext, 3), asContext, atContext)
        assertEquals(asContext.executor, at.context.executor)
        assertEquals(root.rotation, at.context.rotation)
        val rotation = ImmutableRotationHandle(meta(3, atContext.id), RotationNode.Entity(self))
        val rotatedContext = atContext.withRotation(ExecutionContextId(session, 4))
        val rotated = StmtNode.Rotated(rotation, body(rotatedContext, 4), atContext, rotatedContext)
        assertEquals(asContext.executor, rotated.context.executor)
        assertEquals(atContext.position, rotated.context.position)
        assertEquals(rotatedContext.id, rotated.context.rotation)
        assertEquals(asContext.executor, self.executor)
    }

    @Test fun malformedContextTransitionsCannotSmugglePositionChangesOrAnUnboundSelf() {
        val next = ExecutionContextId(session, 2)
        val child = root.withExecutor(next)
        val targets = ImmutableEntitySet(meta(), EntityQuery.allPlayers())
        val self = ImmutableEntityHandle(meta(2, next), next)
        val position = ImmutablePositionHandle(meta(), PositionNode.Current(rootId))
        val rotation = ImmutableRotationHandle(meta(), RotationNode.Current(rootId))
        val forged = ExecutionContextFrame(next, next, next, next)
        assertFailsWith<IllegalArgumentException> { StmtNode.ExecuteAs(targets, self, body(forged), root, forged) }
        assertFailsWith<IllegalArgumentException> { StmtNode.At(position, body(forged), root, forged) }
        assertFailsWith<IllegalArgumentException> { StmtNode.Rotated(rotation, body(forged), root, forged) }
        assertFailsWith<IllegalArgumentException> { StmtNode.ExecuteAs(targets, self, body(root), root, child) }
        val escapedSelf = ImmutableEntityHandle(meta(9, next), next)
        assertFailsWith<IllegalArgumentException> { StmtNode.ExecuteAs(targets, escapedSelf, body(child), root, child) }
        val wrongExecutor = ImmutableEntityHandle(meta(2, next), rootId)
        assertFailsWith<IllegalArgumentException> { StmtNode.ExecuteAs(targets, wrongExecutor, body(child), root, child) }
        assertFailsWith<IllegalArgumentException> {
            StmtNode.ExecuteAs(targets, ImmutableEntityHandle(meta(2), rootId), body(root), root, root)
        }
        assertFailsWith<IllegalArgumentException> { StmtNode.At(position, body(root), root, root) }
        assertFailsWith<IllegalArgumentException> { StmtNode.Rotated(rotation, body(root), root, root) }
    }

    private fun compileJava(className: String, source: String): Pair<Int, String> {
        val directory = Files.createTempDirectory("mni-context-api-")
        try {
            val file = directory.resolve("$className.java")
            Files.writeString(file, source)
            val classpath = listOf(MinecraftBuilder::class.java, kotlin.Unit::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
                .joinToString(java.io.File.pathSeparator)
            val output = ByteArrayOutputStream()
            val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "The Java 25 toolchain requires a JDK")
            val exit = compiler.run(
                null, output, output, "-proc:none", "--release", "21", "-classpath", classpath,
                "-d", directory.toString(), file.toString()
            )
            return exit to output.toString(Charsets.UTF_8)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun javaCompilesActualVoidSamsAndEveryPublicContextOperation() {
        val result = compileJava(
            "ContextApiExample",
            """
            import top.mcfpp.mni.api.*;
            public class ContextApiExample {
                public static void build(MinecraftBuilder b) {
                    EntitySet targets = b.entities(EntityQuery.allEntities().withType("minecraft:pig")
                        .withTag("target").withLimit(4).sorted(EntitySort.NEAREST));
                    b.executeAs(targets, (scope, self) -> {
                        Ref<IntT> hp = scope.score(self, "hp");
                        scope.assign(hp, scope.sub(hp.load(), scope.intLiteral(1)));
                        scope.at(scope.position(self), placed -> placed.rotated(placed.rotation(self), turned -> {
                            PositionHandle step = turned.localOffset(turned.currentPosition(), turned.currentRotation(), 0, 0, 0.5);
                            BlockHandle block = turned.blockAt(step);
                            turned.ifThen(turned.blockMatches(block, BlockPredicate.block("minecraft:oak_log")
                                .withState("axis", "y")), hit -> hit.assign(hit.minecraft().score(ScoreHolder.named("#hits"), "ray_hits"), hit.intLiteral(1)));
                        }));
                    });
                    b.at(b.offset(b.position(1, 2, 3), 0, 1, 0), placed ->
                        placed.rotated(placed.rotation(90, 0), turned -> turned.evaluate(
                            turned.blockMatches(turned.blockAt(turned.currentPosition()), BlockPredicate.tag("minecraft:logs")))));
                }
            }
            """.trimIndent()
        )
        assertEquals(0, result.first, result.second)
    }

    @Test fun javaCannotUseARuntimeEntitySetAsASingleEntityOrConstructScopeHandles() {
        val result = compileJava(
            "InvalidContextApi",
            """
            import top.mcfpp.mni.api.*;
            public class InvalidContextApi {
                public static void build(MinecraftBuilder b) {
                    b.score(b.entities(EntityQuery.allPlayers()), "hp");
                    EntityHandle self = new EntityHandle();
                }
            }
            """.trimIndent()
        )
        assertNotEquals(0, result.first)
        assertTrue(result.second.contains("EntitySet"), result.second)
        assertTrue(result.second.contains("EntityHandle"), result.second)
    }
}
