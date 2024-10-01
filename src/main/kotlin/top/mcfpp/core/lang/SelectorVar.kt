package top.mcfpp.core.lang

import net.querz.nbt.tag.StringTag
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPType
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntitySelector.Companion.SelectorType
import top.mcfpp.mni.SelectorData
import top.mcfpp.model.CompoundData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*


/**
 * 目标选择器（Target Selector）可在无需指定确切的玩家名称或UUID的情况下在命令中选择任意玩家与实体。目标选择器变量可以选择一个或多个实体，
 * 目标选择器参数可以根据特定条件筛选目标。
 *
 * selector拥有一个可选泛型参数limit，用于限制目标选择器选择的实体数量。例如selector<1>将会选择一个实体。
 *
 * 在mcfpp中你可以直接让目标选择器作为命令函数的参数，也可以让目标选择器选择实体，得到一个实体或者一个实体列表，之后再对它们进行操作
 *
 * 在构造一个目标选择器示例的时候，目标选择器的类型是必然确定的，因此只有用于构造确定变量的构造函数
 *
 * @see EntityVar
 */
open class SelectorVar : ConcreteVar<SelectorVar, EntitySelector>, EntityBase {

    override var type: MCFPPType = MCFPPEntityType.Selector

    /**
     * 创建一个目标选择器。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(selector: EntitySelector, identifier: String = UUID.randomUUID().toString())
            : super(identifier, selector)

    /**
     * 复制一个目标选择器
     * @param b 被复制的目标选择器值
     */
    constructor(b: SelectorVar) : super(b)

    override fun isPlayer(): Boolean {
        return value.onlyIncludingPlayers()
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return data.getVar(key) to false
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyParams, normalParams) to true
    }

    override fun clone(): SelectorVar {
        return SelectorVar(this)
    }

    override fun getTempVar(): SelectorVar {
        return SelectorVar(value.clone())
    }

    companion object {

        val data = CompoundData("selector","mcfpp")

    }
}