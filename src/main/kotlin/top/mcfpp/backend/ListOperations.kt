package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** List members use immutable values for folding and shared places for runtime effects. */
object ListOperations {
    fun size(context: NativeCallContext) = context.withAdapters { caller, _ ->
        val value = caller as NBTList
        val length = sequence(value)?.elements?.size?.let(::MCInt) ?: StorageAccess.iterationLength(value)
        if (length != null && !length.isError) context.publishResult(length)
    }
    fun isEmpty(context: NativeCallContext) = context.withAdapters { caller, _ ->
        val value = caller as NBTList
        val length = sequence(value)?.elements?.size?.let(::MCInt) ?: StorageAccess.iterationLength(value)
        if (length != null && !length.isError) context.publishResult(StorageAccess.binary(length, MCInt(0), "=="))
    }
    private fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun sequence(value: Var<*>) = payload(top.mcfpp.analysis.StorageAccess.snapshot(value)) as? CompilerValue.Sequence
    private fun integer(value: MCInt) = (payload(top.mcfpp.analysis.StorageAccess.snapshot(value)) as? CompilerValue.Integral)?.value?.toInt()
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())
    private fun scratch() = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
    private fun key(value: MCInt) = "${value.name} ${value.sbObject}"
    private fun macroSupported(index: MCInt): Boolean {
        if (integer(index) != null || TargetCapabilities.forVersion(Project.config.version)?.functionMacros == true) return true
        LogProcessor.error("Target '${Project.config.version}' cannot modify a list at a runtime index without function macros")
        return false
    }
    private fun capturedIndex(index: MCInt): MCInt {
        integer(index)?.let { return MCInt(it) }
        val arguments = scratch()
        emit(Commands.dataSetValue(arguments, CompoundTag()))
        return MCInt().apply {
            nbtPath = arguments.memberIndex(identifier)
            StorageAccess.encodeTo(nbtPath, index)
            StorageAccess.publishNbt(this)
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
        if (binding.data.facts.length(binding.place) == children.size) return children.values.map {
            (it.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get)?.let(StorageAccess::encoding)
        }
        return listOf(StorageAccess.encoding(list.genericType))
    }

    private fun canAdd(caller: NBTList, source: Var<*>, bulk: Boolean): Boolean {
        val binding = StorageAccess.ensure(caller)
        if (binding.data.layout == StorageLayout.CompilerOnly) {
            if (sequence(caller) != null && top.mcfpp.analysis.StorageAccess.snapshot(source) != null) return true
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

    private data class Saved(val binding: StorageBinding, val facts: FlowFacts)
    private fun save(value: Var<*>): Saved = StorageAccess.ensure(value).let { Saved(it, it.data.facts.fork()) }
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
        val possible = incomingType?.let { if (old.facts.length(binding.place) == 0) it else elementType(caller, old).join(it) }
            ?: elementType(caller, old)
        data.write(binding.place, ValueFacts(rootType, ValueKnowledge.Unknown))
        data.facts.forgetDescendants(binding.place)
        incoming?.binding?.data?.types?.let(data.types::putAll)
        fun copy(saved: Saved, from: Place, to: Place, fallback: TypeKnowledge) {
            data.facts.copyFrom(saved.facts, from, to)
            if (data.facts.read(to) == null) data.facts.initialize(to, ValueFacts(fallback, ValueKnowledge.Unknown))
        }
        val size = old.facts.length(binding.place)
        if (size != null && start != null && added != null) {
            for (index in 0 until size) {
                if (index in start until start + removed) continue
                val target = if (index < start) index else index - removed + added
                copy(old, binding.place.index(index), binding.place.index(target), elementType(caller, old))
            }
            if (incoming != null) for (index in 0 until added) copy(incoming,
                if (bulk) incoming.binding.place.index(index) else incoming.binding.place,
                binding.place.index(start + index), incomingType!!)
            data.facts.setLength(binding.place, size - removed + added)
        }
        data.facts.initialize(binding.place.unknownIndex(), ValueFacts(possible, ValueKnowledge.Unknown))
    }

    fun clear(caller: NBTList) {
        StorageAccess.ensure(caller)
        StorageAccess.writeReceiver(caller, StorageAccess.listLiteral(caller.type, emptyList()))
    }

    fun clear(context: NativeCallContext) {
        context.writeReceiver(CompilerValue.Sequence(emptyList()))
    }

    fun add(context: NativeCallContext, prepend: Boolean) = context.withAdapters { caller, arguments ->
        add(caller as NBTList, arguments[0], prepend)
    }

    fun addAll(context: NativeCallContext, prepend: Boolean) = context.withAdapters { caller, arguments ->
        addAll(caller as NBTList, arguments[0] as NBTList, prepend)
    }

    fun insert(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        insert(caller as NBTList, arguments[0] as MCInt, arguments[1])
    }

    fun removeAt(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        removeAt(caller as NBTList, arguments[0] as MCInt)
    }

    fun remove(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        remove(caller as NBTList, arguments[0])
    }

    fun indexOf(context: NativeCallContext, last: Boolean) = context.withAdapters { caller, arguments ->
        context.publishResult(indexOf(caller as NBTList, arguments[0], last))
    }

    fun contains(context: NativeCallContext) = context.withAdapters { caller, arguments ->
        context.publishResult(contains(caller as NBTList, arguments[0]))
    }

    fun add(caller: NBTList, source: Var<*>, prepend: Boolean) {
        if (!canAdd(caller, source, false)) return
        val old = sequence(caller)
        val part = top.mcfpp.analysis.StorageAccess.snapshot(source)
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
        commit(caller, saved, if (prepend) 0 else saved.facts.length(saved.binding.place), 0, incoming, false, 1)
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
        commit(caller, saved, if (prepend) 0 else saved.facts.length(saved.binding.place), 0, incoming, true,
            incoming.facts.length(incoming.binding.place))
    }

    private fun normalized(caller: NBTList, index: Int?, insertion: Boolean): Int? {
        val size = StorageAccess.ensure(caller).let { it.data.facts.length(it.place) } ?: return index?.takeIf { it >= 0 }
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
        val part = top.mcfpp.analysis.StorageAccess.snapshot(source)
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

    fun indexOf(caller: NBTList, needle: Var<*>, last: Boolean): MCInt {
        val old = sequence(caller)
        val value = top.mcfpp.analysis.StorageAccess.snapshot(needle)
        if (old != null && value != null) ListValues.indexOf(old.elements, value, last)?.let { return MCInt(it) }
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
            val size = binding.data.facts.length(binding.place)
            val children = binding.data.facts.children(binding.place).filterKeys { it.path.last() is PathSegment.Index }
            if (size == null || size != children.size || children.values.any { it.type !is TypeKnowledge.Exact }) {
                LogProcessor.error("List lookup requires known element types; use an explicit as list<T> view")
                return MCInt().apply { isError = true }
            }
            children.filterValues { it.type == TypeKnowledge.Exact(type) }.keys.map { (it.path.last() as PathSegment.Index).index }
        } else null
        val workspace = scratch()
        emit(Commands.dataSetValue(workspace, CompoundTag()))
        StorageAccess.encodeTo(workspace.memberIndex("source"), caller)
        StorageAccess.encodeTo(workspace.memberIndex("needle"), needle)
        return ListSearch.find(workspace, eligible, last, ::emit)
    }

    fun contains(caller: NBTList, needle: Var<*>): ScoreBool {
        val index = indexOf(caller, needle, false)
        if (index.isError) return ScoreBool().apply { isError = true }
        integer(index)?.let { return ScoreBool(it >= 0) }
        return ScoreBool().apply {
            isTemp = true
            Function.addCommand("scoreboard players set $name $boolObject 0")
            Function.addCommand("execute if score ${key(index)} matches 0.. run scoreboard players set $name $boolObject 1")
            StorageAccess.publishBoolean(this, StorageLayout.Scoreboard(name, boolObject.toString()))
        }
    }

    fun remove(caller: NBTList, needle: Var<*>) {
        val index = indexOf(caller, needle, false)
        if (index.isError) return
        integer(index)?.let { if (it >= 0) removeAt(caller, index); return }
        val saved = save(caller)
        val workspace = scratch()
        emit(Commands.dataSetValue(workspace, CompoundTag()))
        StorageAccess.encodeTo(workspace.memberIndex("source"), caller)
        ListSearch.remove(workspace, index, ::emit)
        emit(Command("execute if score ${key(index)} matches 0.. run")
            .build(Commands.dataSetFrom(saved.binding.path, workspace.memberIndex("output"))))
        commit(caller, saved, null, 1, null, false, 0)
    }
}
