package top.mcfpp.analysis

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool

/** A live address adapter. Facts and synchronization caches contain immutable values/layouts. */
data class StorageBinding(
    val data: StoredData,
    val place: Place,
    val path: NBTPath,
    val view: ValueRef.TypedView? = null,
    val trustConstants: Boolean = true
) {
    fun field(name: String) = copy(place = place.field(name), path = path.memberIndex(name))
}

class StoredData(val root: Place, val path: NBTPath, private var initialize: (() -> Unit)? = null) {
    val facts = FlowFacts()
    val versions = StorageVersions()
    val types = mutableMapOf<TypeId, MCFPPType>()
    private val registers = mutableMapOf<Pair<Place, TypeId>, StorageLayout.Scoreboard>()
    private val nbtLayout get() = StorageLayout.Nbt(path.source.toString(), path.toCommandPart().toString())

    fun materialize() {
        initialize?.let { it(); initialize = null }
        versions.materialize(root, nbtLayout)
    }

    fun register(place: Place, type: TypeId, objective: String): StorageLayout.Scoreboard =
        registers.getOrPut(place to type) { StorageLayout.Scoreboard(TempPool.getVarIdentify(), objective) }

    fun write(place: Place, fact: ValueFacts) {
        versions.invalidate(place)
        facts.write(place, fact)
    }

    fun barrier() {
        materialize()
        versions.invalidate(root)
        facts.barrier()
    }
}

/** Central boundary between Place/TypedView and the remaining Var-based backends. */
object StorageAccess {
    private val erasedTypes get() = setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)

    fun ensure(value: Var<*>): StorageBinding {
        value.storageBinding?.let { return it }
        val snapshot = ValueSnapshot.of(value)
        val frozen = constantEncoding(value)?.let { Tag.toSNBT(it) }
        if (value.symbol == null && snapshot != null) value.hasAssigned = true
        value.bindDeclaration()
        if (value.nbtPath.pathList.isEmpty()) value.nbtPath = NBTPath.getNormalStackPath(value)
        val place = Place(value.symbol!!.id)
        val path = value.nbtPath.clone()
        // Capture constants and physical addresses now. A delayed write never captures a mutable Var.
        val initial: (() -> Unit)? = if (frozen != null) ({ Function.addCommand(Commands.dataSetValue(path, Tag.toNBT(frozen))) })
            else when (value) {
                is MCInt -> if (!value.isDataOnly) scoreWriter(path, value.name, value.sbObject.toString(), numericTag(value.type)) else null
                is ScoreBool -> if (!value.isDataOnly) scoreWriter(path, value.name, value.boolObject.toString(), "byte") else null
                is MCFloat -> if (!FloatProviders.enabled) {
                    val parts = listOf("sign" to value.sign, "int0" to value.int0, "int1" to value.int1, "exp" to value.exp)
                        .map { (key, score) -> key to (score.name to score.sbObject.toString()) }
                    ({
                        Function.addCommand(Commands.dataSetValue(path, CompoundTag()))
                        parts.forEach { (key, score) -> scoreWriter(path.memberIndex(key), score.first, score.second, "int")() }
                    })
                } else null
                else -> null
            }
        val data = StoredData(place, path, initial)
        data.types[actualType(value).typeId] = actualType(value)
        val binding = StorageBinding(data, place, path)
        data.facts.write(place, ValueFacts(if (value is MCAny) value.typeKnowledge else TypeKnowledge.Exact(value.type.typeId),
            snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown,
            if (value.symbol != null && !value.hasAssigned) ValueState.UNINITIALIZED else ValueState.INITIALIZED))
        if (value is DataTemplateObject) seedFields(data, place, value)
        value.storageBinding = binding
        return binding
    }

    private fun seedFields(data: StoredData, parent: Place, value: DataTemplateObject) {
        for (field in value.instanceField.allVars.filterNot { it.isStatic }) {
            val place = parent.field(field.identifier)
            val snapshot = ValueSnapshot.of(field)
            data.types[field.type.typeId] = field.type
            data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(field.type.typeId),
                snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown))
            if (field is DataTemplateObject) seedFields(data, place, field)
        }
    }

    fun view(source: Var<*>, target: MCFPPType, diagnose: Boolean = true): Var<*> {
        if (source.isError) return source
        if (!target.hasRuntimeRepresentation || !actualType(source).hasRuntimeRepresentation) {
            if (target == actualType(source) && source is MCAny && source.compilerPayload != null) return source.compilerPayload!!
            if (target in erasedTypes) return source.implicitCast(target)
            return error(target, "Compiler-only value has no storage layout accessible as '$target'")
        }
        val compatibility = TypeRelations.checkReinterpretation(source.type, target)
        if (diagnose && compatibility is ReinterpretationCompatibility.Result.Unproven && (source !is MCAny || source is MCObject))
            LogProcessor.warn("Unproven reinterpretation from '${source.type}' to '$target': ${compatibility.reason}")
        val binding = ensure(source)
        val ref = ValueRef.TypedView(target.typeId, ValueRef.Read(source.type.typeId, binding.place), binding.place)
        val trusted = binding.trustConstants && (actualType(source) == target ||
            source !is MCAny && compatibility is ReinterpretationCompatibility.Result.Compatible)
        val re = adapter(target, source.identifier, binding.copy(view = ref, trustConstants = trusted))
        re.symbol = source.symbol
        re.isConst = source.isConst
        re.hasAssigned = source.hasAssigned
        if (re is MCAny) re.payloadType = (source as? MCAny)?.inferredType ?: source.type
        if (trusted && snapshot(re) != null) {
            re.isDynamic = source.isDynamic
            return read(re)
        }
        return re
    }

    fun adapter(type: MCFPPType, name: String, binding: StorageBinding): Var<*> {
        val value = type.buildUnConcrete(name)
        value.storageBinding = if (binding.view != null) binding.copy(view = ValueRef.TypedView(type.typeId,
            if (binding.view.place == binding.place) binding.view.source else ValueRef.Read(
                (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type ?: type.typeId, binding.place),
            binding.place)) else binding
        value.nbtPath = binding.path.clone()
        value.hasAssigned = true
        value.isDynamic = true
        return value
    }

    fun snapshot(value: Var<*>): CompilerValue? {
        val binding = value.storageBinding ?: return null
        if (!binding.trustConstants) return null
        val fact = binding.data.facts.read(binding.place) ?: return null
        if (value.type !in erasedTypes && fact.type != TypeKnowledge.Exact(value.type.typeId) && value !is DataTemplateObject) return null
        val constant = (fact.value as? ValueKnowledge.Constant)?.value ?: return null
        if (constant is CompilerValue.Typed && constant.type == value.type.typeId) return constant
        val payload = if (value.type in erasedTypes) {
            if (constant is CompilerValue.Typed) constant else (fact.type as? TypeKnowledge.Exact)?.type
                ?.let { CompilerValue.Typed(it, constant) } ?: return null
        } else if (constant is CompilerValue.Typed) constant.payload else constant
        return CompilerValue.Typed(value.type.typeId, payload)
    }

    /** Loading a register is materialization, not a logical write. */
    fun read(value: Var<*>): Var<*> {
        val binding = value.storageBinding ?: return value
        val data = binding.data
        val version = data.versions.version(binding.place)
        if (value.storageReadVersion == version) return value
        if (value is DataTemplateObject) return if (value is MCFPPValue<*> && snapshot(value) == null)
            adapter(value.type, value.identifier, binding).apply { setAs(value); storageReadVersion = version } else value
        if (value is MCAny) return value
        val constant = snapshot(value)
        if (constant != null && !value.isDynamic) {
            restore(value.type, constant, value.identifier)?.let { re ->
                re.setAs(value)
                re.storageReadVersion = version
                return re
            }
        }
        val re = adapter(value.type, value.identifier, binding).apply { setAs(value); storageReadVersion = version }
        data.materialize()
        when (re) {
            is MCInt -> {
                val register = data.register(binding.place, re.type.typeId, re.sbObject.toString())
                re.name = register.player
                re.isDataOnly = false
                loadScore(binding, register)
            }
            is ScoreBool -> {
                val register = data.register(binding.place, re.type.typeId, re.boolObject.toString())
                re.name = register.player
                re.isDataOnly = false
                loadScore(binding, register)
            }
            is MCFloat -> if (!FloatProviders.enabled) {
                if ((data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type in
                    setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId, MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId))
                    return error(re.type, "Legacy float requires its four-component layout; use toFloat(value) for numeric conversion")
                val registers = listOf("sign" to re.sign, "int0" to re.int0, "int1" to re.int1, "exp" to re.exp)
                for ((key, score) in registers) {
                    val field = binding.field(key)
                    val register = data.register(field.place, re.type.typeId, score.sbObject.toString())
                    score.name = register.player
                    loadScore(field, register)
                }
            }
        }
        return re
    }

    private fun loadScore(binding: StorageBinding, layout: StorageLayout.Scoreboard) {
        if (binding.data.versions.isMaterialized(binding.place, layout)) return
        Function.addCommand(Command("execute store result score ${layout.player} ${layout.objective} run data get")
            .build(binding.path.toCommandPart()).build("1"))
        binding.data.versions.materialize(binding.place, layout)
    }

    fun write(target: Var<*>, source: Var<*>): Var<*> {
        val binding = target.storageBinding ?: error("Missing storage binding")
        binding.data.materialize()
        encodeTo(binding.path, source)
        val snapshot = ValueSnapshot.of(source)
        binding.data.types[actualType(source).typeId] = actualType(source)
        binding.data.write(binding.place, ValueFacts(if (source is MCAny) source.typeKnowledge else TypeKnowledge.Exact(source.type.typeId),
            snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown))
        return adapter(target.type, target.identifier, binding).apply {
            setAs(target)
            hasAssigned = true
            storageReadVersion = null
            if (this is MCAny) payloadType = (source as? MCAny)?.inferredType ?: source.type
        }
    }

    fun materialize(value: Var<*>) { ensure(value).data.materialize() }

    fun capture(value: Var<*>): Var<*> {
        val loaded = read(value)
        val captured = if (loaded is MCFloat) {
            val constant = (snapshot(loaded) as? CompilerValue.Typed)?.payload as? CompilerValue.FloatBits
            if (!loaded.isDynamic && loaded is MCFloatConcrete) MCFloatConcrete(loaded.value).apply { isTemp = true }
            else if (!loaded.isDynamic && constant != null) MCFloatConcrete(Float.fromBits(constant.bits)).apply { isTemp = true }
            else if (FloatProviders.enabled) FloatProviders.snapshot(loaded)
            else MCFloat().apply { isTemp = true }.assignedBy(loaded)
        } else if (loaded is MCInt || loaded is ScoreBool || loaded is NBTBasedData) {
            loaded.type.buildUnConcrete(TempPool.getVarIdentify()).apply {
                isTemp = true
                nbtPath = NBTPath.temp.memberIndex(identifier)
            }.assignedBy(loaded)
        } else loaded.getTempVar()
        if (captured.nbtPath.pathList.isEmpty()) captured.nbtPath = NBTPath.getNormalStackPath(captured)
        return captured
    }

    /** Legacy natives can mutate a concrete host container; commit that change before dropping its facts. */
    fun hostSnapshot(value: Var<*>): CompilerValue? {
        if (value !is MCFPPValue<*> || !actualType(value).hasRuntimeRepresentation) return null
        return ValueSnapshot.of(value.clone().apply { storageBinding = null; symbol = null })
    }

    fun commitHostChanges(before: List<Pair<Var<*>, CompilerValue>>) {
        for ((value, old) in before) {
            val changed = hostSnapshot(value) ?: continue
            if (changed == old) continue
            val binding = value.storageBinding ?: continue
            val tag = snapshotTag(changed) ?: continue
            Function.addCommand(Commands.dataSetValue(binding.path, tag))
            binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(actualType(value).typeId), ValueKnowledge.Constant(changed)))
        }
    }

    data class Spill(val value: Var<*>, val path: NBTPath)

    /** Expression temporaries outlive calls but must not share the callee's scratch slots. */
    fun spill(values: Collection<Var<*>>): List<Spill> = values.distinct().mapNotNull { value ->
        if (!value.isTemp || value.isError || !actualType(value).hasRuntimeRepresentation || ValueSnapshot.of(value) != null) return@mapNotNull null
        val slot = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        encodeTo(slot, value)
        Spill(value, slot)
    }

    fun restore(spills: List<Spill>) {
        for ((value, slot) in spills) {
            fun score(player: String, objective: String, path: NBTPath = slot) {
                Function.addCommand(Command("execute store result score $player $objective run data get").build(path.toCommandPart()).build("1"))
            }
            when (value) {
                is MCInt -> score(value.name, value.sbObject.toString())
                is ScoreBool -> score(value.name, value.boolObject.toString())
                is MCFloat -> if (!FloatProviders.enabled) {
                    for ((key, part) in listOf("sign" to value.sign, "int0" to value.int0, "int1" to value.int1, "exp" to value.exp))
                        score(part.name, part.sbObject.toString(), slot.memberIndex(key))
                } else Function.addCommand(Commands.dataSetFrom(value.nbtPath, slot))
                else -> Function.addCommand(Commands.dataSetFrom(value.nbtPath, slot))
            }
            value.storageBinding?.let {
                if (value is MCInt || value is ScoreBool || value is MCFloat && !FloatProviders.enabled)
                    Function.addCommand(Commands.dataSetFrom(it.path, slot))
                it.data.versions.invalidate(it.place)
                value.storageReadVersion = it.data.versions.version(it.place)
            }
        }
    }

    /** Rebase an address while a callee frame is active; data identity and write versions stay shared. */
    fun callerValue(value: Var<*>): Var<*> = value.clone().apply {
        val first = nbtPath.pathList.firstOrNull() as? top.mcfpp.lib.MemberPath
        val member = first?.value as? top.mcfpp.core.lang.nbt.MCStringConcrete
        val text = member?.value?.value
        val index = text?.let { Regex("stack_frame\\[(\\d+)]").matchEntire(it) }?.groupValues?.get(1)?.toInt()
        if (index != null) {
            nbtPath.pathList[0] = top.mcfpp.lib.MemberPath(top.mcfpp.core.lang.nbt.MCStringConcrete(
                top.mcfpp.nbt.tags.primitive.StringTag("stack_frame[${index + 1}]")))
        } else if (text == "stack_frame") {
            val element = nbtPath.pathList.getOrNull(1) as? top.mcfpp.lib.IntPath
            val frame = element?.value as? MCIntConcrete
            if (frame != null) nbtPath.pathList[1] = top.mcfpp.lib.IntPath(MCIntConcrete(frame.value + 1))
        }
        storageBinding = storageBinding?.copy(path = nbtPath.clone())
    }

    fun restoreScore(value: Var<*>, player: String, objective: String): Boolean {
        val binding = value.storageBinding ?: return false
        binding.data.materialize()
        loadScore(binding, StorageLayout.Scoreboard(player, objective))
        return true
    }

    fun barrier(values: Collection<Var<*>>) {
        values.mapNotNull { it.storageBinding?.data }.distinct().forEach(StoredData::barrier)
    }

    fun flush(values: Collection<Var<*>>) {
        values.filter { it.hasAssigned && actualType(it).hasRuntimeRepresentation }
            .map { ensure(it).data }.distinct().forEach(StoredData::materialize)
    }

    fun visibleValues(scope: top.mcfpp.model.scope.IScope): List<Var<*>> {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<top.mcfpp.model.scope.IScope, Boolean>())
        val values = mutableListOf<Var<*>>()
        fun collect(current: top.mcfpp.model.scope.IScope) {
            if (!visited.add(current)) return
            if (current is top.mcfpp.model.scope.IScopeWithVar) values.addAll(current.allVars)
            current.parent.filterNotNull().forEach(::collect)
        }
        collect(scope)
        return values
    }

    fun encodeTo(path: NBTPath, source: Var<*>) {
        if (!actualType(source).hasRuntimeRepresentation) {
            LogProcessor.error("Compiler-only value '${actualType(source)}' cannot be stored in an erased runtime payload")
            return
        }
        source.storageBinding?.let { binding ->
            binding.data.materialize()
            if (path.toCommandPart().toString() != binding.path.toCommandPart().toString())
                Function.addCommand(Commands.dataSetFrom(path, binding.path))
            return
        }
        constantEncoding(source)?.let { Function.addCommand(Commands.dataSetValue(path, it)); return }
        when (source) {
            is MCInt -> if (source.isDataOnly) Function.addCommand(Commands.dataSetFrom(path, source.nbtPath))
                else scoreWriter(path, source.name, source.sbObject.toString(), numericTag(source.type))()
            is ScoreBool -> if (source.isDataOnly) Function.addCommand(Commands.dataSetFrom(path, source.nbtPath))
                else scoreWriter(path, source.name, source.boolObject.toString(), "byte")()
            is BaseBool -> encodeTo(path, source.toScoreBool(false))
            is MCFloat -> if (FloatProviders.enabled) Function.addCommand(Commands.dataSetFrom(path, source.nbtPath)) else {
                Function.addCommand(Commands.dataSetValue(path, CompoundTag()))
                for ((key, score) in listOf("sign" to source.sign, "int0" to source.int0, "int1" to source.int1, "exp" to source.exp))
                    scoreWriter(path.memberIndex(key), score.name, score.sbObject.toString(), "int")()
            }
            is DataTemplateObject, is NBTBasedData, is MCAny -> Function.addCommand(Commands.dataSetFrom(path, source.nbtPath))
            else -> {
                source.storeToStack()
                Function.addCommand(Commands.dataSetFrom(path, source.nbtPath))
            }
        }
    }

    fun constantEncoding(value: Var<*>): Tag<*>? {
        if (value.storageBinding != null) {
            val frozen = snapshot(value) ?: return null
            return snapshotTag(frozen)
        }
        if (ValueSnapshot.of(value) == null) return null
        if (value is MCFloatConcrete && !FloatProviders.enabled) {
            val parts = MCFloat.floatToMCFloat(value.value)
            return CompoundTag().apply { for ((i, key) in listOf("sign", "int0", "int1", "exp").withIndex()) put(key, IntTag(parts[i])) }
        }
        return NBTUtil.varToNBT(value)?.let { Tag.toNBT(Tag.toSNBT(it)) }
    }

    private fun scoreWriter(path: NBTPath, player: String, objective: String, tag: String): () -> Unit = {
        Function.addCommand(Command("execute store result").build(path.toCommandPart())
            .build("$tag 1 run scoreboard players get $player $objective"))
    }

    private fun snapshotTag(value: CompilerValue, type: TypeId? = null): Tag<*>? = when (value) {
        is CompilerValue.Typed -> snapshotTag(value.payload, value.type)
        is CompilerValue.Integral -> when (type) {
            MCFPPNBTType.Byte.typeId -> top.mcfpp.nbt.tags.primitive.ByteTag(value.value.toByte())
            MCFPPNBTType.Short.typeId -> top.mcfpp.nbt.tags.primitive.ShortTag(value.value.toShort())
            MCFPPNBTType.Long.typeId -> top.mcfpp.nbt.tags.primitive.LongTag(value.value)
            else -> IntTag(value.value.toInt())
        }
        is CompilerValue.Bool -> top.mcfpp.nbt.tags.primitive.ByteTag(value.value)
        is CompilerValue.Nbt -> Tag.toNBT(value.snbt)
        is CompilerValue.Text -> top.mcfpp.nbt.tags.primitive.StringTag(value.value)
        is CompilerValue.FloatBits -> if (FloatProviders.enabled) top.mcfpp.nbt.tags.primitive.FloatTag(Float.fromBits(value.bits)) else {
            val parts = MCFloat.floatToMCFloat(Float.fromBits(value.bits))
            CompoundTag().apply { for ((i, key) in listOf("sign", "int0", "int1", "exp").withIndex()) put(key, IntTag(parts[i])) }
        }
        is CompilerValue.DoubleBits -> top.mcfpp.nbt.tags.primitive.DoubleTag(Double.fromBits(value.bits))
        is CompilerValue.Sequence -> {
            val elements = value.elements.map { snapshotTag(it) }
            if (elements.any { it == null }) null else top.mcfpp.nbt.tags.collection.ListTag().apply {
                elements.forEach { add(it!!) }
            }
        }
        is CompilerValue.Record -> {
            val fields = value.fields.mapValues { snapshotTag(it.value) }
            if (fields.values.any { it == null }) null else CompoundTag().apply { fields.forEach { (key, tag) -> put(key, tag!!) } }
        }
        else -> null
    }

    private fun numericTag(type: MCFPPType) = when (type) {
        MCFPPNBTType.Byte -> "byte"
        MCFPPNBTType.Short -> "short"
        else -> "int"
    }

    fun actualType(value: Var<*>) = if (value is MCAny) value.inferredType ?: value.type else value.type

    private fun restore(type: MCFPPType, snapshot: CompilerValue, name: String): Var<*>? {
        val payload = if (snapshot is CompilerValue.Typed) snapshot.payload else snapshot
        val raw: Any = when (payload) {
            is CompilerValue.Typed -> return restore(type, payload, name)
            is CompilerValue.Integral -> when (type) {
                MCFPPNBTType.Byte -> payload.value.toByte()
                MCFPPNBTType.Short -> payload.value.toShort()
                else -> payload.value.toInt()
            }
            is CompilerValue.Bool -> payload.value
            is CompilerValue.FloatBits -> Float.fromBits(payload.bits)
            is CompilerValue.Nbt -> Tag.toNBT(payload.snbt)
            is CompilerValue.Text -> top.mcfpp.nbt.tags.primitive.StringTag(payload.value)
            else -> return null
        }
        if (type is MCFPPDataTemplateType || type in erasedTypes || type == MCFPPBaseType.JsonText) return null
        return type.build(name, raw)
    }

    private fun error(type: MCFPPType, message: String): Var<*> {
        LogProcessor.error("Cannot generate reinterpretation access: $message; use a conversion function when a value conversion is intended")
        return UnknownVar(TempPool.getVarIdentify()).apply { this.type = type; isError = true }
    }
}
