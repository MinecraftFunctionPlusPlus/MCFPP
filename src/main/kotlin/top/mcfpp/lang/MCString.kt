package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Class
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.Function
import top.mcfpp.lib.Member
import java.util.*

/**
 * string表示一个字符串。要声明一个字符串，应该使用string类型，例如string abc = "abc"。
 *
 * string重写了+运算符，因此能够对字符串进行加运算。例如string ps = "abc" + "def"，结果
 *得到ps为”abcdef”。
 *
 * 和MCFPP中所有变量一样，string类型有编译时静态和动态两种方式。类似上文中的声明方式，编译器
 *能发现自己能够跟踪到这个字符串的内容，因此在编译过程中会进行简单的替换。而动态则不同，动态字符
 *串通常是由于将一个jstring转换成string类型而导致的。动态字符串储存在nbt中，以供操作。
 *
 * 得益于宏和data string命令，让字符串的动态操作成为了可能。在1.19.4-的版本中，MCFPP只支持静
 *态的字符串和原始JSON文本功能。
 *
 */
class MCString : Var {
    var value: String? = null
    override var type = "string"

    /**
     * 构造一个字符串
     *
     * @param container 域容器，用于确定字符串的mc名。如果不指定identifier，则此项指定无效。如果为null则标识符名和mc名相同
     * @param identifier 标识符。如果为null则为随机uuid
     * @param value 字符串的值，可以为null
     */
    constructor(container: FieldContainer?, identifier: String?, value: String?) {
        if(value != null){
            this.value = value
            isConcrete = true
        }else{
            isConcrete = false
        }
        if(identifier == null){
            this.identifier = UUID.randomUUID().toString()
        }else{
            if(container != null){
                this.identifier = container.prefix + identifier
            }else{
                this.identifier = identifier
            }
        }
    }

    constructor(value: String?) : this(null, null, value)

    constructor(b: MCString) {
        value = b.value
        isConcrete = b.isConcrete
    }

    @Override
    override fun toString(): String {
        return value!!
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param params 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        params: List<String>,
        accessModifier: Member.AccessModifier
    ): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
        hasAssigned = true
        if(b is MCString){
            assignCommand(b)
        }else{
            throw VariableConverseException()
        }
    }

    private fun assignCommand(a: MCString){
        if (parent != null){
            //是成员
            val b = if(a.parent != null){
                a.getTempVar() as NBT
            }else a
            isConcrete = false
            Function.addCommand(
                Commands.selectRun(
                    parent!!,
                    Command.build("data modify entity @s data.$identifier set from storage mcfpp:temp temp.${b.identifier}")
                )
            )
        }else{
            //是局部变量
            if(a.isConcrete){
                value = a.value
                isConcrete = true
            }else{
                isConcrete = false
                if(a.parent != null){
                    Commands.selectRun(
                        a.parent!!,
                        Command.build("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set from entity @s data.${a.identifier}")
                    )
                }else{
                    val command = Command.build("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set from storage mcfpp:system ${Project.currNamespace}.stack_frame[${a.stackIndex}].${a.identifier}")
                    Function.addCommand(command)
                }
            }
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): MCString {
        return MCString(this)
    }

    override fun getTempVar(): Var {
        TODO("Not yet implemented")
    }

    override fun storeToStack() {
        //什么都不用做哦
        return
    }

    override fun getFromStack() {
        //什么都不用做哦
        return
    }

    override fun toDynamic() {
        val parent = parent
        if(!isConcrete) return
        isConcrete = false
        if (parent != null) {
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is ClassType -> {
                    arrayOf(Command.build("execute as ${(parent.dataType as Class).uuid} run "))
                }
                else -> TODO()
            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build("data modify entity @s data.${identifier} set value \"$value\""))
        } else {
            val cmd = Command.build("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set value \"$value\"")
            Function.addCommand(cmd)
        }
    }
}