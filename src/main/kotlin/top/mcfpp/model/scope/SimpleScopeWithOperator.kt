package top.mcfpp.model.scope

import org.jetbrains.annotations.Nullable
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

interface SimpleScopeWithOperator: IScopeWithOperator {

    /**
     * 方法
     */
    var operators: HashMap<String, HashMap<MCFPPType?, Function>>

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
    override fun forEachOperator(operation: (Pair<String, Function>) -> Any?){
        for ((i, function) in operators){
            for (f in function.values){
                operation(Pair(i, f))
            }
        }
    }


    @Nullable
    override fun getOperator(identifier: String, type: MCFPPType?): Function? {
        return when (val selection = ParameterMatcher.selectOperatorTypes(operators[identifier]?.map { it.key to it.value } ?: emptyList(), type)) {
            is ParameterMatcher.TypeSelection.Selected -> selection.function
            is ParameterMatcher.TypeSelection.Ambiguous -> {
                LogProcessor.error("Ambiguous operator '$identifier'")
                UnknownFunction(identifier)
            }
            ParameterMatcher.TypeSelection.Missing -> null
        }
    }

    override fun addOperator(identifier: String, type: MCFPPType?, operator: Function, force: Boolean): Boolean {
        if(operators.containsKey(identifier) && operators[identifier]!!.containsKey(type)){
            if(force){
                if(operators[identifier] == null) operators[identifier] = HashMap()
                operators[identifier]!![type] = operator
                return true
            }
            return false
        }
        if(operators[identifier] == null) operators[identifier] = HashMap()
        operators[identifier]!![type] = operator
        return true
    }

    override fun hasOperator(itf: Function): Boolean {
        return operators.values.any { it.values.contains(itf) }
    }

    override fun hasOperator(identifier: String, type: MCFPPType?): Boolean {
        //注意重载
        return operators.containsKey(identifier) && operators[identifier]!!.containsKey(type)
    }

    override fun removeOperator(identifier: String): MutableCollection<Function>? {
        return operators.remove(identifier)?.values
    }

    override fun removeOperator(identifier: String, type: MCFPPType?): Function? {
        return operators[identifier]?.remove(type)
    }

}
