package top.mcfpp.analysis

import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType

/** Parse a literal's sign before its magnitude, so each signed minimum remains valid. */
internal object IntegralLiteralSyntax {
    sealed interface Result {
        data class Value(val type: MCFPPType, val value: CompilerValue.Integral) : Result
        data class Invalid(val diagnostic: String) : Result
    }

    fun parse(syntax: ParseTree): Result? {
        var node = syntax
        var negate = false
        while (true) {
            val type = when (node) {
                is Parser.NbtByteContext -> MCFPPNBTType.Byte
                is Parser.NbtShortContext -> MCFPPNBTType.Short
                is Parser.NbtIntContext -> MCFPPBaseType.Int
                is Parser.NbtLongContext -> MCFPPNBTType.Long
                else -> null
            }
            if (type != null) {
                val raw = node.text
                // Two explicit signs are an operation on an already parsed literal.
                if (negate && raw.startsWith('-')) return null
                val text = if (negate) "-$raw" else raw
                val digits = if (type == MCFPPBaseType.Int) text else text.dropLast(1)
                val value = digits.toLongOrNull()
                val valid = value != null && when (type) {
                    MCFPPNBTType.Byte -> value in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()
                    MCFPPNBTType.Short -> value in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
                    MCFPPBaseType.Int -> value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                    else -> true
                }
                return if (valid) Result.Value(type, CompilerValue.Integral(value!!))
                else Result.Invalid("Integer literal '$text' is outside the range of ${type.typeName}")
            }
            if (node is Parser.UnaryExpressionContext && node.SUB() != null) {
                if (negate) return null
                negate = true
                node = node.unaryExpression()
            } else {
                if (node.childCount != 1) return null
                node = node.getChild(0)
            }
        }
    }
}
