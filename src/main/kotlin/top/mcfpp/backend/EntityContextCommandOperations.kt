package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.*
import top.mcfpp.core.lang.entity.EntityTargetAccess

/** Shared operand boundary for native commands and later ancestor-handle preservation. */
object EntityContextCommandOperations {
    fun encoder(): ContextCommandEncoder = ContextRuntime

    /** This call may return a prefixed command; consumers must retain the entire result. */
    fun command(arguments: List<Var<*>>, changesExecutor: Boolean = false, build: (List<Command>) -> Command): Command {
        val values = arguments.map(StorageAccess::read)
        if (values.any { it.isError }) throw ContextAccessException("Cannot encode an invalid context command argument")
        val selected = values.firstNotNullOfOrNull { value -> when (value) {
            is BoundExecutorVar -> value.encoder
            is BoundPositionVar -> value.encoder
            else -> null
        } } ?: encoder()
        val operands = values.map { value -> when (value) {
            is BoundExecutorVar -> ContextCommandOperand.Executor(value.binding)
            is BoundPositionVar -> ContextCommandOperand.Position(value.requireBinding())
            is EntityTargetAccess -> ContextCommandOperand.Entity(value)
            else -> ContextCommandOperand.Coordinates(value)
        } }
        return selected.operation(operands, changesExecutor, build)
    }
}
