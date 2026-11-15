package top.mcfpp.mni

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.backend.NativeEntityTargetOperations
import top.mcfpp.backend.NativeStdCommandOperations
import top.mcfpp.backend.UUIDOperations
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor

object EntityValuesData {
    private fun requested(value: top.mcfpp.core.lang.Var<*>?): MCFPPType? =
        (value as? MCFPPTypeVar)?.value?.takeIf {
            it is MCFPPEntityType || it is MCFPPDataTemplateType && it.template.isEntityTemplate
        }.also {
            if (it == null) LogProcessor.error("Expected an entity type argument")
        }

    @top.mcfpp.mni.annotation.NoExternalWrites
    @JvmStatic @MNIFunction(normalParams = ["string"], returnType = "UUID")
    fun uuidFromText(context: NativeCallContext) = context.withArguments { context.publishResult(UUIDOperations.fromText(it.single())) }

    @top.mcfpp.mni.annotation.NoExternalWrites
    @JvmStatic @MNIFunction(normalParams = ["IntArray"], returnType = "UUID")
    fun uuidFromWords(context: NativeCallContext) = context.withArguments { context.publishResult(UUIDOperations.fromWords(it.single())) }

    @top.mcfpp.mni.annotation.NoExternalWrites
    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["string"], returnType = "EntityRef<T>")
    fun typedTextTarget(context: NativeCallContext) = context.withArguments { args ->
        val type = requested(args[0]) ?: return@withArguments
        val nested = NativeCallContext(context.function, null, listOf(args[1]), MCFPPEntityType.NormalSelector)
        NativeEntityTargetOperations.entityByUUID(nested)
        val result = nested.resultAdapter() as? SelectorVar ?: return@withArguments
        context.publishResult(typed(result, type))
    }

    @top.mcfpp.mni.annotation.NoExternalWrites
    @JvmStatic @MNIFunction(normalParams = ["UUID"], returnType = "entity")
    fun uuidTarget(context: NativeCallContext) = context.withArguments { args ->
        context.publishResult(UUIDOperations.target(args.single()))
    }

    @top.mcfpp.mni.annotation.NoExternalWrites
    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["UUID"], returnType = "EntityRef<T>")
    fun typedUUIDTarget(context: NativeCallContext) = context.withArguments { args ->
        val type = requested(args[0]) ?: return@withArguments
        context.publishResult(typed(UUIDOperations.target(args[1]), type))
    }

    private fun typed(value: SelectorVar, type: MCFPPType): SelectorVar {
        if (type is MCFPPDataTemplateType) {
            val target = value.entityTargetOrNull() as top.mcfpp.lib.EntityTarget.UUIDText
            val known = StorageAccess.constantEncoding(target.uuid) as? top.mcfpp.nbt.tags.primitive.StringTag
            if (known != null) return StorageAccess.literal(MCFPPEntityRefType(type), CompilerValue.Text(known.value)) as SelectorVar
            val result = MCFPPEntityRefType(type).buildUnConcrete(top.mcfpp.util.TempPool.getVarIdentify()) as SelectorVar
            StorageAccess.encodeTo(StorageAccess.ensure(result).path, target.uuid)
            return StorageAccess.publishNbt(result)
        }
        return SelectorVar().also { result ->
            result.type = type
            value.entityTargetOrNull()?.let { StorageAccess.updateEntityTarget(result, it) }
        }
    }

    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["pos3", "nbt"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun summon(context: NativeCallContext) = context.withArguments { args ->
        val type = requested(args[0]) ?: return@withArguments
        val kind = if (type is MCFPPDataTemplateType) top.mcfpp.core.lang.entity.EntityTemplates.kind(type.template)
            else (type as MCFPPEntityType).entityKinds?.singleOrNull()
        if (kind == null || kind in setOf("minecraft:player", "minecraft:fishing_bobber")) {
            LogProcessor.error("Cannot summon abstract or non-summonable entity type '$type'")
            return@withArguments
        }
        val resourceType = top.mcfpp.model.scope.GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.resource")!!
            .scope.getTemplate("EntityType")!!.getType()
        val resource = StorageAccess.literal(resourceType, CompilerValue.Record(mapOf("id" to
            CompilerValue.Typed(MCFPPBaseType.String.typeId, CompilerValue.Text(kind)))))
        val input = if (args[2] is top.mcfpp.core.lang.MCAny) StorageAccess.actualView(args[2]) else args[2]
        val encoded = top.mcfpp.backend.NumericConversions.toNBT(input)
        val known = StorageAccess.constantEncoding(encoded)
        if (known != null && known !is top.mcfpp.nbt.tags.CompoundTag) {
            LogProcessor.error("summon requires compound NBT data")
            return@withArguments
        }
        val nested = NativeCallContext(context.function, null, listOf(resource, args[1], encoded), context.declaredReturnType)
        NativeStdCommandOperations.summonWithNbt(nested)
        nested.resultAdapter()?.let(context::publishResult)
    }

    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["pos3", "any"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun summonValue(context: NativeCallContext) = summon(context)

    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["ContextPosition", "nbt"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun summonAtContext(context: NativeCallContext) = summon(context)

    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["ContextPosition", "any"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun summonValueAtContext(context: NativeCallContext) = summon(context)

    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["pos3"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun summonEmpty(context: NativeCallContext) = context.withArguments { args ->
        val empty = StorageAccess.literal(MCFPPNBTType.NBT, CompilerValue.Nbt("{}"))
        val nested = NativeCallContext(context.function, null, args + empty, context.declaredReturnType)
        summon(nested)
        nested.resultAdapter()?.let(context::publishResult)
    }

    @JvmStatic @MNIFunction(readOnlyParams = ["type"], normalParams = ["ContextPosition"], returnType = "mcfpp.minecraft.std:CommandResult")
    fun summonEmptyAtContext(context: NativeCallContext) = summonEmpty(context)
}
