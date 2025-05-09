package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType

class SimpleFieldWithOperator: IFieldWithOperator {

    /**
     * 方法
     */
    private var operators: HashMap<String, ArrayList<Function>> = HashMap()

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachOperator(operation: (Pair<String, Function>) -> Any?){
        for (function in operators){
            for (f in function.value){
                operation(Pair(function.key, f))
            }
        }
    }


    @Nullable
    override fun getOperator(identifier: String, type: MCFPPType): Function? {
        //注意重载
        var qwq = false //类型不是完全一致，但是可以用多态选中的
        var re : Function? = null
        operators[identifier]?.forEach { function ->
            if(function.normalParams[0].type == type){
                return function
            }else if(!qwq && function.normalParams[0].type.isSubOf(type)){
                qwq = true
                re = function
            }
        }
        return re
    }

    override fun addOperator(identifier: String, operator: Function, force: Boolean): Boolean {
        if(hasOperator(operator)){
            if(force){
                if(operators[identifier] == null) operators[identifier] = ArrayList()
                operators[identifier]!!.add(operator)
                return true
            }
            return false
        }
        if(operators[identifier] == null) operators[identifier] = ArrayList()
        operators[identifier]!!.add(operator)
        return true
    }

    override fun hasOperator(itf: Function): Boolean {
        return operators.values.any { it.contains(itf) }
    }

    override fun hasOperator(identifier: String, type: MCFPPType): Boolean {
        //注意重载
        operators[identifier]?.forEach { function ->
            if(function.normalParams[0].type.isSubOf(type)){
                return true
            }
        }
        return false
    }

    override fun removeOperator(identifier: String): List<Function>? {
        return operators.remove(identifier)
    }

}