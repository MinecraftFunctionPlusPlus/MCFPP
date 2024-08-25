package top.mcfpp.lang


/**
 * 表示一个类型可以通过索引访问其中的成员。
 *
 * @param T 通过索引访问所获得的类型
 */
interface Indexable {

    fun getByIndex(index: Var<*>): Accessor

}