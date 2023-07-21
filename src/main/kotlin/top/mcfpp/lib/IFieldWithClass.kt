package top.mcfpp.lib

interface IFieldWithClass:IField {
    fun addClass(identifier: String, cls: Class, force: Boolean = false): Boolean

    fun removeClass(identifier: String):Boolean

    fun getClass(identifier: String):Class?

    fun hasClass(identifier: String):Boolean

    fun hasClass(cls: Class): Boolean
}