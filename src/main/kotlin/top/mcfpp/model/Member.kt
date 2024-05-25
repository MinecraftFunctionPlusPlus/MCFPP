package top.mcfpp.model

/**
 * 一个类的成员。类的成员拥有三种访问级别，即[私有][AccessModifier.PRIVATE]、[保护][AccessModifier.PROTECTED]和[公开][AccessModifier.PUBLIC]。
 * 具体细则和java一致。
 *
 * 成员在编译器读取类的声明的时候，即被添加到类的成员缓存中。
 */
interface Member {

    enum class AccessModifier {
        PUBLIC, PROTECTED, PRIVATE
    }

    /**
     * 类的访问修饰符
     */
    var accessModifier: AccessModifier

    /**
     * 这个成员是否是静态的
     */
    var isStatic: Boolean

    /**
     * 获取这个成员的父类，可能不存在
     * @return
     */
    fun parentClass(): Class?

    /**
     * 获取这个成员的父结构体，可能不存在
     *
     * @return
     */
    fun parentStruct(): Template?
}