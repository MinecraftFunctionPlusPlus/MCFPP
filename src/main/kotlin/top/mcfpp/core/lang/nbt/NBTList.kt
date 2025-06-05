package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.NBTListConcreteData
import top.mcfpp.mni.NBTListData
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.property.AnonymousNativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

/**
 * 表示一个列表类型。基于NBTBasedData实现。
 */
open class NBTList : NBTBasedData {

    @Suppress("MUST_BE_INITIALIZED_OR_BE_FINAL_WARNING")
    override var type: MCFPPType
        get() = (field as? MCFPPDeclaredConcreteType)?.type ?: field

    var genericType: MCFPPType

    override var nbtType = NBTBasedData.Companion.NBTTypeWithTag.LIST

    /**
     * 创建一个list值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify(),
                genericType : MCFPPType
    ) : super(identifier){
        type = MCFPPListType(genericType)
        this.genericType = genericType
    }

    /**
     * 复制一个list
     * @param b 被复制的list值
     */
    constructor(b: NBTList) : super(b){
        type = b.type
        this.genericType = (type as MCFPPListType).generic
    }

    override fun doAssignedBy(b: Var<*>): NBTList {
        return when (b) {
            is NBTList -> {
                if(genericType == b.genericType || (b is NBTListConcrete && b.isEmptyTemp)){
                    assignCommand(b)
                }else{
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                    return this
                }
            }
            is NBTBasedDataConcrete -> {
                if (b.nbtType == this.nbtType) {
                    assignCommand(b)
                } else {
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                    return this
                }
            }

            is NBTBasedData -> {
                if (b.nbtType == this.nbtType) {
                    assignCommand(b)
                } else {
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                    return this
                }
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is NBTBasedData){
            return b.nbtType == this.nbtType
        }
        return false
    }


    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : NBTList {
        nbtType = a.nbtType
        if (parentClass() != null){
            val b = if(a.parentClass() != null){
                a.getTempVar()
            }else a
            val final = Commands.selectRun(parent!!)
            if (b is NBTBasedDataConcrete) {
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetValue(nbtPath, b.value))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
            } else {
                //对类中的成员的值进行修改
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
            }
        }else{
            if(a.parentClass() != null){
                val final = Commands.selectRun(parent!!)
                //对类中的成员的值进行修改
                //a必然是不确定的
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, a.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
            }else {
                //对类中的成员的值进行修改
                if(a is NBTListConcrete){
                    return NBTListConcrete(this, a.value)
                }else if(a is NBTBasedDataConcrete){
                    return NBTListConcrete(this, ArrayList((a.value as ListTag).map {
                        NBTBasedDataConcrete(it)
                    }))
                }else{
                    Function.addCommand(Commands.dataSetFrom(nbtPath, a.nbtPath))
                }
            }
        }
        //返回值
        return NBTList(this)
    }

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param readOnlyArgs 只读参数
     * @param normalArgs 普通参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        var re: Function = UnknownFunction(key)
        data.field.forEachFunction {
            //TODO 我们约定it为NativeFunction，但是没有考虑拓展函数=
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to genericType))
            if(nf.isSelf(key, normalArgs)){
                re = nf
            }
        }
        val iterator = data.parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key, readOnlyArgs, normalArgs,isStatic)
        }
        return re to true
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        if(index is MCInt){
            val v = genericType.build(TempPool.getVarIdentify())
            v.nbtPath = nbtPath.intIndex(index)
            v.parent = this
            return PropertyVar(Property.buildSimpleProperty(v), v,this)
        }else{
            LogProcessor.error("Index must be a int")
            val re = UnknownVar("error_${identifier}_index_${index.identifier}")
            return PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
    }

    override fun clone(): NBTList {
        return NBTList(this)
    }

    companion object {
        val data by lazy {
            CompoundData("list", "mcfpp.lang").apply {
                field.putType("E", MCFPPGenericParamType("E", arrayListOf(MCFPPBaseType.Any)))
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(NBTListData::class.java)
            }
        }

    }
}

/**
 * 一个值已知的列表。
 *
 * 当一个列表部分未知的时候，仍然被作为已知列表对待。例如
 *
 * ```
 * var l = [1,2,3];
 * dynamic i = 4;
 * l[1] = 4;
 * print(l[0]);
 * ```
 *
 * 此时仍然会直接编译为`tellraw @a "1"`，而不是输出为计分板的值或者NBT的值。
 */
class NBTListConcrete: NBTList, PartialConcreteValue<ListTag, ArrayList<Var<*>>> {

    var isEmptyTemp: Boolean = false

    override var value: ArrayList<Var<*>>

    constructor(value: ArrayList<Var<*>>, identifier: String, genericType: MCFPPType) : super(identifier, genericType){
        type = MCFPPListType(genericType)
        this.value = value
    }

    constructor(list : NBTList, value: ArrayList<Var<*>>):super(list){
        this.value = value
    }

    constructor(v: NBTListConcrete) : super(v){
        this.value = v.value
        isEmptyTemp = v.isEmptyTemp
    }

    override fun clone(): NBTListConcrete {
        return NBTListConcrete(this)
    }

    fun synchronous(){
        hasStoredInStack = true
        if(value.isEmpty()) {
            Function.addCommands(Commands.buildMacroAdjustedCommands(this, Commands.dataSetValue(nbtPath, ListTag())))
            return
        }
        var isSet = true
        val list = ListTag()
        val commands = Commands.tempFunction(Function.currFunction){
            for (v in value){
                if(v is MCFPPValue<*>){
                    if(isSet){
                        list.add(NBTUtil.valueToNBT(v.value))
                    }else{
                        Function.addCommand(Commands.dataAppendValue(NBTPath.temp, NBTUtil.valueToNBT(v.value)))
                    }
                }else{
                    if(list.size != 0 && isSet){
                        isSet = false
                        Function.addCommand(Commands.dataSetValue(NBTPath.temp, list))
                        list.clear()
                    }
                    Function.addCommand(Commands.dataAppendFrom(NBTPath.temp, v.nbtPath))
                }
            }
            Function.addCommand(Commands.dataAppendFrom(nbtPath, NBTPath.temp.iteratorIndex()))
        }
        if(isSet){
            //循环内一直没更改过isSet的值，说明列表所有变量都可被追踪
            Function.addCommands(Commands.buildMacroAdjustedCommands(this, Commands.dataSetValue(nbtPath, list)))
            GlobalField.localNamespaces[commands.second.namespace]!!.field.removeFunction(commands.second)
        }else{
            Function.addCommands(Commands.buildMacroAdjustedCommands(this, commands.first))
        }
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        val re = NBTList(this)
        if(replace){
            if(parentTemplate() != null) {
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun getByIndex(index: Var<*>): PropertyVar {
        val re = if(index is MCInt){
            if(index is MCIntConcrete){
                if(index.value >= value.size){
                    LogProcessor.error("Index out of bounds")
                    val re = UnknownVar("error_${identifier}_index_${index.identifier}")
                    return PropertyVar(Property.buildSimpleProperty(re), re, this)
                }else{
                    value[index.value]
                }
            }else {
                //index未知
                if(!hasStoredInStack) synchronous()
                toDynamic(true)
                return super.getByIndex(index)
            }
        }else{
            LogProcessor.error("Index must be a int")
            val re = UnknownVar("error_${identifier}_index_${index.identifier}")
            return PropertyVar(Property.buildSimpleProperty(re), re, this)
        }
        re.nbtPath = getTempPath(re)
        re.parent = this
        re.isDynamic = true
        val property = Property(re.identifier, SimpleAccessor(), AnonymousNativeMutator { _, v ->
            if(v !is MCFPPValue<*> && !hasStoredInStack){
                synchronous()
            }
            re.assignedBy(v)
        })
        return PropertyVar(property, re, this)
    }

    override fun toString(): String {
        return "[$type,value=$value]"
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        var re: Function = UnknownFunction(key)
        data.field.forEachFunction {
            //TODO 我们约定it为NativeFunction，但是没有考虑拓展函数
            assert(it is NativeFunction)
            val nf = (it as NativeFunction).replaceGenericParams(mapOf("E" to genericType))
            if(nf.isSelf(key, normalArgs)){
                re = nf
            }
        }
        val iterator = data.parent.iterator()
        while (re is UnknownFunction && iterator.hasNext()){
            re = iterator.next().getFunction(key, readOnlyArgs, normalArgs,isStatic)
        }
        return re to true
    }

    override fun replaceMemberVar(v: Var<*>) {
        value[value.indexOfFirst { it.identifier == v.identifier }] = v
    }

    override fun isAllConcrete(): Boolean {
        return value.all { it is MCFPPValue<*> && (it !is NBTListConcrete || it.isAllConcrete()) }
    }

    override fun getConcretePart(): ListTag {
        return ListTag(value.map { NBTUtil.valueToNBT((it as MCFPPValue<*>).value) })
    }

    override fun getNotConcretePart(): ArrayList<Var<*>> {
        return ArrayList(value.filter { it !is MCFPPValue<*> })
    }

    companion object {
        val data by lazy {
            CompoundData("list", "mcfpp.lang").apply {
                extends(MCFPPNBTType.NBT.instanceData)
                injectedBy(NBTListConcreteData::class.java)
            }
        }

        fun getEmpty() = NBTListConcrete(ArrayList(), "empty", MCFPPBaseType.Any).apply { isEmptyTemp = true }

        val listTempNBTPath = NBTPath(StorageSource("mcfpp:system")).memberIndex("temp_list")

        val listIndexHashMap: HashMap<Var<*>, Int> = HashMap()

        fun getTempPath(v: Var<*>): NBTPath {
            val index = if (listIndexHashMap.containsKey(v)) {
                listIndexHashMap[v]!!
            } else {
                val re = listIndexHashMap.size
                listIndexHashMap[v] = re
                re
            }
            return listTempNBTPath.memberIndex(index.toString())
        }

    }
}
