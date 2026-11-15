package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.analysis.PrimitiveTypes.any
import top.mcfpp.analysis.PrimitiveTypes.bool
import top.mcfpp.analysis.PrimitiveTypes.float
import top.mcfpp.analysis.PrimitiveTypes.int
import top.mcfpp.analysis.PrimitiveTypes.nbt
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.debug.PrimitiveDebugSite
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.optimization.references
import top.mcfpp.optimization.resultReferences
import top.mcfpp.type.*
import top.mcfpp.util.TempPool

/**
 * 将已绑定的 Typed IR 降低为 Minecraft 命令。语义诊断和优化在创建本对象之前完成。
 * 布局准备先发布地址适配器，实际命令生成仍由原有 pendingBackend 边界触发。
 */
internal class PrimitiveBackend(
    val function: Function,
    private val bindings: PrimitiveBackendBindings,
    val ir: TypedIR,
    val facts: FlowAnalysis.Result,
    val typeFacts: FlowAnalysis.Result,
    val graph: Map<SymbolId, TypedIR>,
    private val resolveFunction: (SymbolId) -> Function,
) {
    private var blockId = 0
    private val commands = mutableListOf<String>()
    val materializedPlaces = hashSetOf<Place>()
    private data class Score(val name: String, val objective: String) { override fun toString() = "$name $objective" }
    private val results = mutableMapOf<Int, Score>()
    private val nbtResults = mutableMapOf<Int, NBTPath>()
    private val fusedFloats = ir.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Binary>()
        .filter { it.result in ir.fusedFloatResults }.associateBy { it.result }
    private val viewResults = mutableMapOf<Int, ValueRef.TypedView>()
    private val constants = mutableMapOf<Pair<TypeId, CompilerValue>, Score>()
    private val symbols = bindings.symbols.values.associateBy { it.id } + ir.declarations
    private val storageNames = ir.declarations.mapValues { it.value.name } + bindings.symbols.entries.associate { it.value.id to it.key }
    private val types = bindings.types + ir.boundTypes
    private val destinations = mutableMapOf(0 to function)
    private val storagePlaces = hashSetOf<Place>()
    private val initialized = hashSetOf<Place>()
    private val scorePlaces = hashSetOf<Place>()
    private val liveAtCalls = top.mcfpp.optimization.ResultLiveness.atCalls(ir, facts.entries.keys)
    private val livePlacesAtCalls = top.mcfpp.optimization.StorageLiveness.atCalls(ir)
    private var callNumber = 0
    private val program = top.mcfpp.optimization.ProgramAnalysis(graph)
    private val optimizeSaves = Project.config.optimization.enabled(top.mcfpp.optimization.PassIds.SAVES)
    private val staticFrame = top.mcfpp.optimization.OptimizationPipeline.staticFrame(function)
    private val backendRuntime = ir.runtimeValues
    private val optimizeCopies = Project.config.optimization.enabled(top.mcfpp.optimization.PassIds.COPIES)
    private val resultUses = ir.blocks.flatMap { block ->
        block.instructions.flatMap { it.references() } + when (val exit = block.terminator) {
            is Terminator.Return -> exit.value?.resultReferences().orEmpty()
            is Terminator.Branch -> exit.condition.resultReferences()
            else -> emptySet()
        }
    }.groupingBy { it }.eachCount()
    private val scoreDestinations: Map<Int, Score> = buildMap {
        if (optimizeCopies) for (block in ir.blocks) for ((index, instruction) in block.instructions.withIndex()) {
            if (instruction !is Instruction.Binary || instruction.type !in setOf(int, bool) || resultUses[instruction.result] != 1) continue
            val next = nextExecutable(block, index)
            if (next is Instruction.Write && next.value == ValueRef.Result(instruction.type, instruction.result) &&
                top.mcfpp.optimization.RuntimeValues.scalarRoot(next.location, symbols)) put(instruction.result, place(next.place))
            val exit = block.terminator
            if (next == null && function.optimizedScalar && exit is Terminator.Return &&
                exit.value == ValueRef.Result(instruction.type, instruction.result)) put(instruction.result, returnScore(function))
        }
    }
    private fun frame(offset: Int = 0) = staticFrame ?: NBTPath.stack.intIndex(offset)
    private fun calleeFrame(target: Function) = top.mcfpp.optimization.OptimizationPipeline.staticFrame(target) ?: NBTPath.stack.intIndex(0)
    private fun returnScore(target: Function) = Score(top.mcfpp.optimization.OptimizationPipeline.scoreName(target, "\$return"), objective(target.returnType.typeId))

    private fun nextExecutable(block: BasicBlock, position: Int): Instruction? {
        for (index in position + 1..block.instructions.lastIndex) {
            val instruction = block.instructions[index]
            if (instruction !is Instruction.Declare) return instruction
        }
        return null
    }

    private fun mayReuseRead(instruction: Instruction.Read, block: BasicBlock, position: Int): Boolean {
        if (!optimizeCopies || resultUses[instruction.result] != 1 ||
            !top.mcfpp.optimization.RuntimeValues.scalarRoot(instruction.location, symbols)) return false
        val reference = ValueRef.Result(instruction.type, instruction.result)
        return when (val next = nextExecutable(block, position)) {
            is Instruction.Binary -> instruction.result in next.references() &&
                (scoreDestinations[next.result] != place(instruction.place) ||
                    next.left == reference && next.operation in setOf("+", "-", "*", "&&"))
            is Instruction.Write -> next.value == reference && top.mcfpp.optimization.RuntimeValues.scalarRoot(next.location, symbols)
            null -> function.optimizedScalar && (block.terminator as? Terminator.Return)?.value == reference
            else -> false
        }
    }

    private fun immediateArithmetic(instruction: Instruction.Binary): Boolean {
        if (!optimizeCopies || instruction.type != int || instruction.operation !in setOf("+", "-")) return false
        var operand = instruction.left
        var value = ((instruction.right as? ValueRef.Constant)?.value as? CompilerValue.Integral)?.value?.toInt()
        if (value == null && instruction.operation == "+") {
            value = ((instruction.left as? ValueRef.Constant)?.value as? CompilerValue.Integral)?.value?.toInt()
            operand = instruction.right
        }
        if (value == null || value == Int.MIN_VALUE) return false
        val source = score(operand)
        val destination = scoreDestinations[instruction.result] ?: temporary(int)
        results[instruction.result] = destination
        if (destination != source) commands += "scoreboard players operation $destination = $source"
        val delta = if (instruction.operation == "-") -value else value
        commands += "scoreboard players ${if (delta < 0) "remove" else "add"} $destination ${kotlin.math.abs(delta)}"
        return true
    }
    private fun objective(type: TypeId) = if (type == int) "mcfpp_default" else "mcfpp_boolean"
    private fun temporary(type: TypeId) = Score(TempPool.getVarIdentify(), objective(type))
    private fun place(place: Place): Score {
        val symbol = symbols.getValue(place.root)
        return Score(top.mcfpp.optimization.OptimizationPipeline.scoreName(function, storageNames.getValue(place.root), place.root), objective(symbol.declaredType))
    }
    fun address(place: Place): NBTPath = address(Location(place))
    fun address(location: Location): NBTPath {
        val place = location.place
        val name = storageNames.getValue(place.root)
        var path = if (name.startsWith("$")) internal(name, location.frameOffset)
            else frame(location.frameOffset).memberIndex(name)
        for ((position, segment) in place.path.withIndex()) path = when (segment) {
            is PathSegment.Field -> path.memberIndex(StorageAccess.quotedKey(segment.name))
            is PathSegment.Index -> path.intIndex(segment.index)
            PathSegment.UnknownIndex -> {
                val key = location.keys[position]
                if (key != null) {
                    val predicate = if (key is ValueRef.Constant) top.mcfpp.core.lang.nbt.NBTBasedData(
                        top.mcfpp.nbt.tags.CompoundTag().apply { put("key", StringTag(MapFacts.text(key.value)!!)) })
                    else top.mcfpp.core.lang.nbt.NBTBasedData("map_key_${(key as ValueRef.Result).instruction}").apply {
                        nbtPath = internal(identifier, location.frameOffset)
                        StorageAccess.publishNbt(this)
                    }
                    path.nbtIndex(predicate)
                } else {
                    val id = "index_${location.indices.getValue(position)}"
                    path.intIndex(MCInt(id).apply {
                        isDataOnly = true
                        nbtPath = internal(id, location.frameOffset)
                        StorageAccess.publishNbt(this)
                    })
                }
            }
        }
        return path
    }
    private fun path(place: Place) = address(place)
    fun layout(place: Place): StorageLayout = if (place in scorePlaces) {
        val score = place(place)
        StorageLayout.Scoreboard(score.name, score.objective)
    } else StorageLayout.Nbt(address(place).source.toString(), address(place).pathToCommandPart().toString())
    private fun internal(name: String, frame: Int = 0) = frame(frame).memberIndex("\$ir").memberIndex(name)
    private fun legacyComponents(value: MCFloat) = LegacyFloatComparison.Components(
        "${value.sign.name} ${value.sign.sbObject}", "${value.int0.name} ${value.int0.sbObject}",
        "${value.int1.name} ${value.int1.sbObject}", "${value.exp.name} ${value.exp.sbObject}")
    private fun legacyFloat(value: ValueRef): NBTPath {
        top.mcfpp.Project.enableModulePackage("math.float", "stdlib")
        return (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
            ?: internal("float_operand_${callNumber++}").also { encode(it, value) }
    }
    private fun floatFromInt(result: Int, source: Score) {
        if (FloatProviders.enabled) {
            computeFloat(result, "{type:\"minecraft:from_int\",input:${FloatProviders.scoreProvider(source.name, source.objective)}}")
        } else {
            top.mcfpp.Project.enableModulePackage("math.float", "stdlib")
            val destination = internal("float_$result")
            LegacyFloatCommands.fromInt(source.toString(), destination, ::emit)
            nbtResults[result] = destination
        }
    }
    private fun floatProvider(value: ValueRef): String {
        val fused = (value as? ValueRef.Result)?.let { fusedFloats[it.instruction] }
        if (fused != null) return FloatProviders.arithmeticProvider(
            floatProvider(fused.left), floatProvider(fused.right), fused.operation)
        val stored = (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
        if (stored != null) return FloatProviders.storageProvider((stored.source as top.mcfpp.lib.StorageSource).storage,
            stored.pathToCommandPart().toString())
        val known = constant(value) as? CompilerValue.FloatBits ?: error("Unmaterialized float $value")
        return Float.fromBits(known.bits).toString()
    }
    private fun computeFloat(result: Int, provider: String) {
        val destination = internal("float_$result")
        emit(Command("data modify").build(destination.toCommandPart()).build("set compute default float $provider"))
        nbtResults[result] = destination
    }
    private fun convert(instruction: Instruction.Convert) {
        val value = instruction.value
        val destination = instruction.place?.let(::path) ?: internal("convert_${instruction.result}")
        val result = ValueRef.Result(instruction.type, instruction.result)
        when {
            instruction.type == MCFPPNBTType.NBT.typeId || instruction.type == value.type -> encode(destination, value)
            !runtime(result) -> emit(Commands.dataSetValue(destination, StorageAccess.snapshotTag(constant(result)!!, instruction.type)!!))
            value.type == MCFPPNBTType.Long.typeId && instruction.type == MCFPPNBTType.Double.typeId -> {
                val input = internal("convert_input_${instruction.result}")
                encode(input, value)
                val operand = top.mcfpp.core.lang.nbt.MCLong("convert_input_${instruction.result}").apply {
                    nbtPath = input
                    StorageAccess.publishNbt(this)
                }
                emit(Command("data modify").build(destination.toCommandPart()).build("set value")
                    .buildMacro(operand).build("d", false))
            }
            instruction.type == float && value.type in setOf(MCFPPNBTType.Long.typeId, MCFPPNBTType.Double.typeId) -> {
                val input = internal("convert_input_${instruction.result}")
                encode(input, value)
                val provider = FloatProviders.storageProvider((input.source as top.mcfpp.lib.StorageSource).storage,
                    input.pathToCommandPart().toString())
                emit(Command("data modify").build(destination.toCommandPart()).build("set compute default float $provider"))
            }
            instruction.type == float -> {
                val input = score(value)
                floatFromInt(instruction.result, input)
                emit(Commands.dataSetFrom(destination, nbtResults.getValue(instruction.result)))
            }
            else -> {
                val source = if (value.type == float) temporary(int).also {
                    if (FloatProviders.enabled)
                        commands += "execute store result score $it run compute default integer {type:\"minecraft:from_float\",input:${floatProvider(value)}}"
                    else LegacyFloatCommands.toInt(legacyFloat(value), it.toString(), ::emit)
                } else if (value.type in setOf(MCFPPNBTType.Long.typeId, MCFPPNBTType.Double.typeId)) {
                    // Even literals use the target data-get rule, never a host numeric cast.
                    val input = internal("convert_input_${instruction.result}")
                    encode(input, value)
                    temporary(int).also { commands += "execute store result score $it run data get ${input.toCommandPart()} 1" }
                } else score(value)
                val encoded = if (instruction.type in setOf(MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId)) {
                    val period = if (instruction.type == MCFPPNBTType.Byte.typeId) 256 else 65536
                    val modulus = score(ValueRef.Constant(int, CompilerValue.Integral(period.toLong())))
                    temporary(int).also {
                        commands += "scoreboard players operation $it = $source"
                        commands += "scoreboard players operation $it %= $modulus"
                        commands += "execute if score $it matches ..-1 run scoreboard players operation $it += $modulus"
                        commands += "execute if score $it matches ${period / 2}.. run scoreboard players operation $it -= $modulus"
                    }
                } else source
                val tag = when (instruction.type) {
                    MCFPPNBTType.Byte.typeId -> "byte"
                    MCFPPNBTType.Short.typeId -> "short"
                    MCFPPNBTType.Long.typeId -> "long"
                    MCFPPNBTType.Double.typeId -> "double"
                    else -> "int"
                }
                commands += "execute store result ${destination.toCommandPart()} $tag 1 run scoreboard players get $encoded"
                if (instruction.type == int) results[instruction.result] = encoded
            }
        }
        nbtResults[instruction.result] = destination
        instruction.place?.let { initialized.add(it); materializedPlaces.add(it) }
    }
    private fun emit(command: Command) {
        // Every dynamic address parameter belongs to this frame's IR slots.
        commands += command.buildMacroFunction(frame().memberIndex("\$ir")).analyze()
    }
    private fun encode(destination: NBTPath, value: ValueRef) {
        val existing = (value as? ValueRef.Result)?.let { nbtResults[it.instruction] }
        if (existing != null) { emit(Commands.dataSetFrom(destination, existing)); return }
        val known = constant(value)
        if (known != null && (value !is ValueRef.Result || value.instruction !in backendRuntime)) {
            val tag = StorageAccess.snapshotTag(known, value.type) ?: error("Unsupported constant encoding")
            emit(Commands.dataSetValue(destination, tag))
        } else {
            val tag = if (value.type == bool) "byte" else "int"
            emit(Command("execute store result").build(destination.toCommandPart()).build("$tag 1 run scoreboard players get ${score(value)}"))
        }
    }
    private fun projection(instruction: Instruction.MapProjection, position: Int) {
        val destination = path(instruction.place)
        val entries = address(instruction.receiver).memberIndex("entries")
        if (instruction.dictionary) {
            val state = typeFacts.beforeWrites.getValue(blockId to position)
            val source = MapFacts.resolve(state, instruction.receiver).field("entries")
            val names = MapFacts.keys(state, source)!!
            emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
            names.forEachIndexed { index, name ->
                emit(Commands.dataSetFrom(destination.memberIndex(StorageAccess.quotedKey(name)), entries.intIndex(index).memberIndex("value")))
            }
        } else {
            val workspace = internal("map_keys_${callNumber++}")
            emit(Commands.dataSetValue(workspace, top.mcfpp.nbt.tags.CompoundTag()))
            emit(Commands.dataSetFrom(workspace.memberIndex("source"), entries))
            top.mcfpp.backend.MapCommands.keys(workspace, ::emit)
            emit(Commands.dataSetFrom(destination, workspace.memberIndex("output")))
        }
        nbtResults[instruction.result] = destination
        initialized.add(instruction.place)
        materializedPlaces.add(instruction.place)
    }
    private fun map(instruction: Instruction.MapMember) {
        val destination = address(instruction.receiver).memberIndex("entries")
        if (instruction.operation.query && constant(ValueRef.Result(
                if (instruction.operation == MapOperation.SIZE) int else bool, instruction.result!!)) != null &&
            instruction.result !in backendRuntime) return
        if (instruction.operation == MapOperation.CLEAR) {
            emit(Commands.dataSetValue(destination, ListTag()))
            return
        }
        if (instruction.operation == MapOperation.SIZE || instruction.operation == MapOperation.IS_EMPTY) {
            val count = temporary(int)
            emit(Command("execute store result score $count run data get").build(destination.toCommandPart()))
            results[instruction.result!!] = if (instruction.operation == MapOperation.SIZE) count else temporary(bool).also {
                commands += "execute store success score $it if score $count matches 0"
            }
            return
        }
        val workspace = internal("map_${callNumber++}")
        emit(Commands.dataSetValue(workspace, top.mcfpp.nbt.tags.CompoundTag()))
        emit(Commands.dataSetFrom(workspace.memberIndex("source"), destination))
        when (instruction.operation) {
            MapOperation.CONTAINS_KEY -> {
                encode(workspace.memberIndex("needle"), instruction.key!!)
                val found = top.mcfpp.backend.MapCommands.contains(workspace, ::emit)
                results[instruction.result!!] = Score(found.name, found.sbObject.toString())
            }
            MapOperation.MERGE -> {
                encode(workspace.memberIndex("map"), instruction.argument!!)
                emit(Commands.dataSetFrom(workspace.memberIndex("rows"), workspace.memberIndex("map").memberIndex("entries")))
                top.mcfpp.backend.MapCommands.merge(workspace, ::emit)
                emit(Commands.dataSetFrom(destination, workspace.memberIndex("output")))
            }
            MapOperation.PUT, MapOperation.REMOVE -> {
                val incoming = workspace.memberIndex("incoming")
                emit(Commands.dataSetValue(incoming, top.mcfpp.nbt.tags.CompoundTag()))
                encode(incoming.memberIndex("key"), instruction.key!!)
                instruction.argument?.let { encode(incoming.memberIndex("value"), it) }
                top.mcfpp.backend.MapCommands.overlay(workspace, instruction.operation == MapOperation.REMOVE, ::emit)
                emit(Commands.dataSetFrom(destination, workspace.memberIndex("output")))
            }
            else -> error("Unsupported map operation")
        }
    }
    private fun constant(value: ValueRef): CompilerValue? = when (value) {
        is ValueRef.Constant -> value.value
        is ValueRef.Result -> (facts.values[blockId to value.instruction]?.value as? ValueKnowledge.Constant)?.value
        else -> null
    }
    private fun runtime(value: ValueRef) = value is ValueRef.Result && value.instruction in backendRuntime || constant(value) == null
    private fun number(value: CompilerValue): Int = when (value) {
        is CompilerValue.Integral -> value.value.toInt()
        is CompilerValue.Bool -> if (value.value) 1 else 0
        is CompilerValue.Typed -> number(value.payload)
        else -> error("Unsupported constant")
    }
    private fun score(value: ValueRef): Score {
        if (value is ValueRef.Result && value.instruction in results) return results.getValue(value.instruction)
        if (value is ValueRef.Result && value.instruction in viewResults) {
            val view = viewResults.getValue(value.instruction)
            val sourcePath = (view.source as? ValueRef.Result)?.let { nbtResults[it.instruction] } ?: address(view.location)
            if (!nbt(symbols.getValue(view.place.root).declaredType) && view.place.path.isEmpty()) encode(sourcePath, view.source)
            return temporary(value.type).also {
                emit(Command("execute store result score $it run data get").build(sourcePath.toCommandPart()).build("1"))
                results[value.instruction] = it
            }
        }
        if (value is ValueRef.Result && value.instruction in nbtResults) {
            return temporary(value.type).also {
                commands += "execute store result score $it run data get ${nbtResults.getValue(value.instruction).toCommandPart()} 1"
                results[value.instruction] = it
            }
        }
        val known = constant(value) ?: error("Unmaterialized result $value")
        return constants.getOrPut(value.type to known) {
            temporary(value.type).also { commands += "scoreboard players set $it ${number(known)}" }
        }
    }

    private fun physical(value: Var<*>): Score? {
        val binding = StorageAccess.ensure(value)
        val layout = binding.data.layoutAt(binding.place) as? StorageLayout.Scoreboard ?: return null
        return Score(layout.player, layout.objective)
    }

    private fun call(instruction: Instruction.Call, position: Int) {
        check(!instruction.provisional) { "An unresolved call cannot reach command generation" }
        val target = bindings.calls[instruction.declaration] ?: resolveFunction(instruction.declaration)
        if (target.typedIR == null && target.hasBody && !target.bodyCompiled && !target.bodyBeingCompiled)
            target.compileBody()
        function.recordCall(target)
        if (target.optimizedScalar) {
            val arguments = instruction.arguments.map(::score)
            target.normalParams.forEachIndexed { index, parameter ->
                val destination = physical(target.scope.getVar(parameter.identifier)!!)!!
                commands += "scoreboard players operation $destination = ${arguments[index]}"
            }
            commands += "function ${target.namespaceID}"
            instruction.result?.let { result ->
                val destination = temporary(instruction.returnType!!)
                commands += "scoreboard players operation $destination = ${returnScore(target)}"
                results[result] = destination
                instruction.resultPlace?.let { place ->
                    commands += "scoreboard players operation ${place(place)} = $destination"
                    scorePlaces.add(place); initialized.add(place); materializedPlaces.add(place)
                }
            }
            return
        }
        val id = callNumber++
        // Read snapshots and earlier argument results already live in the current frame.
        instruction.arguments.forEachIndexed { index, value -> encode(internal("arg_${id}_$index"), value) }
        val saved = linkedMapOf<Score, NBTPath>()
        val clobbered = !optimizeSaves || program.effects[instruction.declaration]?.unknown != false ||
            program.reaches(instruction.declaration, function.declarationId)
        val livePlaces = livePlacesAtCalls[blockId to position].orEmpty()
        for (location in storagePlaces) if (clobbered && location in initialized && location in scorePlaces &&
            (!optimizeSaves || livePlaces.any { it.overlaps(location) }))
            saved[place(location)] = path(location)
        val live = liveAtCalls[blockId to position].orEmpty()
        for (value in (if (clobbered) results.filterKeys { it in live }.values else emptyList()).distinct())
            saved.putIfAbsent(value, internal("spill_${id}_${saved.size}"))
        for ((value, location) in saved)
            commands += "execute store result ${location.toCommandPart()} int 1 run scoreboard players get $value"
        val staticTarget = top.mcfpp.optimization.OptimizationPipeline.staticFrame(target)
        if (staticTarget == null) emit(Commands.stackIn())
        else emit(Commands.dataSetValue(staticTarget, top.mcfpp.nbt.tags.CompoundTag()))
        target.normalParams.forEachIndexed { index, parameter ->
            val destination = calleeFrame(target).memberIndex(parameter.identifier)
            emit(Commands.dataSetFrom(destination, internal("arg_${id}_$index", if (staticTarget == null) 1 else 0)))
            physical(target.scope.getVar(parameter.identifier)!!)?.let { score ->
                commands += "execute store result score $score run data get ${destination.toCommandPart()} 1"
            }
        }
        commands += "function ${target.namespaceID}"
        // Unknown implementations may use registers from an independently compiled pack.
        // Recreate literals on demand instead of spilling their cached registers to NBT.
        if (program.effects[instruction.declaration]?.unknown != false) constants.clear()
        instruction.result?.let {
            val destination = internal("result_$id", if (staticTarget == null) 1 else 0)
            emit(Commands.dataSetFrom(destination, calleeFrame(target).memberIndex("return")))
        }
        if (staticTarget == null) emit(Commands.stackOut())
        for ((value, location) in saved)
            commands += "execute store result score $value run data get ${location.toCommandPart()} 1"
        instruction.result?.let { result ->
            val location = internal("result_$id")
            if (nbt(instruction.returnType!!)) nbtResults[result] = location
            else results[result] = temporary(instruction.returnType!!).also {
                commands += "execute store result score $it run data get ${location.toCommandPart()} 1"
            }
            instruction.resultPlace?.let { place ->
                emit(Commands.dataSetFrom(path(place), location))
                if (!nbt(instruction.returnType)) {
                    commands += "scoreboard players operation ${place(place)} = ${results.getValue(result)}"
                    scorePlaces.add(place)
                }
                initialized.add(place)
                materializedPlaces.add(place)
            }
        }
    }

    private fun search(instruction: Instruction.ListMember, position: Int) {
        val type = if (instruction.operation == ListOperation.CONTAINS) bool else int
        if (instruction.operation.query && !runtime(ValueRef.Result(type, instruction.result!!))) return
        val workspace = internal("list_search_${callNumber++}")
        emit(Commands.dataSetValue(workspace, top.mcfpp.nbt.tags.CompoundTag()))
        emit(Commands.dataSetFrom(workspace.memberIndex("source"), address(instruction.receiver)))
        encode(workspace.memberIndex("needle"), instruction.argument!!)
        val state = typeFacts.beforeWrites.getValue(blockId to position)
        val needle = (instruction.argument as? ValueRef.Result)?.let { typeFacts.values[blockId to it.instruction]?.type as? TypeKnowledge.Exact }?.type
            ?: instruction.argument.type
        val scope = ListFacts.matchScope(state, instruction, needle)
        check(scope != ListMatchScope.Unknown) { "Unbound list comparison reached command generation" }
        val index = ListSearch.find(workspace, (scope as? ListMatchScope.Indices)?.values,
            instruction.operation == ListOperation.LAST_INDEX_OF, ::emit)
        if (instruction.operation == ListOperation.REMOVE) {
            ListSearch.remove(workspace, index, ::emit)
            emit(Command("execute if score ${ListSearch.key(index)} matches 0.. run")
                .build(Commands.dataSetFrom(address(instruction.receiver), workspace.memberIndex("output"))))
        } else if (instruction.operation == ListOperation.CONTAINS) {
            val result = temporary(bool)
            commands += "scoreboard players set $result 0"
            commands += "execute if score ${ListSearch.key(index)} matches 0.. run scoreboard players set $result 1"
            results[instruction.result!!] = result
        } else results[instruction.result!!] = Score(index.name, index.sbObject.toString())
    }

    fun prepareLayout() {
        val reachable = ir.blocks.filter { it.id in facts.entries }
        for (parameter in function.normalParams) {
            val symbol = bindings.symbols[parameter.identifier] ?: continue
            if (symbol.declaredType == int || symbol.declaredType == bool) scorePlaces.add(Place(symbol.id))
        }
        if (reachable.any { block -> block.instructions.any { it is Instruction.RawCommand || it is Instruction.Call && it.effect == Effect.Unknown } }) {
            // Raw commands can observe any physical register and have unknown writes.
            storagePlaces.addAll(symbols.values.map { Place(it.id) })
        }
        // Values read without a full constant, and values crossing a conditional write,
        // need locations. Unchanged unrelated constants remain unmaterialized.
        for (block in reachable) for (instruction in block.instructions) {
            if (instruction is Instruction.Read && ((facts.values[block.id to instruction.result]?.value !is ValueKnowledge.Constant) || instruction.result in backendRuntime)) storagePlaces.add(instruction.place)
            if (block.id != 0 && instruction is Instruction.Write) storagePlaces.add(instruction.place)
            if (instruction is Instruction.Call && instruction.effect is Effect.Writes) storagePlaces.addAll(instruction.effect.places)
        }
        for (block in reachable) for (instruction in block.instructions.filterIsInstance<Instruction.Write>()) {
            if (nbt(symbols.getValue(instruction.place.root).declaredType) || instruction.place.path.isNotEmpty() ||
                instruction.place in storagePlaces || runtime(instruction.value) || symbols.getValue(instruction.place.root).forceRuntime) {
                materializedPlaces.add(instruction.place)
                if (!nbt(symbols.getValue(instruction.place.root).declaredType) && instruction.place.path.isEmpty()) scorePlaces.add(instruction.place)
            }
        }
    }

    fun generate() {
        val reachable = ir.blocks.filter { it.id in facts.entries }
        fun containsFunction(declaration: Function): Boolean = declaration === function || declaration.compiledFunctions.values.any(::containsFunction)
        fun contains(template: top.mcfpp.model.compound.DataTemplate): Boolean {
            if (template.constructors.any(::containsFunction) || template.scope.functions.values.flatten().any(::containsFunction)) return true
            return template is top.mcfpp.model.compound.GenericDataTemplate && template.compiledTemplates.values.any(::contains)
        }
        val helperScope = (if (function.bodyHandle != null) function.declarationFile?.namespace?.scope else null) ?: (
            GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values
        ).firstOrNull { namespace ->
            var found = namespace.scope.functions.values.flatten().any(::containsFunction)
            namespace.scope.forEachTemplate { if (contains(it)) found = true }
            namespace.scope.forEachObject { if (it is top.mcfpp.model.compound.DataTemplate && contains(it)) found = true }
            found
        }?.scope
        if (reachable.any { it.id != 0 } && helperScope == null) {
            top.mcfpp.util.LogProcessor.error("Cannot resolve declaration scope for '${function.namespaceID}'")
            return
        }
        for (block in reachable.filter { it.id != 0 }) {
            val destination = NoStackFunction(TempPool.getFunctionIdentify("ir_block"), function)
            destination.namespace = function.namespace
            destination.bodyCompiled = true
            destinations[block.id] = destination
            helperScope!!.addFunction(destination, false)
        }
        val hasControlFlow = ir.blocks.size > 1
        val supportsReturn = top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionReturnRun == true
        val exits = mutableListOf<Function.FrameExit>()
        for (block in reachable) {
            blockId = block.id
            commands.clear()
            val debugCommands = linkedMapOf<Int, top.mcfpp.debug.DebugPoint?>()
            if (block.id == 0 && hasControlFlow && !supportsReturn) commands.add("execute unless data storage mcfpp:system ir_branch_stack run data modify storage mcfpp:system ir_branch_stack set value []")
            results.clear()
            nbtResults.clear()
            viewResults.clear()
            constants.clear() // Literals must be initialized on every reachable entry.
            initialized.clear()
            initialized.addAll(storagePlaces.filter { facts.entries.getValue(block.id).read(it)?.state == ValueState.INITIALIZED })
            if (!function.optimizedScalar && block.id == 0 && ((!FloatProviders.enabled && function.returnVar is MCFloat) || reachable.any { candidate -> candidate.instructions.any { it is Instruction.Call ||
                it is Instruction.Construct || it is Instruction.Promote || it is Instruction.Convert ||
                it is Instruction.Binary && (it.left.type == float || it.right.type == float) ||
                it is Instruction.Read && nbt(symbols.getValue(it.place.root).declaredType) ||
                it is Instruction.Write && storageNames.getValue(it.place.root).startsWith("$") && nbt(symbols.getValue(it.place.root).declaredType) } }))
                emit(Commands.dataSetValue(frame().memberIndex("\$ir"), top.mcfpp.nbt.tags.CompoundTag()))
            for ((position, instruction) in block.instructions.withIndex()) {
                if (Project.config.debugInfo) debugCommands[commands.size] = debugPoint(bindings.debugSites[block.id to position], block.id, position)
                when (instruction) {
                is Instruction.Declare -> {
                    val root = Place(instruction.symbol.id)
                    initialized.removeAll { it.root == root.root }
                    materializedPlaces.removeAll { it.root == root.root }
                    scorePlaces.removeAll { it.root == root.root }
                }
                is Instruction.Read -> if (nbt(symbols.getValue(instruction.place.root).declaredType) || instruction.place.path.isNotEmpty()) {
                    val snapshot = internal("read_${instruction.result}")
                    emit(Commands.dataSetFrom(snapshot, address(instruction.location)))
                    nbtResults[instruction.result] = snapshot
                } else if (runtime(ValueRef.Result(instruction.type, instruction.result))) {
                    val source = place(instruction.place)
                    val snapshot = if (mayReuseRead(instruction, block, position)) source else temporary(instruction.type)
                    if (snapshot != source) commands += "scoreboard players operation $snapshot = $source"
                    results[instruction.result] = snapshot
                }
                is Instruction.Write -> {
                    if (nbt(symbols.getValue(instruction.place.root).declaredType) || instruction.place.path.isNotEmpty()) {
                        materializedPlaces.add(instruction.place)
                        encode(address(instruction.location), instruction.value)
                        initialized.add(instruction.place)
                        continue
                    }
                    if (instruction.place !in storagePlaces && !runtime(instruction.value) && !symbols.getValue(instruction.place.root).forceRuntime) continue
                    materializedPlaces.add(instruction.place)
                    val destination = place(instruction.place)
                    val known = constant(instruction.value)
                    if (known != null && (instruction.value !is ValueRef.Result || instruction.value.instruction !in backendRuntime)) commands += "scoreboard players set $destination ${number(known)}"
                    else {
                        val source = score(instruction.value)
                        if (!optimizeCopies || source != destination) commands += "scoreboard players operation $destination = $source"
                    }
                    initialized.add(instruction.place)
                    scorePlaces.add(instruction.place)
                }
                is Instruction.Binary -> {
                    if (instruction.result in ir.fusedFloatResults) continue
                    if (!runtime(ValueRef.Result(instruction.type, instruction.result))) continue
                    if (instruction.left.type == float && instruction.right.type == float) {
                        if (FloatProviders.enabled) {
                            val left = floatProvider(instruction.left)
                            val right = floatProvider(instruction.right)
                            if (instruction.type == float) computeFloat(instruction.result,
                                FloatProviders.arithmeticProvider(left, right, instruction.operation))
                            else {
                                val result = temporary(bool)
                                commands += "execute store success score $result ${FloatProviders.comparisonClause(left, right, instruction.operation)}"
                                results[instruction.result] = result
                            }
                        } else {
                            val left = legacyFloat(instruction.left)
                            val right = legacyFloat(instruction.right)
                            if (instruction.type == float) {
                                val destination = internal("float_${instruction.result}")
                                LegacyFloatCommands.arithmetic(left, right, instruction.operation, destination, ::emit)
                                nbtResults[instruction.result] = destination
                            } else {
                                val result = temporary(bool)
                                LegacyFloatCommands.comparison(left, right, instruction.operation,
                                    temporary(int).toString(), result.toString(), ::emit)
                                results[instruction.result] = result
                            }
                        }
                        continue
                    }
                    if (immediateArithmetic(instruction)) continue
                    val left = score(instruction.left)
                    val right = score(instruction.right)
                    val result = scoreDestinations[instruction.result] ?: temporary(instruction.type)
                    results[instruction.result] = result
                    when (instruction.operation) {
                        "+", "-", "*", "&&" -> {
                            if (!optimizeCopies || result != left) commands += "scoreboard players operation $result = $left"
                            val operation = if (instruction.operation == "&&") "*" else instruction.operation
                            commands += "scoreboard players operation $result $operation= $right"
                        }
                        "||" -> {
                            commands += "scoreboard players set $result 0"
                            commands += "execute if score $left matches 1 run scoreboard players set $result 1"
                            commands += "execute if score $right matches 1 run scoreboard players set $result 1"
                        }
                        else -> {
                            commands += "scoreboard players set $result 0"
                            val operation = if (instruction.operation in setOf("==", "!=")) "=" else instruction.operation
                            val condition = if (instruction.operation == "!=") "unless" else "if"
                            commands += "execute $condition score $left $operation $right run scoreboard players set $result 1"
                        }
                    }
                }
                is Instruction.Promote -> {
                    if (!runtime(ValueRef.Result(instruction.type, instruction.result))) continue
                    val source = score(instruction.value)
                    floatFromInt(instruction.result, source)
                }
                is Instruction.Convert -> convert(instruction)
                is Instruction.RawCommand -> {
                    // Raw observers see the current assigned values, including score-backed locals.
                    for (location in initialized.filter { it.path.isEmpty() && it in scorePlaces &&
                        (!Project.config.optimization.enabled(top.mcfpp.optimization.PassIds.FRAMES) ||
                            EffectAnalysis.rawCommandEffect(instruction.command) == Effect.Unknown) }) {
                        val type = symbols.getValue(location.root).declaredType
                        if (type == int || type == bool) emit(Command("execute store result")
                            .build(address(location).toCommandPart())
                            .build("${if (type == bool) "byte" else "int"} 1 run scoreboard players get ${place(location)}"))
                    }
                    commands.add(instruction.command)
                    constants.clear()
                    // SSA result registers are captured values, not reusable reads of a Place.
                    // New Instruction.Read operations always load the actual current score.
                }
                is Instruction.CaptureKey -> {
                    val destination = internal("map_key_${instruction.result}")
                    emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                    encode(destination.memberIndex("key"), instruction.value)
                }
                is Instruction.MapMember -> map(instruction)
                is Instruction.MapProjection -> projection(instruction, position)
                is Instruction.ListMember -> {
                    if (instruction.operation.search) { search(instruction, position); continue }
                    val destination = address(instruction.receiver)
                    val id = callNumber++
                    val source = internal("list_value_$id")
                    instruction.argument?.let { encode(source, it) }
                    val index = instruction.index?.let { value ->
                        instruction.knownIndex?.let(::MCInt) ?: MCInt("list_index_$id").apply {
                            nbtPath = internal(identifier); isDataOnly = true
                            encode(nbtPath, value)
                            StorageAccess.publishNbt(this)
                        }
                    }
                    when (instruction.operation) {
                        ListOperation.CLEAR -> emit(Commands.dataSetValue(destination, ListTag()))
                        ListOperation.REMOVE_AT -> emit(Command("data remove").build(destination.intIndex(index!!).toCommandPart()))
                        else -> {
                            val command = Command("data modify").build(destination.toCommandPart())
                            when (instruction.operation) {
                                ListOperation.APPEND, ListOperation.APPEND_ALL -> command.build("append")
                                ListOperation.PREPEND, ListOperation.PREPEND_ALL -> command.build("prepend")
                                ListOperation.INSERT -> {
                                    command.build("insert")
                                    if (instruction.knownIndex != null) command.build(instruction.knownIndex.toString()) else command.buildMacro(index!!)
                                }
                                else -> error("Not an insertion")
                            }
                            emit(command.build("from").build((if (instruction.operation.bulk) source.iteratorIndex() else source).toCommandPart()))
                        }
                    }
                }
                is Instruction.DictionaryMember -> {
                    val destination = address(instruction.receiver)
                    when (instruction.operation) {
                        DictionaryOperation.CLEAR -> emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                        DictionaryOperation.REMOVE -> emit(Command("data remove").build(destination.memberIndex(StorageAccess.quotedKey(instruction.key!!)).toCommandPart()))
                        DictionaryOperation.MERGE -> {
                            val source = internal("merge_${callNumber++}")
                            encode(source, instruction.argument!!)
                            emit(Command("data modify").build(destination.toCommandPart()).build("merge from").build(source.toCommandPart()))
                        }
                        DictionaryOperation.CONTAINS_KEY -> {
                            val result = temporary(bool)
                            emit(Command("execute store success score $result if data")
                                .build(destination.memberIndex(StorageAccess.quotedKey(instruction.key!!)).toCommandPart()))
                            results[instruction.result!!] = result
                        }
                    }
                }
                is Instruction.CaptureIndex -> {
                    val index = temporary(int)
                    val length = temporary(int)
                    commands += "scoreboard players operation $index = ${score(instruction.value)}"
                    emit(Command("execute store result score $length run data get")
                        .build(address(instruction.container).toCommandPart()).build("1"))
                    commands += "execute if score $index matches ..-1 run scoreboard players operation $index += $length"
                    commands += "execute store result ${internal("index_${instruction.result}").toCommandPart()} int 1 run scoreboard players get $index"
                }
                is Instruction.Construct -> {
                    val destination = path(instruction.place)
                    val literal = constant(ValueRef.Result(instruction.type, instruction.result))?.takeUnless { instruction.result in backendRuntime }
                        ?.let { StorageAccess.snapshotTag(it, instruction.type) }
                    if (literal != null) emit(Commands.dataSetValue(destination, literal))
                    else if (instruction.sequence) {
                        val empty = if (TypeRelations.arrayElementType(instruction.type) != null)
                            types.getValue(instruction.type).defaultValue() as Tag<*> else ListTag()
                        emit(Commands.dataSetValue(destination, empty))
                        for ((segment, value) in instruction.parts) {
                            val source = internal("part_${instruction.result}_${(segment as PathSegment.Index).index}")
                            encode(source, value)
                            commands += "data modify ${destination.toCommandPart()} append from ${source.toCommandPart()}"
                        }
                    } else {
                        emit(Commands.dataSetValue(destination, top.mcfpp.nbt.tags.CompoundTag()))
                        instruction.parts.forEach { (segment, value) ->
                            encode(destination.memberIndex(StorageAccess.quotedKey((segment as PathSegment.Field).name)), value)
                        }
                    }
                    nbtResults[instruction.result] = destination
                    initialized.add(instruction.place)
                    materializedPlaces.add(instruction.place)
                }
                is Instruction.View -> {
                    if (nbt(instruction.value.type)) {
                        nbtResults[instruction.result] = (instruction.value.source as? ValueRef.Result)?.let { nbtResults[it.instruction] }
                            ?: address(instruction.value.location)
                    } else {
                        viewResults[instruction.result] = instruction.value
                        score(ValueRef.Result(instruction.value.type, instruction.result))
                    }
                }
                is Instruction.Call -> call(instruction, position)
                else -> error("Unsupported primitive instruction")
            }
            }
            if (Project.config.debugInfo) debugCommands[commands.size] =
                if (block.terminator is Terminator.Jump) null
                else debugPoint(bindings.debugExits[block.id], block.id, block.instructions.size)
            fun jump(target: Int) {
                val prefix = if (supportsReturn) "return run function" else "function"
                commands += "$prefix ${destinations.getValue(target).namespaceID}"
            }
            when (val terminator = block.terminator) {
                is Terminator.Jump -> jump(terminator.block)
                is Terminator.Branch -> {
                    val known = constant(terminator.condition) as? CompilerValue.Bool
                    if (known != null && (terminator.condition !is ValueRef.Result || terminator.condition.instruction !in backendRuntime)) jump(if (known.value) terminator.whenTrue else terminator.whenFalse)
                    else if (!supportsReturn) {
                        // Each recursive branch owns a guard frame. A loop cannot overwrite
                        // the caller's guard and accidentally execute its opposite branch.
                        val condition = score(terminator.condition)
                        commands += "data modify storage mcfpp:system ir_branch_stack prepend value {condition:0b}"
                        commands += "execute store result storage mcfpp:system ir_branch_stack[0].condition byte 1 run scoreboard players get $condition"
                        commands += "execute if data storage mcfpp:system ir_branch_stack[0]{condition:1b} run function ${destinations.getValue(terminator.whenTrue).namespaceID}"
                        commands += "execute unless data storage mcfpp:system ir_branch_stack[0]{condition:1b} run function ${destinations.getValue(terminator.whenFalse).namespaceID}"
                        commands += "data remove storage mcfpp:system ir_branch_stack[0]"
                    }
                    else {
                        commands += "execute if score ${score(terminator.condition)} matches 1 run return run function ${destinations.getValue(terminator.whenTrue).namespaceID}"
                        jump(terminator.whenFalse)
                    }
                }
                is Terminator.Return -> {
                    terminator.value?.let { value ->
                        val destination = frame().memberIndex("return")
                        if (function.optimizedScalar) {
                            val destinationScore = returnScore(function)
                            val known = constant(value)
                            if (known != null && (value !is ValueRef.Result || value.instruction !in backendRuntime))
                                commands += "scoreboard players set $destinationScore ${number(known)}"
                            else {
                                val source = score(value)
                                if (!optimizeCopies || source != destinationScore) commands += "scoreboard players operation $destinationScore = $source"
                            }
                        }
                        else if (!FloatProviders.enabled && function.returnVar is MCFloat)
                            emit(Commands.dataSetFrom(destination, legacyFloat(value)))
                        else encode(destination, value)
                    }
                    if ((hasControlFlow || terminator.value != null) && supportsReturn) {
                        commands += "return 0"
                        val destination = destinations.getValue(block.id)
                        exits.add(Function.FrameExit(destination, destination.commands.size + commands.size - 1))
                    }
                }
                Terminator.Unreachable -> error("Reachable block without terminator")
            }
            val destination = destinations.getValue(block.id)
            destination.runInFunction { commands.forEachIndexed { index, text ->
                val command = Command(text)
                if (Project.config.debugInfo) {
                    command.debugPointCaptured = true
                    command.debugPoint = debugCommands.entries.lastOrNull { it.key <= index }?.value
                }
                Function.addCommand(command)
            } }
        }
        function.frameExits.addAll(exits)
    }

    private fun debugPoint(site: PrimitiveDebugSite?, block: Int, position: Int): top.mcfpp.debug.DebugPoint? {
        site ?: return null
        val state = typeFacts.beforeWrites[block to position] ?: typeFacts.entries[block]
        val variables = site.variables.map { symbol ->
            val place = Place(symbol.id)
            val type = types[symbol.declaredType]?.typeName ?: symbol.declaredType.toString()
            val constant = if (!symbol.mutable) state?.read(place)?.value as? ValueKnowledge.Constant else null
            if (constant != null) top.mcfpp.debug.DebugVariable(symbol.name, type, "constant",
                value = top.mcfpp.debug.DebugMetadata.constantJson(constant.value))
            else {
                val address = address(bindings.location(symbol))
                val layout = layout(place).let { if (it is StorageLayout.Nbt) StorageLayout.Nbt(address.source.toCommand().toString(), address.pathToCommandPart().toString()) else it }
                top.mcfpp.debug.DebugMetadata.variable(symbol.name, type, layout, address.toCommandPart().toString())
            }
        }
        return top.mcfpp.debug.DebugMetadata.source(function, site.context, variables)
    }
}
