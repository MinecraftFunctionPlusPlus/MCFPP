package top.mcfpp.backend

import top.mcfpp.mni.NativeCallContext

/** Commands reuse the existing resource codecs, context restoration and result capture. */
object StandardCommandOperations {
    fun genericDamage(context: NativeCallContext, mode: String = "") = captureCommandResult(context) { args ->
        if (!requireDamageCommand()) {
            return@captureCommandResult top.mcfpp.command.Command("")
        }
        val target = requireEntityTarget(args[0]) ?: return@captureCommandResult top.mcfpp.command.Command("")
        val extra: List<Any> = when (mode) {
            "at" -> listOf("at", args[2])
            "by" -> listOf("by", requireEntityTarget(args[2], single = true) ?: return@captureCommandResult top.mcfpp.command.Command(""))
            "from" -> listOf("by", requireEntityTarget(args[2], single = true) ?: return@captureCommandResult top.mcfpp.command.Command(""),
                "from", requireEntityTarget(args[3], single = true) ?: return@captureCommandResult top.mcfpp.command.Command(""))
            else -> emptyList()
        }
        if ((target as top.mcfpp.core.lang.entity.EntityTargetAccess).entityFacts.selectingSingleEntity)
            nativeEntityCommand("damage", target, args[1], "minecraft:generic", *extra.toTypedArray())
        else nativeEntityCommand("execute as", target, "run damage @s", args[1], "minecraft:generic", *extra.toTypedArray(), changesExecutor = true)
    }
    fun cloneDefault(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("clone", args[0], args[1], "replace normal") }
    fun enchantResult(context: NativeCallContext) = captureCommandResult(context) { args -> nativeEntityCommand("enchant", args[0], args[1], args.getOrNull(2) ?: "1") }
    private fun item(vararg parts: Any?): top.mcfpp.command.Command {
        val target = top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)
        if (target?.itemCommands != true) {
            top.mcfpp.util.LogProcessor.error("item commands require Minecraft 1.17 or newer")
            return top.mcfpp.command.Command("")
        }
        return nativeEntityCommand(*parts)
    }
    fun fillOutline(context: NativeCallContext) = captureCommandResult(context) { a -> nativeEntityCommand("fill", a[0], a[1], "outline") }
    fun itemReplaceEntity(context: NativeCallContext) = captureCommandResult(context) { a -> item("item replace entity", a[0], a[1], "with", a[2], a.getOrNull(3) ?: "1") }
    fun itemReplaceBlock(context: NativeCallContext) = captureCommandResult(context) { a -> item("item replace block", a[0], a[1], "with", a[2], a.getOrNull(3) ?: "1") }
    fun itemReplaceEntityFromEntity(context: NativeCallContext) = captureSingleEntityCommandResult(context, 2) { a -> item("item replace entity", a[0], a[1], "from entity", a[2], a[3]) }
    fun itemReplaceEntityFromBlock(context: NativeCallContext) = captureCommandResult(context) { a -> item("item replace entity", a[0], a[1], "from block", a[2], a[3]) }
    fun itemReplaceBlockFromEntity(context: NativeCallContext) = captureSingleEntityCommandResult(context, 2) { a -> item("item replace block", a[0], a[1], "from entity", a[2], a[3]) }
    fun itemReplaceBlockFromBlock(context: NativeCallContext) = captureCommandResult(context) { a -> item("item replace block", a[0], a[1], "from block", a[2], a[3]) }
    fun itemModifyEntity(context: NativeCallContext) = captureCommandResult(context) { a -> item("item modify entity", a[0], a[1], a[2]) }
    fun itemModifyBlock(context: NativeCallContext) = captureCommandResult(context) { a -> item("item modify block", a[0], a[1], a[2]) }
    fun titleTimes(context: NativeCallContext) = capturePlayerCommandResult(context) { a -> nativeEntityCommand("title", a[0], "times", a[1], a[2], a[3]) }

    internal fun requireDamageCommand(): Boolean {
        if (top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.damageCommand == true) return true
        top.mcfpp.util.LogProcessor.error("damage requires Minecraft 1.19.4 or newer")
        return false
    }
}
