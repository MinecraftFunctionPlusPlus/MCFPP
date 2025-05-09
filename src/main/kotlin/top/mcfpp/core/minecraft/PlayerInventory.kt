package top.mcfpp.core.minecraft

import top.mcfpp.core.lang.PrivateVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.PlayerVar
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType

class PlayerInventory(val player: PlayerVar): PrivateVar<PlayerInventory>() {

    override var type: MCFPPType = object : MCFPPPrivateType() {
        override val typeName: String = "PlayerInventory"
        init {
            registerType()
        }

        override fun buildReturnVar(): Var<*> {
            TODO("Not yet implemented")
        }
    }

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return data.getVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: List<Var<*>>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return data.getFunction(key, readOnlyArgs, normalArgs) to true
    }

    companion object {
        val data by lazy {
            CompoundData("PlayerInventory", "mcfpp.minecraft").apply {
                injectedBy(PlayerInventory::class.java)
            }
        }
    }

}