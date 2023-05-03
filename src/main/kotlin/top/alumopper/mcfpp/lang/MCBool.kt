package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.exception.VariableConverseException
import top.alumopper.mcfpp.lib.*
import java.util.*
import top.alumopper.mcfpp.lib.Function

class MCBool : Var, OnScoreboard {
    var value = false
    var boolObject: SbObject = SbObject.MCS_boolean

    constructor(id: String, curr: CacheContainer) {
        key = id
        identifier = curr.prefix + "_" + id
    }

    constructor(b: Boolean, id: String, curr: CacheContainer) : this(id, curr) {
        value = b
        isConcrete = true
    }

    constructor(id: String) {
        key = id
        identifier = id
    }

    constructor() : this(UUID.randomUUID().toString()) {
        isTemp = true
    }

    constructor(b: MCBool) : super(b) {
        this.value = b.value;
        this.boolObject = b.boolObject;
    }

    constructor(b: Boolean) : this(UUID.randomUUID().toString()) {
        isTemp = true
        isConcrete = true
        value = b
    }
    @get:Override
    override val type: String
        get() = "bool"

    @Override
    override fun assign(b: Var?) {
        if (b is MCBool) {
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

    fun equalCommand(a: MCBool): MCBool {
        //re = t == a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(Objects.equals(value, a.value))
        } else if (isConcrete) {
            re = a.equalCommand(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + re.boolObject
                        + " if score " + identifier + " " + boolObject + " matches " + if (a.value) 1 else 0
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + re.boolObject
                        + " if score " + identifier + " " + boolObject + " = " + a.identifier + " " + a.boolObject
            )
        }
        return re
    }

    fun notEqualCommand(a: MCBool): MCBool {
        //re = t != a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(!Objects.equals(value, a.value))
        } else if (isConcrete) {
            re = a.equalCommand(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + re.boolObject
                        + " unless score " + identifier + " " + boolObject + " matches " + if (a.value) 1 else 0
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + re.boolObject
                        + " unless score " + identifier + " " + boolObject + " = " + a.identifier + " " + a.boolObject
            )
        }
        return re
    }

    fun opposeCommand(): MCBool {
        val re: MCBool
        if (isConcrete) {
            if (isTemp) {
                value = !value
                re = this
            } else {
                re = MCBool(!value)
            }
        } else {
            if (isTemp) {
                Function.addCommand(
                    "execute store success score " + identifier + " " + boolObject
                            + " if score " + identifier + " " + boolObject + " matches " + 0
                )
                re = this
            } else {
                re = MCBool()
                Function.addCommand(
                    "execute store success score " + re.identifier + " " + re.boolObject
                            + " if score " + identifier + " " + boolObject + " matches " + 0
                )
            }
        }
        return re
    }

    fun orCommand(a: MCBool): MCBool {
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value || a.value)
        } else if (isConcrete) {
            re = a.orCommand(this)
        } else if (a.isConcrete) {
            if (a.value) {
                re = MCBool(true)
            } else {
                re = MCBool()
                Function.addCommand(
                    "execute store success score " + re.identifier + " " + re.boolObject
                            + " if score " + identifier + " " + boolObject + " matches " + 1
                )
            }
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + re.boolObject
                        + " if score " + identifier + " " + boolObject + " matches " + 1
            )
            Function.addCommand(
                "execute" +
                        " if score " + re.identifier + " " + re.boolObject + " matches " + 0 +
                        " store success score " + re.identifier + " " + re.boolObject +
                        " if score " + a.identifier + " " + a.boolObject + " matches " + 1
            )
        }
        return re
    }

    fun andCommand(a: MCBool): MCBool {
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value && a.value)
        } else if (isConcrete) {
            re = a.andCommand(this)
        } else if (a.isConcrete) {
            if (!a.value) {
                re = MCBool(false)
            } else {
                re = MCBool()
                Function.addCommand(
                    "execute store success score " + re.identifier + " " + re.boolObject
                            + " if score " + identifier + " " + boolObject + " matches " + 1
                )
            }
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.identifier + " " + re.boolObject
                        + " if score " + identifier + " " + boolObject + " matches " + 1
            )
            Function.addCommand(
                "execute" +
                        " if score " + re.identifier + " " + re.boolObject + " matches " + 1 +
                        " store success score " + re.identifier + " " + re.boolObject +
                        " if score " + a.identifier + " " + a.boolObject + " matches " + 1
            )
        }
        return re
    }

    fun assignCommand(a: MCBool) {
        if (a.isConcrete) {
            isConcrete = true
            value = true
        } else {
            isConcrete = false
            //变量进栈
            Function.addCommand(
                "execute" +
                        " store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + key +
                        " run scoreboard players operation " + identifier + " " + boolObject + " = " + a.identifier + " " + a.boolObject
            )
        }
    }

    @Override
    override fun clone(): MCBool {
        return MCBool(this)
    }

    @Override
    override fun setObj(sbObject: SbObject): MCBool {
        boolObject = sbObject
        return this
    }
}