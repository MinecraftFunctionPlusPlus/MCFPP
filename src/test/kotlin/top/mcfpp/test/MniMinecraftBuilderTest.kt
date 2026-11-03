package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test

class MniMinecraftBuilderTest {
    private class Fixture(id: Long = 410) {
        val int = ImmutableTypeRef<IntT>(TypeKey(410, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(410, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(410, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(410, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(410, 5), "void", kind = TypeKind.VOID)
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("No lookup")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No lookup")
        }
        var charged = 0
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { count, _ -> charged += count }
        val origin = Origin(SourceSpan("builder-context.mcfpp", 0, 5, 1, 1, 1, 6))
        val quote = DefaultQuote(arena, types, origin)
        fun build(body: (CodeBuilder) -> Unit): Block = quote.block(Consumer(body))
    }

    private fun node(statement: Stmt) = (statement as ImmutableStmt).node
    private fun metadata(code: Code) = (code as OwnedCode).metadata

    @Test fun defaultAdapterConstructsIndependentExecutorPositionAndRotationFrames() {
        val f = Fixture()
        var hostCalls = 0
        val tree = f.build { b ->
            val mc = b.minecraft()
            val fixed = mc.score(ScoreHolder.named("#fixed"), "hp")
            val external = b.local(f.int, b.intLiteral(2))
            val position = mc.position(1.0, 2.0, 3.0)
            val rotation = mc.rotation(90.0, 10.0)
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                hostCalls++
                actor.at(position) { placed ->
                    hostCalls++
                    placed.rotated(rotation) { turned ->
                        hostCalls++
                        val score = turned.score(self, "hp")
                        turned.assign(score, turned.add(score.load(), turned.intLiteral(1)))
                        turned.assign(fixed, fixed.load())
                        turned.assign(external, external.load())
                        turned.evaluate(turned.blockMatches(turned.blockAt(turned.currentPosition()), BlockPredicate.tag("minecraft:logs")))
                        turned.position(self)
                        turned.rotation(self)
                        turned.currentRotation()
                    }
                }
            }
        }
        assertEquals(3, hostCalls)
        val asNode = assertIs<StmtNode.ExecuteAs>(node(tree.statements()[1]))
        assertNotEquals(asNode.parentContext.executor, asNode.context.executor)
        assertEquals(asNode.parentContext.position, asNode.context.position)
        assertEquals(asNode.parentContext.rotation, asNode.context.rotation)
        assertEquals(asNode.context.executor, (asNode.self as ImmutableEntityHandle).executor)
        val at = assertIs<StmtNode.At>(node(asNode.body.statements().single()))
        assertEquals(asNode.context.executor, at.context.executor)
        assertNotEquals(asNode.context.position, at.context.position)
        assertEquals(asNode.context.rotation, at.context.rotation)
        val rotated = assertIs<StmtNode.Rotated>(node(at.body.statements().single()))
        assertEquals(at.context.executor, rotated.context.executor)
        assertEquals(at.context.position, rotated.context.position)
        assertNotEquals(at.context.rotation, rotated.context.rotation)
        val statements = rotated.body.statements()
        val score = assertIs<ImmutableMinecraftScoreRef>(assertIs<StmtNode.Assign>(node(statements[0])).destination)
        assertSame(asNode.self, assertIs<MinecraftScoreTarget.Entity>(score.target).entity)
        val fixedAssign = assertIs<StmtNode.Assign>(node(statements[1]))
        assertIs<MinecraftScoreTarget.Fixed>((fixedAssign.destination as ImmutableMinecraftScoreRef).target)
        assertSame(fixedAssign.destination.load(), fixedAssign.expression)
        assertEquals(asNode.parentContext.id, metadata(fixedAssign.destination).executionContextId)
        val externalAssign = assertIs<StmtNode.Assign>(node(statements[2]))
        assertIs<ImmutableLocal<*>>(externalAssign.destination)
        assertSame(externalAssign.destination.load(), externalAssign.expression)
        val matches = assertIs<ExprNode.BlockMatches>((assertIs<StmtNode.Eval>(node(statements[3])).expression as ImmutableExpr<*>).node)
        assertEquals(rotated.context.position, assertIs<PositionNode.Current>((matches.block as ImmutableBlockHandle).position.let { it as ImmutablePositionHandle }.node).context)
        assertTrue(statements.all { metadata(it).executionContextId == rotated.context.id })
    }

    @Test fun everyPositionRotationAndBlockOperationRetainsItsActualDependencies() {
        val f = Fixture()
        lateinit var base: PositionHandle
        lateinit var world: PositionHandle
        lateinit var local: PositionHandle
        lateinit var rotation: RotationHandle
        lateinit var currentRotation: RotationHandle
        lateinit var entityPosition: PositionHandle
        lateinit var entityRotation: RotationHandle
        lateinit var self: EntityHandle
        f.build { b ->
            val mc = b.minecraft()
            base = mc.position(-1.0, 4.0, 2.0)
            rotation = mc.rotation(30.0, -20.0)
            world = mc.offset(base, 1.0, 2.0, 3.0)
            local = mc.localOffset(world, rotation, 0.25, 0.0, 0.5)
            val block = mc.blockAt(local)
            val predicate = BlockPredicate.block("minecraft:oak_log").withState("axis", "y")
            val matches = mc.blockMatches(block, predicate)
            assertSame(block, assertIs<ExprNode.BlockMatches>((matches as ImmutableExpr<*>).node).block)
            assertSame(predicate, (matches.node as ExprNode.BlockMatches).predicate)
            assertSame(f.bool, matches.type())
            mc.executeAs(mc.entities(EntityQuery.allEntities().withType("minecraft:pig").withTag("target").withLimit(2).sorted(EntitySort.NEAREST))) { child, entity ->
                self = entity
                entityPosition = child.position(entity)
                entityRotation = child.rotation(entity)
                currentRotation = child.currentRotation()
            }
        }
        assertEquals(PositionNode.Absolute(-1.0, 4.0, 2.0), (base as ImmutablePositionHandle).node)
        assertSame(base, assertIs<PositionNode.Offset>((world as ImmutablePositionHandle).node).base)
        val offset = assertIs<PositionNode.LocalOffset>((local as ImmutablePositionHandle).node)
        assertSame(world, offset.base)
        assertSame(rotation, offset.rotation)
        assertSame(self, assertIs<PositionNode.Entity>((entityPosition as ImmutablePositionHandle).node).entity)
        assertSame(self, assertIs<RotationNode.Entity>((entityRotation as ImmutableRotationHandle).node).entity)
        assertIs<RotationNode.Current>((currentRotation as ImmutableRotationHandle).node)
    }

    @Test fun scoreReadsAreCachedAndAssignmentsKeepTheSameAddressWithoutExtraAllocation() {
        val f = Fixture()
        f.build { b ->
            val mc = b.minecraft()
            val before = f.charged
            val ref = mc.score(ScoreHolder.named("#counter"), "counter")
            val allocated = f.charged
            assertTrue(allocated > before)
            repeat(5) { assertSame(ref.load(), ref.load()) }
            assertEquals(allocated, f.charged)
            mc.assign(ref, ref.load())
        }.also { tree ->
            val assignment = assertIs<StmtNode.Assign>(node(tree.statements().single()))
            val ref = assertIs<ImmutableMinecraftScoreRef>(assignment.destination)
            assertSame(ref.load(), assignment.expression)
            val read = assertIs<ExprNode.ScoreRead>((assignment.expression as ImmutableExpr<*>).node)
            assertSame(ref.target, read.target)
            assertEquals(ref.objective, read.objective)
        }
    }

    @Test fun parentAdapterIsInactiveDuringAChildAndBecomesActiveAgainAfterward() {
        val f = Fixture()
        f.build { b ->
            val mc = b.minecraft()
            val targets = mc.entities(EntityQuery.allPlayers())
            mc.executeAs(targets) { child, _ ->
                assertFailsWith<CodeModelException> { mc.currentPosition() }
                assertFailsWith<CodeModelException> { mc.intLiteral(3) }
                child.evaluate(child.intLiteral(1))
            }
            mc.evaluate(mc.intLiteral(2))
        }.also { assertEquals(2, it.statements().size) }
    }

    @Test fun closedChildAndRootAdaptersRejectBothMinecraftAndDelegatedOperations() {
        val f = Fixture()
        lateinit var root: MinecraftBuilder
        lateinit var child: MinecraftBuilder
        f.build { b ->
            root = b.minecraft()
            root.executeAs(root.entities(EntityQuery.allPlayers())) { scoped, _ -> child = scoped }
            assertFailsWith<CodeModelException> { child.position(0.0, 0.0, 0.0) }
            assertFailsWith<CodeModelException> { child.intLiteral(1) }
        }
        assertFailsWith<CodeModelException> { root.minecraft() }
        assertFailsWith<CodeModelException> { root.entities(EntityQuery.allPlayers()) }
        assertFailsWith<CodeModelException> { root.intLiteral(1) }
    }

    @Test fun childHandlesAndScoresCannotEscapeToParentOrSiblingScopes() {
        val f = Fixture()
        f.build { b ->
            val mc = b.minecraft()
            lateinit var escaped: EntityHandle
            lateinit var score: Ref<IntT>
            lateinit var position: PositionHandle
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { child, self ->
                escaped = self
                score = child.score(self, "hp")
                position = child.position(self)
            }
            assertFailsWith<CodeModelException> { mc.score(escaped, "hp") }
            assertFailsWith<CodeModelException> { mc.assign(score, mc.intLiteral(1)) }
            var calls = 0
            assertFailsWith<CodeModelException> { mc.at(position) { calls++ } }
            assertEquals(0, calls)
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { sibling, _ ->
                assertFailsWith<CodeModelException> { sibling.position(escaped) }
            }
        }
    }

    @Test fun independentSessionsRejectForeignHandlesBeforeRunningCallbacks() {
        val first = Fixture(411)
        lateinit var position: PositionHandle
        lateinit var targets: EntitySet
        first.build { b ->
            position = b.minecraft().position(0.0, 0.0, 0.0)
            targets = b.minecraft().entities(EntityQuery.allPlayers())
        }
        val second = Fixture(412)
        second.build { b ->
            val mc = b.minecraft()
            var calls = 0
            assertFailsWith<CodeModelException> { mc.at(position) { calls++ } }
            assertFailsWith<CodeModelException> { mc.executeAs(targets) { _, _ -> calls++ } }
            assertEquals(0, calls)
        }
    }

    @Test fun callbackFailureClosesTheChildAndRestoresTheParentWithoutPublishingAPartialStatement() {
        val f = Fixture()
        lateinit var retained: MinecraftBuilder
        val tree = f.build { b ->
            val mc = b.minecraft()
            assertFailsWith<UnsupportedOperationException> {
                mc.at(mc.currentPosition()) { child ->
                    retained = child
                    child.evaluate(child.intLiteral(99))
                    throw UnsupportedOperationException("stop")
                }
            }
            assertFailsWith<CodeModelException> { retained.currentRotation() }
            mc.evaluate(mc.intLiteral(7))
        }
        assertEquals(1, tree.statements().size)
        assertEquals(ConstantValue.Integral(7), (assertIs<StmtNode.Eval>(node(tree.statements().single())).expression as ImmutableExpr<*>).let { assertIs<ExprNode.Literal>(it.node).value })
    }

    @Test fun invalidNumericInputsAndObjectivesFailBeforeCallbackConsumption() {
        val f = Fixture()
        f.build { b ->
            val mc = b.minecraft()
            assertFailsWith<IllegalArgumentException> { mc.position(Double.NaN, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { mc.rotation(0.0, Double.POSITIVE_INFINITY) }
            val base = mc.currentPosition()
            val rotation = mc.currentRotation()
            assertFailsWith<IllegalArgumentException> { mc.offset(base, 0.0, Double.NaN, 0.0) }
            assertFailsWith<IllegalArgumentException> { mc.localOffset(base, rotation, Double.NaN, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { mc.score(ScoreHolder.named("#fixed"), "invalid objective") }
        }
    }

    @Test fun actualScoreTypesCannotBeLiedAboutByJavaErasure() {
        val f = Fixture()
        f.build { b ->
            val score = b.minecraft().score(ScoreHolder.named("#fixed"), "hp")
            @Suppress("UNCHECKED_CAST")
            val lie = score as Ref<BoolT>
            assertFailsWith<CodeModelException> { b.assign(lie, b.boolLiteral(true)) }
        }
    }

    @Test fun nestedPositionAndRotationCanYieldTheEnclosingExpressionButEntityIterationCannotGuaranteeIt() {
        val f = Fixture()
        val expression = f.quote.expression(
            f.int,
            Consumer { b ->
                val mc = b.minecraft()
                mc.at(mc.currentPosition()) { placed -> placed.rotated(placed.currentRotation()) { turned -> turned.yield(turned.intLiteral(7)) } }
            }
        ) as ImmutableExpr<*>
        assertIs<ExprNode.ExprBlock>(expression.node)
        assertFailsWith<CodeModelException> {
            f.quote.expression(
                f.int,
                Consumer { b ->
                    val mc = b.minecraft()
                    mc.executeAs(mc.entities(EntityQuery.allPlayers())) { child, _ -> child.yield(child.intLiteral(7)) }
                }
            )
        }
    }

    @Test fun appendFreshensContextChildrenAndKeepsExternalScoreReferences() {
        val f = Fixture()
        lateinit var external: Ref<IntT>
        val appended = f.build { b ->
            val mc = b.minecraft()
            external = mc.score(ScoreHolder.named("#fixed"), "hp")
            val fragment = f.quote.block(
                Consumer { nested ->
                    val actor = nested.minecraft()
                    actor.executeAs(actor.entities(EntityQuery.allPlayers())) { child, _ -> child.assign(external, external.load()) }
                }
            )
            b.append(fragment)
            b.append(fragment)
        }
        val nodes = appended.statements().map { statement ->
            assertIs<StmtNode.ExecuteAs>(node(assertIs<StmtNode.ScopeBlock>(node(statement)).block.statements().single()))
        }
        assertNotEquals(nodes[0].context.id, nodes[1].context.id)
        assertNotEquals(metadata(nodes[0].self).scopeId, metadata(nodes[1].self).scopeId)
        nodes.forEach { assertSame(external, assertIs<StmtNode.Assign>(node(it.body.statements().single())).destination) }
    }

    @Test fun actualTypeServiceDescriptorsAreCheckedBeforeBuildingScoreAndBlockExpressions() {
        val f = Fixture()
        val dishonest = object : TypeService by f.types {
            @Suppress("UNCHECKED_CAST")
            override fun intType(): TypeRef<IntT> = f.bool as TypeRef<IntT>
            @Suppress("UNCHECKED_CAST")
            override fun boolType(): TypeRef<BoolT> = f.int as TypeRef<BoolT>
        }
        DefaultQuote(f.arena, dishonest, f.origin).block(
            Consumer { b ->
                val mc = b.minecraft()
                assertFailsWith<CodeModelException> { mc.score(ScoreHolder.named("#fixed"), "hp") }
                val block = mc.blockAt(mc.currentPosition())
                assertFailsWith<CodeModelException> { mc.blockMatches(block, BlockPredicate.block("minecraft:stone")) }
            }
        )
    }

    @Test fun closedArenaAndDifferentExpansionsCannotReuseTheAdapter() {
        val f = Fixture(415)
        lateinit var retained: MinecraftBuilder
        f.build { b -> retained = b.minecraft() }
        f.arena.close()
        assertFailsWith<CodeModelException> { retained.currentPosition() }
        val other = Fixture(416)
        other.build { b -> assertNotEquals(f.arena.sessionId, metadata(b.minecraft().currentPosition()).sessionId) }
    }

    @Test fun compiledJavaVoidSamsActuallyConstructTheDefaultAdapterTree() {
        val directory = Files.createTempDirectory("mni-minecraft-builder-java-")
        try {
            val source = directory.resolve("MinecraftBuilderFixture.java")
            Files.writeString(
                source,
                """
                import top.mcfpp.mni.api.*;
                public class MinecraftBuilderFixture {
                    public static void build(CodeBuilder root) {
                        MinecraftBuilder mc = root.minecraft();
                        Ref<IntT> fixed = mc.score(ScoreHolder.named("#hits"), "hits");
                        mc.executeAs(mc.entities(EntityQuery.allPlayers()), (actor, self) -> {
                            actor.at(actor.position(self), placed -> placed.rotated(placed.rotation(self), turned -> {
                                Ref<IntT> hp = turned.score(self, "hp");
                                turned.assign(hp, turned.sub(hp.load(), turned.intLiteral(1)));
                                turned.assign(fixed, turned.intLiteral(1));
                                PositionHandle step = turned.localOffset(turned.currentPosition(), turned.currentRotation(), 0, 0, 0.5);
                                turned.evaluate(turned.blockMatches(turned.blockAt(step), BlockPredicate.tag("minecraft:logs")));
                            }));
                        });
                    }
                }
                """.trimIndent()
            )
            val classpath = listOf(CodeBuilder::class.java, kotlin.Unit::class.java).map {
                Path.of(it.protectionDomain.codeSource.location.toURI()).toString()
            }.distinct().joinToString(java.io.File.pathSeparator)
            val diagnostics = ByteArrayOutputStream()
            val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler())
            assertEquals(
                0,
                compiler.run(
                    null, diagnostics, diagnostics, "-proc:none", "--release", "21", "-classpath", classpath,
                    "-d", directory.toString(), source.toString()
                ),
                diagnostics.toString()
            )
            URLClassLoader(arrayOf(directory.toUri().toURL()), CodeBuilder::class.java.classLoader).use { loader ->
                val build = loader.loadClass("MinecraftBuilderFixture").getMethod("build", CodeBuilder::class.java)
                val tree = Fixture().build { build.invoke(null, it) }
                val actor = assertIs<StmtNode.ExecuteAs>(node(tree.statements().single()))
                val at = assertIs<StmtNode.At>(node(actor.body.statements().single()))
                val turned = assertIs<StmtNode.Rotated>(node(at.body.statements().single()))
                assertEquals(3, turned.body.statements().size)
                assertIs<ImmutableMinecraftScoreRef>(assertIs<StmtNode.Assign>(node(turned.body.statements()[0])).destination)
                assertIs<ExprNode.BlockMatches>((assertIs<StmtNode.Eval>(node(turned.body.statements()[2])).expression as ImmutableExpr<*>).node)
            }
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
