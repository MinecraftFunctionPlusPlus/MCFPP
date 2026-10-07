package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.core.lang.obj.EnumVarConcrete
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Internal command codecs for the two nominal standard-library argument types. */
internal object TemplateCommandFormat {
    fun build(value: DataTemplateObject): Command? = when {
        instance(value, "mcfpp.minecraft", "Area") -> area(value)
        instance(value, "mcfpp.minecraft.other", "Slot") -> slot(value)
        else -> null
    }

    private fun instance(value: DataTemplateObject, namespace: String, name: String): Boolean {
        val template = GlobalScope.getTemplate(namespace, name) ?: return false
        return value.templateType.isSubOf(template)
    }

    private fun field(value: DataTemplateObject, name: String): Var<*> {
        val result = DataTemplate.getField(value, name)!!
        return if (result is PropertyVar) result.get() else result
    }

    private fun area(value: DataTemplateObject): Command {
        val points = listOf("start", "end").map { name ->
            val point = field(value, name)
            StorageAccess.snapshot(point)?.let { StorageAccess.restore(point.type, it, point.identifier) } as? Pos3Var
        }
        if (points.any { it == null }) {
            LogProcessor.error("Area command coordinates require complete compile-time values")
            return Command()
        }
        if (points.any { point -> point!!.value.any { it.prefix == "^" } }) {
            LogProcessor.error("Area block coordinates cannot use local-coordinate prefixes")
            return Command()
        }
        return Command.buildAll(points[0]!!, points[1]!!)
    }

    private val slotTokens = mapOf(
        "contents" to "contents", "container" to "container", "hotbar" to "hotbar",
        "inventory" to "inventory", "enderchest" to "enderchest", "villager" to "villager",
        "horse" to "horse", "weapon" to "weapon", "weapon_mainhand" to "weapon.mainhand",
        "weapon_offhand" to "weapon.offhand", "armor" to "armor", "armor_head" to "armor.head",
        "armor_chest" to "armor.chest", "armor_legs" to "armor.legs", "armor_feet" to "armor.feet",
        "armor_body" to "armor.body", "saddle" to "saddle", "horse_chest" to "horse.chest",
        "player_cursor" to "player.cursor", "player_crafting" to "player.crafting"
    )

    private fun slot(value: DataTemplateObject): Command {
        val type = field(value, "type") as EnumVar
        val known = StorageAccess.snapshot(type)?.let { StorageAccess.restore(type.type, it, type.identifier) } as? EnumVarConcrete
        val record = (StorageAccess.snapshot(value) as? CompilerValue.Typed)?.payload as? CompilerValue.Record
        if (known != null) {
            val token = slotTokens.getValue(known.value.identifier)
            if (record != null) {
                val index = record.fields["index"].let { if (it is CompilerValue.Typed) it.payload else it }
                if (index == null || index == CompilerValue.NullValue) return Command(token)
                if (index is CompilerValue.Integral) return Command("$token.${index.value}")
            }
        }
        if (TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Dynamic Slot command arguments require a function-macro target")
            return Command()
        }
        val binding = StorageAccess.ensure(value)
        binding.data.materialize()
        val token = temporaryString()
        if (known != null) Function.addCommand(Commands.dataSetValue(token.nbtPath, StringTag(slotTokens.getValue(known.value.identifier))))
        else {
            Function.addCommand(Commands.dataSetValue(token.nbtPath, StringTag("")))
            for (member in type.enum.members.values) {
                val filter = CompoundTag().apply { put("type", member.data) }
                val match = binding.path.toCommandPart().build(NbtEncoding.snbt(filter), false)
                Function.addCommand(Command("execute if data").build(match).build("run")
                    .build(Commands.dataSetValue(token.nbtPath, StringTag(slotTokens.getValue(member.identifier)))))
            }
        }
        val result = temporaryString()
        Function.addCommand(Commands.dataSetFrom(result.nbtPath, token.nbtPath))
        // Read the optional index only when it exists. Macro preparation must not load a
        // missing integer into a register or turn absence into an indexed slot ending in .0.
        val arguments = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        Function.addCommand(Commands.dataSetValue(arguments, CompoundTag()))
        Function.addCommand(Commands.dataSetFrom(arguments.memberIndex("token"), token.nbtPath))
        val indexPath = binding.path.memberIndex("index")
        Function.addCommand(Command("execute if data").build(indexPath.toCommandPart()).build("run")
            .build(Commands.dataSetFrom(arguments.memberIndex("index"), indexPath)))
        val indexed = Command("data modify").build(result.nbtPath.toCommandPart()).build("set value \"")
            .buildMacro(MCString("token"), false).build(".", false).buildMacro(MCInt("index"), false)
            .build("\"", false).buildMacroFunction(arguments)
        Function.addCommand(Command("execute if data").build(indexPath.toCommandPart()).build("run").build(indexed))
        return result.toCommandPart()
    }

    private fun temporaryString(): MCString = MCString(TempPool.getVarIdentify()).apply {
        nbtPath = NBTPath.stack.intIndex(0).memberIndex(identifier)
        isTemp = true
        isDynamic = true
        hasAssigned = true
        StorageAccess.bindIncomingParameter(this)
    }
}
