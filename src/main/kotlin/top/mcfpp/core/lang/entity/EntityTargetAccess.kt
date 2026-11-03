package top.mcfpp.core.lang.entity

import top.mcfpp.command.Command
import top.mcfpp.lib.EntityFacts

/** Common consumer contract for ordinary targets and scoped executor handles. */
interface EntityTargetAccess {
    val entityFacts: EntityFacts

    /** Resolve at the command use site; scoped implementations may prepare a stable handle. */
    fun toEntityTargetCommand(): Command
}
