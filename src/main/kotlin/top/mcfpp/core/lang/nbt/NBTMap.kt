package top.mcfpp.core.lang.nbt

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.MapOperations
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NBTMapData
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.AnonymousNativeMutator
import top.mcfpp.model.property.AbstractAccessor
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** One ordered entry list owns both keys and values; no second membership cache exists. */
open class NBTMap : NBTBasedData {
    @Suppress("MUST_BE_INITIALIZED_OR_BE_FINAL_WARNING")
    override var type: MCFPPType
    val genericType get() = (type as MCFPPMapType).generic.single()

    constructor(identifier: String = TempPool.getVarIdentify(), genericType: MCFPPType) : super(identifier) {
        type = MCFPPMapType(genericType)
    }

    constructor(source: NBTMap) : super(source) { type = source.type }

    override fun doAssignedBy(b: Var<*>): NBTMap {
        StorageAccess.write(this, b)
        return this
    }

    override fun assignCommand(a: NBTBasedData): NBTBasedData {
        StorageAccess.write(this, a)
        return this
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val value = when (key) {
            "keys" -> MapOperations.keys(this)
            "keyValueSet" -> MapOperations.dictionary(this)
            else -> return null to true
        }
        return PropertyVar(Property.buildSimpleGetter(key), value, this) to true
    }

    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                   accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        val candidates = arrayListOf<Function>()
        data.scope.forEachFunction {
            candidates.add(if (it is NativeFunction) it.replaceGenericParams(mapOf("E" to genericType)) else it)
        }
        var result = top.mcfpp.model.function.ParameterMatcher.select(candidates, key, readOnlyArgs, normalArgs)
        val parents = data.parent.iterator()
        while (result == null && parents.hasNext()) {
            val inherited = parents.next().getFunction(key, readOnlyArgs, normalArgs, isStatic)
            if (inherited !is UnknownFunction) result = inherited
        }
        val selected = result ?: UnknownFunction(key)
        return selected to (accessModifier.ordinal >= selected.accessModifier.ordinal)
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        val key = if (index is MCAny && index !is MCObject) top.mcfpp.analysis.StorageAccess.actualView(index) else index
        if (key !is MCString || key.type.typeId != MCFPPBaseType.String.typeId) {
            LogProcessor.error("Map index must be a string")
            val error = UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
            return PropertyVar(Property.buildSimpleProperty(error), error, this)
        }
        val selected = MapOperations.captureKey(key)
        val value = genericType.buildUnConcrete(TempPool.getVarIdentify())
        val accessor = object : AbstractAccessor() {
            override fun getter(caller: CanSelectMember, field: Var<*>) = MapOperations.element(this@NBTMap, selected)
        }
        val property = Property("", accessor, AnonymousNativeMutator { _, incoming ->
            MapOperations.put(this, selected, incoming)
            value
        })
        return PropertyVar(property, value, this)
    }

    override fun clone() = NBTMap(this)

    companion object {
        val data by lazy {
            CompoundData("map", "mcfpp.lang").apply {
                scope.putType("E", MCFPPGenericParamType("E", arrayListOf(MCFPPBaseType.Any)))
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(NBTMapData::class.java)
            }
        }
        internal val entryType get() = MCFPPDictType(MCFPPBaseType.Any)
        internal val entriesType get() = MCFPPListType(entryType)
    }
}
