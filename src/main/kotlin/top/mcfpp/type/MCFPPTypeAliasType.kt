package top.mcfpp.type

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.util.LogProcessor

class MCFPPTypeAliasType(val t: mcfppParser.TypeContext): MCFPPType() {
    override val isValueType: Boolean get() = false

    @Transient private var resolving = false
    @Transient private var failed = false
    @Transient private var resolved: MCFPPType? = null

    internal val cachedTarget: MCFPPType? get() = resolved

    /** The alias is a declaration placeholder, never a distinct language value type. */
    fun resolve(scope: IScopeWithType): MCFPPType? {
        resolved?.let { return it }
        if (failed) return null
        if (resolving) {
            failed = true
            LogProcessor.error("Cyclic type alias: ${t.text}")
            return null
        }
        resolving = true
        try {
            val target = MCFPPType.parseFromContext(t, scope)
            if (target == null) {
                if (!failed) LogProcessor.error("Undefined type in alias: ${t.text}")
                failed = true
            } else {
                resolved = target
            }
            return target
        } finally {
            resolving = false
        }
    }
}
