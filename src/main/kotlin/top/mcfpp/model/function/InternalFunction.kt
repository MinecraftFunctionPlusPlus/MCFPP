package top.mcfpp.model.function

import top.mcfpp.model.Class
import top.mcfpp.model.Template
import top.mcfpp.model.field.InternalFunctionField
import java.util.*

/**
 * 一个匿名的内部函数。
 *
 * 由于minecraft没有直接允许单tick中的循环或者if-else，我们采用的是命令函数的方式
 * 模拟的循环语句或者条件语句中的代码块。而代码块对应的函数名字通常是随机的。
 *
 * 匿名内部函数和普通的命令函数有一个细小的区别。为了方便起见，我们还是先看一小段代码
 * ```
 *  int i = 0;
 *  if(qwq){
 *     i ++;
 *  }
 * ```
 * 如果我们仍然简单采用直接调用函数的方式，将if后面的`i++`单独放在一个函数中，
 * 我们便会发现编译器抛出了一个异常，表示这个匿名函数中没有定义变量i。但是显然，无论是
 * 在if语句还是其他的循环语句中，我们都可以直接调用函数之前已经声明好的变量。
 *
 * 因此，这里就是匿名内部函数和普通的函数的主要差异点了。匿名函数类是允许访问它的父函数，
 * 即声明if、while语句的函数的栈的。
 *
 * @param prefix 匿名内部函数的前缀。
 * @param parent 这个函数的调用者，即父函数
 *
 */
class InternalFunction(prefix: String, parent: Function) : Function(prefix + UUID.randomUUID()) {

    init {
        field = InternalFunctionField(parent.field.clone(), this)
        setParentFunction(parent)
        ownerType = Companion.OwnerType.NONE
    }

    /**
     * 设置它的父函数
     * @param parent 父函数对象
     */
    private fun setParentFunction(parent: Function) {
        this.parent.add(parent)
        parent.child.add(this)
    }

    @Override
    override fun parentClass(): Class? {
        return parent[0].parentClass()
    }

    @Override
    override fun parentStruct(): Template? {
        return parent[0].parentStruct()
    }
}