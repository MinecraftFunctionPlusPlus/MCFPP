package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTListConcrete
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** List members use immutable values for folding and shared places for runtime effects. */
object ListOperations {
    private fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun sequence(value: Var<*>) = payload(ValueSnapshot.of(value)) as? CompilerValue.Sequence
    private fun integer(value: MCInt) = (payload(ValueSnapshot.of(value)) as? CompilerValue.Integral)?.value?.toInt()
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())
    private fun scratch() = NBTPath.temp.memberIndex(TempPool.getVarIdentify())
    private fun score() = MCInt().apply { sbObject = SbObject.MCFPP_TEMP; hasAssigned = true; isDynamic = true; isTemp = true }
    private fun key(value: MCInt) = "${value.name} ${value.sbObject}"
    private fun set(value: MCInt, number: Int) = Function.addCommand("scoreboard players set ${key(value)} $number")
    private fun macroSupported(index: MCInt): Boolean {
        if (integer(index) != null || TargetCapabilities.forVersion(Project.config.version)?.functionMacros == true) return true
        LogProcessor.error("Target '${Project.config.version}' cannot modify a list at a runtime index without function macros")
        return false
    }
    private fun capturedIndex(index: MCInt): MCInt {
        integer(index)?.let { return MCIntConcrete(it) }
        val arguments = scratch()
        emit(Commands.dataSetValue(arguments, CompoundTag()))
        return MCInt().apply {
            nbtPath = arguments.memberIndex(identifier)
            hasAssigned = true; isDynamic = true; isDataOnly = true
            StorageAccess.encodeTo(nbtPath, index)
        }
    }

    private fun replace(caller: NBTList, elements: List<CompilerValue>, sources: List<Var<*>> = emptyList()): Boolean {
        val binding = StorageAccess.ensure(caller)
        val types = binding.data.types.toMutableMap()
        sources.forEach { types.putAll(StorageAccess.ensure(it).data.types) }
        val restored = StorageAccess.restore(caller.type, CompilerValue.Typed(caller.type.typeId,
            CompilerValue.Sequence(elements)), TempPool.getVarIdentify(), types) ?: return false
        // A diagnosed write failure is handled too; do not emit a fallback mutation after it.
        StorageAccess.write(caller, restored)
        return true
    }

    private fun encodings(list: NBTList): List<Class<*>?> {
        val binding = StorageAccess.ensure(list)
        val children = binding.data.facts.children(binding.place).filterKeys { it.path.last() is PathSegment.Index }
        if (binding.data.listSizes[binding.place] == children.size) return children.values.map {
            (it.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get)?.let(StorageAccess::encoding)
        }
        return listOf(StorageAccess.encoding(list.genericType))
    }

    private fun canAdd(caller: NBTList, source: Var<*>, bulk: Boolean): Boolean {
        val binding = StorageAccess.ensure(caller)
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            if (sequence(caller) != null && ValueSnapshot.of(source) != null) return true
            LogProcessor.error("Compiler-only list modification requires complete compile-time values and a known index")
            return false
        }
        if (!StorageAccess.hasRuntimeRepresentation(source)) {
            LogProcessor.error("Compiler-only elements cannot be inserted into a runtime list")
            return false
        }
        if (!StorageAccess.collectionEncodingSupported(source)) { StorageAccess.reportListEncoding(); return false }
        if (TargetCapabilities.forVersion(Project.config.version)?.heterogeneousLists == true) return true
        val codecs = encodings(caller) + if (bulk) encodings(source as NBTList) else listOf(StorageAccess.sourceEncoding(source))
        if (codecs.size <= 1 || codecs.all { it != null } && codecs.distinct().size == 1) return true
        StorageAccess.reportListEncoding()
        return false
    }

    private data class Saved(val binding: StorageBinding, val facts: FlowFacts, val sizes: Map<Place, Int>)
    private fun save(value: Var<*>): Saved = StorageAccess.ensure(value).let { Saved(it, it.data.facts.fork(), it.data.listSizes.toMap()) }
    private fun elementType(caller: NBTList, saved: Saved): TypeKnowledge =
        saved.facts.read(saved.binding.place.unknownIndex())?.type ?: saved.facts.children(saved.binding.place).values.map { it.type }
            .reduceOrNull(TypeKnowledge::join) ?: if (caller.genericType in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object))
            TypeKnowledge.Unknown else TypeKnowledge.Exact(caller.genericType.typeId)

    /** Reindex complete subtrees, including nested list lengths, after a known splice. */
    private fun commit(caller: NBTList, old: Saved, start: Int?, removed: Int, incoming: Saved?, bulk: Boolean, added: Int?) {
        val binding = old.binding
        val data = binding.data
        val rootType = old.facts.read(binding.place)?.type ?: TypeKnowledge.Exact(caller.type.typeId)
        val incomingType = incoming?.let { saved -> if (bulk) elementType(caller, saved) else
            saved.facts.read(saved.binding.place)?.type ?: TypeKnowledge.Unknown }
        val possible = incomingType?.let { if (old.sizes[binding.place] == 0) it else elementType(caller, old).join(it) }
            ?: elementType(caller, old)
        data.write(binding.place, ValueFacts(rootType, ValueKnowledge.Unknown))
        data.facts.forgetDescendants(binding.place)
        incoming?.binding?.data?.types?.let(data.types::putAll)
        fun copy(saved: Saved, from: Place, to: Place, fallback: TypeKnowledge) {
            data.facts.copyFrom(saved.facts, from, to)
            if (data.facts.read(to) == null) data.facts.initialize(to, ValueFacts(fallback, ValueKnowledge.Unknown))
            for ((place, size) in saved.sizes) if (place.root == from.root && place.path.take(from.path.size) == from.path)
                data.listSizes[Place(to.root, to.path + place.path.drop(from.path.size))] = size
        }
        val size = old.sizes[binding.place]
        if (size != null && start != null && added != null) {
            for (index in 0 until size) {
                if (index in start until start + removed) continue
                val target = if (index < start) index else index - removed + added
                copy(old, binding.place.index(index), binding.place.index(target), elementType(caller, old))
            }
            if (incoming != null) for (index in 0 until added) copy(incoming,
                if (bulk) incoming.binding.place.index(index) else incoming.binding.place,
                binding.place.index(start + index), incomingType!!)
            data.listSizes[binding.place] = size - removed + added
        }
        data.facts.initialize(binding.place.unknownIndex(), ValueFacts(possible, ValueKnowledge.Unknown))
    }

    fun clear(caller: NBTList) {
        StorageAccess.ensure(caller)
        StorageAccess.write(caller, NBTListConcrete(arrayListOf(), TempPool.getVarIdentify(), caller.genericType))
    }

    fun add(caller: NBTList, source: Var<*>, prepend: Boolean) {
        if (!canAdd(caller, source, false)) return
        val old = sequence(caller)
        val part = ValueSnapshot.of(source)
        if (old != null && part != null && replace(caller,
                if (prepend) listOf(part) + old.elements else old.elements + part, listOf(source))) return
        val saved = save(caller)
        if (saved.binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only list layout cannot access the inserted value")
            return
        }
        val incoming = save(source)
        val slot = scratch()
        StorageAccess.encodeTo(slot, source)
        saved.binding.data.materialize()
        emit(Command("data modify").build(saved.binding.path.toCommandPart())
            .build(if (prepend) "prepend from" else "append from").build(slot.toCommandPart()))
        commit(caller, saved, if (prepend) 0 else saved.sizes[saved.binding.place], 0, incoming, false, 1)
    }

    fun addAll(caller: NBTList, source: NBTList, prepend: Boolean) {
        if (!canAdd(caller, source, true)) return
        val old = sequence(caller)
        val parts = sequence(source)
        if (parts?.elements?.isEmpty() == true) return
        if (old != null && parts != null && replace(caller,
                if (prepend) parts.elements + old.elements else old.elements + parts.elements, listOf(source))) return
        val saved = save(caller)
        if (saved.binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only list layout cannot access the inserted values")
            return
        }
        val incoming = save(source)
        val slot = scratch()
        // Freeze the source first, so self-append and overlapping aliases copy their original contents.
        StorageAccess.encodeTo(slot, source)
        saved.binding.data.materialize()
        emit(Command("data modify").build(saved.binding.path.toCommandPart())
            .build(if (prepend) "prepend from" else "append from").build(slot.iteratorIndex().toCommandPart()))
        commit(caller, saved, if (prepend) 0 else saved.sizes[saved.binding.place], 0, incoming, true,
            incoming.sizes[incoming.binding.place])
    }

    private fun normalized(caller: NBTList, index: Int?, insertion: Boolean): Int? {
        val size = StorageAccess.ensure(caller).let { it.data.listSizes[it.place] } ?: return index?.takeIf { it >= 0 }
        if (index == null) return null
        val position = if (index < 0) size + index + if (insertion) 1 else 0 else index
        if (position !in 0..(if (insertion) size else size - 1)) {
            LogProcessor.error("Index $index out of bounds for ${if (insertion) "insertion into" else "length"} $size")
            return null
        }
        return position
    }

    fun insert(caller: NBTList, index: MCInt, source: Var<*>) {
        if (!macroSupported(index) || !canAdd(caller, source, false)) return
        val errors = Project.errorCount
        val known = integer(index)
        val position = normalized(caller, known, true)
        if (errors != Project.errorCount) return
        val old = sequence(caller)
        val part = ValueSnapshot.of(source)
        if (old != null && part != null && position != null && replace(caller,
                old.elements.take(position) + part + old.elements.drop(position), listOf(source))) return
        val saved = save(caller)
        if (saved.binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only list insertion requires a known index and complete value")
            return
        }
        val incoming = save(source)
        val slot = scratch()
        StorageAccess.encodeTo(slot, source)
        saved.binding.data.materialize()
        val command = Command("data modify").build(saved.binding.path.toCommandPart()).build("insert")
        val selected = capturedIndex(index)
        if (known != null) command.build(known.toString()) else command.buildMacro(selected)
        emit(command.build("from").build(slot.toCommandPart()))
        commit(caller, saved, position, 0, incoming, false, 1)
    }

    fun removeAt(caller: NBTList, index: MCInt) {
        if (!macroSupported(index)) return
        val errors = Project.errorCount
        val position = normalized(caller, integer(index), false)
        if (errors != Project.errorCount) return
        val old = sequence(caller)
        if (old != null && position != null && replace(caller, old.elements.filterIndexed { i, _ -> i != position })) return
        val saved = save(caller)
        if (saved.binding.data.layout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only list removal requires a known index and complete value")
            return
        }
        val selected = capturedIndex(index)
        saved.binding.data.materialize()
        emit(Command("data remove").build(saved.binding.path.intIndex(selected).toCommandPart()))
        commit(caller, saved, position, 1, null, false, 0)
    }

    private fun unbox(value: CompilerValue): CompilerValue = if (value is CompilerValue.Typed &&
        value.type in setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)) unbox(value.payload) else value

    private fun equal(left: CompilerValue, right: CompilerValue): Boolean {
        val a = unbox(left)
        val b = unbox(right)
        if (a is CompilerValue.Typed && b is CompilerValue.Typed) return a.type == b.type && equal(a.payload, b.payload)
        if (a is CompilerValue.Sequence && b is CompilerValue.Sequence) return a.elements.size == b.elements.size &&
            a.elements.zip(b.elements).all { (x, y) -> equal(x, y) }
        if (a is CompilerValue.Record && b is CompilerValue.Record) return a.fields.keys == b.fields.keys &&
            a.fields.all { (key, value) -> equal(value, b.fields.getValue(key)) }
        if (a is CompilerValue.Nbt && b is CompilerValue.Nbt) return Tag.toNBT(a.snbt) == Tag.toNBT(b.snbt)
        return a == b
    }

    private fun foldableTag(tag: Tag<*>): Boolean = when (tag) {
        is FloatTag, is DoubleTag -> false
        is ListTag -> tag.all(::foldableTag)
        is CompoundTag -> tag.value.values.all(::foldableTag)
        else -> true
    }

    /** Float bits do not prove equality in the legacy layout; retain the actual command backend. */
    private fun foldable(value: CompilerValue): Boolean = when (value) {
        is CompilerValue.FloatBits, is CompilerValue.DoubleBits -> false
        is CompilerValue.Typed -> value.type !in setOf(MCFPPBaseType.Float.typeId, MCFPPNBTType.Double.typeId) && foldable(value.payload)
        is CompilerValue.Sequence -> value.elements.all(::foldable)
        is CompilerValue.Record -> value.fields.values.all(::foldable)
        is CompilerValue.Nbt -> foldableTag(Tag.toNBT(value.snbt))
        else -> true
    }

    fun indexOf(caller: NBTList, needle: Var<*>, last: Boolean): MCInt {
        val old = sequence(caller)
        val value = ValueSnapshot.of(needle)
        if (old != null && value != null && foldable(old) && foldable(value)) return MCIntConcrete(if (last) old.elements.indexOfLast { equal(it, value) }
            else old.elements.indexOfFirst { equal(it, value) })
        val binding = StorageAccess.ensure(caller)
        if (binding.data.layout == StorageLayout.CompilerOnly || !StorageAccess.hasRuntimeRepresentation(needle)) {
            LogProcessor.error("Compiler-only list lookup requires complete compile-time values")
            return MCInt().apply { isError = true }
        }
        val type = StorageAccess.ensure(needle).let { (it.data.facts.read(it.place)?.type as? TypeKnowledge.Exact)?.type }
        if (type == null) {
            LogProcessor.error("List lookup requires a known needle type; use an explicit as view")
            return MCInt().apply { isError = true }
        }
        val eligible = if (caller.genericType in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)) {
            val size = binding.data.listSizes[binding.place]
            val children = binding.data.facts.children(binding.place).filterKeys { it.path.last() is PathSegment.Index }
            if (size == null || size != children.size || children.values.any { it.type !is TypeKnowledge.Exact }) {
                LogProcessor.error("List lookup requires known element types; use an explicit as list<T> view")
                return MCInt().apply { isError = true }
            }
            children.filterValues { it.type == TypeKnowledge.Exact(type) }.keys.map { (it.path.last() as PathSegment.Index).index }
        } else null
        val list = scratch()
        val element = scratch()
        val probe = scratch()
        StorageAccess.encodeTo(list, caller)
        StorageAccess.encodeTo(element, needle)
        val result = score()
        val cursor = score()
        val size = score()
        val changed = score()
        set(result, -1); set(cursor, 0)
        emit(Command("execute store result score ${key(size)} run data get").build(list.toCommandPart()))
        val loop = Commands.tempFunction("list_find", Function.currFunction) { function ->
            emit(Commands.dataSetFrom(probe, list.intIndex(0)))
            emit(Command("execute store success score ${key(changed)} run").build(Commands.dataSetFrom(probe, element)))
            val match = Command("execute if score ${key(changed)} matches 0 run scoreboard players operation ${key(result)} = ${key(cursor)}")
            if (eligible == null) emit(match) else eligible.forEach { index ->
                emit(Command("execute if score ${key(cursor)} matches $index run").build(match))
            }
            emit(Command("data remove").build(list.intIndex(0).toCommandPart()))
            Function.addCommand("scoreboard players add ${key(cursor)} 1")
            val recurse = Command("execute if score ${key(cursor)} < ${key(size)} run").build(Commands.function(function))
            emit(if (last) recurse else Command("execute if score ${key(result)} matches -1 run").build(recurse))
        }
        emit(Command("execute if score ${key(size)} matches 1.. run").build(loop.first))
        return result
    }

    fun contains(caller: NBTList, needle: Var<*>): ScoreBool {
        val index = indexOf(caller, needle, false)
        if (index.isError) return ScoreBool().apply { isError = true }
        integer(index)?.let { return ScoreBoolConcrete(it >= 0) }
        return ScoreBool().apply {
            hasAssigned = true; isDynamic = true; isTemp = true
            Function.addCommand("scoreboard players set $name $boolObject 0")
            Function.addCommand("execute if score ${key(index)} matches 0.. run scoreboard players set $name $boolObject 1")
        }
    }

    fun remove(caller: NBTList, needle: Var<*>) {
        val index = indexOf(caller, needle, false)
        if (index.isError) return
        integer(index)?.let { if (it >= 0) removeAt(caller, index); return }
        val saved = save(caller)
        val list = scratch()
        val output = scratch()
        val cursor = score()
        val size = score()
        val rebuild = Commands.tempFunction("list_remove", Function.currFunction) {
            StorageAccess.encodeTo(list, caller)
            emit(Commands.dataSetValue(output, ListTag()))
            set(cursor, 0)
            emit(Command("execute store result score ${key(size)} run data get").build(list.toCommandPart()))
            val loop = Commands.tempFunction("list_keep", Function.currFunction) { function ->
                emit(Command("execute unless score ${key(cursor)} = ${key(index)} run")
                    .build(Commands.dataAppendFrom(output, list.intIndex(0))))
                emit(Command("data remove").build(list.intIndex(0).toCommandPart()))
                Function.addCommand("scoreboard players add ${key(cursor)} 1")
                emit(Command("execute if score ${key(cursor)} < ${key(size)} run").build(Commands.function(function)))
            }
            emit(Command("execute if score ${key(size)} matches 1.. run").build(loop.first))
            emit(Commands.dataSetFrom(saved.binding.path, output))
        }
        emit(Command("execute if score ${key(index)} matches 0.. run").build(rebuild.first))
        commit(caller, saved, null, 1, null, false, 0)
    }
}
