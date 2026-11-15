package top.mcfpp.core.lang.entity

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.backend.UUIDOperations
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import java.util.LinkedHashMap

/** Entity templates are ordinary stored values; only summon crosses into world state. */
internal object EntityTemplates {
    fun kind(template: DataTemplate): String? {
        val pending = ArrayDeque<DataTemplate>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        pending.add(template)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            if (current.namespace == "mcfpp.minecraft.entity")
                EntityTypes.named(current.identifier)?.entityKinds?.singleOrNull()?.let { return it }
            pending.addAll(current.parent.filterIsInstance<DataTemplate>())
        }
        return null
    }

    fun fieldPath(field: Var<*>): String {
        val owner = requireNotNull(field.declaredParentTemplate)
        return "data.mcfpp.${StorageAccess.quotedKey(owner.namespaceID)}.${StorageAccess.quotedKey(field.identifier)}"
    }

    fun typeTag(template: DataTemplate): String = "mcfpp.entity.${template.namespaceID}"

    fun kinds(template: DataTemplate): Set<String>? = kind(template)?.let(::setOf)
        ?: if (template.identifier == "DisplayEntity" && template.namespace == "mcfpp.minecraft.entity")
            EntityTypes.DisplayEntity.entityKinds else null

    /** Installs the covariant default `summon(d: nbt)` member on an entity template. */
    fun installDefaultSummon(template: DataTemplate) {
        installDefaultSummon(template, hashSetOf())
    }

    private fun installDefaultSummon(template: DataTemplate, visited: MutableSet<TypeId>) {
        if (!template.isEntityTemplate || !visited.add(template.inheritanceIdentity)) return
        template.scope.ensureMembers()
        val parents = template.parent.filterIsInstance<DataTemplate>().filter { it.isEntityTemplate }
        parents.forEach { installDefaultSummon(it, visited) }
        fun matches(function: Function) = function.normalParams.size == 1 &&
            function.normalParams.single().type == MCFPPNBTType.NBT
        val local = template.scope.functions["summon"].orEmpty().firstOrNull(::matches)
        if (local != null && local !is SummonFunction) return
        val inherited = parents.flatMap { it.scope.getFunctionCandidates("summon") }.filter(::matches)
        // Inherit user overrides unchanged. Only the intrinsic default is covariant in
        // the receiver template; synthesizing over a user method would hide its body.
        if (inherited.any { it !is SummonFunction }) {
            if (local != null) template.scope.functions["summon"]?.remove(local)
            return
        }
        if (local != null) local.isOverride = inherited.isNotEmpty()
        else template.scope.addFunction(SummonFunction(template).apply { isOverride = inherited.isNotEmpty() }, false)
    }

    /** Kept for source compatibility with the old zero/position-argument helper. */
    fun summonFunction(template: DataTemplate, arguments: List<Var<*>>): Function {
        if (arguments.size > 1 || arguments.singleOrNull()?.type?.let {
                it != MCFPPBaseType.Pos3 && it != top.mcfpp.core.lang.context.ContextTypes.Position
            } == true) return UnknownFunction("summon")
        return LegacySummonFunction(template, arguments)
    }

    class SummonFunction(template: DataTemplate) : Function("summon", template, null) {
        init {
            isDerivedImplementation = true
            returnType = MCFPPEntityRefType(template.getType())
            appendNormalParam(MCFPPNBTType.NBT, "d")
        }
        override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> =
            summon(caller as DataTemplateObject, null, normalArgs.getValue("d"))
    }

    class LegacySummonFunction(template: DataTemplate, arguments: List<Var<*>>) : Function("summon", template, null) {
        init {
            isDerivedImplementation = true
            returnType = MCFPPEntityRefType(template.getType())
            arguments.singleOrNull()?.let { appendNormalParam(it.type, "position") }
        }
        override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
            val template = caller as DataTemplateObject
            if (normalArgs["position"] != null) return summon(template, normalArgs["position"], null)
            val empty = NBTBasedData(CompoundTag())
            val member = template.getMemberFunction("summon", emptyList(), listOf(empty), Member.AccessModifier.PUBLIC).first
            return member.invoke(listOf(empty), template)
        }
    }

    private fun summon(template: DataTemplateObject, position: Var<*>?, overrideData: Var<*>?): Var<*> {
        val owner = template.templateType
        if (top.mcfpp.command.TargetCapabilities.forVersion(Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Entity template summon requires function macros (Minecraft 1.20.2 or newer)")
            return UnknownVar("summon").apply { isError = true }
        }
        val kind = kind(owner)
        if (owner.isAbstract || kind == null || kind in setOf("minecraft:player", "minecraft:fishing_bobber")) {
            LogProcessor.error("Cannot summon abstract or non-summonable entity template '${owner.identifier}'")
            return UnknownVar("summon").apply { isError = true }
        }
        val resultType = MCFPPEntityRefType(owner.getType())
        val payload = NBTBasedData()
        val payloadPath = StorageAccess.ensure(payload).path
        val source = (template.getMemberVar("initialNBT", Member.AccessModifier.PRIVATE).first as? PropertyVar)?.get()
        val known = source?.let(StorageAccess::constantEncoding)
        if (known != null && known !is CompoundTag) {
            LogProcessor.error("Entity initialNBT requires compound NBT data")
            return UnknownVar("summon").apply { isError = true }
        }
        if (source == null) Function.addCommand(Commands.dataSetValue(payloadPath, CompoundTag()))
        else StorageAccess.encodeTo(payloadPath, source)
        StorageAccess.publishNbt(payload)

        val frame = Function.currFunction
        val previousThis = frame.scope.vars["this"]
        frame.scope.putVar("this", template, true)
        try {
            for (field in owner.scope.allVars.filter { it.isEntityMember }) {
                val declared = requireNotNull(field.declaredParentTemplate)
                val expression = declared.preInit[field.identifier]
                val value = if (expression == null) field.type.defaultValueVar() else frame.withFieldInitializerOwner(declared) {
                    MCFPPExprVisitor().visitExpression(expression, field.type)
                }
                if (value.isError) return value
                StorageAccess.encodeTo(nestedField(payloadPath, field), value)
            }
        } finally {
            if (previousThis == null) frame.scope.removeVar("this") else frame.scope.putVar("this", previousThis, true)
        }

        // Caller data is merged last so explicit values override template defaults and
        // entity-field initializers. The source can be a runtime nbt value.
        overrideData?.let { data ->
            val knownData = StorageAccess.constantEncoding(data)
            if (knownData != null && knownData !is CompoundTag) {
                LogProcessor.error("Entity summon requires compound NBT data")
                return UnknownVar("summon").apply { isError = true }
            }
            StorageAccess.materialize(data)
            val sourcePath = StorageAccess.ensure(data).path
            Function.addCommand(Command("data modify").build(payloadPath.toCommandPart()).build("merge from")
                .build(sourcePath.toCommandPart()))
        }

        val uuid = MCFPPUUIDType.buildUnConcrete(TempPool.getVarIdentify())
        val uuidPath = StorageAccess.ensure(uuid).path
        Function.addCommand(Commands.dataSetValue(uuidPath, top.mcfpp.nbt.tags.collection.IntArrayTag(intArrayOf())))
        val (initialize, _) = Commands.tempFunction("entity_summon", frame) {
            Function.addCommand(Command("data modify entity @s {} merge from").build(payloadPath.toCommandPart()))
            // Persistent nominal identity also permits recovery without retaining the template object.
            Function.addCommand(Command("data modify entity @s data.mcfpp.type set value")
                .build(top.mcfpp.backend.NbtEncoding.snbt(StringTag(owner.namespaceID))))
            val pending = ArrayDeque<DataTemplate>()
            val visited = hashSetOf<TypeId>()
            pending.add(owner)
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (!visited.add(current.inheritanceIdentity)) continue
                if (current.isEntityTemplate) Function.addCommand("tag @s add ${typeTag(current)}")
                pending.addAll(current.parent.filterIsInstance<DataTemplate>())
            }
            Function.addCommand(Command("data modify").build(uuidPath.toCommandPart()).build("set from entity @s UUID"))
        }
        val command = if (position == null) Command("execute summon $kind run").build(initialize)
            else top.mcfpp.backend.EntityContextCommandOperations.command(listOf(position), changesExecutor = true) { encoded ->
                Command("execute positioned").build(encoded.single()).build("summon $kind run").build(initialize)
            }
        Function.addCommands(command.buildMacroFunction())
        StorageAccess.publishNbt(uuid)
        val target = UUIDOperations.target(uuid).entityTargetOrNull() as top.mcfpp.lib.EntityTarget.UUIDText
        val result = resultType.buildUnConcrete(TempPool.getVarIdentify())
        StorageAccess.encodeTo(StorageAccess.ensure(result).path, target.uuid)
        return StorageAccess.publishNbt(result)
    }

    private fun nestedField(root: NBTPath, field: Var<*>): NBTPath = root.memberIndex("data").memberIndex("mcfpp")
        .memberIndex(StorageAccess.quotedKey(requireNotNull(field.declaredParentTemplate).namespaceID))
        .memberIndex(StorageAccess.quotedKey(field.identifier))
}
