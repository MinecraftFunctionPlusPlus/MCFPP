package top.mcfpp.core.lang.obj


import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.core.lang.nbt.NBTDictionaryConcrete
import top.mcfpp.mni.annotation.ConcreteOnly
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate


/**
 * 一个数据模板对象
 */
open class DataTemplateObject : Var<DataTemplateObject> {

    val templateType: DataTemplate

    var instanceField: CompoundDataField

    final override var type: MCFPPType

    /**
     * 创建一个模板对象
     * @param template 模板的类型
     * @param identifier 标识符
     */
    constructor(template: DataTemplate, identifier: String = TempPool.getVarIdentify()): super(identifier) {
        this.templateType = template
        this.identifier = identifier
        instanceField = template.field.createDataTemplateInstance(this)
        type = templateType.getType()
    }

    /**
     * 复制一个模板对象
     * @param templateObject 被复制的模板对象
     */
    constructor(templateObject: DataTemplateObject) : super(templateObject) {
        templateType = templateObject.templateType
        instanceField = templateObject.instanceField
        type = templateType.getType()
    }

    override fun doAssignedBy(b: Var<*>): DataTemplateObject {
        when (b) {

            is NBTDictionaryConcrete -> {
                val value = NBTUtil.valueToNBT(b.value.filter { it.value !is ConcreteVar<*, *> }) as CompoundTag
                if (templateType.checkCompoundStruct(value)) {
                    this.assignMembers(b.value)
                    return this
                } else {
                    LogProcessor.error("Error compound struct: ${b.value}")
                    return this
                }
            }

            is NBTBasedDataConcrete -> {
                if (b.value !is CompoundTag) {
                    LogProcessor.error("Not a compound tag: ${b.value}")
                    return this
                }
                if (templateType.checkCompoundStruct(b.value as CompoundTag)) {
                    this.assignMembers(b.value as CompoundTag)
                    return this
                } else {
                    LogProcessor.error("Error compound struct: ${b.value}")
                    return this
                }
            }

            is DataTemplateObjectConcrete -> {
                if (b.type.objectData.isSubOf(this.templateType)) {
                    this.assignMembers(b)
                    return this
                } else {
                    LogProcessor.error("Error compound struct: ${b.value}")
                    return this
                }
            }

            is DataTemplateObject -> {
                if(this is DataTemplateObjectConcrete){
                    this.toDynamic(true)
                }
                assignCommand(b)
                return this
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, this.type.typeName))
                return this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        return when(b){
            is NBTBasedDataConcrete -> {
                b.value is CompoundTag && templateType.checkCompoundStruct(b.value as CompoundTag)
            }

            is DataTemplateObjectConcrete -> {
                b.type.objectData.isSubOf(this.templateType)
            }

            is DataTemplateObject -> {
                true
            }

            else -> false
        }
    }

    private fun assignMembers(map: HashMap<String, Var<*>>){
        instanceField.forEachVar {
            if(it !is ConcreteVar<*, *>){
                it.replacedBy(it.assignedBy(map[it.identifier]!!))
            }
        }
    }

    private fun assignMembers(tag: CompoundTag){
        instanceField.forEachVar {
            if(it !is ConcreteVar<*, *>){
                it.replacedBy(it.assignedBy(NBTBasedDataConcrete(tag[it.identifier]!!)))
            }
        }
    }

    private fun assignMembers(template: DataTemplateObjectConcrete){
        instanceField.forEachVar {
            if(it !is ConcreteVar<*, *>){
                it.replacedBy(it.assignedBy(template.value[it.identifier]!!))
            }else{
                it.replacedBy(it.assignedBy(template.instanceField.getVar(it.identifier)!!))
            }
        }
    }

    fun assignCommand(obj: DataTemplateObject){
        if(parentClass() != null){
            //是成员
            //TODO 选择两个实体的代价和复制整个模板数据的代价谁更大？
            val b = if(obj.parentClass() != null) obj.getTempVar() else obj
            val c = Commands.selectRun(parent!!, Commands.dataSetFrom(nbtPath, b.nbtPath))
            Function.addCommands(c)
        }else {
            if (obj.parentClass() != null) {
                //obj是成员
                val c = Commands.selectRun(obj.parent!!, Commands.dataSetFrom(obj.nbtPath, nbtPath))
                Function.addCommands(c)
            } else {
                Function.addCommand(Commands.dataSetFrom(nbtPath, obj.nbtPath))
            }
        }
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val r = super.explicitCast(type)
        if(!r.isError) return r
        when(type){
            is MCFPPDataTemplateType -> {
                if(templateType.isParentOf(type.template) || templateType.isSubOf(type.template)){
                    val re = if(this is DataTemplateObjectConcrete){
                        DataTemplateObjectConcrete(type.template, this.value, this.identifier)
                    }else{
                        DataTemplateObject(type.template, this.identifier)
                    }
                    re.nbtPath = nbtPath
                    return re
                }else{
                    return buildCastErrorVar(type)
                }
            }

            else -> return r
        }
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return super.canExplicitCast(type) || (type is MCFPPDataTemplateType && templateType.isSubOf(type.template))
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        val r = super.implicitCast(type)
        if(!r.isError) return r
        when(type){
            is MCFPPDataTemplateType -> {
                if(this.templateType.isSubOf(type.template)){
                    val re = if(this is DataTemplateObjectConcrete){
                        DataTemplateObjectConcrete(type.template, this.value, this.identifier)
                    }else{
                        DataTemplateObject(type.template, this.identifier)
                    }
                    re.nbtPath = nbtPath
                    return re
                }else{
                    return r
                }
            }

            else -> return r
        }
    }

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return super.canImplicitCast(type) || (type is MCFPPDataTemplateType && templateType.isSubOf(type.template))
    }

    override fun clone(): DataTemplateObject {
        return DataTemplateObject(this)
    }

    override fun getTempVar(): DataTemplateObject {
        if(isTemp) return this
        val re = DataTemplateObject(templateType)
        re.isTemp = true
        return re.assignedBy(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val v = instanceField.getVar(key)?.clone(this)
        v?.parent = this
        val property = instanceField.getProperty(key)
        return if(property == null){
            Pair(null, true)
        }else{
            v!!.isDynamic = templateType.alwaysDynamic
            v.nbtPath = this.nbtPath.memberIndex(v.identifier)
            Pair(PropertyVar(property, v, this), accessModifier >= property.accessModifier)
        }
    }

    fun <T: Var<*>> getMemberVarWithT(key: String, clazz: Class<T>): T? {
        val member = instanceField.getVar(key)
        return if(member == null){
            null
        }else{
            member.parent = this
            return if (clazz.isAssignableFrom(member.javaClass)) clazz.cast(member) else null
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        //获取函数
        val member = templateType.field.getFunction(key, readOnlyArgs, normalArgs)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun onMemberVarChanged(member: Var<*>) {
        if(member is MCFPPValue<*> && isConcrete()){
            this.replacedBy(this.toConcrete())
        }
    }

    override fun replaceMemberVar(v: Var<*>) {
        instanceField.putVar(v.identifier, v, true)
    }

    fun isConcrete(): Boolean{
        if(this is DataTemplateObjectConcrete) return true
        var re = true
        instanceField.forEachVar {
            if(it is DataTemplateObject){
                if(!it.isConcrete()) {
                    re = false
                    return@forEachVar
                }
            }else{
                if (it !is MCFPPValue<*>) {
                    re = false
                    return@forEachVar
                }
            }
        }
        return re
    }

    fun toConcrete(): DataTemplateObjectConcrete {
        if (this is DataTemplateObjectConcrete) return this
        val map = HashMap<String, Var<*>>()
        instanceField.forEachVar {
            map[it.identifier] = it
        }
        return DataTemplateObjectConcrete(this, map)
    }

    fun toFunctionParam(){
        Function.extraFunction.runInFunction {
            for (field in this.instanceField.allVars){
                if(field is MCFPPValue<*> && field !is ConcreteVar<*, *>){
                    field.toDynamic(true)
                }
            }
        }
    }

    override fun toCommandPart(): Command {
        val f = getMemberFunction("toCommandPart", arrayListOf(), arrayListOf(), Member.AccessModifier.PUBLIC).first
        if(f is UnknownFunction) throw IllegalArgumentException("Cannot find toCommandPart function")
        if(f.isOverride){
            val command = (f.invoke(linkedMapOf(), this) as JavaVar).value as Command
            return command
        }else{
            return super.toCommandPart()
        }
    }

    fun isInstance(template: DataTemplate): Boolean{
        return templateType.isSubOf(template)
    }

    fun isInstance(namespace: String?, templateID: String): Boolean{
        return isInstance(GlobalField.getTemplate(namespace, templateID)!!)
    }
}

class DataTemplateObjectConcrete: DataTemplateObject, MCFPPValue<HashMap<String, Var<*>>> {

    override var value: HashMap<String, Var<*>>

    var tagCache: CompoundTag? = null
        get() {
            if(field == null) {
                field = NBTUtil.varToNBT(this) as CompoundTag
            }
            return field
        }
        private set

    /**
     * 创建一个固定的DataTemplate
     *
     * @param identifier 标识符
     * @param value 值
     */
    constructor(
        template: DataTemplate,
        value: HashMap<String, Var<*>>,
        identifier: String = TempPool.getVarIdentify()
    ) : super(template, identifier) {
        this.value = HashMap()
        if(template.checkDictionaryStruct(value)){
            for ((k,v) in value) {
                val thisV = instanceField.getVar(k)!!.assignedBy(v)
                thisV.parent = this
                instanceField.putVar(k, thisV, true)
                this.value[k] = thisV
            }
        }else{
            LogProcessor.error("Error data struct: $value")
        }
    }

    constructor(obj: DataTemplateObject, value: HashMap<String, Var<*>>) : super(obj){
        this.value = HashMap()
        if(templateType.checkDictionaryStruct(value)){
            for ((k,v) in value) {
                val thisV = instanceField.getVar(k)!!.assignedBy(v)
                thisV.parent = this
                instanceField.putVar(k, thisV, true)
                this.value[k] = thisV
            }
        }else{
            this.value = HashMap()
            LogProcessor.error("Error data struct: $value")
        }
    }

    constructor(obj: DataTemplateObjectConcrete) : super(obj){
        this.value = HashMap()
        if(templateType.checkDictionaryStruct(obj.value)){
            for ((k,v) in obj.value) {
                val thisV = instanceField.getVar(k)!!.assignedBy(v)
                thisV.parent = this
                instanceField.putVar(k, thisV, true)
                this.value[k] = thisV
            }
        }else{
            this.value = HashMap()
            LogProcessor.error("Error data struct: ${obj.value}")
        }
    }

    override fun clone(): DataTemplateObjectConcrete {
        return DataTemplateObjectConcrete(this)
    }

    override fun getTempVar(): DataTemplateObjectConcrete {
        return DataTemplateObjectConcrete(super.getTempVar(), HashMap(this.value.map { it.key to it.value.clone()}.toMap()))
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        if(templateType.annotations.any{it is ConcreteOnly}){
            LogProcessor.error(
                "Cannot convert to dynamic because of ${templateType.namespaceID} is modified by @ConcreteOnly"
            )
            return this
        }
        val parent = this.parent
        val nbt = tagCache!!
        if(parent != null){
            Function.addCommands(Commands.selectRun(parent, Commands.dataSetValue(nbtPath, nbt)))
        }else {
            Function.addCommand(Commands.dataSetValue(nbtPath, nbt))
        }
        val re = DataTemplateObject(this)
        if(replace){
            if(parentTemplate() != null) {
                (parent as DataTemplateObject).instanceField.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun toString(): String {
        return "[$type,value=${Tag.toSNBT(tagCache!!)}]"
    }

    override fun onMemberVarChanged(member: Var<*>) {
        if(member !is MCFPPValue<*>) {
            toDynamic(true)
        }else if(member !is ConcreteVar<*, *>){
            value[member.identifier] = member
            tagCache = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Tag<*>> getTag(identifier: String): T{
        if(identifier.contains(".")){
            val ids = identifier.split('.')
            var tag: Tag<*> = tagCache!!
            for (id in ids){
                tag = (tag as CompoundTag)[id]!!
            }
            return tag as T
        }else{
            return value["id"] as T
        }
    }

    fun getTagStr(identifier: String): String{
        val t = if(identifier.contains(".")){
            val ids = identifier.split('.')
            var tag: Tag<*> = tagCache!!
            for (id in ids){
                tag = (tag as CompoundTag)[id]!!
            }
            tag
        }else{
            tagCache!!["id"]!!
        }
        return Tag.toSNBT(t)
    }

}