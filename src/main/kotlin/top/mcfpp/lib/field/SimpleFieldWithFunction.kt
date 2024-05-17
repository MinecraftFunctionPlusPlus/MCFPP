package top.mcfpp.lib.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.generic.Generic

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
    override fun getFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>): Function? {
        for (f in functions) {
            if(f is Generic<*> && f.isSelf(key, readOnlyParams, normalParams)){
                return f
            }
            if(f.isSelf(key, normalParams)){
                return f
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