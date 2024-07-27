package top.mcfpp.lib

import top.mcfpp.model.field.GlobalField
import java.io.Serializable
import java.util.*

/**
 * 一个计分板对象。
 *
 * @constructor 创建一个记分板，它有指定的名字，准则以及显示名称
 */
class SbObject(name: String, rule: String, var display: ListChatComponent?): Serializable {

    /**
     * 记分板的id
     */
    var name: String

    /**
     * 记分板的准则
     */
    var criterion: String

    /**
     * 创建一个记分板，它有指定的名字和准则
     */
    constructor(name: String, rule: String) : this(name, rule, null)

    /**
     * 创建一个记分板，它有指定的名字，且准则默认为dummy
     */
    constructor(name: String) : this(name, "dummy", null)

    init {
        this.name = name.lowercase(Locale.getDefault())
        this.criterion = rule.lowercase(Locale.getDefault())
        if(!GlobalField.scoreboards.containsKey(this.name)){
            GlobalField.scoreboards[this.name] = this
        }
    }

    @Override
    override fun toString(): String {
        return name
    }

    @Override
    override fun equals(other: Any?): Boolean {
        return other is SbObject && name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        /**
         * mcfpp默认的计分板变量
         */
        val MCFPP_default = SbObject("mcfpp_default")
        val MCFPP_boolean = SbObject("mcfpp_boolean")

        val MCFPP_INIT = SbObject("mcfpp_init")
        val MCFPP_TEMP = SbObject("mcfpp_temp")
        val MCFPP_POINTER_COUNTER = SbObject("mcfpp_pointer_counter")

        val MCS_float_sign = SbObject("mcs_float_sign")
        val MCS_float_int0 = SbObject("mcs_float_int0")
        val MCS_float_int1 = SbObject("mcs_float_int1")
        val MCS_float_exp = SbObject("mcs_float_exp")
        val Math_float_sign = SbObject("float_sign")
        val Math_float_int0 = SbObject("float_int0")
        val Math_float_int1 = SbObject("float_int1")
        val Math_float_exp = SbObject("float_exp")
        val Math_int = SbObject("int")
    }
}