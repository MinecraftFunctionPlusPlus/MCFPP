package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.model.function.Function
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Shared dictionary implementations. Constant knowledge and storage effects are independent of Var subclasses. */
object DictionaryOperations {
    private fun size(value: NBTDictionary): top.mcfpp.core.lang.MCInt? {
        record(value)?.fields?.size?.let { return top.mcfpp.core.lang.MCInt(it) }
        if (!StorageAccess.hasRuntimeRepresentation(value)) {
            LogProcessor.error("Dictionary size requires a complete value or a runtime producer")
            return null
        }
        val errors = Project.errorCount
        StorageAccess.materialize(value)
        if (Project.errorCount != errors) return null
        val result = top.mcfpp.core.lang.MCInt()
        val score = StorageLayout.Scoreboard(result.name, result.sbObject.toString())
        Function.addCommands(Command("execute store result score ${score.player} ${score.objective} run data get")
            .build(StorageAccess.ensure(value).path.toCommandPart()).buildMacroFunction())
        return StorageAccess.publishScore(result, score)
    }
    fun size(context: NativeCallContext) = context.withAdapters { caller, _ ->
        size(caller as NBTDictionary)?.let(context::publishResult)
    }
    fun isEmpty(context: NativeCallContext) = context.withAdapters { caller, _ ->
        size(caller as NBTDictionary)?.let { context.publishResult(StorageAccess.binary(it, top.mcfpp.core.lang.MCInt(0), "==")) }
    }
    fun clear(context: NativeCallContext) = context.withAdapters { caller, _ ->
        clear(caller as NBTDictionary)
    }

    fun merge(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        merge(caller as NBTDictionary, arguments[0] as NBTDictionary)
    }

    fun remove(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        remove(caller as NBTDictionary, arguments[0] as MCString)
    }

    fun containsKey(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        context.publishResult(containsKey(caller as NBTDictionary, arguments[0] as MCString))
    }

    private fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun record(value: Var<*>) = payload(top.mcfpp.analysis.StorageAccess.snapshot(value)) as? CompilerValue.Record

    private fun key(caller: NBTDictionary, value: MCString): String? {
        val snapshot = payload(top.mcfpp.analysis.StorageAccess.snapshot(value))
        val text = when (snapshot) {
            is CompilerValue.Text -> snapshot.value
            is CompilerValue.Nbt -> (Tag.toNBT(snapshot.snbt) as? StringTag)?.value
            else -> null
        }
        if (text == null) {
            LogProcessor.error("Cannot generate dictionary access with an unknown string key: no verified NBT-path escaping backend is available")
            return null
        }
        if (text.isEmpty() && StorageAccess.hasRuntimeRepresentation(caller) &&
            TargetCapabilities.forVersion(Project.config.version)?.emptyNbtPathKeys != true) {
            LogProcessor.error("Target '${Project.config.version}' cannot traverse an empty NBT path key")
            return null
        }
        return text
    }

    private fun replace(caller: NBTDictionary, value: CompilerValue.Record, types: Map<TypeId, MCFPPType>): Boolean {
        val restored = StorageAccess.restore(caller.type, CompilerValue.Typed(caller.type.typeId, value), TempPool.getVarIdentify(), types)
            ?: return false
        StorageAccess.write(caller, restored)
        return true
    }

    fun clear(caller: NBTDictionary) {
        StorageAccess.ensure(caller)
        StorageAccess.writeReceiver(caller, StorageAccess.dictionaryLiteral(caller.type, emptyMap()))
    }

    fun containsKey(caller: NBTDictionary, key: MCString): ScoreBool {
        val name = key(caller, key) ?: return ScoreBool().apply { isError = true }
        record(caller)?.let { return ScoreBool(it.fields.containsKey(name)) }
        val binding = StorageAccess.ensure(caller)
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only dictionary lookup requires a complete compile-time value")
            return ScoreBool().apply { isError = true }
        }
        binding.data.materialize()
        return ScoreBool().apply {
            Function.addCommand(Command("execute store success score ${this.name} ${boolObject} if data")
                .build(binding.path.memberIndex(StorageAccess.quotedKey(name)).toCommandPart()))
            StorageAccess.publishBoolean(this, StorageLayout.Scoreboard(this.name, boolObject.toString()))
        }
    }

    fun remove(caller: NBTDictionary, key: MCString) {
        val name = key(caller, key) ?: return
        val binding = StorageAccess.ensure(caller)
        val old = record(caller)
        if (old != null && name !in old.fields) return
        if (old != null && replace(caller, CompilerValue.Record(old.fields - name), binding.data.types)) return
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only dictionary removal requires a complete compile-time value")
            return
        }
        binding.data.materialize()
        Function.addCommand(Command("data remove").build(binding.path.memberIndex(StorageAccess.quotedKey(name)).toCommandPart()))
        binding.data.write(binding.place.field(name), ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown, ValueState.UNINITIALIZED))
    }

    private fun merged(left: CompilerValue, right: CompilerValue): CompilerValue {
        val a = payload(left) as? CompilerValue.Record
        val b = payload(right) as? CompilerValue.Record
        if (a == null || b == null) {
            val oldTag = StorageAccess.snapshotTag(left) as? CompoundTag ?: return right
            val incomingTag = StorageAccess.snapshotTag(right) as? CompoundTag ?: return right
            return wrappedLike(left, CompilerValue.Nbt(NbtEncoding.snbt(mergedTags(oldTag, incomingTag))))
        }
        val fields = a.fields.toMutableMap()
        b.fields.forEach { (key, value) -> fields[key] = fields[key]?.let { merged(it, value) } ?: value }
        val value = CompilerValue.Record(fields)
        return wrappedLike(left, value)
    }

    private fun wrappedLike(original: CompilerValue, value: CompilerValue): CompilerValue = if (original is CompilerValue.Typed)
        CompilerValue.Typed(original.type, wrappedLike(original.payload, value)) else value

    private fun mergedTags(left: CompoundTag, right: CompoundTag): CompoundTag = (left.copy() as CompoundTag).apply {
        for ((key, incoming) in right.value) {
            val old = this[key]
            put(key, if (old is CompoundTag && incoming is CompoundTag) mergedTags(old, incoming) else incoming.copy())
        }
    }

    private fun compound(value: CompilerValue) = payload(value) is CompilerValue.Record || StorageAccess.snapshotTag(value) is CompoundTag

    fun merge(caller: NBTDictionary, source: NBTDictionary) {
        val binding = StorageAccess.ensure(caller)
        if (binding.data.layout != StorageLayout.CompilerOnly && !StorageAccess.hasRuntimeRepresentation(source)) {
            LogProcessor.error("Compiler-only dictionary fields cannot be merged into a runtime place")
            return
        }
        val incoming = record(source)
        if (binding.data.layout != StorageLayout.CompilerOnly && incoming?.fields?.keys?.any { it.isEmpty() } == true &&
            TargetCapabilities.forVersion(Project.config.version)?.emptyNbtPathKeys != true) {
            LogProcessor.error("Cannot merge known dictionary fields with an empty key: the target cannot traverse empty keys and no verified literal encoding is available")
            return
        }
        val old = record(caller)
        if (binding.data.layout == StorageLayout.CompilerOnly && (old == null || incoming == null)) {
            LogProcessor.error("Compiler-only dictionary merge requires complete compile-time values")
            return
        }
        val original = StorageAccess.ensure(source)
        val types = binding.data.types + original.data.types
        if (old != null && incoming != null && replace(caller, merged(old, incoming) as CompilerValue.Record, types)) return
        if (incoming != null && incoming.fields.values.none(::compound)) {
            // Scalar fields overwrite their slots; untouched siblings keep their facts and caches.
            val parts = incoming.fields.mapNotNull { (name, constant) ->
                val type = original.data.facts.read(original.place.field(name))?.type
                val partType = (type as? TypeKnowledge.Exact)?.type?.let(types::get) ?: return@mapNotNull null
                val value = StorageAccess.restore(partType, constant, TempPool.getVarIdentify(), types) ?: return@mapNotNull null
                name to value
            }
            if (parts.size == incoming.fields.size) {
                for ((name, value) in parts) {
                    val destination = StorageAccess.adapter(MCFPPBaseType.Any, TempPool.getVarIdentify(),
                        binding.copy(place = binding.place.field(name), path = binding.path.memberIndex(StorageAccess.quotedKey(name))))
                    StorageAccess.write(destination, value)
                }
                return
            }
        }
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only dictionary layout cannot access the merged value")
            return
        }
        binding.data.materialize()
        StorageAccess.constantEncoding(source)?.let { Function.addCommand(Commands.dataMergeValue(binding.path, it)) }
            ?: run {
                original.data.materialize()
                Function.addCommand(Command("data modify").build(binding.path.toCommandPart()).build("merge from").build(original.path.toCommandPart()))
            }
        if (incoming == null) binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(caller.type.typeId), ValueKnowledge.Unknown))
        else {
            binding.data.types.putAll(original.data.types)
            // Freeze all incoming facts before writes can invalidate an overlapping source view.
            val fields = incoming.fields.map { (name, value) ->
                val nested = compound(value)
                val fact = if (nested) ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown)
                    else original.data.facts.read(original.place.field(name)) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Constant(value))
                Triple(name, nested, fact)
            }
            for ((name, nested, fact) in fields) {
                // Compound fields merge into existing data; scalar fields replace their slots completely.
                binding.data.write(binding.place.field(name), fact)
                if (!nested) binding.data.facts.forgetDescendants(binding.place.field(name))
            }
        }
    }
}
