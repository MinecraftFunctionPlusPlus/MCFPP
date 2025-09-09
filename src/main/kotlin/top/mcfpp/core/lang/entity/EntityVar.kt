package top.mcfpp.core.lang.entity

import top.mcfpp.command.Command
import top.mcfpp.core.lang.ConcreteVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
class EntityVar: ConcreteVar<EntityVar, Var<*>?> {

    override var type: MCFPPType = MCFPPEntityType.NormalSelector

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier, null)

    /**
     * 复制一个目标选择器
     * @param b 被复制的目标选择器值
     */
    constructor(b: EntityVar) : super(b)

    fun isMulti(): Boolean {
        return value is SelectorVar && !(value as SelectorVar).value.selectingSingleEntity()
    }

    override fun doAssignedBy(b: Var<*>): EntityVar {
        when(b){
            is EntityVar -> {
                this.value = b.value!!.clone()
                this.value!!.identifier = identifier
            }
            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
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
        return value?.getMemberVar(key, accessModifier)?: Pair(null, true)
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return value?.getMemberFunction(key, readOnlyArgs, normalArgs, accessModifier)?: Pair(UnknownFunction(key), true)
    }

    override fun toCommandPart(): Command {
        return value?.toCommandPart()?: Command("")
    }
}