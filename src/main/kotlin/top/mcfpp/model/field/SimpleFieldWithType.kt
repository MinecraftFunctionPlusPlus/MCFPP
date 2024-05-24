package top.mcfpp.model.field

import top.mcfpp.lang.type.MCFPPType

open class SimpleFieldWithType : IFieldWithType {

    val fieldTypeSet = HashSet<String>()

    /**
     * 类型
     */
    protected val types: HashMap<String, MCFPPType> = HashMap()

    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        fieldTypeSet.add(key)
        if (forced) {
            types[key] = type
            return true
        }
        return if (types.containsKey(key)) {
            false
        } else {
            types[key] = type
            true
        }
    }

    override fun getType(key: String): MCFPPType? {
        return types.getOrDefault(key, null)
    }

    override fun containType(id: String): Boolean {
        return types.containsKey(id)
    }

    override fun removeType(id: String): MCFPPType? {
        return types.remove(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        for (t in types.values) {
            action(t)
        }
    }

    override val allTypes: Collection<MCFPPType>
        get() = types.values

}