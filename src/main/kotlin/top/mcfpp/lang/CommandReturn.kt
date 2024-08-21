package top.mcfpp.lang

import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import java.util.*

class CommandReturn : Var<CommandReturn> {

    /**
     * 创建一个string类型的变量。它的mc名和变量所在的域容器有关。
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
     * 创建一个string值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = UUID.randomUUID().toString()) : super(identifier)

    /**
     * 复制一个string
     * @param b 被复制的string值
     */
    constructor(b: MCString) : super(b)

    override fun doAssign(b: Var<*>): CommandReturn {
        TODO("Not yet implemented")
    }

    override fun clone(): CommandReturn {
        TODO("Not yet implemented")
    }

    override fun getTempVar(): CommandReturn {
        TODO("Not yet implemented")
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

    companion object {

    }

}

class CommandSuccess: Var<CommandSuccess>{

    constructor(re: CommandReturn): super("success"){

    }

    override fun doAssign(b: Var<*>): CommandSuccess {
        TODO("Not yet implemented")
    }

    override fun clone(): CommandSuccess {
        TODO("Not yet implemented")
    }

    override fun getTempVar(): CommandSuccess {
        TODO("Not yet implemented")
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

}

class CommandResult: Var<CommandResult>{

    constructor(re: CommandReturn): super("result"){

    }

    override fun doAssign(b: Var<*>): CommandResult {
        TODO("Not yet implemented")
    }

    override fun clone(): CommandResult {
        TODO("Not yet implemented")
    }

    override fun getTempVar(): CommandResult {
        TODO("Not yet implemented")
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

}