package top.mcfpp.antlr

/** Reserved spelling retained only to produce a precise source migration diagnostic. */
object LegacyReferenceSyntax {
    const val MESSAGE = "Legacy 'ref expression' is no longer supported; create ScoreRef or NBTRef<T> from an explicit Minecraft address and pass it as an ordinary value."

    fun argument(arguments: mcfppParser.ArgumentsContext): mcfppParser.CallArgumentContext? =
        arguments.normalArgs().callArgumentList()?.callArgument()?.firstOrNull { it.REF() != null }
}
