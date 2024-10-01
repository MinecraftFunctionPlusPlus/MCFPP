package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import java.util.*

class CommandReturn : Var<CommandReturn> {

    val result: CommandResult

    val success: CommandSuccess

    val command: Command

    override var type: MCFPPType = MCFPPBaseType.CommandReturn

    /**
     * 创建一个string类型的变量。它的mc名和变量所在的域容器有关。
     *
     * @param identifier 标识符。默认为
     */
    constructor(
        command: Command,
        curr: FieldContainer,
        identifier: String = UUID.randomUUID().toString()
    ) : this(command, curr.prefix + identifier) {
        this.identifier = identifier
    }

    /**
     * 创建一个string值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(command: Command, identifier: String = UUID.randomUUID().toString()) : super(identifier){
        this.result = CommandResult(this)
        this.success = CommandSuccess(this)
        this.command = command
    }

    /**
     * 复制一个string
     * @param b 被复制的string值
     */
    constructor(b: CommandReturn) : super(b){
        this.command = b.command
        this.result = b.result
        this.success = b.success
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.CommandReturn -> this
            else -> buildCastErrorVar(type)
        }
    }

    override fun doAssignedBy(b: Var<*>): CommandReturn = this

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): CommandReturn = CommandReturn(this)

    override fun getTempVar(): CommandReturn = this

    override fun storeToStack() {
        result.storeToStack()
        success.storeToStack()
    }

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = Pair(null, true)

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyParams, normalParams) to true
    }

    companion object {
        val data = CompoundData("CommandReturn","mcfpp")

        init {
            data.extends(MCAny.data)
        }
    }

}

class CommandSuccess(val re: CommandReturn) : Var<CommandSuccess>("success") {

    override var nbtPath: NBTPath
        get() {
            return re.nbtPath.memberIndex("success")
        }
        set(value) {}

    override fun doAssignedBy(b: Var<*>): CommandSuccess = this

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): CommandSuccess = this

    override fun getTempVar(): CommandSuccess = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = Pair(null, true)

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> = Pair(UnknownFunction(key), true)

}

class CommandResult(val re: CommandReturn): Var<CommandResult>("result"){

    override var nbtPath: NBTPath
        get() {
            return re.nbtPath.memberIndex("result")
        }
        set(value) {}

    override fun doAssignedBy(b: Var<*>): CommandResult = this

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): CommandResult = this

    override fun getTempVar(): CommandResult = this

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return Pair(null, true)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return Pair(UnknownFunction(key), true)
    }

}