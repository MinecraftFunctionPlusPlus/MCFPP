package top.mcfpp.model

import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import java.io.Serializable

/**
 * 一个类的成员。类的成员拥有三种访问级别，即[私有][AccessModifier.PRIVATE]、[保护][AccessModifier.PROTECTED]和[公开][AccessModifier.PUBLIC]。
 * 具体细则和java一致。
 *
 * 成员在编译器读取类的声明的时候，即被添加到类的成员缓存中。
 */
interface Member: Serializable {

    enum class AccessModifier: Serializable {
        /**
         * 公开的，所有类都可以访问
         */
        PUBLIC,

        /**
         * 保护的，只有子类可以访问
         */
        PROTECTED,

        /**
         * 私有的，只有本类可以访问
         */
        PRIVATE,

        /**
         * 编译器私有的，只有编译器可以访问。此访问等级不能通过mcfpp代码定义。
         */
        COMPILE_PRIVATE
    }

    /**
     * 类的访问修饰符
     */
    var accessModifier : AccessModifier

    /**
     * 是否是静态的。默认为否
     */
    val isStatic : Boolean
        get() = parentTemplate() is ObjectDataTemplate

    /**
     * 这个成员是否不可被继承
     */
    var isFinal: Boolean

    /**
     * 获取这个成员的父数据模板，可能不存在
     *
     * @return
     */
    fun parentTemplate(): DataTemplate?
}