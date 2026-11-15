package top.mcfpp.mni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.util.LogProcessor

/** Explicit runtime queries; a declared reference type is never used as runtime proof. */
@top.mcfpp.mni.annotation.NoExternalWrites
object EntityInspectionData {
    /** Counts targets when the query executes; saved selectors continue to resolve at use. */
    @JvmStatic @MNIFunction(caller = "entity", returnType = "int")
    fun count(context: NativeCallContext) = context.withAdapters { receiver, _ ->
        val entity = receiver as? EntityTargetAccess ?: return@withAdapters
        val result = top.mcfpp.core.lang.MCInt()
        top.mcfpp.model.function.Function.addCommand("scoreboard players set ${result.name} ${result.sbObject} 0")
        top.mcfpp.model.function.Function.addCommands(Command("execute as").build(entity.toEntityTargetCommand())
            .build("run scoreboard players add ${result.name} ${result.sbObject} 1").buildMacroFunction())
        StorageAccess.publishScore(result, StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
        context.publishResult(result)
    }
    /** True when at least one selected entity has the supplied tag. */
    @JvmStatic @MNIFunction(caller = "entity", normalParams = ["string"], returnType = "bool")
    fun hasTag(context: NativeCallContext) = context.withAdapters { receiver, args ->
        val entity = receiver as? EntityTargetAccess ?: return@withAdapters
        val taggedSelf = top.mcfpp.lib.EntitySelector('s').addPredicate(top.mcfpp.lib.TagPredicate(args[0] as top.mcfpp.core.lang.nbt.MCString, false))
        val result = ScoreBool()
        top.mcfpp.model.function.Function.addCommand("scoreboard players set ${result.name} ${result.boolObject} 0")
        top.mcfpp.model.function.Function.addCommands(Command("execute as").build(entity.toEntityTargetCommand())
            .build("if entity").build(taggedSelf.toCommandPart()).build("run scoreboard players set ${result.name} ${result.boolObject} 1").buildMacroFunction())
        StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
        context.publishResult(result)
    }
    @JvmStatic
    @MNIFunction(caller = "entity", returnType = "bool")
    fun exists(context: NativeCallContext) = query(context, false)

    @JvmStatic
    @MNIFunction(caller = "entity", readOnlyParams = ["type"], returnType = "bool")
    fun checkType(context: NativeCallContext) = query(context, true)

    private fun query(context: NativeCallContext, checkType: Boolean) = context.withAdapters { receiver, args ->
        val entity = receiver as? EntityTargetAccess ?: return@withAdapters
        val requested = if (checkType) (args.singleOrNull() as? MCFPPTypeVar)?.value else null
        val template = (requested as? top.mcfpp.type.MCFPPDataTemplateType)?.template?.takeIf { it.isEntityTemplate }
        if (checkType && requested !is MCFPPEntityType && template == null) {
            LogProcessor.error("checkType<T> requires an entity type")
            return@withAdapters
        }
        if (checkType && !entity.entityFacts.selectingSingleEntity) {
            LogProcessor.error("checkType<T> requires at most one entity; use withEntityCtx to check each target")
            return@withAdapters
        }
        val result = ScoreBool()
        top.mcfpp.model.function.Function.addCommand("scoreboard players set ${result.name} ${result.boolObject} 0")
        val kinds = if (template != null) top.mcfpp.core.lang.entity.EntityTemplates.kinds(template)
            else (requested as? MCFPPEntityType)?.entityKinds
        if (template != null && template.namespace != "mcfpp.minecraft.entity") {
            val tag = top.mcfpp.core.lang.entity.EntityTemplates.typeTag(template)
            top.mcfpp.model.function.Function.addCommands(Command("execute as").build(entity.toEntityTargetCommand())
                .build("if entity @s[tag=$tag] run scoreboard players set ${result.name} ${result.boolObject} 1").buildMacroFunction())
        } else if (kinds == null) {
            top.mcfpp.model.function.Function.addCommands(Command("execute if entity").build(entity.toEntityTargetCommand())
                .build("run scoreboard players set ${result.name} ${result.boolObject} 1").buildMacroFunction())
        } else for (kind in kinds) {
            top.mcfpp.model.function.Function.addCommands(Command("execute as").build(entity.toEntityTargetCommand())
                .build("if entity @s[type=$kind] run scoreboard players set ${result.name} ${result.boolObject} 1").buildMacroFunction())
        }
        StorageAccess.publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
        context.publishResult(result)
    }
}
