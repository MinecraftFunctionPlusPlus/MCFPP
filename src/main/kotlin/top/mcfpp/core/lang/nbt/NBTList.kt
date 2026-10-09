package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.nbt.NBTBasedData.Companion.NBTTypeWithTag
import top.mcfpp.core.lang.*
import top.mcfpp.mni.NBTListData
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.Property
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

open class NBTList : NBTBasedData {
    override var type: MCFPPType
    val genericType: MCFPPType get() = (type as MCFPPTypeWithGeneric).generic.single()
    override val nbtType = NBTTypeWithTag.LIST
    constructor(identifier: String = TempPool.getVarIdentify(), genericType: MCFPPType) : super(identifier) {
        type = MCFPPListType(genericType)
    }
    constructor(other: NBTList) : super(other) { type = other.type }
    override fun doAssignedBy(b: Var<*>): NBTList {
        StorageAccess.write(this, b)
        return this
    }
    override fun assignCommand(a: NBTBasedData): NBTList {
        StorageAccess.write(this, a)
        return this
    }
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> =
        null to true
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                  accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        val members = type.instanceData
        val candidates = arrayListOf<Function>()
        members.scope.forEachFunction { function ->
            candidates.add(if (function is NativeFunction) function.replaceGenericParams(mapOf("E" to genericType)) else function)
        }
        var result = top.mcfpp.model.function.ParameterMatcher.select(candidates, key, readOnlyArgs, normalArgs)
        for (parent in members.parent) if (result == null) {
            val inherited = parent.getFunction(key, readOnlyArgs, normalArgs, isStatic)
            if (inherited !is UnknownFunction) result = inherited
        }
        val selected = result ?: UnknownFunction(key)
        return selected to (accessModifier.ordinal >= selected.accessModifier.ordinal)
    }
    override fun getByIndex(index: Var<*>): PropertyVar {
        val actual = if (index is MCAny && index !is MCObject) StorageAccess.actualView(index) else index
        if (actual is MCInt && actual.type.typeId == MCFPPBaseType.Int.typeId) {
            val value = StorageAccess.element(this, actual, genericType)
            val property = if (type is MCFPPImmutableListType) Property.buildSimpleGetter(value.identifier)
                else Property.buildSimpleProperty(value)
            return PropertyVar(property, value, this)
        }
        LogProcessor.error("Index must be an int")
        val error = UnknownVar("error_${identifier}_index_${index.identifier}")
        return PropertyVar(Property.buildSimpleProperty(error), error, this)
    }
    override fun clone() = NBTList(this)
    companion object {
        internal fun copyCompilerPart(value: Var<*>) = StorageAccess.capture(value)
        val data by lazy {
            CompoundData("list", "mcfpp.lang").apply {
                scope.putType("E", MCFPPGenericParamType("E", arrayListOf(MCFPPBaseType.Any)))
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(NBTListData::class.java)
            }
        }
        fun getEmpty() = StorageAccess.listLiteral(MCFPPListType(MCFPPPrivateType.Wildcard), emptyList(), "empty") as NBTList
    }
}
