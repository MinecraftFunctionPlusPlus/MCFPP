package top.mcfpp.core.lang.context

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType

internal object ContextPositions {
    fun recognizes(type: MCFPPType, name: String): Boolean =
        type == ContextTypes.Position && name == "offset" ||
            type is MCFPPExecutionContextType && name == "local" && ContextTypes.supports(type, ContextTypes.ExecutionContext)

    fun invoke(receiver: Var<*>, syntax: P.FunctionCallContext, cache: ArrayList<Var<*>>): Var<*> {
        val expressions = syntax.arguments().normalArgs().expressionList()?.expression().orEmpty()
        if (syntax.arguments().readOnlyArgs() != null || expressions.size != 3)
            throw ContextAccessException("Context position offsets require three numeric arguments")
        val arguments = expressions.map { expression ->
            when (val value = ContextEntryOperations.payload(StorageAccess.snapshot(MCFPPExprVisitor(processVarCache = cache).visitExpression(expression)))) {
                is CompilerValue.Integral -> value.value.toDouble()
                is CompilerValue.FloatBits -> Float.fromBits(value.bits).toDouble()
                is CompilerValue.DoubleBits -> Double.fromBits(value.bits)
                else -> throw ContextAccessException("Context position offsets require known numeric values")
            }.also { if (!it.isFinite()) throw ContextAccessException("Context position offsets must be finite") }
        }
        val position = when (receiver) {
            is BoundPositionVar -> receiver.requireBinding().offset(arguments[0], arguments[1], arguments[2])
            is ExecutionContextVar -> receiver.requireContext().pos.local(arguments[0], arguments[1], arguments[2])
            else -> throw ContextAccessException("Expected a bound context position")
        }
        return BoundPositionVar("context_offset", position, ContextRuntime)
    }
}
