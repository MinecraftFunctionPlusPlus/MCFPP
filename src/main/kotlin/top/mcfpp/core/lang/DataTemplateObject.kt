package top.mcfpp.core.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.CompoundTag
import top.mcfpp.command.Commands
import top.mcfpp.mni.annotation.ConcreteOnly
import top.mcfpp.model.DataTemplate
import top.mcfpp.model.Member
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*


/**
 * 一个数据模板对象
 */
open class DataTemplateObject : Var<DataTemplateObject> {

    val templateType: DataTemplate

    var instanceField: CompoundDataField

    override var type: MCFPPType
        get() = templateType.getType()
        set(_) {}

    /**
     * 创建一个模板对象
     * @param template 模板的类型
     * @param identifier 标识符
     */
    constructor(template: DataTemplate, identifier: String = UUID.randomUUID().toString()): super(identifier) {
        this.templateType = template
        this.name = identifier
        this.identifier = identifier
        instanceField = template.field.createDataTemplateInstance(this)
    }

    /**
     * 复制一个模板对象
     * @param templateObject 被复制的模板对象
     */
    constructor(templateObject: DataTemplateObject) : super(templateObject) {
        templateType = templateObject.templateType
        instanceField = templateObject.instanceField
    }

    override fun doAssignedBy(b: Var<*>): DataTemplateObject {
        when (b) {
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
                if (b.type.objectData.canCastTo(this.templateType)) {
                    this.assignMembers(b.value)
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
                b.type.objectData.canCastTo(this.templateType)
            }

            is DataTemplateObject -> {
                true
            }

            else -> false
        }
    }

    private fun assignMembers(tag: CompoundTag){
        instanceField.forEachVar {
            it.replacedBy(it.assignedBy(NBTBasedDataConcrete(tag.get(it.identifier))))
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
            MCFPPNBTType.NBT -> {
                val re = NBTBasedData(this.identifier)
                re.nbtPath = nbtPath
                return re
            }

            is MCFPPDataTemplateType -> {
                if(type.template.canCastTo(templateType)){
                    val re = DataTemplateObject(type.template, this.identifier)
                    re.nbtPath = nbtPath
                    return re
                }else{
                    return buildCastErrorVar(type)
                }
            }

            else -> return r
        }
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        val r = super.implicitCast(type)
        if(!r.isError) return r
        when(type){
            is MCFPPDataTemplateType -> {
                if(type.template.canCastTo(templateType)){
                    val re = DataTemplateObject(type.template, this.identifier)
                    re.nbtPath = nbtPath
                    return re
                }else{
                    return buildCastErrorVar(type)
                }
            }

            else -> return r
        }
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
        val member = instanceField.getProperty(key)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(PropertyVar(member, this), accessModifier >= member.accessModifier)
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
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        //获取函数
        val member = templateType.field.getFunction(key, readOnlyParams, normalParams)
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
        val compoundTag = CompoundTag()
        instanceField.forEachVar {
            if(it is DataTemplateObject){
                compoundTag.put(it.identifier, it.toConcrete().value)
            }else{
                compoundTag.put(it.identifier, NBTUtil.toNBT(it))
            }
        }
        return DataTemplateObjectConcrete(this, compoundTag)
    }

    fun toFunctionParam(){
        Function.extraFunction.runInFunction {
            for (field in this.instanceField.allVars){
                if(field is MCFPPValue<*>){
                    field.toDynamic(true)
                }
            }
        }
    }


}

class DataTemplateObjectConcrete: DataTemplateObject, MCFPPValue<CompoundTag> {

    override lateinit var value: CompoundTag

    /**
     * 创建一个固定的DataTemplate
     *
     * @param identifier 标识符
     * @param value 值
     */
    constructor(
        template: DataTemplate,
        value: CompoundTag,
        identifier: String = UUID.randomUUID().toString()
    ) : super(template, identifier) {
        this.value = value
    }

    constructor(obj: DataTemplateObject, value: CompoundTag) : super(obj){
        this.value = value
    }

    constructor(obj: DataTemplateObjectConcrete) : super(obj){
        this.value = obj.value
    }

    override fun clone(): DataTemplateObject {
        return DataTemplateObject(this)
    }

    override fun getTempVar(): DataTemplateObjectConcrete {
        return DataTemplateObjectConcrete(super.getTempVar(), this.value.clone())
    }

    override fun toDynamic(replace: Boolean): Var<*> {

        if(templateType.annotations.any{it is ConcreteOnly}){
            LogProcessor.error("Cannot convert to dynamic because of ${templateType.namespaceID} is @ConcreteOnly")
            return this
        }

        val parent = this.parent

        if(parent != null){
            Function.addCommands(Commands.selectRun(parent, Commands.dataSetValue(nbtPath, value)))
        }else {
            Function.addCommand(Commands.dataSetValue(nbtPath, value))
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
        return "[$type,value=${SNBTUtil.toSNBT(value)}]"
    }

    override fun onMemberVarChanged(member: Var<*>) {
        if(member !is MCFPPValue<*>){
            toDynamic(true)
        }else{
            val key = member.identifier
            val data = NBTUtil.toNBT(member)
            value.put(key, data)
        }
    }

}