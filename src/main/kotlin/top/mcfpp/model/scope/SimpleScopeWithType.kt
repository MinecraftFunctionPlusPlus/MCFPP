package top.mcfpp.model.scope

import top.mcfpp.type.MCFPPType

interface SimpleScopeWithType : IScopeWithType {

    val fieldTypeSet :HashSet<String>
    /**
     * 类型
     */
    val types : HashMap<String, MCFPPType>

    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        fieldTypeSet.add(key)
        if(forced){
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

    override fun getType(key: String) : MCFPPType? {
        return types.getOrDefault(key, null)?: parent.filterIsInstance<IScopeWithType>().firstOrNull { it.containType(key) }?.getType(key)
    }
    override fun containType(id: String): Boolean {
        return types.containsKey(id)|| parent.filterIsInstance<IScopeWithType>().any { it.containType(id) }
    }

    override fun removeType(id: String): MCFPPType? {
        return types.remove(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        for (t in types.values){
            action(t)
        }
        parent.forEach {
            if(it is IScopeWithType){
                it.forEachType(action)
            }
        }
    }

    override val allTypes: Collection<MCFPPType>
        get() = types.values + parent.filterIsInstance<IScopeWithType>().flatMap { it.allTypes }

    companion object{
        fun getTypeScope(): SimpleScopeWithType{
            return object :SimpleScopeWithType{
                override val fieldTypeSet = HashSet<String>()
                override val types = HashMap<String, MCFPPType>()
                override var parent = ArrayList<IScope?>()
            }
        }
    }

}