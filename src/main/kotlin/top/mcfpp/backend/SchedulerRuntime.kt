package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.analysis.lambda.LambdaLowering
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** One queue per compilation's namespace, with a frozen active batch for each game tick. */
internal object SchedulerRuntime {
    private var ownerTick: Function? = null
    private var process: Function? = null
    private var runtimeStorage = ""
    private var nextCode = 0

    fun register(interval: Var<*>, callback: Var<*>, repeating: Boolean) {
        val callable = callback as? FunctionValue
        if (interval !is MCInt || callable == null) { LogProcessor.error("Scheduler requires an int delay and a callback"); return }
        val known = StorageAccess.snapshot(interval)?.let { ((it as? CompilerValue.Typed)?.payload ?: it) as? CompilerValue.Integral }?.value
        if (known != null && known < if (repeating) 1 else 0) {
            LogProcessor.error(if (repeating) "loop interval must be at least 1 tick" else "schedule delay must be nonnegative")
            return
        }
        if (!repeating && known == 0L) { LambdaLowering.invoke(callable, emptyList()); return }
        ensureRuntime()
        val caller = Function.currFunction
        val code = nextCode++
        val entry = Function(TempPool.getFunctionIdentify("scheduled_callback"), caller.namespace, null)
        entry.isDerivedImplementation = true
        GlobalScope.getOrCreateNamespace(entry.namespace).scope.addFunction(entry, false)
        entry.runInFunction {
            val runtimeCallback = FunctionValue("callback", callable.type as MCFPPFunctionType)
            runtimeCallback.nbtPath = current().memberIndex("callback")
            StorageAccess.bindIncomingParameter(runtimeCallback)
            StorageAccess.copyRuntimeEvidence(StorageAccess.ensure(callable), StorageAccess.ensure(runtimeCallback))
            val args = if (repeating) {
                val frame = MCInt("frame")
                frame.nbtPath = current().memberIndex("frame")
                StorageAccess.bindIncomingParameter(frame)
                listOf(frame)
            } else emptyList()
            val result = LambdaLowering.invoke(runtimeCallback, args)
            if (repeating && result is BaseBool) {
                val keep = result.toScoreBool(false)
                Function.addCommand("execute if score ${keep.name} ${keep.boolObject} matches 1 run data modify storage $runtimeStorage current.keep set value 1b")
            }
        }
        entry.bodyCompiled = true
        process!!.commands.add(Command("execute if data storage $runtimeStorage current{code:$code} run function ${entry.namespaceID}"))
        process!!.child.add(entry)
        entry.parent.add(process!!)

        val enqueue = Commands.tempFunction("schedule_register", caller) {
            Function.addCommand("execute unless data storage $runtimeStorage queued run data modify storage $runtimeStorage queued set value []")
            val record = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
            Function.addCommand(Commands.dataSetValue(record, CompoundTag("code" to code, "frame" to 0, "keep" to 0)))
            StorageAccess.encodeTo(record.memberIndex("interval"), interval)
            StorageAccess.encodeTo(record.memberIndex("remaining"), interval)
            StorageAccess.encodeTo(record.memberIndex("callback"), callable)
            Function.addCommand(Command("data modify storage $runtimeStorage queued append from").build(record.toCommandPart()))
        }.first
        val score = StorageAccess.intRegister(interval)
        if (!repeating && known == null) {
            val immediate = Commands.tempFunction("schedule_immediate", caller) { LambdaLowering.invoke(callable, emptyList()) }.first
            Function.addCommand("execute if score ${score.player} ${score.objective} matches 0 run $immediate")
        }
        Function.addCommand("execute if score ${score.player} ${score.objective} matches 1.. run $enqueue")
        Function.addCommand("execute if score ${score.player} ${score.objective} matches ${if (repeating) "..0" else "..-1"} run tellraw @a {\"type\":\"text\",\"text\":\"Invalid scheduler delay or interval\"}")
    }

    private fun current(): NBTPath = NBTPath(top.mcfpp.lib.StorageSource(runtimeStorage)).memberIndex("current")

    private fun ensureRuntime() {
        if (ownerTick === Project.projectTick) return
        ownerTick = Project.projectTick
        nextCode = 0
        runtimeStorage = "${Project.config.rootNamespace}:scheduler"
        val namespace = GlobalScope.getOrCreateNamespace(Project.config.rootNamespace).scope
        val tick = Function("__scheduler/tick", Project.config.rootNamespace, null)
        val batch = Function("__scheduler/batch", Project.config.rootNamespace, null)
        val dispatch = Function("__scheduler/dispatch", Project.config.rootNamespace, null)
        process = dispatch
        for (function in listOf(tick, batch, dispatch)) {
            function.isDerivedImplementation = true
            function.bodyCompiled = true
            namespace.addFunction(function, false)
        }
        val counter = MCInt()
        val frame = MCInt()
        tick.runInFunction {
            Function.addCommand("data modify storage $runtimeStorage active set from storage $runtimeStorage queued")
            Function.addCommand("data modify storage $runtimeStorage queued set value []")
            Function.addCommand("execute if data storage $runtimeStorage active[0] run function ${batch.namespaceID}")
            Function.addCommand("data remove storage $runtimeStorage current")
            Function.addCommand("data remove storage $runtimeStorage active")
        }
        batch.runInFunction {
            Function.addCommand("data modify storage $runtimeStorage current set from storage $runtimeStorage active[0]")
            Function.addCommand("data remove storage $runtimeStorage active[0]")
            Function.addCommand("data modify storage $runtimeStorage current.keep set value 0b")
            Function.addCommand("execute store result score ${counter.name} ${counter.sbObject} run data get storage $runtimeStorage current.remaining")
            Function.addCommand("scoreboard players remove ${counter.name} ${counter.sbObject} 1")
            Function.addCommand("execute store result storage $runtimeStorage current.remaining int 1 run scoreboard players get ${counter.name} ${counter.sbObject}")
            Function.addCommand("execute if score ${counter.name} ${counter.sbObject} matches ..0 run function ${dispatch.namespaceID}")
            // Callback code can use arbitrary temporary scores, so reread the owned task state.
            Function.addCommand("execute store result score ${counter.name} ${counter.sbObject} run data get storage $runtimeStorage current.remaining")
            Function.addCommand("execute if score ${counter.name} ${counter.sbObject} matches 1.. run data modify storage $runtimeStorage queued append from storage $runtimeStorage current")
            val repeat = Commands.tempFunction("schedule_repeat", batch) {
                Function.addCommand("data modify storage $runtimeStorage current.remaining set from storage $runtimeStorage current.interval")
                Function.addCommand("execute store result score ${frame.name} ${frame.sbObject} run data get storage $runtimeStorage current.frame")
                Function.addCommand("scoreboard players add ${frame.name} ${frame.sbObject} 1")
                Function.addCommand("execute store result storage $runtimeStorage current.frame int 1 run scoreboard players get ${frame.name} ${frame.sbObject}")
                Function.addCommand("data modify storage $runtimeStorage queued append from storage $runtimeStorage current")
            }.first
            Function.addCommand("execute if data storage $runtimeStorage current{keep:1b} run $repeat")
            Function.addCommand("execute if data storage $runtimeStorage active[0] run function ${batch.namespaceID}")
        }
        tick.child.add(batch); batch.parent.add(tick)
        batch.child.add(dispatch); dispatch.parent.add(batch)
        Project.projectTick.commands.add(Command("function ${tick.namespaceID}"))
        Project.projectTick.child.add(tick)
        tick.parent.add(Project.projectTick)
        Project.projectLoad.commands.add(Command("data modify storage $runtimeStorage queued set value []"))
        Project.projectLoad.commands.add(Command("data remove storage $runtimeStorage active"))
        Project.projectLoad.commands.add(Command("data remove storage $runtimeStorage current"))
    }
}
