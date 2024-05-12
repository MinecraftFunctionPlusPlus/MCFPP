package top.mcfpp.compiletime

import top.mcfpp.lang.Var
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.field.FunctionField
import top.mcfpp.model.field.IField

class CompileTimeFunctionField(parent: IField?, cacheContainer: FieldContainer?): FunctionField(parent,cacheContainer)  {
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
                val `var`: Var<*>? = functionField.vars[key]
                newFunctionField.vars[key] = `var`!!.clone()
            }
            newFunctionField.fieldVarSet.addAll(functionField.fieldVarSet)
            return newFunctionField
        }
    }
}