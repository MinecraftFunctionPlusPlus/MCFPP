package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.arg.Pos
import java.io.Serializable

interface NBTSource: Serializable {
    fun toCommand(): Command

    fun toChatComponentPart(): Command

}

class StorageSource(val storage: String): NBTSource{
    override fun toCommand(): Command {
        return Command("storage $storage")
    }

    override fun toChatComponentPart(): Command {
        return Command("\"storage\":\"$storage\"")
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StorageSource) return false
        return storage == other.storage
    }

    override fun hashCode(): Int {
        return storage.hashCode()
    }
}

class EntitySource(val entity: SelectorVar): NBTSource{
    override fun toCommand(): Command {
        return Command.build("entity").build(entity.value.toCommandPart())
    }

    override fun toChatComponentPart(): Command {
        return Command("\"entity\":\"${entity.value.toCommandPart()}\"")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntitySource) return false
        return entity == other.entity
    }

    override fun hashCode(): Int {
        return entity.hashCode()
    }
}

class BlockSource(val pos: Pos): NBTSource{
    override fun toCommand(): Command {
        return Command.build("block").build(pos.toCommandPart())
    }

    override fun toChatComponentPart(): Command {
        return Command("\"block\":\"${pos.toCommandPart()}\"")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockSource) return false
        return pos == other.pos
    }

    override fun hashCode(): Int {
        return pos.hashCode()
    }
}