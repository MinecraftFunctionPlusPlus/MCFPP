package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.*
import java.util.*
import top.mcfpp.lib.Function

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

    constructor() : this(UUID.randomUUID().toString())

    constructor(b: MCBool) : super(b) {
        this.value = b.value;
        this.boolObject = b.boolObject;
    }

    constructor(b: Boolean) : this(UUID.randomUUID().toString()) {
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

    fun negation(): MCBool {
        if (isConcrete) {
            value = !value
        } else {
            Function.addCommand(
                "execute store success score " + identifier + " " + boolObject
                        + " if score " + identifier + " " + boolObject + " matches " + 0
            )
        }
        return this
    }

    fun or(a: MCBool): MCBool {
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value || a.value)
        } else if (isConcrete) {
            re = a.or(this)
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

    fun and(a: MCBool): MCBool {
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(value && a.value)
        } else if (isConcrete) {
            re = a.and(this)
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

    private fun assignCommand(a: MCBool) {
        if (a.isConcrete) {
            isConcrete = true
            value = true
        } else {
            isConcrete = false
            //变量进栈
            Function.addCommand(
                "execute" +
                        " store result storage mcfpp:system " + top.mcfpp.Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + key +
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

    @Override
    override fun getTempVar(): Var {
        val re = MCBool()
        if(isClassMember) {
            Function.addCommand(
                "execute as @e[type=marker,tag=${cls!!.clsType.tag}]" +
                        "if score @s ${cls!!.address.`object`.name} = ${cls!!.address.identifier} ${cls!!.address.`object`.name}" +
                        "run" +
                        "scoreboard players operation ${re.identifier} ${SbObject.MCS_boolean.name} = @s ${SbObject.MCS_boolean.name}"
            )
        }else{
            Function.addCommand(
                "scoreboard players operation ${re.identifier} ${SbObject.MCS_boolean.name} = $identifier ${SbObject.MCS_boolean.name}"
            )
        }
        return re
    }
}