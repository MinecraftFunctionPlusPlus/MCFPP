package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.EntityTargetText
import top.mcfpp.lib.SelectorExpression
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.util.LogProcessor

object NativeEntityTargetOperations {
    fun entityByName(context: NativeCallContext) = build(context, SelectorExpression.DirectKind.PLAYER_NAME)
    fun entityByUUID(context: NativeCallContext) = build(context, SelectorExpression.DirectKind.UUID_TEXT)

    private fun build(context: NativeCallContext, kind: SelectorExpression.DirectKind) = context.withArguments { args ->
        val text = args.singleOrNull() as? MCString
        if (text == null) {
            LogProcessor.error("Entity target factories require a string argument")
            return@withArguments
        }
        if (StorageAccess.snapshot(text) != null && !EntityTargetText.isValid(kind, text.value.value)) {
            LogProcessor.error(EntityTargetText.description(kind))
            return@withArguments
        }
        val target = when (kind) {
            SelectorExpression.DirectKind.PLAYER_NAME -> EntityTarget.PlayerName(text)
            SelectorExpression.DirectKind.UUID_TEXT -> EntityTarget.UUIDText(text)
        }
        context.publishResult(SelectorVar(target))
    }
}
