package top.mcfpp.core.lang.entity

import top.mcfpp.command.Command
import top.mcfpp.core.lang.Var
import top.mcfpp.core.minecraft.PlayerInventory
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

open class PlayerVar : Var<PlayerVar> {

    var entityVar: EntityVar

    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier){
        entityVar = EntityVar(identifier)
    }

    constructor(b: PlayerVar) : super(b){
        entityVar = b.entityVar.clone()
    }

    override fun doAssignedBy(b: Var<*>): PlayerVar {
        when(b){
            is PlayerVar -> {
                entityVar = entityVar.assignedBy(b.entityVar)
            }

            is EntityVar -> {
                entityVar = entityVar.assignedBy(b)
            }

            else -> {
                LogProcessor.error(TextTranslator.ASSIGN_ERROR.translate(b.type.typeName, type.typeName))
            }
        }
        return this
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun clone(): PlayerVar {
        return PlayerVar(this)
    }

    override fun getTempVar(): PlayerVar {
        return PlayerVar().assignedBy(this)
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return when(key){
            "Inventory" -> {
                PlayerInventory(this) to true
            }

            else -> {
                data.getVar(key) to true
            }
        }
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyArgs, normalArgs) to true
    }

    override fun toCommandPart(): Command {
        return entityVar.toCommandPart()
    }

    companion object {
        val data = CompoundData("Player", "mcfpp")
    }
}

