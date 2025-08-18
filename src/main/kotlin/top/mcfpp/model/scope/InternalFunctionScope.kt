package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var


/**
 * 内联函数的域。内联函数的域的父级可能是一个内联函数域，也可能是一个普通函数的域。
 *
 * @see top.mcfpp.model.function.InternalFunction
 */
class InternalFunctionScope(parent: FunctionScope?) : FunctionScope(parent) {
    init {
        parent?.let { fieldVarSet.addAll(it.fieldVarSet) }
    }


    /**
     * 从域中取出一个变量。如果此域中没有，则从父域中寻找。同时记录当前栈的级数，也就是向父级追溯了多少级栈
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    override fun getVar(key: String): Var<*>? {
        if(!fieldVarSet.contains(key)) return null
        if (containVar(key)) {
            val re: Var<*>? = vars.getOrDefault(key, null)
            re!!.stackIndex = 0
            return re
        }
        val re: Var<*>? = (parent[0] as IScopeWithVar).getVar(key)
        if (re != null) {
            re.stackIndex++
        }
        return re
    }

    override fun putVar(key: String, `var`: Var<*>, forced: Boolean): Boolean {
        if(`var`.stackIndex != 0){
            `var`.stackIndex --
            val result = (parent[0] as FunctionScope).putVar(key, `var`, forced)
            `var`.stackIndex ++
            return result
        }
        fieldVarSet.add(key)
        return super.putVar(key, `var`, forced)
    }

    override fun forEachVar(action: (Var<*>) -> Unit) {
        for (key in fieldVarSet){
            action(getVar(key)!!)
        }
    }

    override fun clone(): InternalFunctionScope {
        return clone(this)
    }

    companion object{
        /**
         * 复制一个域。
         * @param functionField 原来的域
         */
        fun clone(functionField: InternalFunctionScope): InternalFunctionScope {
            val newFunctionField = InternalFunctionScope(null)
            newFunctionField.parent = functionField.parent
            //变量复制
            for (key in functionField.vars.keys) {
                val `var`: Var<*>? = functionField.vars[key]
                newFunctionField.vars[key] = `var`!!.clone()
            }
            newFunctionField.fieldVarSet.addAll(functionField.fieldVarSet)
            return newFunctionField
        }
    }
}