package top.mcfpp.command

import top.mcfpp.Project
import top.mcfpp.core.lang.CommandReturn
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.entity.EntityVar
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.EntityUUIDVar
import top.mcfpp.core.lang.nbt.EntityUUIDVarConcrete
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.core.lang.obj.ObjectVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySource
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.ObjectClass
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.Function.Companion.addCommand
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.Utils
import top.mcfpp.util.ValueWrapper

/**
 * 命令总类，提供了大量用于生成命令的方法。默认提供了一些可替换的位点
 */
object Commands {

    /**
     * `function <function.namespaceID>`
     *
     * @param function 函数对象
     * @return 生成的命令
     */
    @JvmStatic
    fun function(function: Function): Command {
        return Command.build("function").build(function.namespaceID.toString(),function.namespaceID.toString())
    }

    /**
     * `scoreboard players get <target.name> <target.object>`
     *
     * @param target 被获取计分板分数的目标
     * @return 生成的命令
     */
    @JvmStatic
    fun sbPlayerGet(target: MCInt): Command{
        return Command.build("scoreboard players get")
            .build(target.name,target.name)
            .build(target.sbObject.toString(),target.sbObject.toString())
    }

    /**
     * `scoreboard players add <target.name> <target.object.toString()> value`
     *
     * @param target 被操作的对象
     * @param value 增加的值
     * @return 生成的命令
     */
    @JvmStatic
    fun sbPlayerAdd(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players add")
            .build(target.name, target.name)
            .build(target.sbObject.toString(), target.sbObject.toString())
            .build("$value")
    }

    /**
     * `scoreboard players operation <a.name> <a.object.toString()> <operation> <b.name> <b.object.toString()>`
     *
     * @param a
     * @param operation 操作的字符串，必须为`=`,`<>`, `+=`, `-=`, `*=`, `/=`, `%=`之一。
     * @param b
     * @return 生成的命令
     */
    @JvmStatic
    fun sbPlayerOperation(a: MCInt, operation: String, b: MCInt): Command {
        return Command.build("scoreboard players operation")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(operation,"operation")
            .build(b.name,b.name)
            .build(b.sbObject.toString(),b.sbObject.toString())
    }

    @JvmStatic
    fun sbPlayerOperation(a: ScoreBool, operation: String, b: MCInt): Command {
        return Command.build("scoreboard players operation")
            .build(a.identifier,a.identifier)
            .build(a.boolObject.toString(),a.boolObject.toString())
            .build(operation,"operation")
            .build(b.name,b.name)
            .build(b.sbObject.toString(),b.sbObject.toString())
    }

    @JvmStatic
    fun sbPlayerOperation(a: MCInt, operation: String, b: ScoreBool): Command {
        return Command.build("scoreboard players operation")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(operation,"operation")
            .build(b.identifier,b.identifier)
            .build(b.boolObject.toString(),b.boolObject.toString())
    }

    @JvmStatic
    fun sbPlayerOperation(a: ScoreBool, operation: String, b: ScoreBool): Command {
        return Command.build("scoreboard players operation")
            .build(a.identifier,a.identifier)
            .build(a.boolObject.toString(),a.boolObject.toString())
            .build(operation,"operation")
            .build(b.identifier,b.identifier)
            .build(b.boolObject.toString(),b.boolObject.toString())
    }

    /**
     * `scoreboard players remove <target.name> <target.object.toString()> value`
     *
     * @param target 被操作的对象
     * @param value 减少的值
     * @return 生成的命令
     */
    @JvmStatic
    fun sbPlayerRemove(target: MCInt, value: Int): Command {
        return Command.build("scoreboard players remove")
            .build(target.name, target.name)
            .build(target.sbObject.toString(), target.sbObject.toString())
            .build(value.toString())
    }

    /**
     * `scoreboard players set <a.name> <a.object.toString()> value`
     *
     * @param a 被设置的对象
     * @param value 设置的值
     *
     * @return 生成的命令
     */
    @JvmStatic
    fun sbPlayerSet(a: MCInt, value: Int): Command {
        return Command.build("scoreboard players set")
            .build(a.name,a.name)
            .build(a.sbObject.toString(),a.sbObject.toString())
            .build(value.toString())
    }

    @JvmStatic
    fun sbPlayerSet(a: ScoreBool, value: Boolean): Command {
        return Command.build("scoreboard players set")
            .build(a.identifier,a.identifier)
            .build(a.boolObject.toString(),a.boolObject.toString())
            .build((if(value) 1 else 0).toString())
    }

    @JvmStatic
    fun ifScoreMatches(a: MCInt, value: Int): Command {
        return Command.build("execute if score ${a.name} ${a.sbObject} matches $value run")
    }

    @JvmStatic
    fun unlessScoreMatches(a: MCInt, value: Int): Command {
        return Command.build("execute unless score ${a.name} ${a.sbObject} matches $value run")
    }

    @JvmStatic
    fun ifBoolMatches(a: ScoreBool, value: Boolean): Command {
        return Command.build("execute if score ${a.name} ${a.boolObject} matches ${if(value) 1 else 0} run")
    }

    @JvmStatic
    fun unlessBoolMatches(a: ScoreBool, value: Boolean): Command {
        return Command.build("execute unless score ${a.name} ${a.boolObject} matches ${if(value) 1 else 0} run")
    }

    /**
     * `data get <a>`
     */
    @JvmStatic
    fun dataGet(a: NBTPath, double: Double = 1.0): Command{
        return Command("data get")
            .build(a.toCommandPart())
            .build(double.toString())
    }

    /**
     * `data modify <a> set value <value>`
     *
     * @param a 被设置的nbt的路径
     * @param value 设置的值
     *
     * @return 生成的命令
     */
    @JvmStatic
    fun dataSetValue(a: NBTPath, value: Tag<*>): Command{
        if(a.source is EntitySource){
            val selector = (a.source as EntitySource).entity.value
            if(!selector.selectingSingleEntity()){
                val new = a.clone()
                new.source = EntitySource(SelectorVar(EntitySelector('s')))
                return Command.build("execute as").build(selector.toCommandPart()).build("run")
                    .build("data modify").build(new.toCommandPart()).build("set value ${Tag.toSNBT(value)}")
            }
        }
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set value ${Tag.toSNBT(value)}")
    }

    /**
     * `data modify <a> set from <b>`
     *
     * @param a 被设置的nbt的路径
     * @param b 从哪里获取值
     *
     * @return 生成的命令
     */
    @JvmStatic
    fun dataSetFrom(a: NBTPath, b: NBTPath): Command{
        if(b.source is EntitySource && !(b.source as EntitySource).entity.value.selectingSingleEntity()){
            LogProcessor.error("Can only select single Entity")
        }
        if(a.source is EntitySource){
            val selector = (a.source as EntitySource).entity.value
            if(!selector.selectingSingleEntity()){
                val new = a.clone()
                new.source = EntitySource(SelectorVar(EntitySelector('s')))
                return Command.build("execute as").build(selector.toCommandPart()).build("run")
                    .build("data modify").build(new.toCommandPart()).build("set from ${b.toCommandPart()}")
            }
        }
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set from")
            .build(b.toCommandPart())
    }

    /**
     * `data modify <a> merge value <value>`
     *
     * @param a 被设置的nbt的路径
     * @param value 设置的值
     *
     * @return 生成的命令
     */
    @JvmStatic
    fun dataMergeValue(a: NBTPath, value: Tag<*>): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("merge value ${Tag.toSNBT(value)}")
    }

    @JvmStatic
    fun dataMergeFrom(a: NBTPath, b: NBTPath): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("merge from")
            .build(b.toCommandPart())
    }

    @JvmStatic
    fun dataAppendValue(a: NBTPath, value: Tag<*>): Command{
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("append value ${Tag.toSNBT(value)}")
    }

    @JvmStatic
    fun dataAppendFrom(a: NBTPath, b: NBTPath): Command{
        return Command.build("data modify")
           .build(a.toCommandPart())
           .build("append from")
           .build(b.toCommandPart())
    }

    /**
     * 输入一个变量，判断这个变量是否是类的成员从而选择正确的nbt路径
     */
    @JvmStatic
    private fun adjustCommandForParent(v: Var<*>, command: Command): Array<Command>{
        return if(v.parentClass() != null){
            selectRun(v.parent!!, command)
        }else{
            arrayOf(command)
        }
    }

    /**
     * 输入一个变量，判断这个变量是否是类的成员从而选择正确的nbt路径。同时构建输入命令的宏函数（若为宏命令）并把调用宏函数的
     * 命令作为selectRun的输入命令
     */
    @JvmStatic
    fun buildMacroAdjustedCommands(v: Var<*>, command: Command): Array<Command>{
        val cs = command.buildMacroFunction()
        val last = cs.last()
        val qwq = adjustCommandForParent(v, last)
        return cs.dropLast(1).toTypedArray() + qwq
    }

    /**
     * 判断一条命令是否为宏函数，并让这个命令作为返回值
     */
    @JvmStatic
    fun processMacroCommandReturn(returnVar: ValueWrapper<CommandReturn>, command: Command){
        if (command.isMacro) {
            command.prepend("return run")
            val commandArray = command.buildMacroFunction()
            returnVar.value = CommandReturn(commandArray[commandArray.size - 1], "return")
            for (i in 0..<commandArray.size - 1) {
                addCommand(commandArray[i])
            }
        } else {
            returnVar.value = CommandReturn(command, "return")
        }
    }

    @JvmStatic
    fun processMacroCommand(command: Command){
        if (command.isMacro) {
            command.prepend("return run")
            val commandArray = command.buildMacroFunction()
            for (i in 0..<commandArray.size - 1) {
                addCommand(commandArray[i])
            }
        } else {
            addCommand(command)
        }
    }

    /**
     * 以一个类的对象为执行者，执行一个命令。
     *
     * @param a 执行者
     * @param command 要执行的命令
     * @param hasExecuteRun 是否在execute命令串和要执行的命令之间插入run
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    @JvmStatic
    fun selectRun(a : CanSelectMember, command: Command, hasExecuteRun: Boolean = true) : Array<Command>{
        val qwq = selectRun(a, hasExecuteRun)
        qwq.last().build(command)
        return qwq
    }

    /**
     * 以一个类的对象为执行者，构建一个`execute`命令串，可以继续向后构建命令
     *
     * @param a 执行者
     * @param hasExecuteRun 是否在execute命令串之后添加run
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    @JvmStatic
    fun selectRun(a : CanSelectMember, hasExecuteRun: Boolean = true) : Array<Command>{
        val final = when(a){
            is ClassPointer -> {
                if(a.identifier == "this"){
                    return arrayOf(Command())
                }
                val qwq = if(a.clazz.baseEntity != Class.ENTITY_MARKER && a.clazz.baseEntity != Class.ENTITY_ITEM_DISPLAY){
                    arrayOf(
                        Command.build("data modify entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system stack_frame[${a.stackIndex}].${a.identifier}"),
                        Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin on passengers as @n[tag=${a.tag}_data]")
                    )
                }else{
                    arrayOf(
                        Command.build("data modify entity ${ClassPointer.tempItemEntityUUID} Thrower set from storage mcfpp:system stack_frame[${a.stackIndex}].${a.identifier}"),
                        Command.build("execute as ${ClassPointer.tempItemEntityUUID} on origin")
                    )
                }
                if(hasExecuteRun) {
                    qwq.last().build("run","run")
                }
                qwq
            }
            is ObjectVar -> selectRun(a.value, hasExecuteRun)
            is MCFPPClassType -> {
                if(a.cls is ObjectClass){
                    if(hasExecuteRun){
                        arrayOf(Command.build("execute as ${(a.cls as ObjectClass).mcuuid.uuid}").build("run", "run"))
                    }else{
                        arrayOf(Command.build("execute as ${(a.cls as ObjectClass).mcuuid.uuid}"))
                    }
                }else if(a.cls.objectClass != null){
                    if(hasExecuteRun){
                        arrayOf(Command.build("execute as ${a.cls.objectClass!!.mcuuid.uuid}").build("run", "run"))
                    }else{
                        arrayOf(Command.build("execute as ${a.cls.objectClass!!.mcuuid.uuid}"))
                    }
                } else {
                    if(hasExecuteRun){
                        arrayOf(Command.build("#execute as [Error: No object class ${a.cls.namespaceID}]").build("run", "run"))
                    }else{
                        arrayOf(Command.build("#execute as [Error: No object class ${a.cls.namespaceID}]}"))
                    }
                }
            }
            else -> TODO()
        }
        return final
    }

    /**
     * [selectRun]的简化版本，直接传入一个字符串
     *
     * @param a 执行者
     * @param command 要执行的命令
     * @param hasExecuteRun 是否在execute命令串和要执行的命令之间插入run
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    @JvmStatic
    fun selectRun(a : CanSelectMember, command: String, hasExecuteRun: Boolean = true) : Array<Command>{
        return selectRun(a, Command.build(command), hasExecuteRun)
    }

    /**
     * 提供一个沙箱函数环境，在此函数中执行一些操作并捕获生成在此函数的命令并将其返回。
     *
     * @param parent 沙箱函数的父函数，用于控制作用域
     * @param operation 在此沙箱函数中执行的操作。lambda表达式的参数为此沙箱函数
     *
     * @return 捕获的命令
     */
    @JvmStatic
    fun fakeFunction(parent: Function , operation: (fakeFunction: Function) -> Unit) : Array<Command>{
        val l = Function.currFunction
        val f = NoStackFunction("", parent)
        Function.currFunction = f
        operation(f)
        Function.currFunction = l
        return f.commands.toTypedArray()
    }

    /**
     * 创建一个临时函数，可以在此函数中执行一些操作，命令将会生成在此临时函数中。
     *
     * @param parent 临时函数的父函数，用于控制作用域
     * @param operation 在此临时函数中执行的操作。lambda表达式的参数为此临时函数
     *
     * @return 生成的调用临时函数的命令和这个临时函数
     */
    @JvmStatic
    fun tempFunction(parent: Function, operation: (tempFunction: Function) -> Unit) : Pair<Command, Function>{
        val l = Function.currFunction
        val f = NoStackFunction(TempPool.getFunctionIdentify("temp"), parent)
        GlobalField.localNamespaces[Project.currNamespace]!!.field.addFunction(f, false)
        Function.currFunction = f
        operation(f)
        Function.currFunction = l
        return function(f) to f
    }

    /**
     * 创建一个临时函数，可以在此函数中执行一些操作，命令将会生成在此临时函数中。
     *
     * @param prefix 生成的临时函数的额外前缀
     * @param parent 临时函数的父函数，用于控制作用域
     * @param operation 在此临时函数中执行的操作。lambda表达式的参数为此临时函数
     *
     * @return 生成的调用临时函数的命令和这个临时函数
     */
    @JvmStatic
    fun tempFunction(prefix: String, parent: Function, operation: (tempFunction: Function) -> Unit) : Pair<Command, Function>{
        val l = Function.currFunction
        val f = NoStackFunction(TempPool.getFunctionIdentify("${prefix}_temp"), parent)
        GlobalField.localNamespaces[Project.currNamespace]!!.field.addFunction(f, false)
        Function.currFunction = f
        operation(f)
        Function.currFunction = l
        return function(f) to f
    }

    /**
     * 以一个实体为执行者，执行一个命令
     *
     * @param entityVar 实体
     * @param command 要执行的命令
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    @JvmStatic
    fun runAsEntity(entityVar: EntityUUIDVar, command: Command): Array<Command>{
        return if(entityVar is EntityUUIDVarConcrete){
            if(!entityVar.isName){
                arrayOf(Command("execute as ${Utils.fromNBTArrayUUID(entityVar.value as IntArrayTag)} run").build(command))
            }else{
                arrayOf(Command("execute as ${(entityVar.value as StringTag).value} run").build(command))
            }
        }else{
            if(!entityVar.isName){
                arrayOf(
                    Command("data modify entity ${ClassPointer.tempItemEntityUUID} Thrower set from").build(entityVar.nbtPath.toCommandPart()),
                    Command("execute as ${ClassPointer.tempItemEntityUUID} on origin run").build(command)
                )
            }else{
                Command("execute as").buildMacro(entityVar).build("run").build(command).buildMacroFunction()
            }
        }
    }

    /**
     * 以一个选择器为执行者，执行一个命令
     *
     * @param selector 选择器
     * @param command 要执行的命令
     *
     * @return 生成的命令。数组的最后一个命令为`execute`命令
     */
    @JvmStatic
    fun runAsEntity(selector: EntityVar, command: Command): Array<Command>{
        val c = Command("execute as").build(selector.toCommandPart()).build("run").build(command)
        return if(c.isMacro){
            c.buildMacroFunction()
        }else{
            arrayOf(c)
        }
    }

    @JvmStatic
    fun stackIn(): Command{
        return Command("data modify storage mcfpp:system stack_frame prepend value {}")
    }

    @JvmStatic
    fun stackOut(): Command {
        return Command("data remove storage mcfpp:system stack_frame[0]")
    }

    fun Array<Command>.buildMacroFunction(): Array<Command>{
        val re = ArrayList<Command>()
        for (c in this){
            re.addAll(c.buildMacroFunction())
        }
        return re.toTypedArray()
    }
}