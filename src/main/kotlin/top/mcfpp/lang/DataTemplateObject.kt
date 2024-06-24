package top.mcfpp.lang

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.CompoundTag
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.DataTemplateType
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPNBTType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.Class
import top.mcfpp.model.DataTemplate
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import java.util.*

open class DataTemplateObject : Var<CompoundTag> {

    val templateType
        get() = (type as DataTemplateType).template

    /**
     * 创建一个模板对象
     * @param template 模板的类型
     * @param identifier 标识符
     */
    constructor(template: DataTemplate, identifier: String = UUID.randomUUID().toString()) {
        this.type = template.getType()
        this.identifier = identifier
    }

    /**
     * 复制一个模板对象
     * @param templateObject 被复制的模板对象
     */
    constructor(templateObject: DataTemplateObject) : super(templateObject) {
        type = templateObject.type
    }


    override fun assign(b: Var<*>): Var<CompoundTag> {
        when(b){
            is NBTBasedDataConcrete<*> -> {
                if(b.value !is CompoundTag){
                    throw VariableConverseException()
                }
                if(templateType.checkCompoundStruct(b.value as CompoundTag))
                return DataTemplateObjectConcrete(this, b.value as CompoundTag)
            }

            is DataTemplateObjectConcrete -> {
                if(templateType.checkCompoundStruct(b.value))
                return DataTemplateObjectConcrete(this, b.value)
            }

            is DataTemplateObject -> {
                if (!b.templateType.canCastTo(templateType)) {
                    throw VariableConverseException()
                }

            }

            else -> {
                throw VariableConverseException()
            }
        }
        return this
    }

    fun assignCommand(obj: DataTemplateObject){
        if(parentClass() != null){
            //是成员
            //TODO 选择两个实体的代价和复制整个模板数据的代价谁更大？
            val b = if(obj.parentClass() != null) obj.getTempVar() as DataTemplateObject else obj
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

    override fun cast(type: MCFPPType): Var<*> {
        when(type){
            MCFPPNBTType.NBT -> {
                val re = NBTBasedData<CompoundTag>(this.identifier)
                re.nbtPath = nbtPath
                return re
            }

            is DataTemplateType -> {
                if(type.template.canCastTo(templateType)){
                    val re = DataTemplateObject(type.template, this.identifier)
                    re.nbtPath = nbtPath
                    return re
                }
                LogProcessor.error("Cannot cast [${this.type}] to [$type]")
                throw VariableConverseException()
            }

            else -> {
                throw VariableConverseException()
            }
        }
    }

    override fun clone(): Var<*> {
        return DataTemplateObject(this)
    }

    override fun getTempVar(): Var<*> {
        if(isTemp) return this
        val re = DataTemplateObject(templateType)
        re.isTemp = true
        return re.assign(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = templateType.getVar(key)?.clone(this)
        return if(member == null){
            Pair(null, true)
        }else{
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