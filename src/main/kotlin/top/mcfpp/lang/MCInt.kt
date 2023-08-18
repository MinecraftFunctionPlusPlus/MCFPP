package top.mcfpp.lang

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import java.util.*
import kotlin.collections.HashMap

/**
 * 代表了mc中的一个整数。实质上是记分板中的一个记分项。你可以对它进行加减乘除等基本运算操作，以及大小比较等逻辑运算。
 */
class MCInt : Number<Int> {

    /**
     * 创建一个int类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(curr: FieldContainer, identifier: String = UUID.randomUUID().toString()) : this(curr.prefix + identifier){
        this.identifier = identifier
    }


    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 创建一个固定的int
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Int, identifier: String = UUID.randomUUID().toString()) : super(curr.prefix + identifier) {
        isConcrete = true
        this.value = value
    }

    /**
     * 创建一个固定的int。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Int, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.value = value
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
    @InsertCommand
    override fun assignCommand(a: Number<Int>) {
        if(isClassMember){
            //如果是类的成员
            val cmd : String = if(!isStatic){
                //静态
                "execute as @e[type=marker,tag=${clsPointer!!.clsType.tag}] " +
                        "if score @s ${clsPointer!!.address.`object`.name} = ${clsPointer!!.name} ${clsPointer!!.address.`object`.name}"
            }else{
                "execute as @e[type=marker,tag=${clsPointer!!.clsType.staticTag}]"
            }
            //类的成员是运行时动态的
            //t = a
            if(a.isConcrete){
                isConcrete = false
                //对类中的成员的值进行修改
                Function.addCommand("$cmd run scoreboard players set @s $`object` ${a.value}")
            }else{
                //对类中的成员的值进行修改
                Function.addCommand("$cmd run scoreboard players operation @s $`object` = ${a.name} ${a.`object`}"
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
                    "execute store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1" +
                            " run " + Commands.SbPlayerOperation(this, "=", a as MCInt)
                )
            }
        }
    }

    @Override
    @InsertCommand
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
    @InsertCommand
    override fun minus(a: Number<Int>): Number<Int> {
        //t = t - a
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
    @InsertCommand
    override fun multiple(a: Number<Int>): Number<Int> {
        //t = t * a
        if (a.isConcrete && isConcrete) {
            value = value!! * a.value!!
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }else if(this.isConcrete){
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            Function.addCommand(Commands.SbPlayerOperation(this, "*=", a as MCInt))
        }
        return this
    }

    @Override
    @InsertCommand
    override fun divide(a: Number<Int>): Number<Int> {
        //t = t / a
        if (a.isConcrete && isConcrete) {
            value = value!! / a.value!!
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }else if(this.isConcrete){
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            Function.addCommand(Commands.SbPlayerOperation(this, "/=", a as MCInt))
        }
        return this
    }

    @Override
    @InsertCommand
    override fun modular(a: Number<Int>): Number<Int> {
        //t = t % a
        if (a.isConcrete && isConcrete) {
            value = value!! % a.value!!
        } else {
            if (a.isConcrete) {
                Function.addCommand(Commands.SbPlayerSet(a as MCInt, a.value!!))
            }else if(this.isConcrete){
                Function.addCommand(Commands.SbPlayerSet(this, value!!))
            }
            Function.addCommand(Commands.SbPlayerOperation(this, "%=", a as MCInt))
        }
        return this
    }

    @Override
    @InsertCommand
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
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " matches " + (a.value!! + 1) + ".."
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " > " + a.name + " " + a.`object`
            )
        }
        return re
    }

    @Override
    @InsertCommand
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
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " matches " + ".." + (a.value!! - 1)
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " < " + a.name + " " + a.`object`
            )
        }
        return re
    }

    @Override
    @InsertCommand
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
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " matches " + ".." + a.value
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " <= " + a.name + " " + a.`object`
            )
        }
        return re
    }

    @Override
    @InsertCommand
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
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " matches " + a.value + ".."
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " >= " + a.name + " " + a.`object`
            )
        }
        return re
    }

    @Override
    @InsertCommand
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
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " matches " + a.value
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " if score " + name + " " + `object` + " = " + a.name + " " + a.`object`
            )
        }
        return re
    }

    @Override
    @InsertCommand
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
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " unless score " + name + " " + `object` + " matches " + a.value
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + SbObject.MCS_boolean
                        + " unless score " + name + " " + `object` + " = " + a.name + " " + a.`object`
            )
        }
        return re
    }

    @Override
    override fun clone(): MCInt {
        return MCInt(this)
    }

    private var index : Int = -1
    @Override
    @InsertCommand
    override fun getTempVar(cache: HashMap<Var, String>): Var {
        if(isTemp) return this
        if(isConcrete){
            return MCInt(value!!)
        }
        val re = MCInt()
        val cmd : String = if(isClassMember){
            if(!isStatic){
                "execute as @e[type=marker,tag=${clsPointer!!.clsType.tag}] " +
                        "if score @s ${clsPointer!!.address.`object`.name} = ${clsPointer!!.address.name} ${clsPointer!!.address.`object`.name} " +
                        "run " +
                        "scoreboard players operation ${re.name} ${re.`object`.name} = @s ${`object`.name}"
            }else{
                "execute as @e[type=marker,tag=${clsPointer!!.clsType.staticTag},limit=1] " +
                        "run scoreboard players operation ${re.name} ${re.`object`.name} = @s ${`object`.name}"
            }
        }else{
            "scoreboard players operation ${re.name} ${re.`object`.name} = $name ${`object`.name}"
        }
        //是否已经为此变量声明过临时变量
        if(cache[this] != null){
            val s = cache[this]!!
            val qwq = if(s.contains("run")){
                s.replace("run","store result score ${re.name} ${re.`object`.name} run")
            }else{
                "execute store result score ${re.name} ${re.`object`.name} run $s"
            }
            Function.replaceCommand(qwq, index)
        }else{
            cache[this] = cmd
            index = Function.addCommand(cmd) - 1
        }
        return re
    }
}