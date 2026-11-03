package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.EntityTargetAccess
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

class EntitySource(val entity: Var<*>): NBTSource{
    val target: EntityTargetAccess get() = entity as EntityTargetAccess
    init { require(entity is EntityTargetAccess) { "Entity NBT source requires an entity target" } }
    override fun toCommand(): Command {
        return Command.build("entity").build(target.toEntityTargetCommand())
    }

    override fun toChatComponentPart(): Command {
        return Command("\"entity\":\"").build(target.toEntityTargetCommand(), false).build("\"", false)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntitySource) return false
        return sourceKey(this) == sourceKey(other)
    }

    override fun hashCode(): Int {
        return sourceKey(this).hashCode()
    }
}

class BlockSource(val pos: Pos3Var): NBTSource{
    override fun toCommand(): Command {
        return Command.build("block").build(pos.toCommandPart())
    }

    override fun toChatComponentPart(): Command {
        return Command("\"block\":\"${pos.toCommandPart()}\"")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockSource) return false
        return sourceKey(this) == sourceKey(other)
    }

    override fun hashCode(): Int {
        return sourceKey(this).hashCode()
    }
}
