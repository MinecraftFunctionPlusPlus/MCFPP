package top.mcfpp.lang

import top.mcfpp.lib.GlobalField
import java.util.*

/**
 * 一个计分板对象。
 *
 * @constructor 创建一个记分板，它有指定的名字，准则以及显示名称
 */
class SbObject(name: String, rule: String, var display: JsonText?) {

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
        var MCS_default = SbObject("mcs_default", "dummy")
        var MCS_boolean = SbObject("mcs_boolean", "dummy")
    }
}