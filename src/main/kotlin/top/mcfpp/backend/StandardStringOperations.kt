package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** String operations never insert raw user text into generated command syntax. */
object StandardStringOperations {
    fun contact(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val left = text(receiver); val right = text(args[0])
        if (left != null && right != null) { context.publishResult(string(left + right)); return@withAdapters }
        if (TargetCapabilities.forVersion(Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Runtime contact requires a function-macro target")
            return@withAdapters
        }
        val root = scratch()
        val addresses = root.toCommandPart().analyze()
        emit(Commands.dataSetValue(root, top.mcfpp.nbt.tags.CompoundTag()))
        val leftPath = root.memberIndex("inputLeft"); val rightPath = root.memberIndex("inputRight")
        StorageAccess.encodeTo(leftPath, receiver); StorageAccess.encodeTo(rightPath, args[0])
        val result = MCString().apply { nbtPath = root.memberIndex("result") }
        val owner = Function.currFunction
        fun raw(command: String) {
            if (command.contains("\$(")) {
                val key = TempPool.getFunctionIdentify("string_macro")
                Project.macroFunction[key] = "\$$command"
                Function.addCommand("function mcfpp:dynamic/$key with $addresses")
            } else Function.addCommand(command)
        }
        val runtime = top.mcfpp.mni.expand.RuntimeStringConcatenation(addresses,
            { hint -> "${owner.namespace}:${TempPool.getFunctionIdentify("string_$hint")}" }, owner,
            { _ -> val value = MCInt(); "${value.name} ${value.sbObject}" }, ::raw)
        runtime.add({ raw("data modify $it set from ${leftPath.toCommandPart().analyze()}") },
            { raw("data modify $it set from ${rightPath.toCommandPart().analyze()}") }, result.nbtPath.toCommandPart().analyze())
        StorageAccess.publishNbt(result); context.publishResult(result)
    }
    private fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun text(value: Var<*>) = (payload(StorageAccess.snapshot(value)) as? CompilerValue.Text)?.value
    private fun integer(value: Var<*>) = (payload(StorageAccess.snapshot(value)) as? CompilerValue.Integral)?.value?.toInt()
    private fun string(value: String) = MCString(StringTag(value))
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())
    private fun scratch() = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
    private fun score(value: MCInt) = "${value.name} ${value.sbObject}"
    private fun length(value: Var<*>): MCInt {
        text(value)?.let { return MCInt(it.length) }
        val path = scratch()
        StorageAccess.encodeTo(path, value)
        val result = MCInt()
        emit(Command("execute store result score ${score(result)} run data get").build(path.toCommandPart()))
        return StorageAccess.publishScore(result, StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
    }
    fun length(context: NativeCallContext, empty: Boolean? = null) = context.withAdapters { receiver, _ ->
        val size = length(receiver)
        context.publishResult(if (empty == null) size else StorageAccess.binary(size, MCInt(0), if (empty) "==" else "!="))
    }
    fun substring(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val known = text(receiver); val start = integer(args[0]); val end = integer(args[1])
        if (known != null && start != null && end != null) {
            val first = start.coerceIn(0, known.length)
            context.publishResult(string(known.substring(first, end.coerceIn(first, known.length))))
            return@withAdapters
        }
        if (TargetCapabilities.forVersion(Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Runtime substring requires function macros on target '${Project.config.version}'")
            return@withAdapters
        }
        val input = scratch(); StorageAccess.encodeTo(input, receiver)
        val size = length(receiver)
        fun bound(source: Var<*>): MCInt {
            val path = scratch(); StorageAccess.encodeTo(path, source)
            val result = MCInt()
            emit(Command("execute store result score ${score(result)} run data get").build(path.toCommandPart()).build("1"))
            Function.addCommand("execute if score ${score(result)} matches ..-1 run scoreboard players set ${score(result)} 0")
            val countPath = scratch(); StorageAccess.encodeTo(countPath, size)
            val count = MCInt()
            emit(Command("execute store result score ${score(count)} run data get").build(countPath.toCommandPart()).build("1"))
            Function.addCommand("scoreboard players operation ${score(result)} < ${score(count)}")
            return StorageAccess.publishScore(result, StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
        }
        val first = bound(args[0]); val last = bound(args[1])
        Function.addCommand("scoreboard players operation ${score(last)} > ${score(first)}")
        val result = MCString().apply { nbtPath = scratch() }
        emit(Commands.dataSetValue(result.nbtPath, StringTag("")))
        emit(Command("data modify").build(result.nbtPath.toCommandPart()).build("set string").build(input.toCommandPart())
            .buildMacro(first).buildMacro(last))
        StorageAccess.publishNbt(result); context.publishResult(result)
    }
    fun equal(context: NativeCallContext, negate: Boolean) = context.withAdapters { receiver, args ->
        val left = text(receiver); val right = text(args[0])
        if (left != null && right != null) { context.publishResult(ScoreBool((left == right) != negate)); return@withAdapters }
        val path = scratch(); StorageAccess.encodeTo(path, receiver)
        val other = scratch(); StorageAccess.encodeTo(other, args[0])
        val changed = ScoreBool()
        emit(Command("execute store success score ${changed.name} ${changed.boolObject} run data modify")
            .build(path.toCommandPart()).build("set from").build(other.toCommandPart()))
        StorageAccess.publishBoolean(changed, StorageLayout.Scoreboard(changed.name, changed.boolObject.toString()))
        context.publishResult(if (negate) changed else StorageAccess.binary(changed, ScoreBool(false), "=="))
    }
    fun constant(context: NativeCallContext, operation: String) = context.withAdapters { receiver, args ->
        val source = text(receiver)
        val texts = args.map(::text)
        if (source == null || texts.any { it == null }) {
            LogProcessor.error("string.$operation requires compile-time string values; use length/substring/contact for runtime strings")
            return@withAdapters
        }
        val needle = texts.firstOrNull().orEmpty()
        val result: Var<*> = when (operation) {
            "contains" -> ScoreBool(source.contains(needle))
            "startsWith" -> ScoreBool(source.startsWith(needle))
            "endsWith" -> ScoreBool(source.endsWith(needle))
            "indexOf" -> MCInt(source.indexOf(needle))
            "replace" -> string(source.replace(needle, texts[1]!!))
            "split" -> StorageAccess.listLiteral(MCFPPListType(MCFPPBaseType.String), source.split(needle).map(::string))
            "trim" -> string(source.trim())
            "lowercase" -> string(source.map { if (it in 'A'..'Z') it + 32 else it }.joinToString(""))
            "uppercase" -> string(source.map { if (it in 'a'..'z') it - 32 else it }.joinToString(""))
            else -> error("Unknown string operation $operation")
        }
        context.publishResult(result)
    }
}
