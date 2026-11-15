package top.mcfpp.backend

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.JsonText
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava

/** Value builders copy NBT fields rather than interpolate user strings into command syntax. */
object StandardTextOperations {
    val modernEvents: Boolean get() = TargetCapabilities.forVersion(Project.config.version)?.let {
        it.packFormat != null || (it.legacyPackFormat ?: 0) >= 71
    } == true

    private fun copy(tag: Tag<*>): Tag<*> = NbtEncoding.parse(NbtEncoding.snbt(tag))
    private fun literal(root: Tag<*>): JsonText = StorageAccess.literal(MCFPPBaseType.JsonText,
        CompilerValue.Nbt(NbtEncoding.snbt(if (root is ListTag) root else ListTag().apply { add(root) }))) as JsonText
    private fun leaf(value: String = "") = CompoundTag("type" to StringTag("text"), "text" to StringTag(value))
    fun empty(): JsonText = literal(leaf())
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())
    private fun field(result: JsonText, name: String) = StorageAccess.ensure(result).path.intIndex(0).memberIndex(name)
    private fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun knownString(value: Var<*>) = (payload(StorageAccess.snapshot(value)) as? CompilerValue.Text)?.value

    fun json(tag: Tag<*>): String {
        val flags = setOf("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret")
        fun value(tag: Tag<*>, key: String? = null): Any = when (tag) {
            is CompoundTag -> tag.value.mapValues { (name, child) -> value(child, name) }
            is ListTag -> tag.map { value(it) }
            else -> tag.toJava().let { if (key in flags && it is Number) it.toInt() != 0 else it }
        }
        return JSON.toJSONString(value(tag))
    }

    fun toJson(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val tag = StorageAccess.constantEncoding(receiver)
        if (tag == null) LogProcessor.error("text.toJson requires compile-time text; runtime text is already stored as structured NBT")
        else context.publishResult(MCString(StringTag(json(tag))))
    }

    fun of(context: NativeCallContext) = context.withArguments { args ->
        val value = args[0]
        val known = payload(StorageAccess.snapshot(value))
        val text = when (known) {
            is CompilerValue.Text -> known.value
            is CompilerValue.Integral -> known.value.toString()
            is CompilerValue.Bool -> known.value.toString()
            else -> null
        }
        if (text != null) { context.publishResult(literal(leaf(text))); return@withArguments }
        val result = empty()
        StorageAccess.materialize(result)
        when (value.type) {
            MCFPPBaseType.String -> StorageAccess.encodeTo(field(result, "text"), value)
            MCFPPBaseType.Bool -> {
                val score = StorageAccess.booleanRegister(value as ScoreBool)
                emit(Commands.dataSetValue(field(result, "text"), StringTag("false")))
                emit(Command("execute if score ${score.player} ${score.objective} matches 1 run")
                    .build(Commands.dataSetValue(field(result, "text"), StringTag("true"))))
            }
            MCFPPBaseType.Int -> {
                val target = TargetCapabilities.forVersion(Project.config.version)
                if (target == null || target.legacyPackFormat?.let { it < 12 } == true) {
                    LogProcessor.error("Text.of(runtime int) requires Minecraft 1.19.4 or newer; use Text.score for a live score component")
                    return@withArguments
                }
                val scratch = NBTPath.stack.intIndex(0).memberIndex(top.mcfpp.util.TempPool.getVarIdentify())
                StorageAccess.encodeTo(scratch, value)
                emit(Command("data modify").build(field(result, "text").toCommandPart()).build("set string").build(scratch.toCommandPart()))
            }
            else -> error("Unsupported Text.of type")
        }
        StorageAccess.publishNbt(result); context.publishResult(result)
    }

    fun component(context: NativeCallContext, kind: String, names: List<String>) = context.withArguments { args ->
        val base = CompoundTag("type" to StringTag(kind))
        if (args.indices.all { StorageAccess.constantEncoding(args[it]) != null }) {
            names.forEachIndexed { index, key -> base.put(key, copy(StorageAccess.constantEncoding(args[index])!!)) }
            context.publishResult(literal(base)); return@withArguments
        }
        val result = literal(base)
        StorageAccess.materialize(result)
        names.forEachIndexed { index, key -> StorageAccess.encodeTo(field(result, key), args[index]) }
        StorageAccess.publishNbt(result); context.publishResult(result)
    }

    fun score(context: NativeCallContext) = nestedComponent(context, "score", "score", listOf("name", "objective"))
    fun nbtStorage(context: NativeCallContext) = component(context, "nbt", listOf("storage", "nbt", "interpret"))
    private fun nestedComponent(context: NativeCallContext, kind: String, key: String, names: List<String>) = context.withArguments { args ->
        if (args.all { StorageAccess.constantEncoding(it) != null }) {
            val fields = CompoundTag()
            names.forEachIndexed { index, name -> fields.put(name, copy(StorageAccess.constantEncoding(args[index])!!)) }
            context.publishResult(literal(CompoundTag("type" to StringTag(kind), key to fields)))
            return@withArguments
        }
        val result = literal(CompoundTag("type" to StringTag(kind), key to CompoundTag()))
        StorageAccess.materialize(result)
        names.forEachIndexed { index, name -> StorageAccess.encodeTo(field(result, key).memberIndex(name), args[index]) }
        StorageAccess.publishNbt(result); context.publishResult(result)
    }

    fun style(context: NativeCallContext, key: String, enabled: Boolean? = null) = context.withAdapters { receiver, args ->
        val value = if (enabled != null) ScoreBool(enabled) else args[0]
        if (key == "color") {
            val color = knownString(value)
            if (color == null || !validColor(color)) {
                LogProcessor.error("text.color requires a compile-time Minecraft color name or #RRGGBB")
                return@withAdapters
            }
        }
        if (key == "font" && listOf("1.13", "1.14", "1.15").any { Project.config.version.startsWith(it) }) {
            LogProcessor.error("text.font requires Minecraft 1.16 or newer"); return@withAdapters
        }
        context.publishResult(withField(receiver as JsonText, key, value))
    }

    fun colorRgb(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val rgb = (payload(StorageAccess.snapshot(args[0])) as? CompilerValue.Integral)?.value
        if (rgb == null || rgb !in 0..0xffffff) { LogProcessor.error("text.color requires a compile-time RGB integer in 0..0xffffff"); return@withAdapters }
        context.publishResult(withField(receiver as JsonText, "color", MCString(StringTag("#${rgb.toString(16).padStart(6, '0')}"))))
    }

    private val colors = setOf("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white")
    private fun validColor(value: String) = value in colors || value.matches(Regex("#[0-9a-fA-F]{6}"))

    fun readStyle(source: JsonText, key: String): Var<*> {
        val fallback: Var<*> = when (key) {
            "color" -> MCString(StringTag("white"))
            "font" -> MCString(StringTag("minecraft:default"))
            "insertion" -> MCString(StringTag(""))
            else -> ScoreBool(false)
        }
        val components = StorageAccess.constantEncoding(source) as? ListTag
        if (components != null) {
            val tag = (components.firstOrNull() as? CompoundTag)?.get(key) ?: return fallback
            return if (fallback is ScoreBool) ScoreBool((tag.toJava() as Number).toInt() != 0) else MCString(tag as StringTag)
        }
        StorageAccess.materialize(source)
        val path = field(source, key)
        if (fallback is ScoreBool) {
            Function.addCommand("scoreboard players set ${fallback.name} ${fallback.boolObject} 0")
            emit(Command("execute if data").build(path.toCommandPart()).build("store result score ${fallback.name} ${fallback.boolObject} run data get")
                .build(path.toCommandPart()))
            StorageAccess.publishBoolean(fallback, top.mcfpp.analysis.StorageLayout.Scoreboard(fallback.name, fallback.boolObject.toString()))
        } else {
            StorageAccess.materialize(fallback)
            emit(Command("execute if data").build(path.toCommandPart()).build("run")
                .build(Commands.dataSetFrom(StorageAccess.ensure(fallback).path, path)))
            StorageAccess.publishNbt(fallback)
        }
        return fallback
    }

    fun withField(source: JsonText, key: String, value: Var<*>): JsonText {
        if (key == "color" && knownString(value)?.let(::validColor) != true) {
            LogProcessor.error("text.color requires a compile-time Minecraft color name or #RRGGBB")
            return empty().apply { isError = true }
        }
        if ((key == "font" || key == "color" && knownString(value)?.startsWith("#") == true) &&
            listOf("1.13", "1.14", "1.15").any { Project.config.version.startsWith(it) }) {
            LogProcessor.error("Custom fonts and RGB text colors require Minecraft 1.16 or newer")
            return empty().apply { isError = true }
        }
        val original = StorageAccess.constantEncoding(source) as? ListTag
        val encoded = StorageAccess.constantEncoding(value)
        if (original != null && encoded != null) {
            val result = copy(original) as ListTag
            result.forEach { (it as CompoundTag).put(key, copy(encoded)) }
            return literal(result)
        }
        val result = StorageAccess.capture(source) as JsonText
        StorageAccess.materialize(result)
        StorageAccess.encodeTo( StorageAccess.ensure(result).path.iteratorIndex().memberIndex(key), value)
        StorageAccess.publishNbt(result)
        return result
    }

    fun click(context: NativeCallContext, action: String) = context.withAdapters { receiver, args ->
        val property = if (modernEvents) "click_event" else "clickEvent"
        val valueKey = if (!modernEvents) "value" else when (action) {
            "open_url" -> "url"
            "run_command", "suggest_command" -> "command"
            "change_page" -> "page"
            else -> "value"
        }
        knownString(args[0])?.let { value ->
            if (action == "open_url") {
                val uri = runCatching { java.net.URI(value) }.getOrNull()
                if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) {
                    LogProcessor.error("text.openUrl requires an http or https URL"); return@withAdapters
                }
            }
            if (action in setOf("run_command", "suggest_command") && value.any { it.code < 32 || it == '\u007f' || it == '\u00a7' }) {
                LogProcessor.error("Text click commands cannot contain control or formatting characters"); return@withAdapters
            }
        }
        if (action == "copy_to_clipboard" && TargetCapabilities.forVersion(Project.config.version)?.legacyPackFormat == 4) {
            LogProcessor.error("copy_to_clipboard requires Minecraft 1.15 or newer")
            return@withAdapters
        }
        if (action == "change_page") {
            val page = (payload(StorageAccess.snapshot(args[0])) as? CompilerValue.Integral)?.value
            if (page == null || page < 1) { LogProcessor.error("text.changePage requires a positive compile-time page number"); return@withAdapters }
        }
        StorageAccess.constantEncoding(args[0])?.let { argument ->
            val tag = if (action == "change_page" && !modernEvents) StringTag((payload(StorageAccess.snapshot(args[0])) as CompilerValue.Integral).value.toString()) else argument
            val event = StorageAccess.literal(top.mcfpp.type.MCFPPNBTType.NBT, CompilerValue.Nbt(NbtEncoding.snbt(
                CompoundTag("action" to StringTag(action), valueKey to tag))))
            context.publishResult(withField(receiver as JsonText, property, event))
            return@withAdapters
        }
        val event = StorageAccess.literal(top.mcfpp.type.MCFPPNBTType.NBT, CompilerValue.Nbt(
            NbtEncoding.snbt(CompoundTag("action" to StringTag(action)))))
        StorageAccess.materialize(event)
        val path = StorageAccess.ensure(event).path.memberIndex(valueKey)
        if (action == "change_page" && !modernEvents) {
            val page = (payload(StorageAccess.snapshot(args[0])) as? CompilerValue.Integral)?.value
            if (page == null || page < 1) { LogProcessor.error("text.changePage requires a positive compile-time page number"); return@withAdapters }
            emit(Commands.dataSetValue(path, StringTag(page.toString())))
        } else StorageAccess.encodeTo(path, args[0])
        StorageAccess.publishNbt(event)
        context.publishResult(withField(receiver as JsonText, property, event))
    }

    fun hover(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val property = if (modernEvents) "hover_event" else "hoverEvent"
        val valueKey = if (modernEvents || Project.config.version.startsWith("1.13") || Project.config.version.startsWith("1.14") ||
            Project.config.version.startsWith("1.15")) "value" else "contents"
        StorageAccess.constantEncoding(args[0])?.let { argument ->
            val event = StorageAccess.literal(top.mcfpp.type.MCFPPNBTType.NBT, CompilerValue.Nbt(NbtEncoding.snbt(
                CompoundTag("action" to StringTag("show_text"), valueKey to argument))))
            context.publishResult(withField(receiver as JsonText, property, event)); return@withAdapters
        }
        val event = StorageAccess.literal(top.mcfpp.type.MCFPPNBTType.NBT, CompilerValue.Nbt("{action:\"show_text\"}"))
        StorageAccess.materialize(event)
        StorageAccess.encodeTo(StorageAccess.ensure(event).path.memberIndex(valueKey), args[0])
        StorageAccess.publishNbt(event)
        context.publishResult(withField(receiver as JsonText, property, event))
    }

    fun concatenate(left: JsonText, right: JsonText): JsonText {
        val first = StorageAccess.constantEncoding(left) as? ListTag
        val second = StorageAccess.constantEncoding(right) as? ListTag
        if (first != null && second != null) return literal(CompoundTag("type" to StringTag("text"), "text" to StringTag(""),
            "extra" to ListTag().apply { first.forEach { add(copy(it)) }; second.forEach { add(copy(it)) } }))
        val result = literal(CompoundTag("type" to StringTag("text"), "text" to StringTag(""), "extra" to ListTag()))
        StorageAccess.materialize(result)
        for (part in listOf(left, right)) {
            StorageAccess.materialize(part)
            emit(Command("data modify").build(field(result, "extra").toCommandPart()).build("append from")
                .build(StorageAccess.ensure(part).path.iteratorIndex().toCommandPart()))
        }
        StorageAccess.publishNbt(result)
        return result
    }

    fun fromJson(context: NativeCallContext) = context.withArguments { args ->
        val source = knownString(args[0])
        if (source == null) { LogProcessor.error("Text.fromJson requires compile-time JSON; use Text.of for runtime strings"); return@withArguments }
        try {
            require(JSON.isValid(source)) { "invalid JSON syntax" }
            fun component(value: Any?): MutableMap<String, Any?> = when (value) {
                is String -> linkedMapOf("type" to "text", "text" to value)
                is List<*> -> {
                    if (value.isEmpty()) linkedMapOf("type" to "text", "text" to "") else component(value.first()).apply {
                        val existing = (get("extra") as? List<*>)?.map { component(it) }.orEmpty()
                        if (value.size > 1) put("extra", existing + value.drop(1).map { component(it) })
                    }
                }
                is Map<*, *> -> value.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }.apply {
                    require(keys.count { it in setOf("text", "translate", "score", "selector", "keybind", "nbt") } == 1) { "each component requires exactly one content field" }
                    for (flag in listOf("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret"))
                        require(flag !in this || get(flag) is Boolean) { "$flag must be a boolean" }
                    get("color")?.let { require(it is String && validColor(it)) { "invalid color" } }
                    require((if (modernEvents) "clickEvent" else "click_event") !in this &&
                        (if (modernEvents) "hoverEvent" else "hover_event") !in this) { "event fields do not match the target version" }
                    get("score")?.let { require(it is Map<*, *> && it["name"] is String && it["objective"] is String) { "score requires name and objective strings" } }
                    if ("nbt" in this) require(listOf("storage", "entity", "block").count { get(it) is String } == 1) { "nbt requires one storage, entity or block source" }
                    get("extra")?.let { require(it is List<*>) { "extra must be an array" }; put("extra", it.map { item -> component(item) }) }
                    get("with")?.let { require(it is List<*>) { "with must be an array" }; put("with", it.map { item -> component(item) }) }
                    for (key in listOf("text", "translate", "selector", "keybind", "nbt"))
                        require(key !in this || get(key) is String) { "$key must be a string" }
                }
                else -> throw IllegalArgumentException("a component must be a string, object or array")
            }
            val root = component(JSON.parse(source))
            context.publishResult(literal(NbtEncoding.parse(JSON.toJSONString(root))))
        } catch (failure: IllegalArgumentException) { LogProcessor.error("Text.fromJson: ${failure.message}") }
    }
}
