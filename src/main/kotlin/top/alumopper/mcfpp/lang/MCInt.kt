package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.command.Commands
import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.CacheContainer
import top.alumopper.mcfpp.lib.Function
import java.util.*

/**
 * 代表了mc中的一个整数。实质上是记分板中的一个记分项。
 */
class MCInt : Number<Int> {
    /**
     * 创建一个匿名的动态int
     */
    constructor() : this(UUID.randomUUID().toString()) {
        isTemp = true
    }

    /**
     * 创建一个固定的匿名int
     * @param value 值
     */
    constructor(value: Int) : this(UUID.randomUUID().toString()) {
        isTemp = true
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个固定的int
     * @param id 标识符
     * @param value 值
     */
    constructor(id: String, value: Int) : super(
        Function.currFunction!!.namespaceID + "_" + id
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
    constructor(b: MCInt) : super(b) {
    }

    @get:Override
    override val type: String
        get() = "int"

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        if (b is MCInt) {
            assignCommand(b)
        } else {
            throw VariableConverseException()
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
        //t = a
        if (a.isConcrete) {
            isConcrete = true
            value = a.value
        } else {
            isConcrete = false
            //变量进栈
            Function.addCommand(
                "execute store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + key + " run "
                        + Commands.SbPlayerOperation(this, "=", a as MCInt)
            )
        }
    }

    @Override
    override fun addCommand(a: Number<Int>): Number<Int> {
        //re = t + a
        var re = MCInt()
        if (a.isConcrete) {
            if (isConcrete) {
                re.value = value!! + a.value!!
                re.isConcrete = true
            } else {
                if (!isTemp) {
                    Function.addCommand(Commands.SbPlayerOperation(re, "=", this))
                } else {
                    re = this
                }
                Function.addCommand(Commands.SbPlayerAdd(re, a.value!!))
            }
        } else {
            if (isConcrete) {
                if (!a.isTemp) {
                    Function.addCommand(Commands.SbPlayerOperation(re, "=", a as MCInt))
                } else {
                    re = a as MCInt
                }
                Function.addCommand(Commands.SbPlayerAdd(re, value!!))
            } else {
                if (!a.isTemp) {
                    Function.addCommand(Commands.SbPlayerOperation(re, "=", a as MCInt))
                } else {
                    re = a as MCInt
                }
                Function.addCommand(Commands.SbPlayerOperation(re, "+=", this))
            }
        }
        return re
    }

    @Override
    override fun minusCommand(a: Number<Int>): Number<Int> {
        //re = t - a
        var re = MCInt()
        if (a.isConcrete) {
            if (isConcrete) {
                re.value = value!! - a.value!!
                re.isConcrete = true
            } else {
                if (!isTemp) {
                    Function.addCommand(Commands.SbPlayerOperation(re, "=", this))
                } else {
                    re = this
                }
                Function.addCommand(Commands.SbPlayerRemove(re, a.value!!))
            }
        } else {
            if (isConcrete) {
                if (!a.isTemp) {
                    Function.addCommand(Commands.SbPlayerOperation(re, "=", a as MCInt))
                } else {
                    re = a as MCInt
                }
                Function.addCommand(Commands.SbPlayerRemove(re, value!!))
            } else {
                if (!a.isTemp) {
                    Function.addCommand(Commands.SbPlayerOperation(re, "=", a as MCInt))
                } else {
                    re = a as MCInt
                }
                Function.addCommand(Commands.SbPlayerOperation(re, "-=", this))
            }
        }
        return re
    }

    @Override
    override fun multipleCommand(a: Number<Int>): Number<Int> {
        //re = t * a
        var re = MCInt()
        if (a.isConcrete && isConcrete) {
            re.value = value!! * a.value!!
            re.isConcrete = true
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }
            if (isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            if (!isTemp) {
                Function.addCommand(Commands.SbPlayerOperation(re, "=", this))
            } else {
                re = this
            }
            Function.addCommand(Commands.SbPlayerOperation(re, "*=", a as MCInt))
        }
        return re
    }

    @Override
    override fun divideCommand(a: Number<Int>): Number<Int> {
        //re = t / a
        var re = MCInt()
        if (a.isConcrete && isConcrete) {
            re.value = value!! / a.value!!
            re.isConcrete = true
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }
            if (isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            if (!isTemp) {
                Function.addCommand(Commands.SbPlayerOperation(re, "=", this))
            } else {
                re = this
            }
            Function.addCommand(Commands.SbPlayerOperation(re, "/=", a as MCInt))
        }
        return re
    }

    @Override
    override fun modularCommand(a: Number<Int>): Number<Int> {
        //re = t / a
        var re = MCInt()
        if (a.isConcrete && isConcrete) {
            re.value = value!! % a.value!!
            re.isConcrete = true
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }
            if (isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            if (!isTemp) {
                Function.addCommand(Commands.SbPlayerOperation(re, "=", this))
            } else {
                re = this
            }
            Function.addCommand(Commands.SbPlayerOperation(re, "%=", a as MCInt))
        }
        return re
    }

    @Override
    override fun greaterCommand(a: Number<Int>): MCBool? {
        //re = t > a
        val re: MCBool?
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! > a.value!!)
        } else if (isConcrete) {
            re = a.lessCommand(this)
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
    override fun lessCommand(a: Number<Int>): MCBool? {
        //re = t < a
        val re: MCBool?
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! < a.value!!)
        } else if (isConcrete) {
            re = a.greaterCommand(this)
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
    override fun lessOrEqualCommand(a: Number<Int>): MCBool? {
        //re = t <= a
        val re: MCBool?
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! <= a.value!!)
        } else if (isConcrete) {
            re = a.greaterCommand(this)
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
    override fun greaterOrEqualCommand(a: Number<Int>): MCBool? {
        //re = t <= a
        val re: MCBool?
        if (isConcrete && a.isConcrete) {
            re = MCBool(value!! >= a.value!!)
        } else if (isConcrete) {
            re = a.greaterCommand(this)
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
    override fun equalCommand(a: Number<Int>): MCBool? {
        //re = t == a
        val re: MCBool?
        if (isConcrete && a.isConcrete) {
            re = MCBool(Objects.equals(value, a.value))
        } else if (isConcrete) {
            re = a.equalCommand(this)
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
    override fun notEqualCommand(a: Number<Int>): MCBool? {
        //re = t != a
        val re: MCBool?
        if (isConcrete && a.isConcrete) {
            re = MCBool(!Objects.equals(value, a.value))
        } else if (isConcrete) {
            re = a.equalCommand(this)
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
}