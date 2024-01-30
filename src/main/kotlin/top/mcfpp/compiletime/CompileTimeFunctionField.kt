package top.mcfpp.compiletime

import top.mcfpp.lang.Var
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.FunctionField
import top.mcfpp.lib.IField
import top.mcfpp.lib.InternalFunctionField

class CompileTimeFunctionField(parent: IField?, cacheContainer: FieldContainer?):FunctionField(parent,cacheContainer)  {
    override fun clone(): CompileTimeFunctionField {
        return clone(this)
    }

    companion object{
        /**
         * 复制一个域。
         * @param functionField 原来的域
         */
        fun clone(functionField: CompileTimeFunctionField): CompileTimeFunctionField {
            val newFunctionField = CompileTimeFunctionField(functionField.parent,null)
            //变量复制
            for (key in functionField.vars.keys) {
                val `var`: Var? = functionField.vars[key]
                newFunctionField.vars[key] = `var`!!.clone() as Var
            }
            newFunctionField.fieldVarSet.addAll(functionField.fieldVarSet)
            return newFunctionField
        }
    }
}