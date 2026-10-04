package top.mcfpp.analysis

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete
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

class StoredData(val root: Place, val path: NBTPath, private var initialize: (() -> Unit)? = null,
                 val layout: StorageLayout = StorageLayout.Nbt(path.source.toString(), path.toCommandPart().toString())) {
    val facts = FlowFacts()
    val versions = StorageVersions()
    val types = mutableMapOf<TypeId, MCFPPType>()
    val listSizes = mutableMapOf<Place, Int>()
    private val registers = mutableMapOf<Pair<Place, TypeId>, StorageLayout.Scoreboard>()
    fun materialize() {
        if (layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only place cannot be materialized")
            return
        }
        initialize?.let { it(); initialize = null }
        versions.materialize(root, layout)
    }

    fun register(place: Place, type: TypeId, objective: String): StorageLayout.Scoreboard =
        if (PathSegment.UnknownIndex in place.path) StorageLayout.Scoreboard(TempPool.getVarIdentify(), objective)
        else registers.getOrPut(place to type) { StorageLayout.Scoreboard(TempPool.getVarIdentify(), objective) }

    fun write(place: Place, fact: ValueFacts) {
        versions.invalidate(place)
        if (layout == StorageLayout.CompilerOnly) facts.writeConstant(place, fact) else facts.write(place, fact)
        listSizes.keys.removeAll { it.overlaps(place) && it.path.size >= place.path.size }
    }

    fun barrier() {
        if (layout == StorageLayout.CompilerOnly) return
        materialize()
        versions.invalidate(root)
        facts.barrier()
        listSizes.clear()
    }
}

/** Central boundary between Place/TypedView and the remaining Var-based backends. */
object StorageAccess {
    private val erasedTypes get() = setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)

    fun ensure(value: Var<*>): StorageBinding {
        value.storageBinding?.let { return it }
        val encodingSupported = !hasRuntimeRepresentation(value) || collectionEncodingSupported(value)
        if (!encodingSupported) reportListEncoding()
        val snapshot = ValueSnapshot.of(value)
        val frozen = constantEncoding(value)?.let { top.mcfpp.backend.NbtEncoding.snbt(it) }
        if (value.symbol == null && (snapshot != null || value is NBTListConcrete || value is NBTDictionaryConcrete)) value.hasAssigned = true
        if (value.symbol == null && value.identifier.isBlank()) value.identifier = TempPool.getVarIdentify()
        value.bindDeclaration()
        if (value.nbtPath.pathList.isEmpty()) value.nbtPath = NBTPath.getNormalStackPath(value)
        val place = Place(value.symbol!!.id)
        val path = value.nbtPath.clone()
        // Capture constants and physical addresses now. A delayed write never captures a mutable Var.
        val initial: (() -> Unit)? = if (!encodingSupported || !hasRuntimeRepresentation(value)) null
            else if (frozen != null) ({ emit(Commands.dataSetValue(path, Tag.toNBT(frozen))) })
            else when (value) {
                is MCInt -> if (!value.isDataOnly) scoreWriter(path, value.name, value.sbObject.toString(), numericTag(value.type)) else null
                is ScoreBool -> if (!value.isDataOnly) scoreWriter(path, value.name, value.boolObject.toString(), "byte") else null
                is MCFloat -> if (!FloatProviders.enabled) {
                    val parts = listOf("sign" to value.sign, "int0" to value.int0, "int1" to value.int1, "exp" to value.exp)
                        .map { (key, score) -> key to (score.name to score.sbObject.toString()) }
                    ({
                        emit(Commands.dataSetValue(path, CompoundTag()))
                        parts.forEach { (key, score) -> scoreWriter(path.memberIndex(key), score.first, score.second, "int")() }
                    })
                } else null
                else -> partialWriter(value, path)
            }
        val data = if (hasRuntimeRepresentation(value)) StoredData(place, path, initial)
            else StoredData(place, path, layout = StorageLayout.CompilerOnly)
        data.types[actualType(value).typeId] = actualType(value)
        val binding = StorageBinding(data, place, path)
        data.facts.write(place, ValueFacts(if (value is MCAny) value.typeKnowledge else TypeKnowledge.Exact(value.type.typeId),
            snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown,
            if (value.symbol != null && !value.hasAssigned) ValueState.UNINITIALIZED else ValueState.INITIALIZED))
        seedParts(data, place, value)
        value.storageBinding = binding
        return binding
    }

    private fun seedParts(data: StoredData, parent: Place, value: Var<*>) {
        data.types[value.type.typeId] = value.type
        data.types[actualType(value).typeId] = actualType(value)
        if (value is MCFPPTypeVar) data.types[value.value.typeId] = value.value
        if (value is MCAny && value.compilerPayload != null) {
            seedParts(data, parent, value.compilerPayload!!)
            return
        }
        val parts = when (value) {
            is DataTemplateObject -> value.instanceField.allVars.filterNot { it.isStatic }.map { parent.field(it.identifier) to it }
            is NBTListConcrete -> {
                data.listSizes[parent] = value.value.size
                value.value.mapIndexed { index, element -> parent.index(index) to element }
            }
            is NBTDictionaryConcrete -> value.value.map { (key, element) -> parent.field(key) to element }
            else -> emptyList()
        }
        for ((place, part) in parts) {
            val snapshot = ValueSnapshot.of(part)
            data.types[actualType(part).typeId] = actualType(part)
            part.storageBinding?.data?.types?.let(data.types::putAll)
            data.facts.initialize(place, ValueFacts((part as? MCAny)?.typeKnowledge ?: TypeKnowledge.Exact(part.type.typeId),
                snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown))
            seedParts(data, place, part)
        }
    }

    private fun emit(command: Command) {
        if (command.isMacro && top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot access a runtime index without function macros")
            return
        }
        Function.addCommands(command.buildMacroFunction())
    }

    /** Freeze source codecs/addresses, including partially known containers, before delayed materialization. */
    private fun frozenWriter(value: Var<*>, path: NBTPath): () -> Unit {
        if (!hasRuntimeRepresentation(value)) {
            LogProcessor.error("Compiler-only value '${actualType(value)}' cannot be stored in a runtime collection")
            return {}
        }
        if (!collectionEncodingSupported(value)) { reportListEncoding(); return {} }
        constantEncoding(value)?.let { tag ->
            val snbt = top.mcfpp.backend.NbtEncoding.snbt(tag)
            return { emit(Commands.dataSetValue(path, Tag.toNBT(snbt))) }
        }
        value.storageBinding?.let { binding ->
            val source = binding.path.clone()
            val data = binding.data
            return { data.materialize(); emit(Commands.dataSetFrom(path, source)) }
        }
        return when (value) {
            is MCInt -> if (!value.isDataOnly) scoreWriter(path, value.name, value.sbObject.toString(), numericTag(value.type))
                else copyWriter(path, value.nbtPath)
            is ScoreBool -> if (!value.isDataOnly) scoreWriter(path, value.name, value.boolObject.toString(), "byte")
                else copyWriter(path, value.nbtPath)
            is NBTListConcrete, is NBTDictionaryConcrete -> partialWriter(value, path)!!
            is MCFloat -> if (FloatProviders.enabled) copyWriter(path, value.nbtPath) else {
                val writers = listOf("sign" to value.sign, "int0" to value.int0, "int1" to value.int1, "exp" to value.exp)
                    .map { (key, score) -> scoreWriter(path.memberIndex(key), score.name, score.sbObject.toString(), "int") }
                ({ emit(Commands.dataSetValue(path, CompoundTag())); writers.forEach { it() } })
            }
            else -> copyWriter(path, value.nbtPath)
        }
    }

    private fun copyWriter(destination: NBTPath, source: NBTPath): () -> Unit {
        val path = source.clone()
        return { emit(Commands.dataSetFrom(destination, path)) }
    }

    private fun partialWriter(value: Var<*>, path: NBTPath): (() -> Unit)? = when (value) {
        is NBTListConcrete -> {
            val elements = value.value.map { element ->
                val slot = NBTPath.temp.memberIndex(TempPool.getVarIdentify())
                frozenWriter(element, slot) to slot
            }
            ({
                emit(Commands.dataSetValue(path, top.mcfpp.nbt.tags.collection.ListTag()))
                elements.forEach { (writer, slot) -> writer(); emit(Commands.dataAppendFrom(path, slot)) }
            })
        }
        is NBTDictionaryConcrete -> {
            val fields = value.value.map { (key, element) -> frozenWriter(element, path.memberIndex(quotedKey(key))) }
            ({ emit(Commands.dataSetValue(path, CompoundTag())); fields.forEach { it() } })
        }
        else -> null
    }

    private fun quotedKey(key: String) = if (key.matches(Regex("[A-Za-z0-9_+-]+"))) key
        else top.mcfpp.backend.NbtEncoding.snbt(top.mcfpp.nbt.tags.primitive.StringTag(key))

    /** Ordinary collection assignment copies both encoding and immutable knowledge, with a new root identity. */
    fun copyCollection(target: Var<*>, source: Var<*>): Var<*> {
        val original = ensure(source)
        target.bindDeclaration()
        if (target.nbtPath.pathList.isEmpty()) target.nbtPath = NBTPath.getNormalStackPath(target)
        val place = Place(target.symbol!!.id)
        val path = target.nbtPath.clone()
        val frozen = constantEncoding(source)?.let { top.mcfpp.backend.NbtEncoding.snbt(it) }
        val data = if (original.data.layout == StorageLayout.CompilerOnly && !target.isDynamic) StoredData(place, path, layout = StorageLayout.CompilerOnly)
            else StoredData(place, path, frozen?.let { snbt -> { emit(Commands.dataSetValue(path, Tag.toNBT(snbt))) } })
        data.types.putAll(original.data.types)
        data.types[target.type.typeId] = target.type
        val root = original.data.facts.read(original.place) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown)
        // The copied value has the source's declared interpretation; its physical encoding is preserved.
        data.facts.initialize(place, root.copy(type = TypeKnowledge.Exact(target.type.typeId)))
        data.facts.copyFrom(original.data.facts, original.place, place, includeRoot = false)
        for ((key, size) in original.data.listSizes) if (key.root == original.place.root && key.path.take(original.place.path.size) == original.place.path)
            data.listSizes[Place(place.root, key.path.drop(original.place.path.size))] = size
        target.storageBinding = StorageBinding(data, place, path, trustConstants = original.trustConstants)
        if (data.layout == StorageLayout.CompilerOnly) return adapter(target.type, target.identifier, target.storageBinding!!).apply { setAs(target) }
        if (frozen == null) encodeTo(path, source)
        return target
    }

    fun element(container: Var<*>, index: Var<*>, type: MCFPPType): Var<*> {
        val payload = ValueSnapshot.of(index).let { if (it is CompilerValue.Typed) it.payload else it }
        val number = (payload as? CompilerValue.Integral)?.value?.toInt()
        val key = when (payload) {
            is CompilerValue.Text -> payload.value
            is CompilerValue.Nbt -> (Tag.toNBT(payload.snbt) as? top.mcfpp.nbt.tags.primitive.StringTag)?.value
            else -> null
        }
        if (index is MCString && key == null) {
            LogProcessor.error("Cannot generate dictionary access with an unknown string key: no verified NBT-path escaping backend is available")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        if (key?.isEmpty() == true && hasRuntimeRepresentation(container) && top.mcfpp.command.TargetCapabilities
                .forVersion(top.mcfpp.Project.config.version)?.emptyNbtPathKeys != true) {
            LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot traverse an empty NBT path key")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        if (!hasRuntimeRepresentation(container) && container.storageBinding == null) {
            val part = if (!index.isDynamic) when (container) {
                is NBTListConcrete -> number?.let { container.value.getOrNull(if (it < 0) container.value.size + it else it) }
                is NBTDictionaryConcrete -> key?.let { container.value[it] }
                else -> null
            } else null
            if (part != null && ValueSnapshot.of(part) != null) return part.clone().apply { parent = container }
            LogProcessor.error("Compiler-only collection access requires a known constant index and element")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        val root = ensure(container)
        if (root.data.layout == StorageLayout.CompilerOnly && (index.isDynamic || number == null && key == null)) {
            LogProcessor.error("Compiler-only collection access requires a known constant index and element")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        root.data.types[type.typeId] = type
        val size = root.data.listSizes[root.place]
        val normalized = number?.let { if (it < 0) size?.let { size -> size + it } else it }
        if (index is MCInt && normalized != null && size != null && normalized !in 0 until size) {
            LogProcessor.error("Index $number out of bounds for length $size")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        val place = if (index is MCInt && normalized != null) root.place.index(normalized)
            else if (index is MCString && key != null) root.place.field(key) else root.place.unknownIndex()
        val selected = if (key != null || !index.isDynamic && number != null) null else {
            // The evaluated index belongs to the caller's frame and survives later/recursive RHS calls.
            val captured = index.type.buildUnConcrete(TempPool.getVarIdentify()).apply {
                nbtPath = NBTPath.stack.intIndex(0).memberIndex(identifier)
                hasAssigned = true; isDynamic = true; isTemp = true
                if (this is MCInt) isDataOnly = true
            }
            encodeTo(captured.nbtPath, index)
            captured
        }
        val path = if (index is MCInt) if (selected != null) root.path.intIndex(selected as MCInt) else root.path.intIndex(number!!)
            else if (selected != null) root.path.memberIndex(selected as MCString) else root.path.memberIndex(quotedKey(key!!))
        if (root.data.facts.read(place) == null) {
            val actual = if (type in erasedTypes && place.path.last() == PathSegment.UnknownIndex)
                root.data.facts.children(root.place).values.map { it.type }.reduceOrNull(TypeKnowledge::join) ?: TypeKnowledge.Unknown
                else if (type in erasedTypes) TypeKnowledge.Unknown else TypeKnowledge.Exact(type.typeId)
            root.data.facts.initialize(place, ValueFacts(actual, ValueKnowledge.Unknown))
        }
        return adapter(type, TempPool.getVarIdentify(), root.copy(place = place, path = path)).apply { parent = container }
    }

    fun view(source: Var<*>, target: MCFPPType, diagnose: Boolean = true): Var<*> {
        if (source.isError) return source
        val compatibility = TypeRelations.checkReinterpretation(source.type, target)
        if (diagnose && compatibility is ReinterpretationCompatibility.Result.Unproven && (source !is MCAny || source is MCObject))
            LogProcessor.warn("Unproven reinterpretation from '${source.type}' to '$target': ${compatibility.reason}")
        if (!hasRuntimeRepresentation(source) && ValueSnapshot.of(source) == null) {
            if (target in erasedTypes) return source.implicitCast(target)
            return error(target, "Compiler-only value has no complete immutable value accessible as '$target'")
        }
        val binding = ensure(source)
        if (binding.data.layout == StorageLayout.CompilerOnly && !staticLayoutAccessible(actualType(source), target) ||
            binding.data.layout != StorageLayout.CompilerOnly && !target.hasRuntimeRepresentation)
            return error(target, "Source layout cannot be accessed as '$target'")
        val ref = ValueRef.TypedView(target.typeId, ValueRef.Read(source.type.typeId, binding.place), binding.place)
        val trusted = binding.trustConstants && (binding.data.layout == StorageLayout.CompilerOnly || actualType(source) == target ||
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
        val value = if (binding.data.layout == StorageLayout.CompilerOnly) {
            val constant = constantFor(type, binding)
            if (constant == null) {
                // An address can be a write destination before its first value is known.
                if (type.hasRuntimeRepresentation) type.buildUnConcrete(name) else UnknownVar(name).apply { this.type = type }
            } else restore(type, constant, name, binding.data.types) ?: return error(type, "Compiler-only layout is inaccessible as '$type'")
        } else type.buildUnConcrete(name)
        value.storageBinding = if (binding.view != null) binding.copy(view = ValueRef.TypedView(type.typeId,
            if (binding.view.place == binding.place) binding.view.source else ValueRef.Read(
                (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type ?: type.typeId, binding.place),
            binding.place)) else binding
        value.nbtPath = binding.path.clone()
        value.hasAssigned = true
        value.isDynamic = binding.data.layout != StorageLayout.CompilerOnly
        return value
    }

    fun snapshot(value: Var<*>): CompilerValue? {
        val binding = value.storageBinding ?: return null
        return constantFor(value.type, binding, value is DataTemplateObject)
    }

    private fun constantFor(type: MCFPPType, binding: StorageBinding, template: Boolean = false): CompilerValue? {
        if (!binding.trustConstants) return null
        val fact = binding.data.facts.read(binding.place) ?: return null
        if (type !in erasedTypes && fact.type != TypeKnowledge.Exact(type.typeId) && !template &&
            !(binding.data.layout == StorageLayout.CompilerOnly && (fact.type as? TypeKnowledge.Exact)?.type
                ?.let(binding.data.types::get)?.let { staticLayoutAccessible(it, type) } == true)) return null
        val constant = (fact.value as? ValueKnowledge.Constant)?.value ?: return null
        if (constant is CompilerValue.Typed && constant.type == type.typeId) return constant
        val payload = if (type in erasedTypes) {
            if (constant is CompilerValue.Typed) constant else (fact.type as? TypeKnowledge.Exact)?.type
                ?.let { CompilerValue.Typed(it, constant) } ?: return null
        } else if (constant is CompilerValue.Typed) constant.payload else constant
        return CompilerValue.Typed(type.typeId, payload)
    }

    private fun staticLayoutAccessible(actual: MCFPPType, target: MCFPPType) = target in erasedTypes || actual == target ||
        actual is MCFPPListType && target is MCFPPListType || actual is MCFPPDictType && target is MCFPPDictType

    /** Loading a register is materialization, not a logical write. */
    fun read(value: Var<*>): Var<*> {
        val binding = value.storageBinding ?: return value
        val data = binding.data
        val version = data.versions.version(binding.place)
        if (value.storageReadVersion == version) return value
        if (data.layout == StorageLayout.CompilerOnly) {
            if (snapshot(value) == null) return error(value.type, "Compiler-only place has no known value for '${value.type}'")
            return adapter(value.type, value.identifier, binding).apply {
                setAs(value)
                storageReadVersion = version
            }
        }
        if (value is DataTemplateObject || value is NBTListConcrete || value is NBTDictionaryConcrete) return if (value is MCFPPValue<*> && snapshot(value) == null)
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
        emit(Command("execute store result score ${layout.player} ${layout.objective} run data get")
            .build(binding.path.toCommandPart()).build("1"))
        binding.data.versions.materialize(binding.place, layout)
    }

    fun write(target: Var<*>, source: Var<*>): Var<*> {
        val binding = target.storageBinding ?: error("Missing storage binding")
        val static = binding.data.layout == StorageLayout.CompilerOnly
        val snapshot = ValueSnapshot.of(source)
        if (static && snapshot == null) {
            LogProcessor.error("Compiler-only place requires a complete compile-time value")
            return target.clone().apply { isError = true }
        }
        if (!static && !hasRuntimeRepresentation(source)) {
            LogProcessor.error("Compiler-only value '${actualType(source)}' cannot be written to a runtime place")
            return target.clone().apply { isError = true }
        }
        if (!static && (!collectionEncodingSupported(source) || !listWriteSupported(target, source))) {
            reportListEncoding()
            return target.clone().apply { isError = true }
        }
        val original = source.storageBinding
        val parts = FlowFacts()
        original?.let { parts.copyFrom(it.data.facts, it.place, binding.place, includeRoot = false) }
        val sizes = original?.data?.listSizes?.filterKeys {
            it.root == original.place.root && it.path.take(original.place.path.size) == original.place.path
        }?.mapKeys { (key, _) -> Place(binding.place.root, binding.place.path + key.path.drop(original.place.path.size)) }.orEmpty()
        val seeded = if (original == null) StoredData(binding.place, binding.path, layout = StorageLayout.CompilerOnly).also {
            seedParts(it, binding.place, source)
        } else null
        if (!static) {
            binding.data.materialize()
            encodeTo(binding.path, source)
        }
        binding.data.types[actualType(source).typeId] = actualType(source)
        binding.data.write(binding.place, ValueFacts(if (source is MCAny) source.typeKnowledge else TypeKnowledge.Exact(source.type.typeId),
            snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown))
        if (PathSegment.UnknownIndex !in binding.place.path) {
            original?.data?.types?.let(binding.data.types::putAll)
            binding.data.facts.copyFrom(parts, binding.place, binding.place, includeRoot = false)
            binding.data.listSizes.putAll(sizes)
            seeded?.let {
                binding.data.types.putAll(it.types)
                binding.data.facts.copyFrom(it.facts, binding.place, binding.place, includeRoot = false)
                binding.data.listSizes.putAll(it.listSizes)
            }
        }
        return adapter(target.type, target.identifier, binding).apply {
            setAs(target)
            hasAssigned = true
            storageReadVersion = null
            if (this is MCAny) payloadType = (source as? MCAny)?.inferredType ?: source.type
        }
    }

    fun materialize(value: Var<*>) {
        if (!hasRuntimeRepresentation(value)) {
            LogProcessor.error("Compiler-only value '${actualType(value)}' cannot be materialized")
            return
        }
        ensure(value).data.materialize()
    }

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
        if (value !is MCFPPValue<*> && (value as? MCAny)?.compilerPayload == null) return null
        return ValueSnapshot.of(value.clone().apply { storageBinding = null; symbol = null })
    }

    fun commitHostChanges(before: List<Pair<Var<*>, CompilerValue>>) {
        for ((value, old) in before) {
            val changed = hostSnapshot(value) ?: continue
            if (changed == old) continue
            val binding = value.storageBinding ?: continue
            if (binding.data.layout == StorageLayout.CompilerOnly) {
                write(value, value.clone().apply { storageBinding = null; symbol = null })
                continue
            }
            val tag = snapshotTag(changed) ?: continue
            emit(Commands.dataSetValue(binding.path, tag))
            binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(actualType(value).typeId), ValueKnowledge.Constant(changed)))
        }
    }

    data class Spill(val value: Var<*>, val path: NBTPath)

    /** Expression temporaries outlive calls but must not share the callee's scratch slots. */
    fun spill(values: Collection<Var<*>>): List<Spill> = values.distinct().mapNotNull { value ->
        if (!value.isTemp || value.isError || !hasRuntimeRepresentation(value) || ValueSnapshot.of(value) != null) return@mapNotNull null
        val slot = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        encodeTo(slot, value)
        Spill(value, slot)
    }

    fun restore(spills: List<Spill>) {
        for ((value, slot) in spills) {
            fun score(player: String, objective: String, path: NBTPath = slot) {
                emit(Command("execute store result score $player $objective run data get").build(path.toCommandPart()).build("1"))
            }
            when (value) {
                is MCInt -> score(value.name, value.sbObject.toString())
                is ScoreBool -> score(value.name, value.boolObject.toString())
                is MCFloat -> if (!FloatProviders.enabled) {
                    for ((key, part) in listOf("sign" to value.sign, "int0" to value.int0, "int1" to value.int1, "exp" to value.exp))
                        score(part.name, part.sbObject.toString(), slot.memberIndex(key))
                } else emit(Commands.dataSetFrom(value.nbtPath, slot))
                else -> emit(Commands.dataSetFrom(value.nbtPath, slot))
            }
            value.storageBinding?.let {
                if (value is MCInt || value is ScoreBool || value is MCFloat && !FloatProviders.enabled)
                    emit(Commands.dataSetFrom(it.path, slot))
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
        values.filter { it.hasAssigned && it.storageBinding?.data?.layout != StorageLayout.CompilerOnly && hasRuntimeRepresentation(it) }
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
        if (!hasRuntimeRepresentation(source)) {
            LogProcessor.error("Compiler-only value '${actualType(source)}' cannot be stored in an erased runtime payload")
            return
        }
        if (!collectionEncodingSupported(source)) { reportListEncoding(); return }
        if (source.storageBinding?.data?.layout == StorageLayout.CompilerOnly) {
            constantEncoding(source)?.let { emit(Commands.dataSetValue(path, it)); return }
            LogProcessor.error("Compiler-only place has no runtime encoding")
            return
        }
        source.storageBinding?.let { binding ->
            binding.data.materialize()
            if (path.toCommandPart().toString() != binding.path.toCommandPart().toString())
                emit(Commands.dataSetFrom(path, binding.path))
            return
        }
        constantEncoding(source)?.let { emit(Commands.dataSetValue(path, it)); return }
        when (source) {
            is MCInt -> if (source.isDataOnly) emit(Commands.dataSetFrom(path, source.nbtPath))
                else scoreWriter(path, source.name, source.sbObject.toString(), numericTag(source.type))()
            is ScoreBool -> if (source.isDataOnly) emit(Commands.dataSetFrom(path, source.nbtPath))
                else scoreWriter(path, source.name, source.boolObject.toString(), "byte")()
            is BaseBool -> encodeTo(path, source.toScoreBool(false))
            is MCFloat -> if (FloatProviders.enabled) emit(Commands.dataSetFrom(path, source.nbtPath)) else {
                emit(Commands.dataSetValue(path, CompoundTag()))
                for ((key, score) in listOf("sign" to source.sign, "int0" to source.int0, "int1" to source.int1, "exp" to source.exp))
                    scoreWriter(path.memberIndex(key), score.name, score.sbObject.toString(), "int")()
            }
            is DataTemplateObject, is NBTBasedData, is MCAny -> emit(Commands.dataSetFrom(path, source.nbtPath))
            else -> {
                source.storeToStack()
                emit(Commands.dataSetFrom(path, source.nbtPath))
            }
        }
    }

    fun constantEncoding(value: Var<*>): Tag<*>? {
        if (!hasRuntimeRepresentation(value) || !collectionEncodingSupported(value)) return null
        if (value.storageBinding != null) {
            val frozen = snapshot(value) ?: return null
            return snapshotTag(frozen)
        }
        if (ValueSnapshot.of(value) == null) return null
        if (value is MCFloatConcrete && !FloatProviders.enabled) {
            val parts = MCFloat.floatToMCFloat(value.value)
            return CompoundTag().apply { for ((i, key) in listOf("sign", "int0", "int1", "exp").withIndex()) put(key, IntTag(parts[i])) }
        }
        return NBTUtil.varToNBT(value)?.copy()
    }

    /** A representable declared type may still carry compiler-only parts through erased fields. */
    fun hasRuntimeRepresentation(value: Var<*>): Boolean {
        if (!actualType(value).hasRuntimeRepresentation) return false
        if (value.storageBinding?.data?.layout == StorageLayout.CompilerOnly)
            return snapshot(value)?.let { snapshotTag(it) != null } == true
        return when (value) {
            is MCAny -> value.compilerPayload == null
            is NBTListConcrete -> value.value.all(::hasRuntimeRepresentation)
            is NBTDictionaryConcrete -> value.value.values.all(::hasRuntimeRepresentation)
            else -> true
        }
    }

    private val supportsMixedLists get() = top.mcfpp.command.TargetCapabilities
        .forVersion(top.mcfpp.Project.config.version)?.heterogeneousLists == true

    private fun collectionEncodingSupported(value: Var<*>): Boolean {
        if (supportsMixedLists || value.storageBinding != null) return true
        return when (value) {
            is NBTListConcrete -> {
                val encodings = value.value.map(::sourceEncoding)
                (encodings.size <= 1 || encodings.all { it != null } && encodings.distinct().size == 1) &&
                    value.value.all(::collectionEncodingSupported)
            }
            is NBTDictionaryConcrete -> value.value.values.all(::collectionEncodingSupported)
            else -> true
        }
    }

    private fun listWriteSupported(target: Var<*>, source: Var<*>): Boolean {
        if (supportsMixedLists || target.parent !is NBTList) return true
        val binding = target.storageBinding!!
        val parent = Place(binding.place.root, binding.place.path.dropLast(1))
        val expected = sourceEncoding(source) ?: return false
        return binding.data.facts.children(parent).values.all {
            val type = (it.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get) ?: return@all false
            encoding(type) == expected
        }
    }

    private fun sourceEncoding(value: Var<*>): Class<out Tag<*>>? {
        value.storageBinding?.let { binding ->
            val type = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
                ?.let(binding.data.types::get) ?: return null
            return encoding(type)
        }
        if (value is NBTBasedDataConcrete) return value.value.javaClass
        return encoding(actualType(value))
    }

    private fun encoding(type: MCFPPType): Class<out Tag<*>>? = when (type.typeId) {
        MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId, MCFPPNBTType.NBT.typeId -> null
        MCFPPBaseType.Float.typeId -> if (FloatProviders.enabled) top.mcfpp.nbt.tags.primitive.FloatTag::class.java else CompoundTag::class.java
        MCFPPNBTType.Byte.typeId -> top.mcfpp.nbt.tags.primitive.ByteTag::class.java
        MCFPPNBTType.Short.typeId -> top.mcfpp.nbt.tags.primitive.ShortTag::class.java
        MCFPPNBTType.Long.typeId -> top.mcfpp.nbt.tags.primitive.LongTag::class.java
        MCFPPNBTType.Double.typeId -> top.mcfpp.nbt.tags.primitive.DoubleTag::class.java
        MCFPPNBTType.ByteArray.typeId -> top.mcfpp.nbt.tags.collection.ByteArrayTag::class.java
        MCFPPNBTType.IntArray.typeId -> top.mcfpp.nbt.tags.collection.IntArrayTag::class.java
        MCFPPNBTType.LongArray.typeId -> top.mcfpp.nbt.tags.collection.LongArrayTag::class.java
        else -> when (type) {
            is MCFPPUnionType -> type.types.map(::encoding).distinct().singleOrNull()
            is MCFPPListType, is MCFPPImmutableListType, is MCFPPCompoundType, is MCFPPDataTemplateType -> type.nbtType
            else -> if (type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.String)) type.nbtType else null
        }
    }

    private fun reportListEncoding() {
        LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot materialize or modify a list with mixed or unproven NBT element encodings; convert elements to a common encoding or select a target with heterogeneous lists")
    }

    private fun scoreWriter(path: NBTPath, player: String, objective: String, tag: String): () -> Unit = {
        emit(Command("execute store result").build(path.toCommandPart())
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

    private fun restore(type: MCFPPType, snapshot: CompilerValue, name: String,
                        types: Map<TypeId, MCFPPType> = emptyMap()): Var<*>? {
        val payload = if (snapshot is CompilerValue.Typed) snapshot.payload else snapshot
        if (type in erasedTypes) {
            var actual = payload
            while (actual is CompilerValue.Typed && actual.type in erasedTypes.map { it.typeId }) actual = actual.payload
            if (actual !is CompilerValue.Typed) return null
            val actualType = types[actual.type] ?: return null
            val restored = restore(actualType, actual, name, types) ?: return null
            return (if (type == MCFPPBaseType.Object) MCObject(name) else MCAny(name)).apply {
                payloadType = actualType
                compilerPayload = restored
            }
        }
        if (payload is CompilerValue.Sequence && type is MCFPPListType) {
            val elements = payload.elements.map { part ->
                val elementType = (part as? CompilerValue.Typed)?.type?.let(types::get) ?: return null
                restore(elementType, part, TempPool.getVarIdentify(), types) ?: return null
            }
            return NBTListConcrete(ArrayList(elements), name, type.generic.single())
        }
        if (payload is CompilerValue.Record && type is MCFPPDictType) {
            val fields = payload.fields.mapValues { (key, part) ->
                val elementType = (part as? CompilerValue.Typed)?.type?.let(types::get) ?: return null
                restore(elementType, part, key, types) ?: return null
            }
            return NBTDictionaryConcrete(HashMap(fields), name).apply { this.type = type }
        }
        val raw: Any = when (payload) {
            is CompilerValue.Typed -> return restore(type, payload, name, types)
            is CompilerValue.TypeValue -> return types[payload.id]?.let { MCFPPTypeVar(it, name) }
            is CompilerValue.Integral -> when (type) {
                MCFPPNBTType.Byte -> payload.value.toByte()
                MCFPPNBTType.Short -> payload.value.toShort()
                MCFPPNBTType.Long -> payload.value
                else -> payload.value.toInt()
            }
            is CompilerValue.Bool -> payload.value
            is CompilerValue.FloatBits -> Float.fromBits(payload.bits)
            is CompilerValue.DoubleBits -> Double.fromBits(payload.bits)
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
