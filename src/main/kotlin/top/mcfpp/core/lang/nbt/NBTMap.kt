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
        if (b is NBTMap) return assignCommand(b) as NBTMap
        LogProcessor.error("Cannot assign '${b.type}' to '$type'")
        return this
    }

    override fun assignCommand(a: NBTBasedData): NBTBasedData {
        if (a.storageBinding != null) return StorageAccess.copyCollection(NBTMap(this), a) as NBTMap
        if (a is NBTMapConcrete) return NBTMapConcrete(this, a.value)
        Function.addCommand(Commands.dataSetFrom(nbtPath, a.nbtPath))
        return NBTMap(this)
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
        var result: Function = UnknownFunction(key)
        data.scope.forEachFunction {
            val native = (it as NativeFunction).replaceGenericParams(mapOf("E" to genericType))
            if (native.isSelf(key, normalArgs)) result = native
        }
        val parents = data.parent.iterator()
        while (result is UnknownFunction && parents.hasNext())
            result = parents.next().getFunction(key, readOnlyArgs, normalArgs, isStatic)
        return result to true
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        val key = if (index is MCAny && index !is MCObject) index.semanticValue() else index
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

class NBTMapConcrete : NBTMap, MCFPPValue<HashMap<String, Var<*>>> {
    override var value: HashMap<String, Var<*>>
    internal var extraFields: HashMap<String, Var<*>> = hashMapOf()

    constructor(value: HashMap<String, Var<*>>, identifier: String = TempPool.getVarIdentify(),
                genericType: MCFPPType) : super(identifier, genericType) { this.value = LinkedHashMap(value) }

    constructor(source: NBTMap, value: HashMap<String, Var<*>>) : super(source) {
        this.value = LinkedHashMap(value.mapValues { NBTList.copyCompilerPart(it.value) })
        if (source is NBTMapConcrete) extraFields = HashMap(source.extraFields.mapValues { NBTList.copyCompilerPart(it.value) })
    }

    constructor(source: NBTMapConcrete) : super(source) {
        value = source.value
        extraFields = source.extraFields
    }

    /** The compiler payload mirrors physical paths, so a child write replaces immutable ancestors. */
    internal fun physicalValue(): NBTDictionaryConcrete {
        val rows = value.map { (key, part) ->
            NBTDictionaryConcrete(hashMapOf("key" to MCStringConcrete(StringTag(key), "key"), "value" to part), "entry")
        }
        return NBTDictionaryConcrete(HashMap(extraFields).apply {
            put("entries", NBTListConcrete(ArrayList(rows), "entries", entryType))
        }, "map_layout")
    }

    fun isAllConcrete() = top.mcfpp.analysis.ValueSnapshot.of(this) != null
    override fun clone() = NBTMapConcrete(this)

    override fun toDynamic(replace: Boolean): Var<*> {
        StorageAccess.materialize(this)
        val result = NBTMap(this)
        if (replace) {
            if (parentTemplate() != null) (parent as DataTemplateObject).instanceField.putVar(identifier, result, true)
            else Function.currFunction.scope.putVar(identifier, result, true)
        }
        return result
    }

    companion object { val data get() = NBTMap.data }
}
