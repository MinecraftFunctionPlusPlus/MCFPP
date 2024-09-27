package top.mcfpp.core.lang

import top.mcfpp.model.accessor.SimpleAccessor


/**
 * 表示一个类型可以通过索引访问其中的成员。
 *
 */
interface Indexable {

    fun getByIndex(index: Var<*>): PropertyVar

}