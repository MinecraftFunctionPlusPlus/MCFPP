package top.mcfpp.core.lang

import top.mcfpp.analysis.*
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** any and object use the same untagged NBT payload; type knowledge is independent. */
open class MCAny : Var<MCAny> {
    override var type: MCFPPType = MCFPPBaseType.Any
    var payloadType: MCFPPType? = null
    // This live specialization channel is never a runtime payload or an immutable cache key.
    @Transient var compilerPayload: Var<*>? = null
    var container: FieldContainer? = null

    val typeKnowledge: TypeKnowledge
        get() = storageBinding?.let { it.data.facts.read(it.place)?.type ?: TypeKnowledge.Unknown }
            ?: payloadType?.let { TypeKnowledge.Exact(it.typeId) } ?: TypeKnowledge.Unknown

    val inferredType: MCFPPType?
        get() {
            val knowledge = typeKnowledge as? TypeKnowledge.Exact ?: return null
            val actual = storageBinding?.data?.types?.get(knowledge.type) ?: payloadType
            return actual?.takeIf { it.typeId == knowledge.type && it != MCFPPBaseType.Any && it != MCFPPBaseType.Object }
        }

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)
    constructor(b: MCAny) : super(b) {
        payloadType = b.payloadType
        compilerPayload = b.compilerPayload
        container = b.container
    }

    fun bindPayload(source: Var<*>): MCAny {
        payloadType = (source as? MCAny)?.inferredType ?: source.type
        compilerPayload = if (!payloadType!!.hasRuntimeRepresentation) (source as? MCAny)?.compilerPayload ?: source else null
        if (compilerPayload == null) storageBinding = StorageAccess.ensure(source)
        return this
    }

    override fun doAssignedBy(b: Var<*>): MCAny {
        val sourceType = (b as? MCAny)?.inferredType ?: b.type
        val snapshot = ValueSnapshot.of(b)
        val re: MCAny = when {
            this is MCObject -> MCObject(identifier).apply { setAs(this@MCAny) }
            snapshot != null && b is MCFPPValue<*> -> MCAnyConcrete(this, b.value)
            else -> MCAny(this)
        }
        re.storageBinding = null
        re.payloadType = (b as? MCAny)?.inferredType ?: b.type.takeUnless { it == MCFPPBaseType.Any || it == MCFPPBaseType.Object }
        re.compilerPayload = if (!sourceType.hasRuntimeRepresentation) (b as? MCAny)?.compilerPayload ?: b else null
        if (re.compilerPayload != null) return re
        re.bindDeclaration()
        if (re.nbtPath.pathList.isEmpty()) re.nbtPath = top.mcfpp.lib.NBTPath.getNormalStackPath(re)
        val place = Place(re.symbol!!.id)
        val path = re.nbtPath.clone()
        val frozen = StorageAccess.constantEncoding(b)?.let { top.mcfpp.nbt.tags.Tag.toSNBT(it) }
        val data = StoredData(place, path, frozen?.let { snbt ->
            { Function.addCommand(top.mcfpp.command.Commands.dataSetValue(path, top.mcfpp.nbt.tags.Tag.toNBT(snbt))) }
        })
        re.payloadType?.let { data.types[it.typeId] = it }
        data.facts.write(place, ValueFacts((b as? MCAny)?.typeKnowledge ?: TypeKnowledge.Exact(b.type.typeId),
            snapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown))
        b.storageBinding?.let { original ->
            data.types.putAll(original.data.types)
            data.facts.copyFrom(original.data.facts, original.place, place, includeRoot = false)
            for ((key, size) in original.data.listSizes) if (key.root == original.place.root && key.path.take(original.place.path.size) == original.place.path)
                data.listSizes[Place(place.root, key.path.drop(original.place.path.size))] = size
        }
        re.storageBinding = StorageBinding(data, place, path)
        if (frozen == null) StorageAccess.encodeTo(path, b)
        if (isDynamic) data.materialize()
        if (b is MCAny && b.inferredType == null && inferredType != null)
            LogProcessor.warn("Any actual type information is lost at this assignment; use 'as' before concrete operations")
        return re
    }

    fun semanticValue(): Var<*> {
        val actual = inferredType ?: run {
            LogProcessor.error("Actual type of any '$identifier' is unknown; use 'as' before a concrete operation")
            return UnknownVar(identifier).apply { isError = true }
        }
        return buildInferredVar(actual)
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier) = semanticValue().getMemberVar(key, accessModifier)
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>, accessModifier: Member.AccessModifier) =
        semanticValue().getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)

    override fun explicitCast(type: MCFPPType): Var<*> = StorageAccess.view(this, type)
    override fun canExplicitCast(type: MCFPPType) = true
    override fun implicitCast(type: MCFPPType): Var<*> {
        if (!canImplicitCast(type)) return Var.buildCastErrorVar(type)
        if (type == MCFPPBaseType.Any) return this
        if (type == MCFPPBaseType.Object) return MCObject(identifier).apply { setAs(this@MCAny); copyPayload(this@MCAny) }
        return semanticValue().implicitCast(type)
    }
    override fun canImplicitCast(type: MCFPPType) = top.mcfpp.model.function.ParameterMatcher.accepts(this, type)
    fun copyPayload(source: MCAny) {
        payloadType = source.payloadType
        compilerPayload = source.compilerPayload
    }
    override fun clone(): MCAny = MCAny(this)
    override fun getTempVar(): MCAny {
        if (compilerPayload != null) return this
        val re = MCAny().apply { nbtPath = top.mcfpp.lib.NBTPath.temp.memberIndex(identifier); isTemp = true; bindDeclaration() }
        return re.assignedBy(this)
    }
    override fun storeToStack() { if (compilerPayload == null) StorageAccess.materialize(this) }
    override fun getFromStack() {}
    open fun buildInferredVar(type: MCFPPType): Var<*> {
        compilerPayload?.let { if (it.type == type) return it }
        return StorageAccess.read(StorageAccess.view(this, type, diagnose = false).apply { isDynamic = this@MCAny.isDynamic })
    }
}

class MCAnyConcrete : MCAny, MCFPPValue<Any?> {
    override var value: Any?
    constructor(value: Any?, identifier: String = TempPool.getVarIdentify()) : super(identifier) { this.value = value }
    constructor(v: MCAny, value: Any?) : super(v) { this.value = value }
    constructor(v: MCAnyConcrete) : super(v) { value = v.value }
    override fun clone() = MCAnyConcrete(this)
    override fun toDynamic(replace: Boolean): Var<*> {
        if (compilerPayload != null) {
            LogProcessor.error("Compiler-only value '${compilerPayload!!.type}' cannot be materialized as any")
            return this
        }
        StorageAccess.materialize(this)
        val re = MCAny(this).apply { isDynamic = true }
        if (replace) replacedBy(re)
        return re
    }
}
