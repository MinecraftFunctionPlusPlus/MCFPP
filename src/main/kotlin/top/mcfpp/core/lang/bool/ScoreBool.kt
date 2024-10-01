package top.mcfpp.core.lang.bool

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.lib.SbObject
import top.mcfpp.model.*
import java.util.*
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

/**
 * 布尔型变量是mcfpp的基本类型之一，它表示一个只有0，1两种取值可能性的值。
 *
 * 在实际实现过程中，它仍然是由记分板实现的，也就是说它本质仍然是一个记分板的int型变量。如果直接对mcfunction操作，你也可以对布尔型进行加减法。
 * 但是在mcfpp中你是不允许这么做的。
 *
 * bool型变量实现了多种计算方法，比如与，或，非等基本的逻辑运算。
 */
open class MCBool : Var<MCBool>, OnScoreboard {
    
    /**
     * 此bool变量依托的记分板
     */
    var boolObject: SbObject = SbObject.MCFPP_boolean

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
     * 复制一个bool
     * @param b 被复制的int值
     */
    constructor(b: MCBool) : super(b)

    override var type: MCFPPType = MCFPPBaseType.Bool

    @Override
    override fun doAssignedBy(b: Var<*>) : MCBool {
        when(b){
            is MCBool -> return assignCommand(b)

            is CommandReturn -> {
                if(parentClass() != null){
                    Function.addCommands(
                        Commands.selectRun(parent!!, Command("store result score @s $boolObject run").build(b.command), false)
                    )
                }else{
                    Function.addCommand(
                        Command.build("execute store result score $name $boolObject run").build(b.command)
                    )
                }
                return MCBool(this)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                return this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        return when(b){
            is MCBool -> true
            is CommandReturn -> true
            else -> false
        }
    }

    override fun isEqual(a: Var<*>): Var<*>? {
        //re = t == a
        val re = MCBool()
        when(a){
            is MCBoolConcrete -> {
                //execute store success score qwq qwq if score qwq qwq = owo owo
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " if score " + name + " " + boolObject + " matches " + if (a.value) 1 else 0
                )
            }

            is MCBool -> {
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " if score " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
                )
            }

            else -> return null
        }
        return re
    }

    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*>? {
        //re = t != a
        val re: MCBool = MCBool()
        when(a){
            is MCBoolConcrete -> {
                //execute store success score qwq qwq if score qwq qwq = owo owo
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " unless score " + name + " " + boolObject + " matches " + if (a.value) 1 else 0
                )
            }

            is MCBool -> {
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " unless score " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
                )
            }

            else -> return null
        }
        return re
    }

    @InsertCommand
    override fun negation(): Var<*> {
        Function.addCommand(
            "execute store success score " + name + " " + boolObject
                    + " if score " + name + " " + boolObject + " matches " + 0
        )
        return this
    }

    @InsertCommand
    override fun or(a: Var<*>): Var<*>? {
        val re: MCBool
        when(a){
            is MCBoolConcrete -> {
                if(a.value){
                    return MCBoolConcrete(true)
                } else {
                    re = MCBool()
                    Function.addCommand(
                        "execute store success score " + re.name + " " + re.boolObject
                                + " if score " + name + " " + boolObject + " matches " + 1
                    )
                    return re
                }
            }

            is MCBool -> {
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

            else -> return null
        }
        return re
    }

    @InsertCommand
    override fun and(a: Var<*>): Var<*>? {
        val re: MCBool
        when(a){
            is MCBoolConcrete -> {
                if(!a.value){
                    return MCBoolConcrete(false)
                } else {
                    re = MCBool()
                    Function.addCommand(
                        "execute store success score " + re.name + " " + re.boolObject
                                + " if score " + name + " " + boolObject + " matches " + 1
                    )
                    return re
                }
            }

            is MCBool -> {
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

            else -> return null
        }
        return re
    }

    @InsertCommand
    private fun assignCommand(a: MCBool) : MCBool {
        if(a is ReturnedMCBool){
            Function.addCommand(
                "execute" +
                        " store result storage mcfpp:system " + top.mcfpp.Project.config.rootNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1" +
                        " store result score $name $boolObject" +
                        " run function ${a.parentFunction.namespaceID}"
            )
            return this
        }else if(a is MCBoolConcrete){
            return MCBoolConcrete(this,a.value)
        }else{
            //变量进栈
            Function.addCommand(
                "execute" +
                        " store result storage mcfpp:system " + top.mcfpp.Project.config.rootNamespace + ".stack_frame[" + stackIndex + "]." + identifier + " int 1" +
                        " run scoreboard players operation " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
            )
            return this
        }
    }

    override fun clone(): MCBool {
        return MCBool(this)
    }

    override fun setObj(sbObject: SbObject): MCBool {
        boolObject = sbObject
        return this
    }

    @InsertCommand
    override fun getTempVar(): MCBool {
        if (isTemp) return this
        val re = MCBool()
        re.assignedBy(this)
        return re
    }

    override fun storeToStack() {
        TODO("Not yet implemented")
    }

    override fun getFromStack() {
        TODO("Not yet implemented")
    }

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

class MCBoolConcrete : MCBool, MCFPPValue<Boolean> {

    override var value: Boolean

    /**
     * 创建一个固定的bool
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Boolean, identifier: String = UUID.randomUUID().toString()) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的bool。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Boolean, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    constructor(bool: MCBool, value: Boolean) : super(bool){
        this.value = value
    }

    constructor(v: MCBoolConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCBoolConcrete {
        return MCBoolConcrete(this)
    }

    @InsertCommand
    override fun isEqual(a: Var<*>): Var<*>? {
        when(a){
            is MCBoolConcrete -> {
                return MCBoolConcrete(value == a.value)
            }

            is MCBool -> {
                return a.isEqual(this)
            }

            else -> {
                LogProcessor.error("Unsupported operation between ${type.typeName} and ${a.type.typeName}")
                return UnknownVar("${type.typeName}_isEqual_${a.type.typeName}" + UUID.randomUUID())
            }
        }
    }

    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*>? {
        when(a){
            is MCBoolConcrete -> {
                return MCBoolConcrete(value != a.value)
            }

            is MCBool -> {
                return a.isNotEqual(this)
            }

            else -> {
                LogProcessor.error("Unsupported operation between ${type.typeName} and ${a.type.typeName}")
                return UnknownVar("${type.typeName}_isNotEqual_${a.type.typeName}" + UUID.randomUUID())
            }
        }
    }

    //取反
    @InsertCommand
    override fun negation(): Var<*> {
        value = !value
        return this
    }

    @InsertCommand
    override fun or(a: Var<*>): Var<*>? {
        when(a){
            is MCBoolConcrete -> {
                return MCBoolConcrete(value || a.value)
            }

            is MCBool -> {
                return a.or(this)
            }

            else -> {
                LogProcessor.error("Unsupported operation between ${type.typeName} and ${a.type.typeName}")
                return UnknownVar("${type.typeName}_or_${a.type.typeName}" + UUID.randomUUID())
            }
        }
    }


    @InsertCommand
    override fun and(a: Var<*>): Var<*>? {
        when(a){
            is MCBoolConcrete -> {
                return MCBoolConcrete(value && a.value)
            }

            is MCBool -> {
                return a.and(this)
            }

            else -> {
                LogProcessor.error("Unsupported operation between ${type.typeName} and ${a.type.typeName}")
                return UnknownVar("${type.typeName}_and_${a.type.typeName}" + UUID.randomUUID())
            }
        }
    }

    @InsertCommand
    override fun getTempVar(): MCBoolConcrete {
        if (isTemp) return this
        return MCBoolConcrete(value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val re = MCBool(this)
        if(replace){
            if(parentTemplate() != null){
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }
}