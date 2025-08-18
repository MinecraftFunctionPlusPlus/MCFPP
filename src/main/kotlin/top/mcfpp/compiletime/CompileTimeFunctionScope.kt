package top.mcfpp.compiletime

import top.mcfpp.core.lang.Var
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.IScope

class CompileTimeFunctionScope: FunctionScope  {

    constructor(parent: IScope?) : super(parent)

    constructor(parents: List<IScope?>): super(parents)

    override fun clone(): CompileTimeFunctionScope {
        return clone(this)
    }

    companion object{
        /**
         * 复制一个域。
         * @param functionField 原来的域
         */
        fun clone(functionField: CompileTimeFunctionScope): CompileTimeFunctionScope {
            val newFunctionField = CompileTimeFunctionScope(functionField.parent)
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