package top.mcfpp.lang

import java.util.*

/**
 * 一个计分板对象。
 */
//C# TO JAVA CONVERTER TODO TASK: Java annotations will not correspond to .NET attributes:
//ORIGINAL LINE: [Inline] public class SbObject
class SbObject(name: String, rule: String, var display: JsonText?) {

    /**
     * 记分板的id
     */
    var name: String

    /**
     * 记分板的准则
     */
    var rule: String

    constructor(name: String, rule: String) : this(name, rule, null)
    constructor(name: String) : this(name, "dummy", null)

    init {
        this.name = name.lowercase(Locale.getDefault())
        this.rule = rule.lowercase(Locale.getDefault())
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
         * mcfpp数学计算使用的计分板对象。
         */
        var MCS_intvar = SbObject("mcs_intvar", "dummy")

        /**
         * mcfpp默认的计分板变量
         */
        var MCS_default = SbObject("mcs_default", "dummy")
        var MCS_params = SbObject("mcs_params", "dummy")
        var MCS_boolean = SbObject("mcs_boolean", "dummy")
    }
}