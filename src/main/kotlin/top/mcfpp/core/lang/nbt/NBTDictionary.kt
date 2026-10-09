package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NBTDictionaryData
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class NBTDictionary : NBTBasedData {

    override var type: MCFPPType = MCFPPDictType(MCFPPBaseType.Any)

    /**
     * 创建一个dict值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个dict
     * @param b 被复制的dict值
     */
    constructor(b: NBTDictionary) : super(b) { type = b.type }

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun doAssignedBy(b: Var<*>): NBTDictionary {
        top.mcfpp.analysis.StorageAccess.write(this, b)
        return this
    }

    @InsertCommand
    override fun assignCommand(a: NBTBasedData): NBTBasedData {
        top.mcfpp.analysis.StorageAccess.write(this, a)
        return this
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return null to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        val candidates = arrayListOf<Function>()
        data.scope.forEachFunction {
            candidates.add(if (it is NativeFunction) it.replaceGenericParams(mapOf("E" to (type as MCFPPDictType).generic[0])) else it)
        }
        var re = top.mcfpp.model.function.ParameterMatcher.select(candidates, key, readOnlyArgs, normalArgs)
        val iterator = data.parent.iterator()
        while (re == null && iterator.hasNext()){
            val inherited = iterator.next().getFunction(key, readOnlyArgs, normalArgs,isStatic)
            if (inherited !is UnknownFunction) re = inherited
        }
        val selected = re ?: UnknownFunction(key)
        return selected to (accessModifier.ordinal >= selected.accessModifier.ordinal)
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        val actual = if (index is MCAny && index !is MCObject) top.mcfpp.analysis.StorageAccess.actualView(index) else index
        return if(actual.type.typeId == MCFPPBaseType.String.typeId && actual is MCString){
            val element = top.mcfpp.analysis.StorageAccess.element(this, actual, (type as MCFPPDictType).generic[0])
            PropertyVar(Property.buildSimpleProperty(element), element, this)
        }else{
            LogProcessor.error("Index must be a string")
            val error = UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
            PropertyVar(Property.buildSimpleProperty(error), error, this)
        }
    }

    override fun clone() = NBTDictionary(this)

    override fun implicitCast(type: MCFPPType): Var<*> =
        if (type == this.type) this else super.implicitCast(type)

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return super.canImplicitCast(type)
    }

    companion object{
        val data by lazy {
            CompoundData("dict", "mcfpp.lang").apply {
                initialize()
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(NBTDictionaryData::class.java)
            }
        }
    }
}
