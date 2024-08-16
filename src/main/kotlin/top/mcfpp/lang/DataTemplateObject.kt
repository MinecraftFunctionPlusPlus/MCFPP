package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.CompoundTag
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.MCFPPDataTemplateType
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.DataTemplate
import top.mcfpp.model.Member
import top.mcfpp.model.field.CompoundDataField
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
import java.util.*

/**
 * 一个数据模板对象
 */
open class DataTemplateObject : Var<DataTemplateObject> {

    val templateType
        get() = (type as MCFPPDataTemplateType).template

    var instanceField: CompoundDataField

    /**
     * 创建一个模板对象
     * @param template 模板的类型
     * @param identifier 标识符
     */
    constructor(template: DataTemplate, identifier: String = UUID.randomUUID().toString()) {
        this.type = template.getType()
        this.name = identifier
        this.identifier = identifier
        instanceField = template.field.createInstance(this)
    }

    /**
     * 复制一个模板对象
     * @param templateObject 被复制的模板对象
     */
    constructor(templateObject: DataTemplateObject) : super(templateObject) {
        type = templateObject.type
        instanceField = templateObject.instanceField
    }


    override fun onAssign(b: Var<*>): DataTemplateObject {
        var v = b.implicitCast(this.type)
        if(!v.isError){
            v = b
        }
        hasAssigned = true
        when(v){
            is NBTBasedDataConcrete<*> -> {
                if(v.value !is CompoundTag){
                    throw VariableConverseException()
                }
                if(templateType.checkCompoundStruct(v.value as CompoundTag)){
                    this.assignMembers(v.value as CompoundTag)
                    return this
                }else{
                    throw VariableConverseException()
                }
            }

            is DataTemplateObjectConcrete -> {
                if(templateType.checkCompoundStruct(v.value)){
                    this.assignMembers(v.value)
                    return this
                }else{
                    throw VariableConverseException()
                }
            }

            is DataTemplateObject -> {
                if (!v.templateType.canCastTo(templateType)) {
                    throw VariableConverseException()
                }else{
                    assignCommand(v)
                    return this
                }
            }

            else -> {
                throw VariableConverseException()
            }
        }
    }

    private fun assignMembers(tag: CompoundTag){
        instanceField.forEachVar {
            it.replacedBy(it.assign(NBTBasedDataConcrete(tag.get(it.identifier))))
        }
    }

    fun assignCommand(obj: DataTemplateObject){
        if(parentClass() != null){
            //是成员
            //TODO 选择两个实体的代价和复制整个模板数据的代价谁更大？
            val b = if(obj.parentClass() != null) obj.getTempVar() else obj
            val c = Commands.selectRun(parent!!, Command("data modify $nbtPath set from ${b.nbtPath}"))
            Function.addCommands(c)
        }else{
            if(obj.parentClass() != null){
                //obj是成员
                val c = Commands.selectRun(obj.parent!!, Command("data modify ${obj.nbtPath} set from $nbtPath"))
                Function.addCommands(c)
            }else{
                Function.addCommand("data modify $nbtPath set from ${obj.nbtPath}")
            }
        }
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val r = super.explicitCast(type)
        if(!r.isError) return r
        when(type){
            MCFPPNBTType.NBT -> {
                val re = NBTBasedData<CompoundTag>(this.identifier)
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
        return re.assign(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = instanceField.getVar(key)
        return if(member == null){
            Pair(null, true)
        }else{
            member.parent = this
            Pair(member, accessModifier >= member.accessModifier)
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

    override fun onMemberChanged(member: Member) {
        if(member is MCFPPValue<*> && isConcrete()){
            this.replacedBy(this.toConcrete())
        }
    }

    fun isConcrete(): Boolean{
        if(this is DataTemplateObjectConcrete) return true
        var re = true
        instanceField.forEachVar {
            run@{
                if(it is DataTemplateObject){
                    if(!it.isConcrete()) {
                        re = false
                        return@run
                    }
                }else{
                    if (it !is MCFPPValue<*>) {
                        re = false
                        return@run
                    }
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

    companion object{



    }

}

class DataTemplateObjectConcrete: DataTemplateObject, MCFPPValue<CompoundTag>{

    override var value: CompoundTag

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

    override fun toDynamic(replace: Boolean): Var<*> {
        val parent = this.parent

        if(parent != null){
            val cmd = when(parent){
                is ClassPointer -> {
                    Commands.selectRun(parent)
                }
                is MCFPPClassType -> {
                    arrayOf(Command.build("execute as ${parent.cls.uuid} run "))
                }
                else -> TODO()

            }
            if(cmd.size == 2){
                Function.addCommand(cmd[0])
            }
            Function.addCommand(cmd.last().build("data modify $nbtPath set ${SNBTUtil.toSNBT(value)}"))
        }else {
            Function.addCommand("data modify $nbtPath set ${SNBTUtil.toSNBT(value)}")
        }
        val re = DataTemplateObject(this)
        if(replace){
            Function.currFunction.field.putVar(identifier, re, true)
        }
        return re
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val re = super.getMemberVar(key, accessModifier)
        var qwq = re.first
        if(re.first != null) {
            //re是已知的
            val data = value.get(key)
             qwq = (re.first as Var<*>).assign(NBTBasedDataConcrete(data))
        }
        return qwq to re.second
    }

}