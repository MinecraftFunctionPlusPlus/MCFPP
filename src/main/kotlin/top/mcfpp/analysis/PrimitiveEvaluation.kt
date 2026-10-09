package top.mcfpp.analysis

/** Arithmetic shared with the selected runtime representation; no user functions are evaluated. */
object PrimitiveEvaluation {
    fun binary(operation: String, left: CompilerValue, right: CompilerValue): CompilerValue? {
        if (left is CompilerValue.FloatBits && right is CompilerValue.FloatBits) {
            if (!top.mcfpp.command.FloatProviders.enabled) return null
            val a = Float.fromBits(left.bits)
            val b = Float.fromBits(right.bits)
            if (!a.isFinite() || !b.isFinite()) return null
            return when (operation) {
                "+", "-", "*", "/", "%" -> CompilerValue.FloatBits(top.mcfpp.command.FloatProviders.arithmeticValue(a, b, operation).toRawBits())
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
                "/" -> if (b == 0) null else CompilerValue.Integral(Math.floorDiv(a, b).toLong())
                "%" -> if (b == 0) null else CompilerValue.Integral(Math.floorMod(a, b).toLong())
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

    fun unary(operation: String, value: CompilerValue): CompilerValue? = when (value) {
        is CompilerValue.Integral -> when (operation) {
            "-", "negation" -> CompilerValue.Integral((-value.value.toInt()).toLong())
            "+" -> value
            else -> null
        }
        is CompilerValue.FloatBits -> if (top.mcfpp.command.FloatProviders.enabled) when (operation) {
            "-", "negation" -> Float.fromBits(value.bits).takeIf { it.isFinite() }
                ?.let { CompilerValue.FloatBits((-it).toRawBits()) }
            "+" -> value
            else -> null
        } else null
        is CompilerValue.Bool -> if (operation == "!" || operation == "not") CompilerValue.Bool(!value.value) else null
        else -> null
    }
}
