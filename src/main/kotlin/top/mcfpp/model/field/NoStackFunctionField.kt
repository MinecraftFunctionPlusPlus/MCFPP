package top.mcfpp.model.field

import top.mcfpp.lang.Var
import top.mcfpp.model.FieldContainer

class NoStackFunctionField(parent: FunctionField, cacheContainer: FieldContainer?) : FunctionField(parent, cacheContainer) {

    override fun forEachVar(action: (Var<*>) -> Any?) {
        (parent as FunctionField).forEachVar(action)
    }

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    override fun getVar(key: String): Var<*>? {
        return (parent as FunctionField).getVar(key)
    }
}