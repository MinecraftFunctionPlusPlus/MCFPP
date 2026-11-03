package top.mcfpp.lib

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.MCString
import java.util.Collections

/** Proven properties of an entity access, independent of its language type. */
class EntityFacts(
    val upperBound: Int? = null,
    val playersOnly: Boolean = false,
    knownKinds: Set<String>? = null
) {
    init { require(upperBound == null || upperBound >= 0) }

    val knownKinds: Set<String>? = knownKinds?.let { Collections.unmodifiableSet(LinkedHashSet(it)) }
    val selectingSingleEntity: Boolean get() = upperBound != null && upperBound <= 1

    override fun equals(other: Any?): Boolean = other is EntityFacts &&
        upperBound == other.upperBound && playersOnly == other.playersOnly && knownKinds == other.knownKinds
    override fun hashCode(): Int = 31 * (31 * (upperBound?.hashCode() ?: 0) + playersOnly.hashCode()) +
        (knownKinds?.hashCode() ?: 0)

    companion object {
        val UNKNOWN = EntityFacts()
        val PLAYER = EntityFacts(1, true, setOf("minecraft:player"))
        val SINGLE = EntityFacts(1)
    }
}

/**
 * Direct command targets. Strings become targets only through explicit entity factories.
 * Operand adapters refer to payloads captured by the owning entity value, not source variables.
 */
sealed interface EntityTarget {
    val facts: EntityFacts
    fun toCommandPart(): Command

    class Selector(val selector: EntitySelector) : EntityTarget {
        override val facts: EntityFacts get() = selector.facts()
        override fun toCommandPart(): Command = selector.toCommandPart()
    }

    class PlayerName(val name: MCString) : EntityTarget {
        override val facts: EntityFacts get() = EntityFacts.PLAYER
        override fun toCommandPart(): Command = textCommand(name, SelectorExpression.DirectKind.PLAYER_NAME)
    }

    class UUIDText(val uuid: MCString) : EntityTarget {
        override val facts: EntityFacts get() = EntityFacts.SINGLE
        override fun toCommandPart(): Command = textCommand(uuid, SelectorExpression.DirectKind.UUID_TEXT)
    }

    companion object {
        /** Dynamic strings retain their target-specific guard until the consumer is emitted. */
        private fun textCommand(value: MCString, kind: SelectorExpression.DirectKind): Command = if (StorageAccess.snapshot(value) != null) {
            Command(value.value.value)
        } else {
            Command().buildMacro(value, false).entityTargetStringGuard(value, kind)
        }
    }
}
