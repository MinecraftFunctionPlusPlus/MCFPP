package top.mcfpp.lib

interface IFieldWithFunction: IField {
    fun getFunction(key: String, argsTypes: List<String>): Function?
    fun addFunction(function: Function)

    fun hasFunction(function: Function): Boolean
}