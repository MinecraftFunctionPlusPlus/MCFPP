package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.command.Commands
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.function.Function
import java.util.*

/**
 * 基于nbt实现的数据类型。包括实体[Entity]，原始Json文本[JsonText]，字符串[MCString]，还有三种集合类型[NBTList],[NBTMap],[NBTDictionary]，以及目标选择器[Selector]
 *
 * 对于基于nbt实现的数据类型来说，堆数据存在marker.data中，栈数据存在stack_frame中。而nbt的键名就是变量名，值就是变量的值
 *
 * 例如对于
 * ```
 * list l = []
 * ```
 * 它声明在函数中，是一个栈数据，那么它的储存路径就是在名为`mcfpp:system`的storage下的`项目名.stack_frame\[x].l`下（其中x是函数的栈帧）
 */
abstract class NBTAny<T:Tag<*>> : Var<T> {

    /**
     * 创建一个list类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(curr.prefix + identifier) {
        this.identifier = identifier

    }

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 创建一个固定的list
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: T,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 创建一个固定的list。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: T, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: NBTAny<T>) : super(b)

    protected fun assignCommand(a: NBTAny<T>){
        if(parent != null){
            val b = if(a.parent != null){
                a.getTempVar() as NBTAny<*>
            }else a
            //类的成员是运行时动态的
            isConcrete = false
            //t = a
            Function.addCommands(
                Commands.selectRun(parent!!,"data modify entity @s data.$identifier set from storage mcfpp:temp temp.${b.identifier}")
            )
        }else{
            if (a.isConcrete) {
                isConcrete = true
                javaValue = a.javaValue
            } else if(a.parent != null){
                Function.addCommands(
                    Commands.selectRun(a.parent!!, "data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[$stackIndex].$identifier set from entity @s data.${a.identifier}")
                )
            }else{
                Function.addCommand("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[${stackIndex}].$identifier set from storage mcfpp:system ${Project.currNamespace}.stack_frame[${a.stackIndex}].${a.identifier}")
            }
        }
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
        if(!isConcrete) return
        isConcrete = false
        if(parent != null){
            Function.addCommands(
                Commands.selectRun(parent!!,"data modify entity @s data.$identifier set value ${SNBTUtil.toSNBT(javaValue)}")
            )
        }else{
            Function.addCommand(
                "data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[${stackIndex}].$identifier set value ${SNBTUtil.toSNBT(javaValue)}"
            )
        }
    }

    override fun getTempVar(): Var<*> {
        if (isTemp) return this
        if (isConcrete) {
            return createTempVar(javaValue!!)
        }
        val re = createTempVar()
        re.isTemp = true
        re.assign(this)
        return re
    }

    abstract fun createTempVar(): Var<*>
    abstract fun createTempVar(value: Tag<*>): Var<*>

    override fun clone(): NBTAny<*> {
        TODO("Not yet implemented")
    }

    override fun getVarValue(): Any? {
        return javaValue
    }

    override fun toString(): String {
        return "[$type,value=${if(isConcrete) SNBTUtil.toSNBT(javaValue) else "Unknown"}]"
    }
}