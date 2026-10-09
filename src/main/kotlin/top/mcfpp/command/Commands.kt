package top.mcfpp.command

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.EntityUUIDVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySource
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.Function.Companion.addCommand
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.Utils

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
            .build(a.name,a.name)
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
            .build(b.name,b.name)
            .build(b.boolObject.toString(),b.boolObject.toString())
    }

    @JvmStatic
    fun sbPlayerOperation(a: ScoreBool, operation: String, b: ScoreBool): Command {
        return Command.build("scoreboard players operation")
            .build(a.name,a.name)
            .build(a.boolObject.toString(),a.boolObject.toString())
            .build(operation,"operation")
            .build(b.name,b.name)
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
            .build(a.name,a.name)
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
                    .build("data modify").build(new.toCommandPart()).build("set value ${top.mcfpp.backend.NbtEncoding.snbt(value)}")
            }
        }
        return Command.build("data modify")
            .build(a.toCommandPart())
            .build("set value ${top.mcfpp.backend.NbtEncoding.snbt(value)}")
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
            .build("merge value ${top.mcfpp.backend.NbtEncoding.snbt(value)}")
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
            .build("append value ${top.mcfpp.backend.NbtEncoding.snbt(value)}")
    }

    @JvmStatic
    fun dataAppendFrom(a: NBTPath, b: NBTPath): Command{
        return Command.build("data modify")
           .build(a.toCommandPart())
           .build("append from")
           .build(b.toCommandPart())
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
        return createTempFunction(TempPool.getFunctionIdentify("temp"), parent, operation)
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
        return createTempFunction(TempPool.getFunctionIdentify("${prefix}_temp"), parent, operation)
    }

    private fun createTempFunction(identifier: String, parent: Function, operation: (Function) -> Unit): Pair<Command, Function> {
        val f = NoStackFunction(identifier, parent).apply { namespace = parent.namespace }
        GlobalScope.getUnsolvedImportNamespace(parent.namespace)!!.scope.addFunction(f, false)
        f.runInFunction { operation(f) }
        // Generated bodies must also be exported when their declaration namespace belongs to a library.
        f.bodyCompiled = true
        return function(f) to f
    }

    @JvmStatic
    fun internalFunction(parent: Function , operation: (fakeFunction: Function) -> Unit) : Array<Command>{
        val f = NoStackFunction("", parent).apply { scope = FunctionScope(parent.scope) }
        f.runInFunction { operation(f) }
        return f.commands.toTypedArray()
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
        val known = top.mcfpp.analysis.StorageAccess.constantEncoding(entityVar) as? IntArrayTag
        if (known != null) return arrayOf(Command("execute as ${Utils.fromNBTArrayUUID(known)} run").build(command))
        top.mcfpp.analysis.StorageAccess.materialize(entityVar)
        return arrayOf(
            Command("data modify entity ${Project.config.tempItemEntityUUID} Thrower set from").build(entityVar.nbtPath.toCommandPart()),
            Command("execute as ${Project.config.tempItemEntityUUID} on origin run").build(command)
        )
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
    fun runAsEntity(selector: top.mcfpp.core.lang.entity.SelectorVar, command: Command): Array<Command>{
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
