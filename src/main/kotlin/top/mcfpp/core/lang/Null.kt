package top.mcfpp.core.lang

import top.mcfpp.command.Command
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.model.Member
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

object Null: Var<Null>("") {

    val data = CompoundData("null","mcfpp")
    private fun readResolve(): Any = Null

    override var type: MCFPPType = MCFPPPrivateType.Null

    override fun doAssignedBy(b: Var<*>) : Null {
        LogProcessor.error("Cannot assign value to null type variable")
        return this
    }

    override fun canAssignedBy(b: Var<*>) = false

    override fun explicitCast(type: MCFPPType): Var<*> {
        LogProcessor.error(TextTranslator.VOID_CAST_ERROR.translate())
        return buildCastErrorVar(type)
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        LogProcessor.error(TextTranslator.VOID_CAST_ERROR.translate())
        return buildCastErrorVar(type)
    }

    override fun clone(): Null = Null

    override fun getTempVar(): Null = Null

    override fun storeToStack() {
        LogProcessor.error("$identifier is null")
    }

    override fun getFromStack() {
        LogProcessor.error("$identifier is null")
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        LogProcessor.error("$identifier is null")
        return UnknownVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        LogProcessor.error("$identifier is null")
        return UnknownFunction(key) to true
    }

    override fun toNBTVar(): NBTBasedData {
        LogProcessor.error("$identifier is null")
        return NBTBasedDataConcrete(StringTag("void"),"unknown")
    }

    override fun toCommandPart(): Command {
        return Command("top.mcfpp.lang.Void")
    }
}