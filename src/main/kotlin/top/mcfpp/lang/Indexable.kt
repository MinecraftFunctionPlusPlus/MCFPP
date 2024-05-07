package top.mcfpp.lang

interface Indexable<out T : Var<*>> {

    fun getByIndex(index: Var<*>): T

}