package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.Void
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.context.*
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.*
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class ReferenceAccessTest {
    @Test fun allAddressKindsReadWriteThroughUnknownPayloadsAndMissingReadsClearTheirSlots() = fixture { function ->
        val holder = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_HOLDER,
            mapOf("holder" to text("#fixed"), "objective" to text("hp")))
        assertSame(Void, ReferenceAccess.set(holder, MCInt(0)))
        val directEnd = function.commands.size
        val direct = read(holder, "direct_zero")
        assertTrue(function.commands.drop(directEnd).any { "scoreboard players get #fixed hp" in it.toString() })

        val unknownScore = unknown(holder, "unknown_score")
        assertSame(Void, ReferenceAccess.set(unknownScore, MCInt(9)))
        val score = read(unknownScore, "dynamic_score")
        Function.addCommand("scoreboard players reset #fixed hp")
        val missingValue = MCFPPBaseType.Int.buildUnConcrete("missing_value")
        val missing = ScoreBool("missing_success")
        ReferenceAccess.readInto(unknownScore, missingValue, missing)

        Function.addCommand("data modify storage test:payload payload set value [{\"a.b\":2},{\"a.b\":0}]")
        val index = MCInt("captured_index")
        Function.addCommand("scoreboard players set ${index.name} ${index.sbObject} 0")
        StorageAccess.publishScore(index, StorageLayout.Scoreboard(index.name, index.sbObject.toString()))
        val storage = ReferenceValues.create(MCFPPNBTRefType(MCFPPBaseType.Int), ReferenceKind.NBT_STORAGE,
            mapOf("storage" to text("test:payload"), "path" to text("payload")),
            pathSuffix = listOf(ReferencePathPart.Index(index), ReferencePathPart.Field("a.b")))
        StorageAccess.write(index, MCInt(1))
        val unknownStorage = unknown(storage, "unknown_storage")
        ReferenceAccess.set(unknownStorage, MCInt(6))
        val stored = read(unknownStorage, "storage_value")
        Function.addCommand("data remove storage test:payload payload[0].\"a.b\"")
        val missingNbtValue = MCFPPBaseType.Int.buildUnConcrete("missing_nbt_value")
        val missingNbt = ScoreBool("missing_nbt_success")
        ReferenceAccess.readInto(unknownStorage, missingNbtValue, missingNbt)

        val target = SelectorVar(EntityTarget.PlayerName(text("Alice")))
        val entityScore = unknown(ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_ENTITY,
            mapOf("objective" to text("points")), target), "entity_score")
        ReferenceAccess.set(entityScore, MCInt(12))
        val entityPoints = read(entityScore, "entity_points")
        val entityNbt = unknown(ReferenceValues.create(MCFPPNBTRefType(MCFPPNBTType.Short), ReferenceKind.NBT_ENTITY,
            mapOf("path" to text("Air")), target), "entity_nbt")
        ReferenceAccess.set(entityNbt, StorageAccess.literal(MCFPPNBTType.Short, CompilerValue.Integral(3)))
        val entityAir = read(entityNbt, "entity_air")
        val block = unknown(ReferenceValues.create(MCFPPNBTRefType(MCFPPBaseType.Int), ReferenceKind.NBT_BLOCK,
            mapOf("dimension" to text("minecraft:the_nether"), "x" to MCInt(1), "y" to MCInt(64), "z" to MCInt(2), "path" to text("count"))), "block")
        ReferenceAccess.set(block, MCInt(5))
        val blockRead = read(block, "block_value")
        assertEquals(0, Project.errorCount)

        val machine = execute(function)
        for ((name, expected) in mapOf("direct_zero" to 0, "dynamic_score" to 9, "storage_value" to 6,
            "entity_points" to 12, "entity_air" to 3, "block_value" to 5))
            assertEquals(expected, (machine.readStorage("test:results", name).value as Number).toInt(), name)
        for (success in listOf(direct, score, stored, entityPoints, entityAir, blockRead))
            assertEquals(1, machine.score(success.name, success.boolObject.toString()))
        assertEquals(0, machine.score(missing.name, missing.boolObject.toString()))
        assertEquals(0, machine.score(missingNbt.name, missingNbt.boolObject.toString()))
        assertFailsWith<IllegalStateException> { machine.readStorage("mcfpp:system", path(missingValue)) }
        assertFailsWith<IllegalStateException> { machine.readStorage("mcfpp:system", path(missingNbtValue)) }
        assertFailsWith<IllegalStateException> { machine.readStorage("mcfpp:system", "reference_access") }
        assertEquals(0, (machine.readStorage("test:payload", "payload[1].\"a.b\"").value as Number).toInt())
        assertEquals(3, (machine.readEntity("Alice", "Air").value as Number).toInt())
        assertEquals(5, (machine.readBlock("minecraft:the_nether", 1, 64, 2, "count").value as Number).toInt())
        assertEquals(3, machine.executed.count { it == "scoreboard players get #fixed hp" })
        assertEquals(2, machine.executed.count {
            it.startsWith("data modify storage ") && it.endsWith("set from storage test:payload payload[0].\"a.b\"")
        })
    }

    @Test fun missingSingleReadCapabilityDoesNotInventASelectionAndWritesRemainMultiple() = fixture { function ->
        val players = SelectorVar(EntitySelector(EntitySelector.Companion.SelectorType.ALL_PLAYERS))
        val reference = ReferenceValues.create(MCFPPNBTRefType(MCFPPNBTType.Short), ReferenceKind.NBT_ENTITY,
            mapOf("path" to text("Air")), players)
        assertFailsWith<ContextAccessException> { ReferenceAccess.requireReadable(reference) }
        val runtime = unknown(reference, "multiple")
        ReferenceAccess.set(runtime, StorageAccess.literal(MCFPPNBTType.Short, CompilerValue.Integral(4)))
        val value = MCFPPNBTType.Short.buildUnConcrete("unreadable")
        val success = ScoreBool("multiple_success")
        ReferenceAccess.readInto(runtime, value, success)
        val machine = execute(function)
        assertEquals(0, machine.score(success.name, success.boolObject.toString()))
        assertEquals(4, (machine.readEntity("Alice", "Air").value as Number).toInt())
        assertEquals(4, (machine.readEntity("Bob", "Air").value as Number).toInt())
        assertTrue(machine.executed.none { it.startsWith("data modify storage ") && it.contains("set from entity") })
        assertEquals(0, Project.errorCount)
    }

    @Test fun writeChecksPointeeTypesAndRejectsNestedBorrowedDescriptorsEscapingToExternalNbt() = fixture {
        val reference = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_HOLDER,
            mapOf("holder" to text("#fixed"), "objective" to text("hp")))
        assertTrue(ReferenceAccess.set(reference, text("wrong")).isError)
        val type = MCFPPDictType(MCFPPScoreRefType)
        val external = ReferenceValues.create(MCFPPNBTRefType(type), ReferenceKind.NBT_STORAGE,
            mapOf("storage" to text("test:payload"), "path" to text("saved")))
        ContextSession().use { session ->
            val root = session.root()
            val context = root.withEntityContext(session.openScope(root.scope))
            ContextBorrowing.withContext(context) {
                val executor = BoundExecutorVar("executor", context.executor, top.mcfpp.backend.EntityContextCommandOperations.encoder())
                val borrowed = ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_ENTITY,
                    mapOf("objective" to text("hp")), executor)
                // Model nested evidence arriving through the ordinary synchronous value ABI.
                val wrapped = type.buildUnConcrete("wrapped")
                wrapped.nbtPath = NBTPath.stack.intIndex(0).memberIndex("wrapped")
                Function.addCommand(Commands.dataSetValue(wrapped.nbtPath, top.mcfpp.nbt.tags.CompoundTag()))
                StorageAccess.publishNbt(wrapped)
                StorageAccess.copyRuntimeEvidence(StorageAccess.ensure(borrowed), StorageAccess.ensure(wrapped).field("borrowed"))
                assertTrue(ReferenceAccess.set(external, wrapped).isError)
            }
        }
        assertEquals(2, Project.errorCount)
    }

    private fun fixture(body: (Function) -> Unit) {
        MCFPPStringTest.readFromString("func main() {}", version = "26.3")
        assertEquals(0, Project.errorCount)
        ReferenceAccessHelpers.reset()
        val function = Function("referenceAccess", "test", null)
        GlobalScope.getOrCreateNamespace("test").scope.addFunction(function, false)
        function.runInFunction {
            Function.addCommand(Commands.stackIn())
            body(function)
        }
    }

    private fun text(value: String) = MCString(StringTag(value))
    private fun unknown(source: ReferenceVar, name: String): ReferenceVar = source.referenceType.buildUnConcrete(name).apply {
        nbtPath = NBTPath.stack.intIndex(0).memberIndex(name)
        StorageAccess.encodeTo(nbtPath, source)
        StorageAccess.publishNbt(this)
        assertNull(ReferenceValues.descriptor(this))
    }
    private fun path(value: Var<*>) = StorageAccess.ensure(value).path.pathToCommandPart().analyze()
    private fun read(reference: Var<*>, name: String): ScoreBool {
        val value = (reference.type as MCFPPReferenceType).valueType.buildUnConcrete(name)
        val success = ScoreBool(name + "_found")
        ReferenceAccess.readInto(reference, value, success)
        Function.addCommand(Commands.dataSetFrom(NBTPath(StorageSource("test:results")).memberIndex(name), StorageAccess.ensure(value).path))
        return success
    }
    private fun execute(function: Function): ContextCommandExecutor {
        val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
            .flatMap { it.scope.functions.values.flatten() }.flatMap { listOf(it) + it.compiledFunctions.values }
            .associate { it.namespaceID.toString() to it.commands.flatMap { command -> command.analyze().lines() } }
        val alice = ContextEntity("Alice", "minecraft:player", position = ContextPosition(0.0, 64.0, 0.0),
            rotation = ContextRotation(0.0, 0.0), nbt = mapOf("Air" to ShortTag(9)))
        val bob = alice.copy(name = "Bob")
        return ContextCommandExecutor(function.commands.map { it.analyze() }, functions, listOf(alice, bob), alice,
            macros = Project.macroFunction.toMap(), worldInDimension = { dimension, x, y, z ->
                if (dimension == "minecraft:the_nether" && x == 1 && y == 64 && z == 2)
                    ContextBlock("minecraft:chest", nbt = mapOf("count" to IntTag(0))) else null
            })
    }
}
