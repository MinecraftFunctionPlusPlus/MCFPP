package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.entity.EntityVar
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.nbt.tags.CompoundTag

object NativeStdCommandOperations {
    fun seed(context: NativeCallContext) = context.withArguments {
        val result = (context.declaredReturnType.buildUnConcrete(TempPool.getVarIdentify()) as DataTemplateObject).apply { isTemp = true }
        val binding = StorageAccess.bindIncomingParameter(result)
        Function.addCommand(Commands.dataSetValue(binding.path, CompoundTag()))
        Function.addCommand(Command("execute store result").build(binding.path.memberIndex("result").toCommandPart())
            .build("int 1 store success").build(binding.path.memberIndex("success").toCommandPart()).build("byte 1 run seed"))
        context.publishResult(result)
    }

    fun damage(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[2])
    }

    fun damageAt(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[3], "at", args[2])
    }

    fun damageBy(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[3], "by", args[2])
    }

    fun damageFrom(context: NativeCallContext) = emit(context) { args ->
        damageCommand(args[0], args[1], args[4], "by", args[2], "from", args[3])
    }

    private fun damageCommand(target: Var<*>, amount: Var<*>, kind: Var<*>, vararg extras: Any): Command {
        val multi = if (target is SelectorVar) !target.value.selectingSingleEntity() else (target as EntityVar).isMulti()
        return if (multi) Command.buildAll("execute as", target, "run damage @s", amount, kind, *extras)
        else Command.buildAll("damage", target, amount, kind, *extras)
    }

    fun cloneArea(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], args[2], args[3])
    }

    fun cloneStrictArea(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], "strict", args[2], args[3])
    }

    fun cloneFiltered(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], "filtered", args[2], args[3])
    }

    fun cloneStrictFiltered(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("clone", args[0], args[1], "strict filtered", args[2], args[3])
    }

    fun enchant(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("enchant", args[0], args[1], args[2], args[3])
    }

    fun placeFeature(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place feature", args[0])
    }

    fun placeFeatureAt(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place feature", args[0], args[1])
    }

    fun placeJigsaw(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place jigsaw", args[0], args[1], args[2])
    }

    fun placeJigsawAt(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place jigsaw", args[0], args[1], args[2], args[3])
    }

    fun placeStructure(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place structure", args[0])
    }

    fun placeStructureAt(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place structure", args[0], args[1])
    }

    fun placeTemplate(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place", args[0], args[1], args[2], args[3], args[4], args[5])
    }

    fun placeTemplateStrict(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("place", args[0], args[1], args[2], args[3], args[4], args[5], "strict")
    }

    fun playsound(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("playsound", args[0], args[1], args[2], args[3], args[4], args[5], args[6])
    }

    fun titleTitle(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("title", args[0], args[2], args[1])
    }

    fun titleSet(context: NativeCallContext) = emit(context) { args ->
        Command.buildAll("title", args[0], "times", args[1], args[2], args[3])
    }

    private fun emit(context: NativeCallContext, build: (List<Var<*>>) -> Command) = context.withArguments { args ->
        if (!FloatProviders.enabled && args.any { it is MCFloat && it !is MCFPPValue<*> }) {
            LogProcessor.error("Dynamic float command arguments require a number-provider target")
            return@withArguments
        }
        build(args).buildMacroFunction().forEach { Function.addCommand(it) }
    }
}
