package top.mcfpp.mni.minecraft

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.captureCommandResult
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor

/** Typed handles reject invented rule IDs, wrong value types and unavailable target versions. */
object GameRuleData {
    internal fun constant(context: NativeCallContext, publicName: String) {
        val row = rules.first { it.getString("name") == publicName && compare(top.mcfpp.Project.config.version, it.getString("since")) >= 0 &&
            (it.getString("until")?.let { until -> compare(top.mcfpp.Project.config.version, until) < 0 } != false) }
        context.publishResult(StorageAccess.literal(context.declaredReturnType, CompilerValue.Record(mapOf(
            "id" to CompilerValue.Typed(MCFPPBaseType.String.typeId, CompilerValue.Text(row.getString("id")))))))
    }
    private val rules by lazy {
        GameRuleData::class.java.getResourceAsStream("/stdlib/game-rules.json")!!.use {
            JSON.parseObject(it.readBytes().toString(Charsets.UTF_8)).getJSONArray("rules").map { row -> row as com.alibaba.fastjson2.JSONObject }
        }
    }
    private fun compare(a: String, b: String): Int {
        val first = a.split('.').map(String::toInt); val second = b.split('.').map(String::toInt)
        for (index in 0 until maxOf(first.size, second.size)) {
            val result = (first.getOrElse(index) { 0 }).compareTo(second.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }
    private fun rule(receiver: Var<*>, context: NativeCallContext, value: Var<*>? = null): String? {
        val objectValue = (if (receiver is top.mcfpp.core.lang.MCAny) StorageAccess.actualView(receiver) else receiver) as? DataTemplateObject
        if (objectValue == null) { LogProcessor.error("Game rule access requires a GameRule value"); return null }
        val field = DataTemplate.getField(objectValue, "id") ?: return null
        val id = if (field is top.mcfpp.core.lang.PropertyVar) field.get() else field
        fun payload(v: CompilerValue?): CompilerValue? = if (v is CompilerValue.Typed) payload(v.payload) else v
        val name = (payload(StorageAccess.snapshot(id)) as? CompilerValue.Text)?.value
        val definition = rules.firstOrNull { it.getString("id") == name }
        if (definition == null || compare(Project.config.version, definition.getString("since")) < 0 ||
            definition.getString("until")?.let { compare(Project.config.version, it) >= 0 } == true) {
            LogProcessor.error("Game rule '$name' is unavailable for target '${Project.config.version}'")
            return null
        }
        val expected = if (definition.getString("type") == "bool") MCFPPBaseType.Bool else MCFPPBaseType.Int
        if ((value?.type ?: context.declaredReturnType) != expected) {
            LogProcessor.error("Game rule '$name' requires ${expected.typeName}")
            return null
        }
        return name
    }
    fun get(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val name = rule(receiver, context) ?: return@withAdapters
        if (context.declaredReturnType == MCFPPBaseType.Bool) {
            val result = ScoreBool()
            Function.addCommand("execute store result score ${result.name} ${result.boolObject} run gamerule $name")
            StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
            context.publishResult(result)
        } else {
            val result = MCInt()
            Function.addCommand("execute store result score ${result.name} ${result.sbObject} run gamerule $name")
            StorageAccess.publishScore(result, StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
            context.publishResult(result)
        }
    }
    fun set(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val name = rule(receiver, context, args[0]) ?: return@withAdapters
        if (args[0].type == MCFPPBaseType.Int) captureCommandResult(context) { Command("gamerule $name").buildMacro(args[0]) }
        else {
            val value = args[0] as ScoreBool
            fun payload(v: CompilerValue?): CompilerValue? = if (v is CompilerValue.Typed) payload(v.payload) else v
            val known = (payload(StorageAccess.snapshot(value)) as? CompilerValue.Bool)?.value
            if (known != null) {
                captureCommandResult(context) { Command("gamerule $name $known") }
                return@withAdapters
            }
            val score = StorageAccess.booleanRegister(value)
            val token = top.mcfpp.core.lang.nbt.MCString().apply { nbtPath = top.mcfpp.lib.NBTPath.stack.intIndex(0).memberIndex(identifier) }
            Function.addCommand(top.mcfpp.command.Commands.dataSetValue(token.nbtPath, top.mcfpp.nbt.tags.primitive.StringTag("false")))
            Function.addCommand(Command("execute if score ${score.player} ${score.objective} matches 1 run")
                .build(top.mcfpp.command.Commands.dataSetValue(token.nbtPath, top.mcfpp.nbt.tags.primitive.StringTag("true"))))
            StorageAccess.publishNbt(token)
            captureCommandResult(context) { Command("gamerule $name").buildMacro(token) }
        }
    }
}
