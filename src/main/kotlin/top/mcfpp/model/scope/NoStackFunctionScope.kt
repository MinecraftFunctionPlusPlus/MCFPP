package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType

class NoStackFunctionScope(parent: FunctionScope) : FunctionScope(parent) {

    override fun forEachVar(action: (Var<*>) -> Unit) {
        allVars.forEach { action(it) }
        (parent[0] as FunctionScope).forEachVar(action)
    }

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    override fun getVar(key: String): Var<*>? {
        super.getVar(key)?.let { return it }
        return (parent[0] as FunctionScope).getVar(key)
    }

    override fun putVar(key: String, `var`: Var<*>, forced: Boolean): Boolean {
        return (parent[0] as FunctionScope).putVar(key, `var`, forced)
    }


    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        return (parent[0] as IScopeWithType).putType(key, type, forced)
    }

    override fun getType(key: String) : MCFPPType? {
        return (parent[0] as IScopeWithType).getType(key)
    }
    override fun containType(id: String): Boolean {
        return (parent[0] as IScopeWithType).containType(id)
    }

    override fun removeType(id: String): MCFPPType? {
        return (parent[0] as IScopeWithType).removeType(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        (parent[0] as IScopeWithType).forEachType(action)
    }

    override val allTypes: Collection<MCFPPType>
        get() = (parent[0] as IScopeWithType).allTypes
}