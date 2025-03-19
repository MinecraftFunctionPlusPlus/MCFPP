package top.mcfpp.core.lang.entity

import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class EntityVar: Var<EntityVar> {

    override var type: MCFPPType = MCFPPEntityType.EntityBase

    var selectorVar: SelectorVar? = null
    var specifiedEntityVar: SpecifiedEntityVar? = null

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个目标选择器
     * @param b 被复制的目标选择器值
     */
    constructor(b: EntityVar) : super(b)

    fun isMulti(): Boolean {
        return selectorVar != null && !selectorVar!!.value.selectingSingleEntity()
    }

    override fun doAssignedBy(b: Var<*>): EntityVar {
        return when(b){
            is EntityVar -> {
                if(b.selectorVar != null){
                    selectorVar = b.selectorVar!!
                }else if(b.specifiedEntityVar != null){
                    specifiedEntityVar = SpecifiedEntityVar(identifier)
                    specifiedEntityVar = specifiedEntityVar!!.assignedBy(b.specifiedEntityVar!!) as SpecifiedEntityVar
                }else{
                    throw IllegalStateException("EntityVar is not assigned")
                }
                this
            }
            is SelectorVar -> {
                selectorVar = b
                this
            }
            is SpecifiedEntityVar -> {
                specifiedEntityVar = SpecifiedEntityVar(identifier)
                specifiedEntityVar = specifiedEntityVar!!.assignedBy(b) as SpecifiedEntityVar
                this
            }
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
                this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return when(b){
            is EntityVar -> true
            is SelectorVar -> true
            is SpecifiedEntityVar -> true
            else -> false
        }
    }

    override fun clone(): EntityVar {
        return EntityVar(this)
    }

    override fun getTempVar(): EntityVar {
        val temp = EntityVar()
        temp.isTemp = true
        return temp.assignedBy(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

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

    override fun toCommandPart(): Command {
        return if(selectorVar != null){
            selectorVar!!.value.toCommandPart()
        }else{
            specifiedEntityVar!!.toCommandPart()
        }
    }
}