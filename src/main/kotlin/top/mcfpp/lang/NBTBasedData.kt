package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.command.Commands
import top.mcfpp.lib.FieldContainer
import top.mcfpp.lib.function.Function
import java.util.*

abstract class NBTBasedData<T:Tag<*>> : Var<T> {

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
    constructor(b: NBTBasedData<T>) : super(b)

    protected  fun assignCommand(a : NBT){

    }

    protected fun assignCommand(a: NBTBasedData<T>){
        if(parent != null){
            val b = if(a.parent != null){
                a.getTempVar() as NBTBasedData<T>
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
            Function.addCommand("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[${stackIndex}].$identifier set value ${SNBTUtil.toSNBT(
                javaValue
            )}")
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

    override fun clone(): NBTBasedData<*> {
        TODO("Not yet implemented")
    }

    override fun getVarValue(): Any? {
        return javaValue
    }
}