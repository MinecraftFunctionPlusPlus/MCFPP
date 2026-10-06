package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.function.Function

object NativeStdCommandOperations {
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
        build(args).buildMacroFunction().forEach { Function.addCommand(it) }
    }
}
