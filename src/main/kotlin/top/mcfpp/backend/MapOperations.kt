package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Map keys are string values, never interpolated NBT member names. */
object MapOperations {
    fun clear(context: NativeCallContext) = context.withAdapters { caller, _ ->
        clear(caller as NBTMap)
    }

    fun merge(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        merge(caller as NBTMap, arguments[0] as NBTMap)
    }

    fun remove(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        remove(caller as NBTMap, arguments[0] as MCString)
    }

    fun containsKey(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        context.publishResult(containsKey(caller as NBTMap, arguments[0] as MCString))
    }

    fun isEmpty(context: NativeCallContext) = context.withAdapters { caller, _ ->
        context.publishResult(isEmpty(caller as NBTMap))
    }

    fun size(context: NativeCallContext) = context.withAdapters { caller, _ ->
        context.publishResult(size(caller as NBTMap))
    }

    private fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun text(value: CompilerValue?): String? = when (val part = payload(value)) {
        is CompilerValue.Text -> part.value
        is CompilerValue.Nbt -> (Tag.toNBT(part.snbt) as? StringTag)?.value
        else -> null
    }
    private fun key(value: MCString) = text(ValueSnapshot.of(value))
    private fun scratch() = NBTPath.temp.memberIndex(TempPool.getVarIdentify())
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())
    private fun score() = MCInt().apply { sbObject = SbObject.MCFPP_TEMP; hasAssigned = true; isDynamic = true; isTemp = true }
    private fun address(value: MCInt) = "${value.name} ${value.sbObject}"
    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
    }

    fun captureKey(value: MCString): MCString {
        key(value)?.let { return MCStringConcrete(StringTag(it)) }
        return MCString().apply {
            nbtPath = NBTPath.stack.intIndex(0).memberIndex(identifier)
            hasAssigned = true; isDynamic = true; isTemp = true
            StorageAccess.encodeTo(nbtPath, value)
        }
    }

    private fun entries(caller: NBTMap): NBTList? {
        val root = StorageAccess.ensure(caller)
        val known = payload((root.data.facts.read(root.place)?.value as? ValueKnowledge.Constant)?.value)
        if (known is CompilerValue.Record && payload(known.fields["entries"]) !is CompilerValue.Sequence) {
            fail("Map access requires an entries list; as does not convert or initialize the old map layout")
            return null
        }
        val field = root.field("entries")
        root.data.types[NBTMap.entryType.typeId] = NBTMap.entryType
        root.data.types[NBTMap.entriesType.typeId] = NBTMap.entriesType
        if (root.data.facts.read(field.place) == null)
            root.data.facts.initialize(field.place, ValueFacts(TypeKnowledge.Exact(NBTMap.entriesType.typeId), ValueKnowledge.Unknown))
        // Only the physical entry-list shape is trusted here; each value retains its own actual type.
        return StorageAccess.adapter(NBTMap.entriesType, TempPool.getVarIdentify(), field.copy(trustConstants = true)) as? NBTList
    }

    private fun keys(list: NBTList): List<String>? {
        val binding = StorageAccess.ensure(list)
        val size = binding.data.listSizes[binding.place] ?: return null
        return (0 until size).map { index ->
            val fact = binding.data.facts.read(binding.place.index(index).field("key")) ?: return null
            if (fact.type != TypeKnowledge.Exact(MCFPPBaseType.String.typeId)) return null
            text((fact.value as? ValueKnowledge.Constant)?.value) ?: return null
        }
    }

    private fun valueType(caller: NBTMap, list: NBTList): TypeKnowledge {
        val binding = StorageAccess.ensure(list)
        binding.data.facts.read(binding.place.unknownIndex().field("value"))?.let { return it.type }
        val size = binding.data.listSizes[binding.place]
        val rows = binding.data.facts.children(binding.place).keys.filter { it.path.last() is PathSegment.Index }
        if (size != null && size == rows.size && size > 0) return rows.map {
            binding.data.facts.read(it.field("value"))?.type ?: TypeKnowledge.Unknown
        }.reduce(TypeKnowledge::join)
        return if (caller.genericType in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)) TypeKnowledge.Unknown
            else TypeKnowledge.Exact(caller.genericType.typeId)
    }

    fun element(caller: NBTMap, selected: MCString): Var<*> {
        val list = entries(caller) ?: return UnknownVar().apply { isError = true }
        val binding = StorageAccess.ensure(list)
        val name = key(selected)
        val names = keys(list)
        val index = if (name != null && names != null) names.indexOf(name) else null
        if (index == -1 && binding.data.layout == StorageLayout.CompilerOnly)
            return fail("Compiler-only map has no key '$name'")
        val place = (if (index != null && index >= 0) binding.place.index(index) else binding.place.unknownIndex()).field("value")
        val path = if (index != null && index >= 0) binding.path.intIndex(index).memberIndex("value") else {
            val predicate = if (name != null) NBTBasedDataConcrete(CompoundTag().apply { put("key", StringTag(name)) })
            else {
                if (binding.data.layout == StorageLayout.CompilerOnly || TargetCapabilities.forVersion(Project.config.version)?.functionMacros != true)
                    return fail("Target '${Project.config.version}' cannot expose a map value at an unknown key without function macros")
                NBTBasedData().apply {
                    nbtPath = NBTPath.stack.intIndex(0).memberIndex(identifier)
                    hasAssigned = true; isDynamic = true
                    emit(Commands.dataSetValue(nbtPath, CompoundTag()))
                    StorageAccess.encodeTo(nbtPath.memberIndex("key"), selected)
                }
            }
            binding.path.nbtIndex(predicate).memberIndex("value")
        }
        binding.data.types[caller.genericType.typeId] = caller.genericType
        if (binding.data.facts.read(place) == null) {
            val actual = if (caller.genericType in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)) valueType(caller, list)
                else TypeKnowledge.Exact(caller.genericType.typeId)
            binding.data.facts.initialize(place, ValueFacts(actual, ValueKnowledge.Unknown))
        }
        return StorageAccess.adapter(caller.genericType, TempPool.getVarIdentify(), binding.copy(place = place, path = path)).apply { parent = caller }
    }

    private fun canWrite(list: NBTList, source: Var<*>?, selected: MCString?): Boolean {
        val binding = StorageAccess.ensure(list)
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            if (selected?.let(::key) != null && keys(list) != null && (source == null || ValueSnapshot.of(source) != null)) return true
            fail("Compiler-only map mutation requires known keys and complete compile-time values")
            return false
        }
        if (source != null && !StorageAccess.hasRuntimeRepresentation(source)) {
            fail("Compiler-only values cannot be written to a runtime map")
            return false
        }
        if (source != null && !StorageAccess.collectionEncodingSupported(source)) {
            StorageAccess.reportListEncoding()
            return false
        }
        return true
    }

    private fun row(name: String, source: Var<*>) = NBTDictionaryConcrete(hashMapOf("key" to MCStringConcrete(StringTag(name)), "value" to source))

    fun put(caller: NBTMap, selected: MCString, source: Var<*>) {
        val list = entries(caller) ?: return
        if (!canWrite(list, source, selected)) return
        val name = key(selected)
        val names = keys(list)
        if (name != null && names != null) {
            val index = names.indexOf(name)
            if (index < 0) ListOperations.add(list, row(name, source), false)
            else StorageAccess.write(element(caller, selected), source)
            return
        }
        val binding = StorageAccess.ensure(list)
        val incoming = StorageAccess.ensure(source)
        val actual = incoming.data.facts.read(incoming.place)?.type ?: TypeKnowledge.Unknown
        val possible = if (binding.data.listSizes[binding.place] == 0) actual else valueType(caller, list).join(actual)
        binding.data.types.putAll(incoming.data.types)
        val prepared = scratch()
        emit(Commands.dataSetValue(prepared, CompoundTag()))
        StorageAccess.encodeTo(prepared.memberIndex("key"), selected)
        StorageAccess.encodeTo(prepared.memberIndex("value"), source)
        overlay(list, prepared)
        invalidate(list, possible)
    }

    /** Rebuild with one shallow replacement, or append one new key. Inputs are frozen before the loop. */
    private fun overlay(list: NBTList, incoming: NBTPath, remove: Boolean = false) {
        val binding = StorageAccess.ensure(list)
        val workspace = scratch()
        emit(Commands.dataSetValue(workspace, CompoundTag()))
        StorageAccess.encodeTo(workspace.memberIndex("source"), list)
        emit(Commands.dataSetFrom(workspace.memberIndex("incoming"), incoming))
        MapCommands.overlay(workspace, remove, ::emit)
        binding.data.materialize()
        emit(Commands.dataSetFrom(binding.path, workspace.memberIndex("output")))
    }

    private fun invalidate(list: NBTList, possible: TypeKnowledge) {
        val binding = StorageAccess.ensure(list)
        binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(NBTMap.entriesType.typeId), ValueKnowledge.Unknown))
        binding.data.facts.forgetDescendants(binding.place)
        binding.data.facts.initialize(binding.place.unknownIndex().field("key"), ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.String.typeId), ValueKnowledge.Unknown))
        binding.data.facts.initialize(binding.place.unknownIndex().field("value"), ValueFacts(possible, ValueKnowledge.Unknown))
    }

    fun clear(caller: NBTMap) { entries(caller)?.let(ListOperations::clear) }

    fun size(caller: NBTMap): MCInt {
        val list = entries(caller) ?: return MCInt().apply { isError = true }
        val binding = StorageAccess.ensure(list)
        binding.data.listSizes[binding.place]?.let { return MCIntConcrete(it) }
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            fail("Compiler-only map size requires a known entry count")
            return MCInt().apply { isError = true }
        }
        binding.data.materialize()
        return score().also { emit(Command("execute store result score ${address(it)} run data get").build(binding.path.toCommandPart())) }
    }

    fun isEmpty(caller: NBTMap): ScoreBool {
        val count = size(caller)
        if (count.isError) return ScoreBool().apply { isError = true }
        if (count is MCIntConcrete) return ScoreBoolConcrete(count.value == 0)
        return ScoreBool().apply {
            hasAssigned = true; isDynamic = true; isTemp = true
            emit(Command("execute store success score $name $boolObject if score ${address(count)} matches 0"))
        }
    }

    fun containsKey(caller: NBTMap, selected: MCString): ScoreBool {
        val list = entries(caller) ?: return ScoreBool().apply { isError = true }
        val binding = StorageAccess.ensure(list)
        val name = key(selected)
        val names = keys(list)
        if (name != null && names != null) return ScoreBoolConcrete(name in names)
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            fail("Compiler-only map lookup requires a known key and entry keys")
            return ScoreBool().apply { isError = true }
        }
        val workspace = scratch()
        emit(Commands.dataSetValue(workspace, CompoundTag()))
        StorageAccess.encodeTo(workspace.memberIndex("source"), list)
        StorageAccess.encodeTo(workspace.memberIndex("needle"), selected)
        val found = MapCommands.contains(workspace, ::emit)
        val result = ScoreBool().apply { hasAssigned = true; isDynamic = true; isTemp = true }
        Function.addCommand("scoreboard players operation ${result.name} ${result.boolObject} = ${MapCommands.key(found)}")
        return result
    }

    fun remove(caller: NBTMap, selected: MCString) {
        val list = entries(caller) ?: return
        if (!canWrite(list, null, selected)) return
        val name = key(selected)
        val names = keys(list)
        if (name != null && names != null) {
            val index = names.indexOf(name)
            if (index >= 0) ListOperations.removeAt(list, MCIntConcrete(index))
            return
        }
        val incoming = scratch()
        val possible = valueType(caller, list)
        emit(Commands.dataSetValue(incoming, CompoundTag()))
        StorageAccess.encodeTo(incoming.memberIndex("key"), selected)
        overlay(list, incoming, true)
        invalidate(list, possible)
    }

    fun merge(caller: NBTMap, source: NBTMap) {
        val target = entries(caller) ?: return
        val original = entries(source) ?: return
        val names = keys(original)
        if (names != null) {
            // Capture every value before any write can invalidate an overlapping source.
            val parts = names.map { name -> name to StorageAccess.capture(StorageAccess.read(element(source, MCStringConcrete(StringTag(name))))) }
            if (parts.any { !canWrite(target, it.second, MCStringConcrete(StringTag(it.first))) }) return
            for ((name, value) in parts) put(caller, MCStringConcrete(StringTag(name)), value)
            return
        }
        if (StorageAccess.ensure(target).data.layout == StorageLayout.CompilerOnly) {
            fail("Compiler-only map merge requires known keys and complete compile-time values")
            return
        }
        if (!StorageAccess.hasRuntimeRepresentation(source) || !StorageAccess.collectionEncodingSupported(source)) {
            fail("Map source has no supported runtime encoding")
            return
        }
        val targetBinding = StorageAccess.ensure(target)
        val sourceBinding = StorageAccess.ensure(original)
        val possible = if (targetBinding.data.listSizes[targetBinding.place] == 0) valueType(source, original)
            else valueType(caller, target).join(valueType(source, original))
        targetBinding.data.types.putAll(sourceBinding.data.types)
        // A delayed initializer belongs before the source loop, never in a repeated overlay body.
        StorageAccess.ensure(target).data.materialize()
        val workspace = scratch()
        emit(Commands.dataSetValue(workspace, CompoundTag()))
        StorageAccess.encodeTo(workspace.memberIndex("source"), target)
        StorageAccess.encodeTo(workspace.memberIndex("rows"), original)
        MapCommands.merge(workspace, ::emit)
        emit(Commands.dataSetFrom(targetBinding.path, workspace.memberIndex("output")))
        invalidate(target, possible)
    }

    fun keys(caller: NBTMap): NBTList {
        val list = entries(caller) ?: return NBTList(genericType = MCFPPBaseType.String).apply { isError = true }
        keys(list)?.let { return NBTListConcrete(ArrayList(it.map { name -> MCStringConcrete(StringTag(name)) }), TempPool.getVarIdentify(), MCFPPBaseType.String) }
        val workspace = scratch()
        emit(Commands.dataSetValue(workspace, CompoundTag()))
        StorageAccess.encodeTo(workspace.memberIndex("source"), list)
        MapCommands.keys(workspace, ::emit)
        return NBTList(genericType = MCFPPBaseType.String).apply { nbtPath = workspace.memberIndex("output"); hasAssigned = true; isDynamic = true }
    }

    fun dictionary(caller: NBTMap): NBTDictionary {
        val list = entries(caller) ?: return NBTDictionary().apply { isError = true }
        val names = keys(list)
        if (names == null) {
            fail("Map dictionary projection requires known keys: no verified runtime NBT member-name escaping backend is available")
            return NBTDictionary().apply { isError = true }
        }
        if (names.any(String::isEmpty) && StorageAccess.hasRuntimeRepresentation(caller)) {
            fail("Runtime map dictionary projection cannot encode empty member names with the current NBT backend")
            return NBTDictionary().apply { isError = true }
        }
        val parts = names.associateWith { name -> StorageAccess.capture(StorageAccess.read(element(caller, MCStringConcrete(StringTag(name))))) }
        return NBTDictionaryConcrete(HashMap(parts)).apply { type = MCFPPDictType(caller.genericType) }
    }
}
