package top.mcfpp.core.lang.bool

import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/**
 * 一个函数由函数返回的bool类型变量。
 *
 * 通常的bool变量是依托于记分板储存，即0代表false，1代表true。
 *当一个函数返回一个bool类型变量的时候，会使用`return 1`或者`return 0`的方式返回。因此可以使用`execute if function`来获取一个函数的bool类型的返回值。
 *同时，利用多个if和unless串联，也可以实现非或的效果。
 *
 * @constructor 创建一个函数返回的bool类型变量
 */
class ReturnedMCBool(val parentFunction: Function) : MCBool() {

    init {
        this.isStatic = false
        this.isTemp = false
        this.isConst = false
    }

    override fun doAssignedBy(b: Var<*>): MCBool {
        LogProcessor.error("The bool type returned by the function is read-only")
        throw Exception()
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        LogProcessor.error("The bool type returned by the function is read-only")
        throw Exception()
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        LogProcessor.error("The bool type returned by the function is read-only")
        throw Exception()
    }

    override fun clone(): ReturnedMCBool {
        return ReturnedMCBool(parentFunction)
    }

    override fun getTempVar(): MCBool {
        val re = MCBool()
        re.assignedBy(this)
        return re
    }

    override fun canAssignedBy(b: Var<*>) = false
}