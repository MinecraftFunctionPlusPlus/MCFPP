package top.mcfpp.lib

import top.mcfpp.lang.Storage

interface NBTSource

class StorageSource(val storage: Storage): NBTSource{
    override fun toString(): String {
        return "storage $storage"
    }
}

class EntitySource(val entity: String): NBTSource{
    override fun toString(): String {
        return "entity $entity"
    }
}