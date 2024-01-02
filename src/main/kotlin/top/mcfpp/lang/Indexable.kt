package top.mcfpp.lang

interface Indexable<T : Var> {
    fun getByStringIndex(index: MCString):T

    fun getByIntIndex(index: MCInt):T

}