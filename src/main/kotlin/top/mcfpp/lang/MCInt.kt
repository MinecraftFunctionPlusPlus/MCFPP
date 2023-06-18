package top.mcfpp.lang

import top.mcfpp.Project
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.CacheContainer
import top.mcfpp.lib.Function
import java.util.*

/**
 * 代表了mc中的一个整数。实质上是记分板中的一个记分项。
 */
class MCInt : Number<Int> {
    /**
     * 创建一个匿名的动态int
     */
    constructor() : this(UUID.randomUUID().toString())

    /**
     * 创建一个固定的匿名int
     * @param value 值
     */
    constructor(value: Int) : this(UUID.randomUUID().toString()) {
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个固定的int
     * @param id 标识符
     * @param value 值
     */
    constructor(id: String, value: Int) : super(
        Function.currFunction.namespaceID + "_" + id
    ) {
        key = id
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个int
     * @param id 值
     * @param curr 这边变量被储存在的缓存。用于命名。
     */
    constructor(id: String, curr: CacheContainer) : super(curr.prefix + id) {
        key = id
    }

    /**
     * 创建一个int值。它的key和identifier相同。
     * @param id identifier
     */
    constructor(id: String) : super(id) {
        key = id
    }

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCInt) : super(b)

    @get:Override
    override val type: String
        get() = "int"

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        when(b){
            is MCInt ->{
                assignCommand(b)
            }
            else ->{
                throw VariableConverseException()
            }
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return if (type == this.type) {
            this
        } else null
    }

    @Override
    override fun assignCommand(a: Number<Int>) {
        if(isClassMember){
            //如果是类的成员
            //t = a
            if(a.isConcrete){
                isConcrete = true
                value = a.value
            }else{
                isConcrete = false
                //对类中的成员的值进行修改
                Function.addCommand(
                    "execute as @e[tag=${cls!!.clsType.tag}] " +
                            "if score @s ${cls!!.address.`object`.name} = ${cls!!.identifier} ${cls!!.address.`object`.name} " +
                            "run scoreboard players operation @s $`object` = ${a.identifier} ${a.`object`}"
                )
            }
        }else{
            //t = a
            if (a.isConcrete) {
                isConcrete = true
                value = a.value
            } else {
                isConcrete = false
                //变量进栈
                Function.addCommand(
                    "execute store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + key +
                            " run " + Commands.SbPlayerOperation(this, "=", a as MCInt)
                )
            }
        }
    }

    @Override
    override fun plus(a: Number<Int>): Number<Int> {
        //t = t + a
        if (a.isConcrete) {
            if (isConcrete) {
                value = value!! + a.value!!
            } else {
                Function.addCommand(Commands.SbPlayerAdd(this, a.value!!))
            }
            return this
        } else {
            if (isConcrete) {
                return a.plus(this)
            } else {
                Function.addCommand(Commands.SbPlayerOperation(this, "+=", a as MCInt))
            }
            return this
        }
    }

    @Override
    override fun minus(a: Number<Int>): Number<Int> {
        //re = t - a
        if (a.isConcrete) {
            if (isConcrete) {
                value = value!! - a.value!!
            } else {
                Function.addCommand(Commands.SbPlayerRemove(this, a.value!!))
            }
            return this
        } else {
            if (isConcrete) {
                return a.minus(this)
            } else {
                Function.addCommand(Commands.SbPlayerOperation(this, "-=", a as MCInt))
            }
            return this
        }
    }

    @Override
    override fun multiple(a: Number<Int>): Number<Int> {
        //re = t * a
        if (a.isConcrete && isConcrete) {
            value = value!! * a.value!!
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }else{
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            Function.addCommand(Commands.SbPlayerOperation(this, "*=", a as MCInt))
        }
        return this
    }

    @Override
    override fun divide(a: Number<Int>): Number<Int> {
        //re = t / a
        if (a.isConcrete && isConcrete) {
            value = value!! / a.value!!
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }else {
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            Function.addCommand(Commands.SbPlayerOperation(this, "/=", a as MCInt))
        }
        return this
    }

    @Override
    override fun modular(a: Number<Int>): Number<Int> {
        //re = t % a
        if (a.isConcrete && isConcrete) {
            value = value!! % a.value!!
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }else {
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            Function.addCommand(Commands.SbPlayerOperation(this, "%=", a as MCInt))
        }
        return this
    }

    @Override
    override fun isGreater(a: Number<Int>): MCBool {
        //re = t > a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! > a.value!!)
        } else if (isConcrete) {
            re = a.isLess(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " matches " + (a.value!! + 1) + ".."
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " > " + a.identifier + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun isLess(a: Number<Int>): MCBool {
        //re = t < a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! < a.value!!)
        } else if (isConcrete) {
            re = a.isGreater(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " matches " + ".." + (a.value!! - 1)
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " < " + a.identifier + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun isLessOrEqual(a: Number<Int>): MCBool {
        //re = t <= a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! <= a.value!!)
        } else if (isConcrete) {
            re = a.isGreater(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " matches " + ".." + a.value
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " <= " + a.identifier + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun isGreaterOrEqual(a: Number<Int>): MCBool {
        //re = t <= a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! >= a.value!!)
        } else if (isConcrete) {
            re = a.isGreater(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq matches a+1..
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " matches " + a.value + ".."
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " >= " + a.identifier + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun isEqual(a: Number<Int>): MCBool {
        //re = t == a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(Objects.equals(value, a.value))
        } else if (isConcrete) {
            re = a.isEqual(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " matches " + a.value
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " if score " + identifier + " " + `object` + " = " + a.identifier + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun notEqual(a: Number<Int>): MCBool {
        //re = t != a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(!Objects.equals(value, a.value))
        } else if (isConcrete) {
            re = a.isEqual(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " unless score " + identifier + " " + `object` + " matches " + a.value
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + SbObject.MCS_boolean
                        + " unless score " + identifier + " " + `object` + " = " + a.identifier + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun clone(): MCInt {
        return MCInt(this)
    }

    @Override
    override fun getTempVar(): Var {
        val re = MCInt()
        if(isClassMember){
            Function.addCommand(
                "execute as @e[type=marker,tag=${cls!!.clsType.tag}] " +
                        "if score @s ${cls!!.address.`object`.name} = ${cls!!.address.identifier} ${cls!!.address.`object`.name} " +
                        "run " +
                        "scoreboard players operation ${re.identifier} ${re.`object`.name} = @s ${`object`.name}"
            )
        }else{
            Function.addCommand(
                "scoreboard players operation ${re.identifier} ${re.`object`.name} = $identifier ${`object`.name}"
            )
        }
        return re
    }
}