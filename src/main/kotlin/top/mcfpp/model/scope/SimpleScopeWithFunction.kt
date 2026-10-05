package top.mcfpp.model.scope

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Generic
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction

/**
 * 对[IScopeWithFunction]接口的简单实现。
 *
 */
interface SimpleScopeWithFunction : IScopeWithFunction {

    /**
     * 方法
     */
    var functions: HashMap<String, ArrayList<Function>>


    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachFunction(operation: (Function) -> Any?){
        for (function in functions.values){
            for (f in function){
                operation(f)
            }
        }
    }

    fun forEachFunctionUntil(operation: (Function) -> Boolean) {
        for (function in functions.values) {
            for (f in function) {
                if (!operation(f)) return
            }
        }
    }

    @Nullable
    override fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        top.mcfpp.model.function.ParameterMatcher.select(this.functions[key].orEmpty(), key, readOnlyArgs, normalArgs)?.let { return it }
        parent.forEach {
            if(it is IScopeWithFunction){
                val re = it.getFunction(key, readOnlyArgs, normalArgs)
                if(re !is UnknownFunction) return re
            }
        }
        return UnknownFunction(key)
    }

    fun getFunctionByTypes(key: String, normalArgs: List<top.mcfpp.type.MCFPPType>): top.mcfpp.model.function.ParameterMatcher.TypeSelection {
        val selected = top.mcfpp.model.function.ParameterMatcher.selectTypes(functions[key].orEmpty(), key, normalArgs)
        if (selected != top.mcfpp.model.function.ParameterMatcher.TypeSelection.Missing) return selected
        for (scope in parent) if (scope is SimpleScopeWithFunction) {
            val result = scope.getFunctionByTypes(key, normalArgs)
            if (result != top.mcfpp.model.function.ParameterMatcher.TypeSelection.Missing) return result
        }
        return top.mcfpp.model.function.ParameterMatcher.TypeSelection.Missing
    }

    fun getFunctionCandidates(key: String): List<Function> = functions[key].orEmpty() +
        parent.filterIsInstance<SimpleScopeWithFunction>().flatMap { it.getFunctionCandidates(key) }

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function, false)){
            if(force){
                functions[function.identifier]!!.add(function)
                return true
            }
            return false
        }
        if(!functions.containsKey(function.identifier)){
            functions[function.identifier] = ArrayList()
        }
        functions[function.identifier]!!.add(function)
        return true
    }

    override fun hasFunction(function: Function, considerParent: Boolean): Boolean{
        val qwq = functions.containsKey(function.identifier) && functions[function.identifier]!!.any { top.mcfpp.model.function.ParameterMatcher.sameSignature(it, function) }
        return if(considerParent && !qwq && parent.isNotEmpty()) {
            parent.any { it is IScopeWithFunction && it.hasFunction(function, true) }
        }else{
            qwq
        }
    }

    override fun removeFunction(function: Function) {
        functions[function.identifier]?.remove(function)
    }

}
