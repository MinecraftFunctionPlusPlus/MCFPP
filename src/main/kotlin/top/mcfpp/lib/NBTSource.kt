package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.lang.SelectorVar
import top.mcfpp.lang.arg.Pos
import top.mcfpp.lang.resource.ResourceID
import top.mcfpp.lang.resource.Storage
import top.mcfpp.lang.resource.StorageConcrete
import java.io.Serializable

interface NBTSource: Serializable {
    fun toCommand(): Command
}

class StorageSource(val storage: String): NBTSource{
    override fun toCommand(): Command {
        return Command("storage $storage")
    }
}

class EntitySource(val entity: SelectorVar): NBTSource{
    override fun toCommand(): Command {
        return Command.build("entity").build(entity.value.toCommandPart())
    }
}

class BlockSource(val pos: Pos): NBTSource{
    override fun toCommand(): Command {
        return Command.build("block").build(pos.toCommandPart())
    }
}