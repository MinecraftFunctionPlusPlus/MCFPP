package top.mcfpp.core.lang.nbt

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.JsonTextConcrete
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SpecifiedEntityVar
import top.mcfpp.lib.NBTChatComponent
import top.mcfpp.lib.PlainChatComponent
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

/**
 * string表示一个字符串。要声明一个字符串，应该使用string类型，例如string abc = "abc"。
 *
 * string重写了+运算符，因此能够对字符串进行加运算。例如string ps = "abc" + "def"，结果
 *得到ps为”abcdef”。
 *
 * 和MCFPP中所有变量一样，string类型有编译时静态和动态两种方式。类似上文中的声明方式，编译器
 *能发现自己能够跟踪到这个字符串的内容，因此在编译过程中会进行简单的替换。而动态则不同，动态字符
 *串通常是由于将一个jstring转换成string类型而导致的。动态字符串储存在nbt中，以供操作。
 *
 * 得益于宏和data string命令，让字符串的动态操作成为了可能。在1.19.4-的版本中，MCFPP只支持静
 *态的字符串和原始JSON文本功能。
 *
 */
open class MCString : NBTBasedData {

    override var type: MCFPPType = MCFPPBaseType.String

    /**
     * 创建一个string值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个string
     * @param b 被复制的string值
     */
    constructor(b: MCString) : super(b)

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        TODO("Not yet implemented")
    }

    override fun doAssignedBy(b: Var<*>): MCString {
        when (b) {
            is MCString -> return assignCommand(b)
            is NBTBasedDataConcrete -> {
                if(b.nbtType == NBTBasedData.Companion.NBTTypeWithTag.STRING){
                    return assignCommand(b)
                }else{
                    LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                }
            }
            else -> LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        val re = super.implicitCast(type)
        if(!re.isError) return re
        return when (type) {
            MCFPPBaseType.JsonText -> {
                if(this is MCStringConcrete){
                    JsonTextConcrete(PlainChatComponent(this.value.value))
                }else{
                    if(parentClass() != null && (parent as Var<*>).identifier != "this") {
                        JsonTextConcrete(NBTChatComponent(getTempVar(), false))
                    }else{
                        JsonTextConcrete(NBTChatComponent(this, false))
                    }
                }
            }
            else -> re
        }
    }

    override fun canImplicitCast(type: MCFPPType): Boolean {
        return super.canImplicitCast(type) || type == MCFPPBaseType.JsonText
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        val re = super.explicitCast(type)
        if(!re.isError) return re
        return when (type) {
            MCFPPBaseType.JsonText -> {
                if(this is MCStringConcrete){
                    JsonTextConcrete(PlainChatComponent(this.value.value))
                }else{
                    JsonTextConcrete(NBTChatComponent(this, false))
                }
            }
            is MCFPPEntityType -> {
                if(this is MCStringConcrete){
                    val str = this.value.value
                    val entityVar = SpecifiedEntityVar(identifier).apply {value = this@MCString.value.value}
                    entityVar.isName = !SpecifiedEntityVar.uuidRegex.matches(str)
                    entityVar
                }else{
                    SpecifiedEntityVar(identifier)
                }
            }
            else -> re
        }
    }

    override fun canExplicitCast(type: MCFPPType): Boolean {
        return type == MCFPPBaseType.JsonText || type is MCFPPEntityType || super.canExplicitCast(type)
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        if(!b.implicitCast(type).isError) return true
        if(b is NBTBasedDataConcrete){
            return b.nbtType == NBTBasedData.Companion.NBTTypeWithTag.STRING
        }
        return false
    }

    @InsertCommand
    override fun assignCommand(a: NBTBasedData) : MCString {
        nbtType = a.nbtType
        return assignCommandLambda(a,
            ifThisIsClassMemberAndAIsConcrete = {b, final ->
                b as MCStringConcrete
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
                MCString(this)
            },
            ifThisIsClassMemberAndAIsNotConcrete = {b, final ->
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
                MCString(this)
            },
            ifThisIsNormalVarAndAIsConcrete = {b ->
                MCStringConcrete(this, (b as MCStringConcrete).value)
            },
            ifThisIsNormalVarAndAIsClassMember = {b, final ->
                if(final.size == 2){
                    Function.addCommand(final[0])
                }
                final.last().build(Commands.dataSetFrom(nbtPath, b.nbtPath))
                if(final.last().isMacro){
                    Function.addCommands(final.last().buildMacroFunction())
                }else{
                    Function.addCommand(final.last())
                }
                MCString(this)
            },
            ifThisIsNormalVarAndAIsNotConcrete = {b ->
                Function.addCommand(Commands.dataSetFrom(nbtPath, b.nbtPath))
                NBTBasedData(this)
            }) as MCString
    }

    override fun getTempVar(): MCString {
        val temp = MCString()
        temp.isTemp = true
        return temp.assignCommand(this)
    }

    companion object {
        val data = CompoundData("string","mcfpp")
    }

}

class MCStringConcrete: MCString, MCFPPValue<StringTag> {

    override var value: StringTag

    /**
     * 创建一个固定的string。它的标识符和mc名一致/
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: StringTag, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    constructor(v: MCString, value: StringTag): super(v){
        this.value = value
    }

    constructor(v: MCStringConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCStringConcrete {
        return MCStringConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        NBTBasedDataConcrete(this, value).toDynamic(replace)
        return MCString(this)
    }

    override fun getTempVar(): MCString {
        return MCStringConcrete(value).apply {
            isTemp = true
        }
    }
}