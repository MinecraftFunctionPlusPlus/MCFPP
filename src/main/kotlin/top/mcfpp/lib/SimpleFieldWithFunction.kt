package top.mcfpp.lib

import org.jetbrains.annotations.Nullable

/**
 * 对[IFieldWithFunction]接口的简单实现。
 *
 */
class SimpleFieldWithFunction : IFieldWithFunction {

    /**
     * 方法
     */
    private var functions: ArrayList<Function> = ArrayList()

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachFunction(operation: (Function) -> Any?){
        for (function in functions){
            operation(function)
        }
    }


    @Nullable
    override fun getFunction(key: String, argsTypes: List<String>): Function? {
        for (f in functions) {
            if (f.identifier == key && f.params.size == argsTypes.size) {
                if (f.params.size == 0) {
                    return f
                }
                var hasFoundFunc = true
                //参数比对
                for (i in argsTypes.indices) {
                    if (argsTypes[i] != f.params[i].type) {
                        hasFoundFunc = false
                        break
                    }
                }
                if (hasFoundFunc) {
                    return f
                }
            }
        }
        return null
    }

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function)){
            if(force){
                functions[functions.indexOf(function)] = function
                return true
            }
            return false
        }
        functions.add(function)
        return true
    }

    override fun hasFunction(function: Function): Boolean{
        return functions.contains(function)
    }

}