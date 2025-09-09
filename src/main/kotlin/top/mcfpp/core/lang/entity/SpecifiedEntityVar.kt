package top.mcfpp.core.lang.entity

import top.mcfpp.command.Command
import top.mcfpp.core.lang.ConcreteVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class SpecifiedEntityVar: ConcreteVar<SpecifiedEntityVar, String> {

    var isName: Boolean = false

    override var type: MCFPPType = MCFPPEntityType.SpecifiedEntity

    /**
     * 创建一个string值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个string
     * @param b 被复制的string值
     */
    constructor(b: SpecifiedEntityVar) : super(b){
        isName = b.isName
    }

    override fun doAssignedBy(b: Var<*>): SpecifiedEntityVar {
        when (b) {
            is SpecifiedEntityVar -> value = b.value
            else -> LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun clone(): SpecifiedEntityVar {
        return SpecifiedEntityVar(this)
    }

    override fun getTempVar(): SpecifiedEntityVar {
        val temp = SpecifiedEntityVar()
        temp.isTemp = true
        temp.value = value
        return temp
    }

    override fun toCommandPart(): Command {
        return Command(value)
    }

    companion object {
        val data = CompoundData("entity","mcfpp")

        val uuidRegex = Regex("^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\$")

    }
}