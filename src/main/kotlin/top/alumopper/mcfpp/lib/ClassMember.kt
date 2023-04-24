package top.alumopper.mcfpp.lib

/**
 * 一个类的成员
 */
interface ClassMember {
    enum class AccessModifier {
        PRIVATE, PROTECTED, PUBLIC
    }

    var accessModifier : AccessModifier

    var isStatic: Boolean
    fun Class(): Class?
}