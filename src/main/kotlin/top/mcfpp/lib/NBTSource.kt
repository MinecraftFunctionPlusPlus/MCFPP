package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.lang.SelectorVar
import top.mcfpp.lang.resource.ResourceID
import top.mcfpp.lang.resource.Storage
import top.mcfpp.lang.resource.StorageConcrete
import top.mcfpp.lang.value.MCFPPValue

interface NBTSource {
    fun toCommand(): Command
}

class StorageSource(val storage: ResourceID): NBTSource{
    override fun toCommand(): Command {
        if(storage is StorageConcrete){
            return Command("storage $storage")
        }
        return Commands.buildMacroCommand(Command("storage").buildMacro(storage.identifier))
    }
}

class EntitySource(val entity: SelectorVar): NBTSource{
    override fun toCommand(): Command {
        TODO("Not yet implemented")
    }

}