package top.mcfpp.mni.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.FieldAnnotation

/** Maps an MCFPP field to an NBT field name, optionally for one exact Minecraft target version. */
open class Name @JvmOverloads constructor(name: String, version: String? = null) : FieldAnnotation("name", "mcfpp.lang") {
    /** NBT field name used for entity data access. */
    var name: String = name
        private set
    /** Exact target version for this mapping, or null for the default mapping. */
    var version: String? = version
        private set

    /** Restores the field name and target version from a published library annotation record. */
    fun restorePortableState(value: String, targetVersion: String?) {
        name = value
        version = targetVersion
    }

    override fun forField(field: Var<*>) {}
}
