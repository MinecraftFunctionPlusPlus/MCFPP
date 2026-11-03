package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.*
import top.mcfpp.backend.EntityContextCommandOperations
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class ReferenceMniTest {
    @Test fun projectedOrdinaryValueCarriesRuntimeDescriptorAndReferenceEvidence() {
        MCFPPStringTest.readFromString("""
            func relay(value: ScoreRef): ScoreRef { value.set(19); return value; }
            func main() {}
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val namespace = GlobalScope.localNamespaces.getValue("default.test")
        val caller = namespace.scope.functions.getValue("main").single()
        caller.runInFunction {
            Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
            val objective = MCString("objective").apply {
                nbtPath = NBTPath.stack.intIndex(0).memberIndex("objective")
                Function.addCommand(Commands.dataSetValue(nbtPath, StringTag("hp")))
                StorageAccess.publishNbt(this)
            }
            val input = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_HOLDER,
                linkedMapOf("holder" to StorageAccess.literal(MCFPPBaseType.String, CompilerValue.Text("Alice")),
                    "objective" to objective), name = "input")
            caller.scope.putVar("input", input, true)
            val syntax = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("relay(input)")))).functionCall()
            val prepared = CompilerBindingEnvironment.prepareCall(syntax, caller)
            val ready = assertIs<CompilerCallPreparationResult.Ready>(prepared,
                (prepared as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { "${it.code}: ${it.message}" })
            ExpansionSession(ExpansionSite(ready.call.source, "reference-mni"), ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = namespace.identifier),
                CompilerTransaction(CompilerTransactionBackend {
                    object : PreparedCompilerCommit { override fun apply() = Unit; override fun rollback() = Unit }
                })).use { session ->
                val bindings = CompilerSourceBindings.capture(ready.environment, session, caller)
                val environment = SourceProjectionEnvironment(bindings)
                @Suppress("UNCHECKED_CAST")
                val referenceType = bindings.binding.types.ref(MCFPPScoreRefType) as TypeRef<ScoreRefT>
                val intType = bindings.binding.types.ref(MCFPPBaseType.Int)
                assertFalse(referenceType.sameType(intType))
                assertFalse(intType.isAssignableFrom(referenceType), "An owned ScoreRef cannot satisfy REF int")
                val projected = SourceCodeProjector(environment).arguments(ready.call)
                val arena = session.arena
                val metadata = arena.metadata(projected.scope, session.origin, bindings.loweringTarget.executionContext)
                val invocation = arena.expr(metadata, referenceType,
                    ExprNode.Call(ready.call.target.function, projected.arguments, projected.readonlyArguments))
                val region = arena.freshRegion()
                val block = arena.block(metadata, projected.orderedPrelude.statements() +
                    arena.stmt(metadata, StmtNode.Yield(region, invocation)))
                val code = arena.expr(metadata, referenceType, ExprNode.ExprBlock(block, referenceType, region))
                val bridge = CompilerBridge(bindings)
                bridge.prepare(environment.lower(code)).prepare().apply()
                val returned = assertNotNull(bridge.result())
                assertNull(StorageAccess.snapshot(returned))
                assertEquals(MCFPPScoreRefType, returned.type)
                val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                    .flatMap { it.scope.functions.values.flatten() }
                    .associate { it.namespaceID.toString() to it.commands.analyzeAll().filterNot { command -> command.startsWith("#") } } +
                    Project.macroFunction.mapKeys { "mcfpp:dynamic/" + it.key }.mapValues { it.value.lines() }
                val machine = ScoreCommandExecutor(caller.commands.analyzeAll().filterNot { it.startsWith("#") }, functions)
                assertEquals("Alice", machine.field(returned, "holder"))
                assertEquals("hp", machine.field(returned, "objective"))
                assertEquals(19, machine.values["Alice hp"])
                assertEquals(0, Project.errorCount)
            }
        }
    }

    @Test fun borrowedPublicReferenceKeepsItsOwnerInsideNestedExecuteAs() {
        MCFPPStringTest.readFromString("func consume(value: ScoreRef) {}\nfunc main() {}", version = "26.3")
        assertEquals(0, Project.errorCount)
        val namespace = GlobalScope.localNamespaces.getValue("default.test")
        val caller = namespace.scope.functions.getValue("main").single()
        val consumer = namespace.scope.functions.getValue("consume").single()
        caller.runInFunction {
            ContextSession().use { contextSession ->
                val owner = contextSession.root().withPosContext(contextSession.openScope())
                ContextBorrowing.withContext(owner) {
                    val encoder = EntityContextCommandOperations.encoder()
                    val objective = MCString("objective").apply {
                        nbtPath = NBTPath.stack.intIndex(0).memberIndex("objective")
                        Function.addCommand(Commands.dataSetValue(nbtPath, StringTag("hp")))
                        StorageAccess.publishNbt(this)
                    }
                    val input = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_ENTITY,
                        mapOf("objective" to objective), BoundExecutorVar("self", owner.executor, encoder), "input")
                    caller.scope.putVar("input", input, true)
                    val syntax = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("consume(input)")))).functionCall()
                    val prepared = CompilerBindingEnvironment.prepareCall(syntax, caller)
                    val ready = assertIs<CompilerCallPreparationResult.Ready>(prepared,
                        (prepared as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { "${it.code}: ${it.message}" })
                    ExpansionSession(ExpansionSite(ready.call.source, "borrowed-reference-mni"), ExpansionBudget(),
                        CompilerSourceBindings.expansionEnvironment(ready.environment, namespace = namespace.identifier),
                        CompilerTransaction(CompilerTransactionBackend {
                            object : PreparedCompilerCommit { override fun apply() = Unit; override fun rollback() = Unit }
                        })).use { session ->
                        val bindings = CompilerSourceBindings.capture(ready.environment, session, caller)
                        val environment = SourceProjectionEnvironment(bindings)
                        val projected = SourceCodeProjector(environment).arguments(ready.call)
                        val arena = session.arena
                        val parent = arena.contextFrame(requireNotNull(bindings.loweringTarget.executionContext), projected.scope, ready.call.source)
                        val metadata = arena.metadata(projected.scope, session.origin, parent.id)
                        val childScope = arena.childScope(projected.scope)
                        val child = arena.executorContext(parent, childScope)
                        val childMetadata = arena.metadata(childScope, session.origin, child.id)
                        val self = arena.entityHandle(childMetadata, child)
                        val invocation = arena.expr(childMetadata, ready.call.target.function.returnType(),
                            ExprNode.Call(ready.call.target.function, projected.arguments, projected.readonlyArguments))
                        val body = arena.block(childMetadata, listOf(arena.stmt(childMetadata, StmtNode.Eval(invocation))))
                        val nested = arena.stmt(metadata, StmtNode.ExecuteAs(
                            arena.entitySet(metadata, EntityQuery.allEntities()), self, body, parent, child))
                        val code = arena.block(metadata, projected.orderedPrelude.statements() + nested)
                        CompilerBridge(bindings).prepare(environment.lower(code)).prepare().apply()
                        val actual = namespace.scope.functions.values.flatten().single {
                            it.actualCallBody && it.ast === consumer.ast
                        }
                        val received = assertNotNull(actual.scope.getVar("value"))
                        val borrow = assertIs<ContextBorrowPayload.Executor>(ContextBorrowing.provenance(received).single())
                        assertSame(owner, borrow.owner)
                        assertSame(encoder, borrow.encoder)
                        assertEquals(0, Project.errorCount)
                    }
                }
            }
        }
    }

    private fun ScoreCommandExecutor.field(value: Var<*>, field: String) =
        (readNbt("mcfpp:system", StorageAccess.ensure(value).path.toCommandPart().toString()
            .removePrefix("storage mcfpp:system ") + ".$field") as StringTag).value
}
