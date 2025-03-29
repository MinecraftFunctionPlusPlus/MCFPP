package top.mcfpp.model.field

import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType

interface IFieldWithOperator {

    fun addOperator(identifier: String, operator: Function, force: Boolean = false): Boolean

    fun removeOperator(identifier: String):Boolean

    fun getOperator(identifier: String, type: MCFPPType): Function?

    fun hasOperator(identifier: String, type: MCFPPType):Boolean

    fun hasOperator(itf: Function): Boolean

    fun forEachOperator(operation: (Pair<String, Function>) -> Any?)
}