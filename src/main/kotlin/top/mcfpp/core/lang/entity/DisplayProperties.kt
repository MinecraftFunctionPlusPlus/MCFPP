package top.mcfpp.core.lang.entity

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.*
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Property descriptions are shared by declaration binding, source access, and reference copies. */
internal object DisplayProperties {
    class ViewType(val group: String) : MCFPPType() {
        override val typeId = TypeId.Builtin("display.view.$group")
        override val typeName = "Display${group.replaceFirstChar(Char::uppercaseChar)}"
        override val hasRuntimeRepresentation = false
        override fun buildUnConcrete(identifier: String): Var<*> = UnknownVar(identifier)
    }
    private val views = listOf("transform", "scale", "leftRotation", "rightRotation").associateWith(::ViewType)
    private data class Field(val key: String, val path: String, val type: () -> MCFPPType,
                             val default: Tag<*>?, val writeOnly: Boolean = false)
    private fun template(namespace: String, name: String): MCFPPType =
        requireNotNull(GlobalScope.getUnsolvedImportNamespace(namespace)?.scope?.getTemplate(name)) { "Missing standard type $namespace:$name" }.getType().also { it.tryResolve() }
    private fun enum(name: String): MCFPPType =
        requireNotNull(GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.entity")?.scope?.getEnum(name)) { "Missing standard enum $name" }.getType()
    private fun float(key: String, path: String, default: Float = 0f) = Field(key, path, { MCFPPBaseType.Float }, FloatTag(default))
    private fun integer(key: String, path: String, default: Int) = Field(key, path, { MCFPPBaseType.Int }, IntTag(default))
    private fun bool(key: String, path: String) = Field(key, path, { MCFPPBaseType.Bool }, ByteTag(0))
    private val common = listOf(
        Field("billboard", "billboard", { enum("DisplayBillboard") }, StringTag("fixed")),
        Field("brightness", "brightness", { template("mcfpp.minecraft.entity", "DisplayBrightness") }, null),
        float("viewRange", "view_range", 1f), float("shadowRadius", "shadow_radius"), float("shadowStrength", "shadow_strength", 1f),
        float("width", "width"), float("height", "height"), integer("glowColorOverride", "glow_color_override", -1),
        integer("interpolationDuration", "interpolation_duration", 0), integer("teleportDuration", "teleport_duration", 0),
        Field("startInterpolation", "start_interpolation", { MCFPPBaseType.Int }, null, writeOnly = true))
    private val item = listOf(
        Field("item", "item", { template("mcfpp.minecraft.item", "ItemStackData") }, CompoundTag("id" to "minecraft:air", "count" to 0)),
        Field("itemDisplayMode", "item_display", { enum("ItemDisplayMode") }, StringTag("none")))
    private val block = listOf(Field("blockState", "block_state", { template("mcfpp.minecraft.other", "BlockState") }, CompoundTag("Name" to "minecraft:air")))
    private val text = listOf(
        Field("text", "text", { MCFPPBaseType.JsonText }, ListTag()), integer("lineWidth", "line_width", 200),
        Field("textOpacity", "text_opacity", { MCFPPBaseType.Int }, ByteTag(-1)), integer("background", "background", 0x40000000),
        bool("defaultBackground", "default_background"), bool("shadow", "shadow"), bool("seeThrough", "see_through"),
        Field("alignment", "alignment", { enum("TextAlignment") }, StringTag("center")))
    private val transformation = listOf("x", "y", "z").mapIndexed { i, key -> float(key, "transformation.translation[$i]") }
    private val scaling = listOf("x", "y", "z").mapIndexed { i, key -> float(key, "transformation.scale[$i]", 1f) }
    private val left = listOf("x", "y", "z", "w").mapIndexed { i, key -> float(key, "transformation.left_rotation[$i]", if (i == 3) 1f else 0f) }
    private val right = listOf("x", "y", "z", "w").mapIndexed { i, key -> float(key, "transformation.right_rotation[$i]", if (i == 3) 1f else 0f) }
    private val all get() = common + item + block + text + transformation + scaling + left + right
    private fun entityView(type: MCFPPType): MCFPPType = if (type is MCFPPEntityRefType) {
        val kind = EntityTemplates.kind(type.templateType.template)
        EntityTypes.all.firstOrNull { it.entityKinds == setOf(kind) }
            ?: if (type.templateType.template.identifier == "DisplayEntity") EntityTypes.DisplayEntity else MCFPPEntityType.NormalSelector
    } else type
    private fun fields(original: MCFPPType): List<Field> = when (val type = entityView(original)) {
        is ViewType -> when (type.group) { "transform" -> transformation; "scale" -> scaling; "leftRotation" -> left; else -> right }
        is MCFPPEntityType -> if (!type.isSubOf(EntityTypes.DisplayEntity)) emptyList() else common + when (type.entityKinds?.singleOrNull()) {
            "minecraft:item_display" -> item; "minecraft:block_display" -> block; "minecraft:text_display" -> text; else -> emptyList()
        }
        else -> emptyList()
    }
    private fun view(original: MCFPPType, key: String): ViewType? {
        val type = entityView(original)
        return when {
        type is MCFPPEntityType && type.isSubOf(EntityTypes.DisplayEntity) && key == "transform" -> views["transform"]
        type is ViewType && type.group == "transform" && key in setOf("scale", "leftRotation", "rightRotation") -> views[key]
        else -> null
        }
    }
    fun memberType(type: MCFPPType, key: String): MCFPPType? = view(type, key)
        ?: fields(type).firstOrNull { it.key == key }?.let { MCFPPNBTRefType(it.type()) }
    fun writeType(type: MCFPPType): MCFPPType? = (type as? ViewType)?.let {
        when (it.group) {
            "scale" -> template("mcfpp.minecraft.entity", "DisplayVector3")
            "leftRotation", "rightRotation" -> template("mcfpp.minecraft.entity", "DisplayQuaternion")
            else -> null
        }
    }

    fun member(receiver: Var<*>, key: String): Var<*>? {
        val target = (receiver as? DisplayView)?.target ?: receiver
        view(receiver.type, key)?.let { viewType ->
            val access = DisplayView(StorageAccess.capture(target), viewType)
            if (key == "transform") return access
            return PropertyVar(Property(key, SimpleAccessor(), AnonymousNativeMutator { _, input ->
                val expected = template("mcfpp.minecraft.entity", if (key == "scale") "DisplayVector3" else "DisplayQuaternion")
                if (TypeRelations.resolveImplicitConversion(input.type, expected) == null)
                    return@AnonymousNativeMutator fail("Expected $expected for transform.$key")
                val converted = input.implicitCast(expected)
                if (converted.isError) return@AnonymousNativeMutator converted
                val source = StorageAccess.capture(converted)
                val components = if (key == "scale") listOf("x", "y", "z") else listOf("x", "y", "z", "w")
                val encoded = top.mcfpp.core.lang.nbt.NBTBasedData()
                val path = StorageAccess.ensure(encoded).path
                Function.addCommand(Commands.dataSetValue(path, ListTag().apply { repeat(components.size) { add(FloatTag(0f)) } }))
                for ((index, component) in components.withIndex()) {
                    val member = requireNotNull(source.getMemberVar(component, Member.AccessModifier.PUBLIC).first)
                    val value = if (member is PropertyVar) member.get() else member
                    StorageAccess.encodeTo(path.intIndex(index), VanillaFloatEncoding.encode(value))
                }
                val propertyPath = "transformation." + when (key) { "scale" -> "scale"; "leftRotation" -> "left_rotation"; else -> "right_rotation" }
                val address = ReferenceValues.create(MCFPPNBTRefType(MCFPPNBTType.NBT), ReferenceKind.NBT_ENTITY,
                    mapOf("path" to MCString(StringTag(propertyPath))), target)
                val write = Commands.tempFunction("display_vector_write", Function.currFunction) {
                    initializeTransform()
                    Function.addCommand(Command("data modify entity @s").build(propertyPath).build("set from").build(path.toCommandPart()))
                }.first
                command(address) { entity -> Command("execute as").build(entity).build("run").build(write) }
                access
            }), access, receiver)
        }
        val field = fields(receiver.type).firstOrNull { it.key == key } ?: return null
        val reference = ReferenceValues.create(MCFPPNBTRefType(field.type()), ReferenceKind.NBT_ENTITY,
            mapOf("path" to MCString(StringTag(field.path))), target,
            metadataFields = mapOf("displayProperty" to CompilerValue.Text(field.path)))
        val property = Property(key, SimpleAccessor(), AnonymousNativeMutator { _, input -> set(reference, input); reference })
        return PropertyVar(property, reference, receiver)
    }

    private fun description(reference: Var<*>): Field? {
        val raw = ReferenceValues.descriptor(reference)?.fields?.fields?.get("displayProperty")
        val key = (raw as? CompilerValue.Text)?.value ?: return null
        return all.firstOrNull { it.path == key }
    }
    fun recognizes(reference: Var<*>) = description(reference) != null
    fun selected(value: Var<*>): Var<*>? = ((value as? PropertyVar)?.field ?: value).takeIf(::recognizes)
    fun readable(reference: Var<*>) {
        if (description(reference)?.writeOnly == true)
            throw top.mcfpp.core.lang.context.ContextAccessException("startInterpolation is a write-only trigger")
    }

    fun set(reference: Var<*>, input: Var<*>): Var<*> {
        val field = description(reference) ?: return Void
        if (input.isError) return input
        if (input.type == MCFPPPrivateType.Null && field.key == "brightness") {
            command(reference) { target -> Command("data remove entity").build(target).build(field.path) }
            return Void
        }
        val logicalType = field.type()
        val source = when {
            logicalType == MCFPPBaseType.JsonText && input.type == MCFPPBaseType.String -> input
            logicalType is MCFPPEnumType && input.type == MCFPPBaseType.String -> input
            TypeRelations.resolveImplicitConversion(input.type, logicalType) != null -> input.implicitCast(logicalType)
            else -> return fail("Cannot assign '${input.type}' to display property '${field.key}: $logicalType'")
        }
        val known = StorageAccess.constantEncoding(source)
        if (logicalType is MCFPPEnumType && known is StringTag && logicalType.enum.members.values.none { it.data == known })
            return fail("Invalid value for display property '${field.key}': ${known.value}")
        if (field.key == "textOpacity" && known is IntTag && known.value !in 0..255)
            return fail("textOpacity must be between 0 and 255")
        val value = when {
            logicalType == MCFPPBaseType.Float -> VanillaFloatEncoding.encode(source)
            field.key == "blockState" -> StorageAccess.dictionaryLiteral(MCFPPNBTType.NBT, mapOf(
                "Name" to memberValue(source, "name"), "Properties" to memberValue(source, "properties")))
            logicalType is MCFPPEnumType && source.type is MCFPPEnumType -> enumPayload(source)
            field.key == "textOpacity" -> top.mcfpp.backend.NumericConversions.convert(source, MCFPPNBTType.Byte)
            else -> source
        }
        if (value.isError) return value
        StorageAccess.materialize(value)
        val write = Commands.tempFunction("display_write", Function.currFunction) {
            if (field.path.startsWith("transformation.")) initializeTransform()
            Function.addCommand(Command("data modify entity @s").build(field.path).build("set from")
                .build(StorageAccess.ensure(value).path.toCommandPart()))
        }.first
        command(reference) { target -> Command("execute as").build(target).build("run").build(write) }
        EffectAnalysis.recordEffect(Effect.Unknown)
        return Void
    }

    private fun enumPayload(value: Var<*>): Var<*> {
        val known = StorageAccess.constantEncoding(value)
        if (known != null) return top.mcfpp.core.lang.nbt.NBTBasedData(
            if (known is CompoundTag) requireNotNull(known["data"]) else known)
        StorageAccess.materialize(value)
        val result = top.mcfpp.core.lang.nbt.NBTBasedData()
        Function.addCommand(Commands.dataSetFrom(StorageAccess.ensure(result).path, StorageAccess.ensure(value).path.memberIndex("data")))
        return StorageAccess.publishNbt(result)
    }

    private fun memberValue(value: Var<*>, key: String): Var<*> {
        val member = requireNotNull(value.getMemberVar(key, Member.AccessModifier.PUBLIC).first)
        return if (member is PropertyVar) member.get() else member
    }

    fun readInto(reference: Var<*>, destination: Var<*>, success: ScoreBool) {
        val field = requireNotNull(description(reference))
        readable(reference)
        val path = StorageAccess.ensure(destination).path
        val raw = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        Function.addCommand(Command("data remove").build(path.toCommandPart()))
        Function.addCommand("scoreboard players set ${success.name} ${success.boolObject} 0")
        val read = Commands.tempFunction("display_read", Function.currFunction) {
            Function.addCommand(Command("data remove").build(raw.toCommandPart()))
            Function.addCommand(Command("data modify").build(raw.toCommandPart()).build("set from entity @s").build(field.path))
            field.default?.let { tag -> Function.addCommand(Command("execute unless data").build(raw.toCommandPart())
                .build("run").build(Commands.dataSetValue(raw, tag))) }
            when (val type = field.type()) {
                MCFPPBaseType.Float -> VanillaFloatEncoding.decode(raw, path)
                is MCFPPEnumType -> for (member in type.enum.members.values) {
                    val match = NbtEncoding.snbt(CompoundTag("value" to member.data))
                    // Compare scalar NBT without interpolating runtime text.
                    val state = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
                    Function.addCommand(Commands.dataSetValue(state, CompoundTag()))
                    Function.addCommand(Commands.dataSetFrom(state.memberIndex("value"), raw))
                    Function.addCommand(Command("execute if data").build(state.toCommandPart()).build(match)
                        .build("run").build(Commands.dataSetValue(path, CompoundTag("ordinal" to member.value, "data" to member.data))))
                }
                else -> if (field.key == "blockState") {
                    Function.addCommand(Commands.dataSetValue(path, CompoundTag("properties" to CompoundTag())))
                    Function.addCommand(Commands.dataSetFrom(path.memberIndex("name"), raw.memberIndex("Name")))
                    Function.addCommand(Commands.dataSetFrom(path.memberIndex("properties"), raw.memberIndex("Properties")))
                } else if (field.key == "text") {
                    Function.addCommand(Commands.dataSetValue(path, ListTag()))
                    Function.addCommand(Command("data modify").build(path.toCommandPart()).build("append from").build(raw.toCommandPart()))
                } else {
                    Function.addCommand(Commands.dataSetFrom(path, raw))
                    if (field.key == "item") {
                        Function.addCommand(Command("execute unless data").build(path.memberIndex("count").toCommandPart())
                            .build("run").build(Commands.dataSetValue(path.memberIndex("count"), IntTag(1))))
                        Function.addCommand(Command("execute unless data").build(path.memberIndex("components").toCommandPart())
                            .build("run").build(Commands.dataSetValue(path.memberIndex("components"), CompoundTag())))
                    }
                    if (field.key == "textOpacity") {
                        val alpha = MCInt()
                        Function.addCommand(Command("execute store result score ${alpha.name} ${alpha.sbObject} run data get").build(raw.toCommandPart()))
                        Function.addCommand("execute if score ${alpha.name} ${alpha.sbObject} matches ..-1 run scoreboard players add ${alpha.name} ${alpha.sbObject} 256")
                        Function.addCommand(Command("execute store result").build(path.toCommandPart()).build("int 1 run scoreboard players get ${alpha.name} ${alpha.sbObject}"))
                    }
                }
            }
            Function.addCommand(Command("execute if data").build(path.toCommandPart())
                .build("run scoreboard players set ${success.name} ${success.boolObject} 1"))
        }.first
        command(reference) { target -> Command("execute as").build(target).build("run").build(read) }
        StorageAccess.publishNbt(destination)
        StorageAccess.publishBoolean(success, StorageLayout.Scoreboard(success.name, success.boolObject.toString()))
    }

    private fun command(reference: Var<*>, build: (Command) -> Command) {
        ReferenceAccess.emitKnown(reference, requireNotNull(ReferenceValues.descriptor(reference))) { target -> build(requireNotNull(target)) }
    }
    private fun initializeTransform() {
        val zero = "[0f,0f,0f]"
        for ((path, value) in listOf("transformation" to "{}", "transformation.translation" to zero,
            "transformation.scale" to "[1f,1f,1f]", "transformation.left_rotation" to "[0f,0f,0f,1f]",
            "transformation.right_rotation" to "[0f,0f,0f,1f]"))
            Function.addCommand("execute unless data entity @s $path run data modify entity @s $path set value $value")
    }
    private fun fail(message: String): Var<*> { LogProcessor.error(message); return UnknownVar("display").apply { isError = true } }
}

internal class DisplayView(val target: Var<*>, viewType: DisplayProperties.ViewType) : Var<DisplayView>() {
    init { type = viewType }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier) = DisplayProperties.member(this, key) to true
    override fun clone() = DisplayView(target, type as DisplayProperties.ViewType)
    override fun getTempVar(): Var<*> = clone()
    override fun doAssignedBy(b: Var<*>): DisplayView = clone().apply {
        LogProcessor.error("A display transform is an entity access view; assign its components")
        isError = true
    }
}
