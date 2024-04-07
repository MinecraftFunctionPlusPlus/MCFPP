package top.mcfpp.lang

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.*
import java.util.*
import top.mcfpp.lib.function.Function

/**
 * 布尔型变量是mcfpp的基本类型之一，它表示一个只有0，1两种取值可能性的值。
 *
 * 在实际实现过程中，它仍然是由记分板实现的，也就是说它本质仍然是一个记分板的int型变量。如果直接对mcfunction操作，你也可以对布尔型进行加减法。
 * 但是在mcfpp中你是不允许这么做的。
 *
 * bool型变量实现了多种计算方法，比如与，或，非等基本的逻辑运算。
 */
open class MCBool : Var<Boolean>, OnScoreboard {

    /**
     * 此bool变量含有的值。仅在它为字面量时才有效。
     */
    override var javaValue:Boolean? = false


    /**
     * 此bool变量依托的记分板
     */
    var boolObject: SbObject = SbObject.MCS_boolean

    /**
     * 创建一个bool类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(curr: FieldContainer, identifier: String = UUID.randomUUID().toString()) : this(curr.prefix + identifier){
        this.identifier = identifier
    }


    /**
     * 创建一个bool值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 创建一个固定的bool
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Boolean, identifier: String = UUID.randomUUID().toString()) : super(curr.prefix + identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 创建一个固定的bool。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Boolean, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        isConcrete = true
        this.javaValue = value
    }

    /**
     * 复制一个bool
     * @param b 被复制的int值
     */
    constructor(b: MCBool) : super(b)

    override var type: MCFPPType = MCFPPBaseType.Bool

    @Override
    override fun assign(b: Var<*>?) {
        hasAssigned = true
        if (b is MCBool) {
            assignCommand(b)
        } else {
            throw VariableConverseException()
        }
    }

    @Override
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Bool -> this
            MCFPPBaseType.Any -> MCAny(this)
            else -> throw VariableConverseException()
        }
    }

    @InsertCommand
    fun equalCommand(a: MCBool): MCBool {
        //re = t == a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(Objects.equals(javaValue, a.javaValue))
        } else if (isConcrete) {
            re = a.equalCommand(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + re.boolObject
                        + " if score " + name + " " + boolObject + " matches " + if (a.javaValue!!) 1 else 0
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + re.boolObject
                        + " if score " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
            )
        }
        return re
    }

    @InsertCommand
    fun notEqualCommand(a: MCBool): MCBool {
        //re = t != a
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(!Objects.equals(javaValue, a.javaValue))
        } else if (isConcrete) {
            re = a.equalCommand(this)
        } else if (a.isConcrete) {
            //execute store success score qwq qwq if score qwq qwq = owo owo
            re = MCBool()

            Function.addCommand(
                "execute store success score " + re.name + " " + re.boolObject
                        + " unless score " + name + " " + boolObject + " matches " + if (a.javaValue!!) 1 else 0
            )
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + re.boolObject
                        + " unless score " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
            )
        }
        return re
    }

    @InsertCommand
    fun negation(): MCBool {
        if (isConcrete) {
            javaValue = !javaValue!!
        } else {
            Function.addCommand(
                "execute store success score " + name + " " + boolObject
                        + " if score " + name + " " + boolObject + " matches " + 0
            )
        }
        return this
    }

    @InsertCommand
    fun or(a: MCBool): MCBool {
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(javaValue!! || a.javaValue!!)
        } else if (isConcrete) {
            re = a.or(this)
        } else if (a.isConcrete) {
            if (a.javaValue!!) {
                re = MCBool(true)
            } else {
                re = MCBool()
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " if score " + name + " " + boolObject + " matches " + 1
                )
            }
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + re.boolObject
                        + " if score " + name + " " + boolObject + " matches " + 1
            )
            Function.addCommand(
                "execute" +
                        " if score " + re.name + " " + re.boolObject + " matches " + 0 +
                        " store success score " + re.name + " " + re.boolObject +
                        " if score " + a.name + " " + a.boolObject + " matches " + 1
            )
        }
        return re
    }

    @InsertCommand
    fun and(a: MCBool): MCBool {
        val re: MCBool
        if (isConcrete && a.isConcrete) {
            re = MCBool(javaValue!! && a.javaValue!!)
        } else if (isConcrete) {
            re = a.and(this)
        } else if (a.isConcrete) {
            if (!a.javaValue!!) {
                re = MCBool(false)
            } else {
                re = MCBool()
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " if score " + name + " " + boolObject + " matches " + 1
                )
            }
        } else {
            re = MCBool()
            Function.addCommand(
                "execute store success score " + re.name + " " + re.boolObject
                        + " if score " + name + " " + boolObject + " matches " + 1
            )
            Function.addCommand(
                "execute" +
                        " if score " + re.name + " " + re.boolObject + " matches " + 1 +
                        " store success score " + re.name + " " + re.boolObject +
                        " if score " + a.name + " " + a.boolObject + " matches " + 1
            )
        }
        return re
    }

    @InsertCommand
    private fun assignCommand(a: MCBool) {
        if(a is ReturnedMCBool){
            Function.addCommand(
                "execute" +
                        " store result storage mcfpp:system " + top.mcfpp.Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1" +
                        " store result score $name $boolObject" +
                        " run function ${a.parentFunction.namespaceID}"
            )
        }else{
            if (a.isConcrete) {
                isConcrete = true
                javaValue = true
            } else {
                isConcrete = false
                //变量进栈
                Function.addCommand(
                    "execute" +
                            " store result storage mcfpp:system " + top.mcfpp.Project.defaultNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1" +
                            " run scoreboard players operation " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
                )
            }
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

    override fun getVarValue(): Any? {
        return javaValue
    }

    @Override
    @InsertCommand
    override fun getTempVar(): MCBool {
        if (isTemp) return this
        if (isConcrete) {
            return MCBool(javaValue!!)
        }
        val re = MCBool()
        re.assign(this)
        return re
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
    }

    override fun toDynamic() {
        TODO("Not yet implemented")
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    companion object{
        val data = CompoundData("bool","mcfpp")
    }
}