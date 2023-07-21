package top.mcfpp.lib

import top.mcfpp.lang.Var

interface IFieldWithVar : IField {

    fun putVar(key: String, `var`: Var, forced: Boolean = false): Boolean

    fun getVar(key: String): Var?

    fun containVar(id: String): Boolean

    fun removeVar(id : String): Var?

    val allVars: Collection<Var>
}