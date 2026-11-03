package top.mcfpp.core.lang.entity

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.context.ContextAccessException
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.*
import top.mcfpp.mni.annotation.Name
import top.mcfpp.model.Member
import top.mcfpp.model.property.Property
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Source address syntax retains assignment sugar; its leaf value is an ordinary reference. */
internal class EntityDataAddress private constructor(
    val target: Var<*>,
    val valueType: MCFPPType,
    private val basePath: MCString? = null,
    private val suffix: List<ReferencePathPart> = emptyList(),
    private val objective: MCString? = null,
    private val scoreMap: Boolean = false,
    private val retained: Var<*>? = null
) : Var<EntityDataAddress>(TempPool.getVarIdentify()), Indexable {
    override var type: MCFPPType = if (scoreMap) MCFPPDictType(MCFPPScoreRefType)
        else if (objective != null) MCFPPScoreRefType else if (basePath == null) valueType else MCFPPNBTRefType(valueType)

    private val reference: Var<*>? = retained ?: when {
        scoreMap || basePath == null && objective == null -> null
        objective != null -> ReferenceValues.create(MCFPPScoreRefType, ReferenceKind.SCORE_ENTITY,
            mapOf("objective" to objective), target)
        else -> ReferenceValues.create(type as MCFPPNBTRefType, ReferenceKind.NBT_ENTITY,
            mapOf("path" to requireNotNull(basePath)), target, pathSuffix = suffix)
    }

    init {
        reference?.let {
            identifier = it.identifier
            symbol = it.symbol
            storageBinding = it.storageBinding
            nbtPath = it.nbtPath.clone()
        }
    }

    fun bareRead(): Var<*> = reference ?: fail("Select an entity NBT field or score objective to create a reference")
    fun requireUsable(single: Boolean = true) {
        if (single) ReferenceAccess.requireReadable(bareRead())
        else top.mcfpp.core.lang.context.ContextBorrowing.diagnostic(bareRead())?.let { throw ContextAccessException(it) }
    }
    fun readInto(value: Var<*>, success: ScoreBool) = ReferenceAccess.readInto(bareRead(), value, success)
    fun write(source: Var<*>): EntityDataAddress {
        ReferenceAccess.set(bareRead(), source)
        return this
    }
    override fun doAssignedBy(b: Var<*>) = write(b)
    override fun clone() = EntityDataAddress(target, valueType, basePath, suffix, objective, scoreMap, reference)
    override fun getTempVar(): EntityDataAddress = clone()

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val schema = (valueType as? MCFPPDataTemplateType)?.also { it.tryResolve() }?.template ?: return null to true
        val field = schema.scope.getVar(key) ?: return null to true
        val names = field.annotations.filterIsInstance<Name>()
        val name = names.firstOrNull { it.version == Project.config.version }?.name
            ?: names.lastOrNull { it.version == null }?.name ?: field.identifier
        val address = if (basePath == null) EntityDataAddress(target, field.type, text(StorageAccess.quotedKey(name)))
            else EntityDataAddress(target, field.type, basePath, suffix + ReferencePathPart.Field(name))
        return address to (accessModifier >= field.accessModifier)
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        val element: Var<*> = when {
            scoreMap -> if (index !is MCString) fail("Entity score objective requires a string") else {
                staticText(index)?.let { name ->
                    if (!name.matches(Regex("[A-Za-z0-9_.+\\-]+"))) return property(fail("Invalid scoreboard objective name"))
                }
                EntityDataAddress(target, MCFPPBaseType.Int, objective = StorageAccess.capture(index) as MCString)
            }
            valueType is MCFPPListType || valueType is MCFPPImmutableListType -> {
                if (index !is MCInt) fail("Entity list address requires an int index") else {
                    val owned = StorageAccess.capture(index)
                    EntityDataAddress(target, (valueType as MCFPPTypeWithGeneric).generic.single(), basePath,
                        suffix + ReferencePathPart.Index(owned))
                }
            }
            valueType is MCFPPDictType -> {
                val key = (index as? MCString)?.let(::staticText)
                if (key == null) fail("Entity dictionary projection requires a constant key with verified path escaping")
                else EntityDataAddress(target, valueType.generic.single(), basePath, suffix + ReferencePathPart.Field(key))
            }
            TypeRelations.arrayElementType(valueType.typeId) != null -> {
                if (index !is MCInt) fail("Entity array address requires an int index")
                else EntityDataAddress(target, TypeRelations.arrayElementType(valueType.typeId)!!, basePath,
                    suffix + ReferencePathPart.Index(StorageAccess.capture(index)))
            }
            else -> fail("Cannot index entity address '$valueType'")
        }
        return property(element)
    }

    private fun property(value: Var<*>) = PropertyVar(Property.buildSimpleProperty(value), value, this)
    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar(identifier).apply { isError = true }
    }

    companion object {
        private fun text(value: String) = StorageAccess.literal(MCFPPBaseType.String, top.mcfpp.analysis.CompilerValue.Text(value)) as MCString
        private fun staticText(value: MCString): String? = StorageAccess.snapshot(value)?.let {
            ((it as? top.mcfpp.analysis.CompilerValue.Typed)?.payload as? top.mcfpp.analysis.CompilerValue.Text)?.value
        }
        fun nbt(target: Var<*>, type: MCFPPType) = EntityDataAddress(StorageAccess.capture(target), type)
        fun scores(target: Var<*>) = EntityDataAddress(StorageAccess.capture(target), MCFPPBaseType.Int, scoreMap = true)
        fun selected(value: Var<*>): EntityDataAddress? = value as? EntityDataAddress ?: (value as? PropertyVar)?.field as? EntityDataAddress
    }
}
