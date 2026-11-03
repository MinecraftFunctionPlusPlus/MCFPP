package top.mcfpp.lib

/** The same bounded ASCII contract is used before compilation and at macro consumption. */
internal object EntityTargetText {
    const val PLAYER_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"
    const val HEX_CHARACTERS = "0123456789abcdefABCDEF"
    val UUID_GROUP_WIDTHS = listOf(8, 4, 4, 4, 12)

    fun isValid(kind: SelectorExpression.DirectKind, text: String): Boolean = when (kind) {
        SelectorExpression.DirectKind.PLAYER_NAME -> text.length in 1..16 && text.all { it in PLAYER_CHARACTERS }
        SelectorExpression.DirectKind.UUID_TEXT -> {
            val groups = text.split('-')
            groups.size == UUID_GROUP_WIDTHS.size && groups.withIndex().all { (index, group) ->
                group.length in 1..UUID_GROUP_WIDTHS[index] && group.all { it in HEX_CHARACTERS }
            }
        }
    }

    fun description(kind: SelectorExpression.DirectKind): String = when (kind) {
        SelectorExpression.DirectKind.PLAYER_NAME -> "entityByName requires 1..16 ASCII letters, digits or underscores"
        SelectorExpression.DirectKind.UUID_TEXT ->
            "entityByUUID requires five nonempty hexadecimal groups of at most 8-4-4-4-12 digits; shortened groups are allowed"
    }
}
