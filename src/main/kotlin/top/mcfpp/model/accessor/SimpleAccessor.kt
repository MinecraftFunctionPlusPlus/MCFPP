package top.mcfpp.model.accessor

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

/**
 * 一个MCFPP中的访问器。访问器是对类、模板等复合结构成员访问的封装。
 *
 * 访问器拥有一个成员[field]，表示该访问器所访问的成员。对访问器进行的操作，效果和直接对变量进行操作完全一致
 *
 * 访问器可以控制对成员的访问权限，例如只读，只写等
 *
 * 原则上，访问器不应该直接被创建，而是通过[Var.getMemberVar]等方法获得
 */
class SimpleAccessor(field: Var<*>): AbstractAccessor(field) {

    override fun getter(caller: CanSelectMember): Var<*> {
        field.parent = caller
        return field
    }

}