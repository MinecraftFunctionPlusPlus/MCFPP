package top.mcfpp.model

import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam

interface Generic<T> where T : Function{

    val readOnlyParams: ArrayList<FunctionParam>
    fun invoke(readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, caller: CanSelectMember?): Var<*>

    //fun compile(readOnlyArgs: ArrayList<Var<*>>) : T

    fun isSelf(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean

    fun isSelfWithDefaultValue(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Boolean

    fun mapReadonlyArgs(readOnlyArgs: List<Var<*>>): LinkedHashMap<String, Var<*>>{
        val map = LinkedHashMap<String, Var<*>>()
        for (i in readOnlyArgs.indices){
            map[readOnlyParams[i].identifier] = readOnlyArgs[i]
        }
        return map
    }
}