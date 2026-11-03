package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.analysis.lambda.LambdaCaptureRules
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.*
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class ReferenceFlowTest {
    @Test fun runtimeSelectedReferencesSurviveArgumentsReturnsContainersAndCaptures() {
        MCFPPStringTest.readFromString("""
            func select(flag: bool, first: ScoreRef, second: ScoreRef): ScoreRef {
                if (flag) { return first; }
                return second;
            }
            func identity(value: ScoreRef): ScoreRef { return value; }
            func main() {}
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val scope = GlobalScope.localNamespaces.getValue("default.test").scope
        val caller = scope.functions.getValue("main").single()
        caller.runInFunction {
            Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
            val first = score("Alice", runtimeText("objective", "hp"))
            val second = score("Bob", text("mana"))
            val flag = MCFPPBaseType.Bool.buildUnConcrete("flag").apply {
                nbtPath = NBTPath.stack.intIndex(0).memberIndex("flag")
                Function.addCommand(Commands.dataSetValue(nbtPath, top.mcfpp.nbt.tags.primitive.ByteTag(0)))
                StorageAccess.publishNbt(this)
            }
            val selected = scope.functions.getValue("select").single().invoke(listOf(flag, first, second), null)
            assertFalse(selected.isError)
            assertNull(StorageAccess.snapshot(selected), "A branch-selected address must remain runtime data")
            val relayed = scope.functions.getValue("identity").single().invoke(listOf(selected), null)
            val list = StorageAccess.listLiteral(MCFPPListType(MCFPPScoreRefType), listOf(relayed), "references")
            StorageAccess.materialize(list)
            val extracted = StorageAccess.element(list, StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(0)), MCFPPScoreRefType)
            val local = MCFPPScoreRefType.buildUnConcrete("saved")
            StorageAccess.write(local, extracted)
            val captured = LambdaCaptureRules.capture(local)
            val capture = assertIs<LambdaCaptureResult.Captured>(captured,
                (captured as? LambdaCaptureResult.Rejected)?.diagnostic?.message).capture
            val owned = assertNotNull(capture.owned)
            StorageAccess.materialize(owned)
            val commands = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
                .associate { it.namespaceID.toString() to it.commands.analyzeAll().filterNot { command -> command.startsWith("#") } }
            val machine = ScoreCommandExecutor(caller.commands.analyzeAll().filterNot { it.startsWith("#") }, commands)
            assertEquals("Bob", machine.field(relayed, "holder"))
            assertEquals("mana", machine.field(relayed, "objective"))
            assertEquals("Bob", machine.field(owned, "holder"))
            assertEquals("mana", machine.field(owned, "objective"))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun borrowedReferenceCopiesKeepScopeAndCannotEscapeAfterErasureOrNesting() {
        MCFPPStringTest.readFromString("func consume(value: ScoreRef) {}\nfunc main() {}", version = "26.3")
        val scope = GlobalScope.localNamespaces.getValue("default.test").scope
        val caller = scope.functions.getValue("main").single()
        caller.runInFunction {
            ContextSession().use { session ->
                val context = session.root().withPosContext(session.openScope())
                lateinit var saved: Var<*>
                ContextBorrowing.withContext(context) {
                    val target = BoundExecutorVar("self", context.executor)
                    val reference = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_ENTITY,
                        mapOf("objective" to text("hp")), target, "borrowed")
                    saved = StorageAccess.capture(reference)
                    assertNull(ContextBorrowing.diagnostic(saved))
                    assertEquals(1, ContextBorrowing.provenance(saved).size)
                    assertFalse(scope.functions.getValue("consume").single().invoke(listOf(saved), null).isError)
                    assertIs<LambdaCaptureResult.Rejected>(LambdaCaptureRules.capture(saved))
                    assertNotNull(ContextBorrowing.escapeDiagnostic(saved, "a function return"))
                    // Erased/projected adapters expose the same lifetime facts as their source.
                    val binding = StorageAccess.ensure(saved)
                    val erased = StorageAccess.adapter(MCFPPBaseType.Any, "erased", binding)
                    assertNotNull(ContextBorrowing.escapeDiagnostic(erased, "global storage"))
                    val outer = StorageAccess.literal(MCFPPNBTType.NBT, CompilerValue.Record(emptyMap()), "outer")
                    val child = StorageAccess.ensure(outer).field("nested")
                    StorageAccess.copyRuntimeEvidence(binding, child)
                    assertNotNull(ContextBorrowing.escapeDiagnostic(outer, "a function return"))
                    assertEquals(0, Project.errorCount)
                }
                assertNotNull(ContextBorrowing.diagnostic(saved), "Closing the synchronous scope invalidates copied references")
            }
        }
    }

    @Test fun readonlyFreezingRejectsScopedReferenceCapabilities() {
        MCFPPStringTest.readFromString("func main() {}", version = "26.3")
        val caller = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        caller.runInFunction {
            ContextSession().use { session ->
                val context = session.root().withPosContext(session.openScope())
                ContextBorrowing.withContext(context) {
                    val reference = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_ENTITY,
                        mapOf("objective" to text("hp")), BoundExecutorVar("self", context.executor), "borrowed")
                    assertNotNull(StorageAccess.snapshot(reference), "The record alone must not authorize freezing the capability")
                    val errors = Project.errorCount
                    assertNull(StorageAccess.freezeReadonly(reference, "readonlyRef"))
                    assertTrue(Project.errorCount > errors, "Freezing a scoped reference must emit an escape diagnostic")
                    assertEquals(1, ContextBorrowing.provenance(reference).size)
                    assertNull(ContextBorrowing.diagnostic(StorageAccess.capture(reference)))
                }
            }
        }
    }

    @Test fun runtimeNbtReferenceCrossesDataListAndReturnedClosureBeforeWritingItsTarget() {
        val factories = Files.readString(Path.of("src/main/mcfpp/lang/reference.mcfpp"))
        MCFPPStringTest.readFromString(factories + "\n" + """
            data Saved { var handle: NBTRef<int> }
            func choose(flag: bool, first: NBTRef<int>, second: NBTRef<int>): NBTRef<int> {
                if (flag) { return first; }
                return second;
            }
            func relay(value: NBTRef<int>): NBTRef<int> { return value; }
            func relayBox(value: Saved): Saved { return value; }
            func makeSetter(value: NBTRef<int>): () -> void {
                return { value.set(23); };
            }
            func applySelected(flag: bool) {
                var selected = choose(flag, nbtStorageRef<int>("refs:flow", "first"), nbtStorageRef<int>("refs:flow", "second"));
                var saved: Saved = {handle: relay(selected)} as Saved;
                var box = relayBox(saved);
                var refs: list<NBTRef<int>> = [box.handle];
                var action = makeSetter(refs[0]);
                action();
            }
            func main() {
                /data modify storage mcfpp:system stack_frame prepend value {}
                /data modify storage refs:flow first set value 7
                /data modify storage refs:flow second set value 11
                external choice = false;
                applySelected(choice);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val scope = GlobalScope.localNamespaces.getValue("default.test").scope
        val caller = scope.functions.getValue("main").single()
        caller.runInFunction {
            val selected = assertNotNull(scope.functions.getValue("applySelected").single().scope.getVar("selected"))
            assertNull(StorageAccess.snapshot(selected), "The selected address must depend on the runtime flag")
            val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
                .flatMap { it.scope.functions.values.flatten() }
                .associate { it.namespaceID.toString() to it.commands.analyzeAll().filterNot { command -> command.startsWith("#") } } +
                Project.macroFunction.mapKeys { "mcfpp:dynamic/" + it.key }.mapValues { it.value.lines() }
            val machine = ScoreCommandExecutor(caller.commands.analyzeAll().filterNot { it.startsWith("#") }, functions)
            assertEquals(7, (machine.readNbt("refs:flow", "first") as top.mcfpp.nbt.tags.primitive.IntTag).value)
            assertEquals(23, (machine.readNbt("refs:flow", "second") as top.mcfpp.nbt.tags.primitive.IntTag).value)
        }
    }

    private fun text(value: String): Var<*> = StorageAccess.literal(MCFPPBaseType.String, CompilerValue.Text(value))
    private fun score(holder: String, objective: Var<*>) = ReferenceValues.create(MCFPPScoreRefType,
        ReferenceKind.SCORE_HOLDER, linkedMapOf("holder" to text(holder), "objective" to objective))
    private fun runtimeText(name: String, value: String): MCString = MCString(name).apply {
        nbtPath = NBTPath.stack.intIndex(0).memberIndex(name)
        Function.addCommand(Commands.dataSetValue(nbtPath, StringTag(value)))
        StorageAccess.publishNbt(this)
    }
    private fun ScoreCommandExecutor.field(value: Var<*>, field: String) =
        (readNbt("mcfpp:system", StorageAccess.ensure(value).path.toCommandPart().toString()
            .removePrefix("storage mcfpp:system ") + ".$field") as StringTag).value
}
