package top.mcfpp.analysis

/** Arithmetic shared with the selected runtime representation; no user functions are evaluated. */
object PrimitiveEvaluation {
    fun binary(operation: String, left: CompilerValue, right: CompilerValue): CompilerValue? {
        if (left is CompilerValue.FloatBits && right is CompilerValue.FloatBits) {
            if (!top.mcfpp.command.FloatProviders.enabled) return null
            val a = Float.fromBits(left.bits)
            val b = Float.fromBits(right.bits)
            return when (operation) {
                "+" -> CompilerValue.FloatBits((a + b).toRawBits())
                "-" -> CompilerValue.FloatBits((a - b).toRawBits())
                "*" -> CompilerValue.FloatBits((a * b).toRawBits())
                "/" -> CompilerValue.FloatBits((a / b).toRawBits())
                "%" -> CompilerValue.FloatBits((a % b).toRawBits())
                "==" -> CompilerValue.Bool(a == b)
                "!=" -> CompilerValue.Bool(a != b)
                "<" -> CompilerValue.Bool(a < b)
                ">" -> CompilerValue.Bool(a > b)
                "<=" -> CompilerValue.Bool(a <= b)
                ">=" -> CompilerValue.Bool(a >= b)
                else -> null
            }
        }
        if (left is CompilerValue.Integral && right is CompilerValue.Integral) {
            val a = left.value.toInt()
            val b = right.value.toInt()
            return when (operation) {
                "+" -> CompilerValue.Integral((a + b).toLong())
                "-" -> CompilerValue.Integral((a - b).toLong())
                "*" -> CompilerValue.Integral((a * b).toLong())
                "==" -> CompilerValue.Bool(a == b)
                "!=" -> CompilerValue.Bool(a != b)
                "<" -> CompilerValue.Bool(a < b)
                ">" -> CompilerValue.Bool(a > b)
                "<=" -> CompilerValue.Bool(a <= b)
                ">=" -> CompilerValue.Bool(a >= b)
                else -> null
            }
        }
        if (left is CompilerValue.Bool && right is CompilerValue.Bool) return when (operation) {
            "&&" -> CompilerValue.Bool(left.value && right.value)
            "||" -> CompilerValue.Bool(left.value || right.value)
            "==" -> CompilerValue.Bool(left.value == right.value)
            "!=" -> CompilerValue.Bool(left.value != right.value)
            else -> null
        }
        return null
    }
}
