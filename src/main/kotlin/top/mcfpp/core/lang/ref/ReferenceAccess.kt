package top.mcfpp.core.lang.ref

import top.mcfpp.analysis.*
import top.mcfpp.backend.EntityContextCommandOperations
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.ReferenceCommands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.Void
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.context.*
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor

/** The shared public-reference boundary for source methods, projections and MNI. */
object ReferenceAccess {
    fun requireReadable(reference: Var<*>) {
        requireReference(reference)
        ReferenceValues.provenance(reference).forEach(ContextBorrowing::requireUsable)
        val metadata = ReferenceValues.metadata(reference)
        val descriptor = metadata?.staticDescriptor
        if (descriptor != null && descriptor.kind in setOf(ReferenceKind.SCORE_ENTITY, ReferenceKind.NBT_ENTITY) &&
            metadata?.targetFacts?.selectingSingleEntity != true)
            throw ContextAccessException("Entity data references require a provably single target for tryRead; enter withEntityCtx or use a known limit of 1")
    }

    fun set(reference: Var<*>, input: Var<*>): Var<*> = try {
        val type = requireReference(reference)
        ReferenceValues.provenance(reference).forEach(ContextBorrowing::requireUsable)
        ContextBorrowing.escapeDiagnostic(input, "an external reference target")?.let { throw ContextAccessException(it) }
        if (reference.isError || input.isError) returnError("Cannot write an invalid reference or value") else {
            if (TypeRelations.resolveImplicitConversion(input.type, type.valueType) == null)
                returnError("Cannot assign '${input.type.typeName}' to '${type.valueType.typeName}' reference target")
            else {
                // Capture before flushing or invalidating aliases; both values belong to this operation.
                val owned = StorageAccess.capture(reference)
                val value = StorageAccess.capture(input.implicitCast(type.valueType))
                if (owned.isError || value.isError) returnError("Cannot capture reference write operands") else {
                    val observed = StorageAccess.visibleValues(Function.currFunction.scope)
                    StorageAccess.flush(observed)
                    StorageAccess.materialize(value)
                    val descriptor = ReferenceValues.descriptor(owned)
                    if (descriptor != null) emitKnown(owned, descriptor) { target ->
                        ReferenceCommands.write(owned, descriptor.kind, StorageAccess.ensure(value).path, target,
                            multiple = descriptor.kind == ReferenceKind.NBT_ENTITY &&
                                ReferenceValues.metadata(owned)?.targetFacts?.selectingSingleEntity != true,
                            pathShape = descriptor.pathShape)
                    } else dynamic(owned, value, reading = false)
                    StorageAccess.applyEffect(observed, Effect.Unknown)
                    EffectAnalysis.recordEffect(Effect.Unknown)
                    Void
                }
            }
        }
    } catch (failure: ContextAccessException) { returnError(failure.message.orEmpty()) }

    /** The result slot is empty on failure; success is independent of the numeric value. */
    fun readInto(reference: Var<*>, destination: Var<*>, success: ScoreBool) {
        requireReadable(reference)
        val type = requireReference(reference)
        if (destination.type != type.valueType)
            throw ContextAccessException("Reference read destination must have type '${type.valueType.typeName}'")
        val owned = StorageAccess.capture(reference)
        if (owned.isError) throw ContextAccessException("Cannot capture reference read operand")
        StorageAccess.flush(StorageAccess.visibleValues(Function.currFunction.scope))
        val path = StorageAccess.ensure(destination).path
        Function.addCommand(Command("data remove").build(path.toCommandPart()))
        Function.addCommand("scoreboard players set ${success.name} ${success.boolObject} 0")
        val descriptor = ReferenceValues.descriptor(owned)
        if (descriptor != null) emitKnown(owned, descriptor) { target ->
            ReferenceCommands.read(owned, descriptor.kind, path,
                Command("execute store success score ${success.name} ${success.boolObject} run"), target, descriptor.pathShape)
        } else {
            dynamic(owned, null, reading = true)
            Function.addCommand(Command("execute store result score ${success.name} ${success.boolObject} run data get")
                .build(ReferenceAccessHelpers.request.memberIndex("success").toCommandPart()).build("1"))
            Function.addCommand(Commands.dataSetFrom(path, ReferenceAccessHelpers.request.memberIndex("value")))
            Function.addCommand(Command("data remove").build(ReferenceAccessHelpers.request.toCommandPart()))
        }
        Function.addCommand(Command("execute if score ${success.name} ${success.boolObject} matches 0 run data remove")
            .build(path.toCommandPart()))
        StorageAccess.publishNbt(destination)
        StorageAccess.publishBoolean(success, StorageLayout.Scoreboard(success.name, success.boolObject.toString()))
        EffectAnalysis.recordEffect(Effect.ReadsRuntime)
    }

    private fun requireReference(value: Var<*>): MCFPPReferenceType = value.type as? MCFPPReferenceType
        ?: throw ContextAccessException("Reference access requires ScoreRef or NBTRef<T>")

    private fun emitKnown(reference: Var<*>, descriptor: ReferenceDescriptor, build: (Command?) -> Command) {
        val command = if (descriptor.targetShape == ReferenceTargetShape.BoundExecutor) {
            val targetRecord = descriptor.fields.fields["target"] as? CompilerValue.Record
            val marker = (targetRecord?.fields?.get("borrow") as? CompilerValue.Text)?.value
            val target = ReferenceValues.metadata(reference)?.borrows.orEmpty().filterIsInstance<ContextBorrowPayload.Executor>()
                .singleOrNull { ReferenceValues.borrowIdentity(it) == marker }
                ?: throw ContextAccessException("Scoped reference requires its retained executor capability")
            EntityContextCommandOperations.command(listOf(target.adapter("reference_executor", MCFPPEntityType.NormalSelector))) {
                build(it.single())
            }
        } else build(descriptor.targetShape?.let { requireNotNull(ReferenceValues.entityTarget(reference, descriptor)).toCommandPart() })
        Function.addCommands(command.buildMacroFunction())
    }

    private fun dynamic(reference: Var<*>, input: Var<*>?, reading: Boolean) {
        val request = ReferenceAccessHelpers.request
        Function.addCommand(Commands.dataSetValue(request, CompoundTag("success" to ByteTag(0))))
        StorageAccess.encodeTo(request.memberIndex("reference"), reference)
        Function.addCommand(Command("data remove").build(request.memberIndex("reference").memberIndex("resolvedTarget").toCommandPart()))
        if (input != null) StorageAccess.encodeTo(request.memberIndex("input"), input)
        val method = if (reading) "read" else "write"
        fun dispatch(excludeBound: Boolean) {
            val field = ReferenceValues.field(requestReference(reference), method, MCFPPBaseType.String)
            val commands = Command("function").buildMacro(field).buildMacroFunction()
            val condition = Command("execute if data").build(request.memberIndex("reference").memberIndex(method).toCommandPart())
            if (excludeBound) condition.build("unless data").build(request.memberIndex("reference").memberIndex("target").toCommandPart())
                .build("{kind:\"bound_executor\"}", false)
            commands[commands.lastIndex] = condition.build("run").build(commands.last())
            Function.addCommands(commands)
        }
        // Preserve the entire encoder operation, including any prefix supplied by a custom encoder.
        for (borrow in ReferenceValues.provenance(reference).filterIsInstance<ContextBorrowPayload.Executor>()) {
            ContextBorrowing.requireUsable(borrow)
            val operation = EntityContextCommandOperations.command(listOf(borrow.adapter("reference_executor", MCFPPEntityType.NormalSelector))) { targets ->
                val branch = Commands.tempFunction("reference_borrow", Function.currFunction) {
                    Function.addCommands(Command("data modify").build(request.memberIndex("reference").memberIndex("resolvedTarget").toCommandPart())
                        .build("set value \"").build(targets.single(), false).build("\"", false).buildMacroFunction())
                    dispatch(excludeBound = false)
                }
                branch.second.actualCallBody = true
                branch.first
            }
            val pattern = NbtEncoding.snbt(CompoundTag(
                "kind" to StringTag("bound_executor"), "borrow" to StringTag(ReferenceValues.borrowIdentity(borrow))))
            Function.addCommands(Command("execute if data").build(request.memberIndex("reference").memberIndex("target").toCommandPart())
                .build(pattern, false).build("run").build(operation).buildMacroFunction())
        }
        dispatch(excludeBound = true)
        if (!reading) Function.addCommand(Command("data remove").build(request.toCommandPart()))
    }

    private fun requestReference(original: Var<*>): ReferenceVar = (original.type as MCFPPReferenceType)
        .buildUnConcrete("reference_request").apply {
            nbtPath = ReferenceAccessHelpers.request.memberIndex("reference")
            StorageAccess.publishNbt(this)
        }

    private fun returnError(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("reference_access").apply { isError = true }
    }
}
