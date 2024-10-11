package top.mcfpp.util

import top.mcfpp.core.lang.Var

class ValueWrapper<T: Var<*>>(var value: T){

    fun assign(v: T){
        value.assignedBy(v)
        value = v
    }

}