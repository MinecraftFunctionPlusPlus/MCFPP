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
open class ScoreBool : BaseBool, OnScoreboard {
    
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
    constructor(b: ScoreBool) : super(b)

    override var type: MCFPPType = MCFPPBaseType.Bool

    @Override
    override fun doAssignedBy(b: Var<*>) : ScoreBool {
        when(b){
            is ScoreBool -> return assignCommand(b)

            is ExecuteBool -> {
                if(parentClass() != null){
                    Function.addCommands(
                        Commands.selectRun(parent!!, Command("store result score @s $boolObject").build(b.toCommandPart()), false)
                    )
                }else{
                    Function.addCommand(
                        Command.build("execute store result score $name $boolObject").build(b.toCommandPart())
                    )
                }
                return this
            }

            is BaseBool -> {
                if(parentClass() != null){
                    Function.addCommands(
                        Commands.selectRun(parent!!, Command("store result score @s $boolObject if").build(b.toCommandPart()), false)
                    )
                }else{
                    Function.addCommand(
                        Command.build("execute store result score $name $boolObject if").build(b.toCommandPart())
                    )
                }
                return this
            }

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
                return this
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
            is ScoreBool -> true
            is CommandReturn -> true
            else -> false
        }
    }

    override fun isEqual(a: Var<*>): Var<*>? {
        //re = t == a
        val re = ScoreBool()
        when(a){
            is ScoreBoolConcrete -> {
                //execute store success score qwq qwq if score qwq qwq = owo owo
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " if score " + name + " " + boolObject + " matches " + if (a.value) 1 else 0
                )
            }

            is ScoreBool -> {
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " if score " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
                )
            }

            is BaseBool -> {
                return isEqual(a.toScoreBool())
            }

            else -> return null
        }
        return re
    }

    @InsertCommand
    override fun isNotEqual(a: Var<*>): Var<*>? {
        //re = t != a
        val re = ScoreBool()
        when(a){
            is ScoreBoolConcrete -> {
                //execute store success score qwq qwq if score qwq qwq = owo owo
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " unless score " + name + " " + boolObject + " matches " + if (a.value) 1 else 0
                )
            }

            is ScoreBool -> {
                Function.addCommand(
                    "execute store success score " + re.name + " " + re.boolObject
                            + " unless score " + name + " " + boolObject + " = " + a.name + " " + a.boolObject
                )
            }

            is BaseBool -> {
                return isNotEqual(a.toScoreBool())
            }

            else -> return null
        }
        return re
    }

    @InsertCommand
    override fun negation(): Var<*> {
        return ExecuteBool(this, true)
    }

    @InsertCommand
    override fun or(a: Var<*>): Var<*>? {
        if(a is ScoreBoolConcrete){
            return if(a.value){
                ScoreBoolConcrete(this, true)
            }else{
                this
            }
        }
        return ExecuteBool(this).or(a)
    }

    @InsertCommand
    override fun and(a: Var<*>): Var<*>? {
        if(a is ScoreBoolConcrete ){
            return if(!a.value){
                ScoreBoolConcrete(this, false)
            }else{
                this
            }
        }
        return ExecuteBool(this).and(a)
    }

    @InsertCommand
    private fun assignCommand(a: ScoreBool) : ScoreBool {
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete =  { b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                Function.addCommand(final.last().build(Commands.sbPlayerSet(this, (b as ScoreBoolConcrete).value)))
                this
            },
            ifThisIsClassMemberAndAIsNotConcrete = { b, final ->
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                Function.addCommand(final.last().build(Commands.sbPlayerOperation(this,"=",b as ScoreBool)))
                this
            },
            ifThisIsNormalVarAndAIsConcrete = { b, _ ->
                ScoreBoolConcrete(this, (b as ScoreBoolConcrete).value)
            },
            ifThisIsNormalVarAndAIsClassMember = { c, cmd ->
                if(cmd.size == 2){
                    Function.addCommand(cmd[0])
                }
                Function.addCommand(cmd.last().build(Commands.sbPlayerOperation(this, "=", c as ScoreBool)))
                ScoreBool(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = { c, _ ->
                //变量进栈
                Function.addCommand(Commands.sbPlayerOperation(this, "=", c as ScoreBool))
                ScoreBool(this)
            }
        ) as ScoreBool
    }

    override fun clone(): ScoreBool {
        return ScoreBool(this)
    }

    override fun setObj(sbObject: SbObject): ScoreBool {
        boolObject = sbObject
        return this
    }

    @InsertCommand
    override fun getTempVar(): ScoreBool {
        if (isTemp) return this
        val re = ScoreBool()
        re.assignedBy(this)
        return re
    }

    override fun storeToStack() {
        if(parentClass() != null || hasStoredInStack) return
        Function.addCommand("execute " +
                "store result $nbtPath int 1 " +
                "run scoreboard players get $name $boolObject")
        hasStoredInStack = true
    }

    override fun getFromStack() {
        if(parent != null) return
        Function.addCommand("execute " +
                "store result score $name $boolObject " +
                "run data get $nbtPath")
    }

    override fun toCommandPart(): Command {
        return Command("score $name $boolObject matches 1")
    }

    override fun toScoreBool(): ScoreBool = this

    override fun toNBTVar(): NBTBasedData {
        val n = NBTBasedData()
        n.name = name
        n.identifier = identifier
        n.isStatic = isStatic
        n.accessModifier = accessModifier
        n.isTemp = isTemp
        n.stackIndex = stackIndex
        n.isConst = isConst
        n.nbtPath = nbtPath.clone()
        return n
    }

    open fun asIntVar(): MCInt{
        val n = MCInt()
        n.name = name
        n.identifier = identifier
        n.isStatic = isStatic
        n.accessModifier = accessModifier
        n.isTemp = isTemp
        n.stackIndex = stackIndex
        n.isConst = isConst
        n.nbtPath = nbtPath.clone()
        n.setObj(boolObject)
        return n
    }

    companion object{
        val data = CompoundData("bool","mcfpp")
    }
}

class ScoreBoolConcrete : ScoreBool, MCFPPValue<Boolean> {

    override var value: Boolean

    /**
     * 创建一个固定的bool
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(curr: FieldContainer, value: Boolean, identifier: String = UUID.randomUUID().toString()) : super(curr, identifier) {
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

    constructor(bool: ScoreBool, value: Boolean) : super(bool){
        this.value = value
    }

    constructor(v: ScoreBoolConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): ScoreBoolConcrete {
        return ScoreBoolConcrete(this)
    }

    @InsertCommand
    override fun isEqual(a: Var<*>): Var<*>? {
        when(a){
            is ScoreBoolConcrete -> {
                return ScoreBoolConcrete(value == a.value)
            }

            is BaseBool -> {
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
            is ScoreBoolConcrete -> {
                return ScoreBoolConcrete(value != a.value)
            }

            is BaseBool -> {
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
            is ScoreBoolConcrete -> {
                return ScoreBoolConcrete(value || a.value)
            }

            is BaseBool -> {
                if(value) return ScoreBoolConcrete(this,true)
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
            is ScoreBoolConcrete -> {
                return ScoreBoolConcrete(value && a.value)
            }

            is BaseBool -> {
                if(!value) return ScoreBoolConcrete(this,false)
                return a.and(this)
            }

            else -> {
                LogProcessor.error("Unsupported operation between ${type.typeName} and ${a.type.typeName}")
                return UnknownVar("${type.typeName}_and_${a.type.typeName}" + UUID.randomUUID())
            }
        }
    }

    @InsertCommand
    override fun getTempVar(): ScoreBoolConcrete {
        if (isTemp) return this
        return ScoreBoolConcrete(value)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = parent

        if (parentClass() != null) {
            val cmd = Commands.selectRun(parent!!, "scoreboard players set @s $boolObject $value")
            Function.addCommands(cmd)
        } else {
            Function.addCommand("scoreboard players set $name $boolObject $value")
        }
        val re = ScoreBool(this)
        if(replace){
            replacedBy(re)
        }
        return re
    }

    override fun asIntVar(): MCInt {
        val n = MCIntConcrete(super.asIntVar() , if(value) 1 else 0)
        return n
    }
}