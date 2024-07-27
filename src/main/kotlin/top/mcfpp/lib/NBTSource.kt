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

class StorageSource(val storage: Storage): NBTSource{
    override fun toCommand(): Command {
        if(storage is StorageConcrete){
            return Command("storage $storage")
        }
        return Command.build("storage").buildMacro(storage.identifier)
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