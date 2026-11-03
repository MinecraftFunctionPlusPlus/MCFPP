package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.BoundPositionVar
import top.mcfpp.core.lang.context.ContextAccessException
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.util.LogProcessor

/** Encode entity and spatial operands jointly, preserving any context restoration prefix. */
internal fun nativeEntityCommand(vararg parts: Any?, changesExecutor: Boolean = false): Command {
    val values = parts.filterIsInstance<Var<*>>()
    if (values.none { it is EntityTargetAccess || it is BoundPositionVar }) return Command.buildAll(*parts)
    return try {
        EntityContextCommandOperations.command(values, changesExecutor = changesExecutor) { encoded ->
            var index = 0
            Command.buildAll(*parts.map { if (it is Var<*>) encoded[index++] else it }.toTypedArray())
        }
    } catch (failure: ContextAccessException) {
        LogProcessor.error(failure.message ?: "Cannot resolve entity command operands")
        Command("")
    }
}

internal fun requireEntityTarget(value: Var<*>, single: Boolean = false, players: Boolean = false): Var<*>? {
    val loaded = StorageAccess.read(value)
    if (loaded.isError) return null
    val target = loaded as? EntityTargetAccess
    if (target == null) {
        LogProcessor.error("Command requires an entity target")
        return null
    }
    if (single && !target.entityFacts.selectingSingleEntity) {
        LogProcessor.error("Command requires a provably single entity target; enter withEntityCtx or use a known limit of 1")
        return null
    }
    if (players && !target.entityFacts.playersOnly) {
        LogProcessor.error("Command requires a target proven to include only players")
        return null
    }
    return loaded
}

internal fun capturePlayerCommandResult(context: NativeCallContext, index: Int = 0,
                                        build: (List<Var<*>>) -> Command) = context.withArguments { args ->
    if (requireEntityTarget(args[index], players = true) != null) captureCommandResult(context, build)
}

internal fun captureSingleEntityCommandResult(context: NativeCallContext, index: Int,
                                              playersIndex: Int? = null,
                                              build: (List<Var<*>>) -> Command) = context.withArguments { args ->
    if (requireEntityTarget(args[index], single = true) == null) return@withArguments
    if (playersIndex != null && requireEntityTarget(args[playersIndex], players = true) == null) return@withArguments
    captureCommandResult(context, build)
}
