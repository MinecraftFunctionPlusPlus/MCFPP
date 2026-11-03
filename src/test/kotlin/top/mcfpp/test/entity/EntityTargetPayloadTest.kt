package top.mcfpp.test.entity

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.lambda.LambdaCaptureRules
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.SelectorExpression
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEntityType
import kotlin.test.*

class EntityTargetPayloadTest {
    @Test
    fun staticTextTargetsFreezeSourceValuesAndRoundTripWithoutSelectorKinds() {
        for (kind in SelectorExpression.DirectKind.entries) inFunction {
            val source = MCString("source").apply { bindDeclaration() }
            StorageAccess.write(source, text(initial(kind)))
            val target = SelectorVar(target(kind, source))
            val snapshot = assertNotNull(StorageAccess.snapshot(target))
            StorageAccess.write(source, text(replacement(kind)))
            assertEquals(initial(kind), target.toCommandPart().toString())
            val restored = assertIs<SelectorVar>(StorageAccess.restore(target.type, snapshot, "restored"))
            assertEquals(initial(kind), restored.toCommandPart().toString())
            assertEquals(snapshot, StorageAccess.snapshot(restored))
            val expression = assertNotNull(StorageAccess.selectorProgram(assertNotNull(restored.storageBinding)))
            assertNull(expression.selectorKind)
            assertEquals(kind, assertNotNull(expression.directTarget).kind)
            assertNull(expression.selector(StorageAccess.boundTypes(restored)))
            assertFailsWith<IllegalStateException> { expression.kind }
            assertEquals(0, Project.errorCount)
        }
    }

    @Test
    fun runtimeTextTargetsOwnOperandsAcrossAssignmentAndOrdinaryCapture() {
        for (kind in SelectorExpression.DirectKind.entries) inFunction { function ->
            Function.addCommand(Commands.stackIn())
            val source = incoming("input", initial(kind))
            val selected = SelectorVar(target(kind, source), "selected")
            val copied = assertIs<SelectorVar>(StorageAccess.write(SelectorVar("copied"), selected))
            val closure = assertIs<LambdaCaptureResult.Captured>(LambdaCaptureRules.capture(selected, selected.symbol)).capture
            val owned = assertIs<SelectorVar>(closure.owned)
            val nested = assertIs<LambdaCaptureResult.Captured>(LambdaCaptureRules.capture(owned, owned.symbol)).capture
            val nestedOwned = assertIs<SelectorVar>(nested.owned)
            assertNull(StorageAccess.snapshot(copied))
            assertNull(owned.storageBinding?.view)
            assertNotEquals(operand(copied).nbtPath.toString(), source.nbtPath.toString())
            assertNotEquals(operand(copied).nbtPath.toString(), operand(owned).nbtPath.toString())

            Function.addCommand(Commands.dataSetValue(source.nbtPath, StringTag(replacement(kind))))
            StorageAccess.write(selected, SelectorVar(target(kind, text(replacement(kind)))))
            Function.addCommands(Command.buildAll("say", copied).buildMacroFunction())
            val machine = execute(function)
            assertEquals(listOf(initial(kind)), machine.messages)
            for (copy in listOf(copied, owned, nestedOwned)) {
                assertEquals(initial(kind), valueAt(machine, operand(copy)))
                assertEquals(kind, StorageAccess.selectorProgram(assertNotNull(copy.storageBinding))?.directTarget?.kind)
            }
            assertEquals(0, Project.errorCount)
        }
    }

    @Test
    fun callAndReturnPayloadRoutesRelocateRuntimeOperandsAcrossFrames() {
        for (kind in SelectorExpression.DirectKind.entries) inFunction { function ->
            Function.addCommand(Commands.stackIn())
            val source = incoming("callerInput", initial(kind))
            val caller = SelectorVar(target(kind, source), "caller")
            Function.addCommand(Commands.stackIn())
            // These are the same copy and rebase operations used by Function's argument/return paths.
            val parameter = assertIs<SelectorVar>(StorageAccess.capturePayload(caller.type, caller, "parameter",
                sourceFrameOffset = 1, targetFrame = 0))
            Function.addCommand(Commands.dataSetValue(StorageAccess.callerValue(source).nbtPath, StringTag(replacement(kind))))
            val returned = assertIs<SelectorVar>(StorageAccess.capturePayload(parameter.type, parameter, "returned",
                sourceFrameOffset = 0, targetFrame = 1))
            val result = assertIs<SelectorVar>(StorageAccess.rebasePayload(returned, -1))
            Function.addCommand(Commands.stackOut())
            Function.addCommands(Command.buildAll("say", result).buildMacroFunction())
            val machine = execute(function)
            assertEquals(listOf(initial(kind)), machine.messages)
            assertEquals(initial(kind), valueAt(machine, operand(result)))
            assertEquals(1, machine.stackDepth)
            assertEquals(0, Project.errorCount)
        }
    }

    @Test
    fun ownedErasedPayloadKeepsEntityProducerAndCanBeCapturedAgain() = inFunction { function ->
        Function.addCommand(Commands.stackIn())
        val source = incoming("input", "Alex")
        val entity = SelectorVar(EntityTarget.PlayerName(source))
        val erased = assertNotNull(StorageAccess.capturePayload(MCFPPBaseType.Any, entity, "erased"))
        assertEquals(MCFPPBaseType.Any, erased.type)
        assertEquals(MCFPPEntityType.NormalSelector.typeId, StorageAccess.actualType(erased).typeId)
        val capture = assertIs<LambdaCaptureResult.Captured>(LambdaCaptureRules.capture(erased, erased.symbol)).capture
        val copied = assertNotNull(capture.owned)
        val restored = assertIs<SelectorVar>(StorageAccess.capturePayload(MCFPPEntityType.NormalSelector, copied, "restored"))
        Function.addCommand(Commands.dataSetValue(source.nbtPath, StringTag("Changed")))
        assertEquals("Alex", valueAt(execute(function), operand(restored)))
        assertEquals(0, Project.errorCount)
    }

    @Test
    fun branchDependentTargetStructuresAreDiagnosedRatherThanReboundToSelf() = inFunction {
        val value = SelectorVar(EntityTarget.PlayerName(text("Alex")), "branched")
        val binding = assertNotNull(value.storageBinding)
        val left = binding.data.facts.fork()
        StorageAccess.write(value, SelectorVar(EntityTarget.UUIDText(text(initial(SelectorExpression.DirectKind.UUID_TEXT)))))
        binding.data.facts.replaceWith(left.join(binding.data.facts))
        assertNull(value.entityTargetOrNull())
        assertFalse(value.entityFacts.selectingSingleEntity)
        val errors = Project.errorCount
        assertTrue(StorageAccess.read(value).isError)
        assertEquals(errors + 1, Project.errorCount)
    }

    private fun incoming(name: String, initial: String): MCString = MCString(name).also {
        val binding = StorageAccess.bindIncomingParameter(it)
        Function.addCommand(Commands.dataSetValue(binding.path, StringTag(initial)))
    }

    private fun operand(value: SelectorVar): MCString = when (val target = value.entityTargetOrNull()) {
        is EntityTarget.PlayerName -> target.name
        is EntityTarget.UUIDText -> target.uuid
        else -> error("Expected a direct text target")
    }

    private fun valueAt(machine: ScoreCommandExecutor, value: Var<*>): String =
        assertIs<StringTag>(machine.readNbt("mcfpp:system", value.nbtPath.pathToCommandPart().toString())).value

    private fun execute(function: Function): ScoreCommandExecutor {
        val functions = GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.flatMap { command -> command.analyze().lines() } }
            .toMutableMap()
        Project.macroFunction.forEach { (name, body) -> functions["mcfpp:dynamic/$name"] = body.lines() }
        return ScoreCommandExecutor(function.commands.flatMap { it.analyze().lines() }, functions)
    }

    private fun target(kind: SelectorExpression.DirectKind, value: MCString): EntityTarget = when (kind) {
        SelectorExpression.DirectKind.PLAYER_NAME -> EntityTarget.PlayerName(value)
        SelectorExpression.DirectKind.UUID_TEXT -> EntityTarget.UUIDText(value)
    }

    private fun initial(kind: SelectorExpression.DirectKind): String = when (kind) {
        SelectorExpression.DirectKind.PLAYER_NAME -> "Alex"
        SelectorExpression.DirectKind.UUID_TEXT -> "00000000-0000-0000-0000-000000000001"
    }

    private fun replacement(kind: SelectorExpression.DirectKind): String = when (kind) {
        SelectorExpression.DirectKind.PLAYER_NAME -> "Changed"
        SelectorExpression.DirectKind.UUID_TEXT -> "00000000-0000-0000-0000-000000000002"
    }

    private fun text(value: String) = MCString(StringTag(value))

    private fun inFunction(action: (Function) -> Unit) {
        Project.init()
        val namespace = Namespace("fixture.entity_payload")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val function = Function("payload", namespace.identifier, null)
        namespace.scope.addFunction(function, false)
        function.runInFunction { action(function) }
    }
}
