package top.mcfpp.core.minecraft

import top.mcfpp.core.lang.PrivateVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.PlayerVar
import top.mcfpp.model.compound.CompoundData
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

        override val instanceData: CompoundData by lazy {
            CompoundData("PlayerInventory", "mcfpp.minecraft").apply {
                injectedBy(PlayerInventory::class.java)
            }
        }
    }
}