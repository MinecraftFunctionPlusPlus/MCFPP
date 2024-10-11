package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.Generic

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
    override fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        for (f in functions) {
            if(f is Generic<*> && f.isSelf(key, readOnlyArgs, normalArgs)){
                return f
            }
            if(f.isSelf(key, normalArgs)){
                return f
            }
        }
        return UnknownFunction(key)
    }

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function, true)){
            if(force){
                functions[functions.indexOf(function)] = function
                return true
            }
            return false
        }
        functions.add(function)
        return true
    }

    override fun hasFunction(function: Function, considerParent: Boolean): Boolean{
        return functions.contains(function)
    }

}