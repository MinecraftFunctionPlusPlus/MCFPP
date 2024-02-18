package top.mcfpp.lang

interface Indexable<T : Var<*>> {

    fun getByIndex(index: Var<*>):T

}