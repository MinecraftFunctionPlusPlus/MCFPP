package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.UUID

class Void: Var<Void>("void") {
    override var type: MCFPPType = MCFPPBaseType.Void

    override fun onAssign(b: Var<*>) : Void {
        LogProcessor.error("Cannot assign value to void type variable")
        return this
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        LogProcessor.error(TextTranslator.VOID_CAST_ERROR.translate())
        return buildCastErrorVar(type)
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        LogProcessor.error(TextTranslator.VOID_CAST_ERROR.translate())
        return buildCastErrorVar(type)
    }

    override fun clone(): Void {
        return Void()
    }

    override fun getTempVar(): Void {
        return this
    }

    override fun storeToStack() {
        LogProcessor.error("Cannot store void type variable to stack")
    }

    override fun getFromStack() {
        LogProcessor.error("Cannot get void type variable from stack")
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        LogProcessor.error("Cannot get member from void type variable")
        return UnknownVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        LogProcessor.error("Cannot get member function from void type variable")
        return UnknownFunction(key) to true
    }

    override fun toNBTVar(): NBTBasedData<*> {
        LogProcessor.error("Cannot convert void type variable to NBT")
        return NBTBasedDataConcrete(StringTag("void"),"unknown")
    }

    companion object {
        val data = CompoundData("void","mcfpp")
    }
}