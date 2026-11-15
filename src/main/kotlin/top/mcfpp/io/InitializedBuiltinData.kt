package top.mcfpp.io

import top.mcfpp.core.lang.ImmutableList
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTMap
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPScoreRefType
import top.mcfpp.type.MCFPPType
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

/** Read existing shared member tables without invoking their lazy declaration providers. */
internal object InitializedBuiltinData {
    private val instanceHolders = MCFPPBaseType::class.java.declaredClasses.toSet() +
        MCFPPNBTType::class.java.declaredClasses.toSet() + MCFPPScoreRefType::class.java

    fun all(): List<CompoundData> {
        val seen = Collections.newSetFromMap(IdentityHashMap<CompoundData, Boolean>())
        val result = arrayListOf<CompoundData>()
        fun include(value: CompoundData?) {
            if (value != null && seen.add(value)) result.add(value)
        }
        fun initialized(holder: Class<*>, fieldName: String, receiver: Any? = null) {
            val field = holder.declaredFields.firstOrNull { it.name == fieldName } ?: return
            field.isAccessible = true
            val slot = field.get(if (Modifier.isStatic(field.modifiers)) null else receiver) as? Lazy<*> ?: return
            if (slot.isInitialized()) include(slot.value as? CompoundData)
        }
        for (type in MCFPPType.builtinTypesById().values) {
            if (type.javaClass !in instanceHolders) continue
            include(type.rawDefaultObjectData())
            initialized(type.javaClass, "instanceData\$delegate", type)
        }
        // These tables are shared by constructed container types, or exposed through
        // computed type getters, so they do not have an instance delegate in the catalog.
        for (holder in listOf(NBTList::class.java, NBTDictionary::class.java, NBTMap::class.java,
            ImmutableList::class.java, MCFPPType::class.java, MCFPPEntityType::class.java)) {
            initialized(holder, "data\$delegate")
        }
        initialized(DataTemplate::class.java, "baseDataTemplate\$delegate")
        return result
    }
}
