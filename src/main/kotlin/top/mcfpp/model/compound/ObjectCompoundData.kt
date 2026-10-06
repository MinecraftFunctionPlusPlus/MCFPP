package top.mcfpp.model.compound

import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource

interface ObjectCompoundData {
    val namespaceID: String

    val nbtPath: NBTPath
        get() = NBTPath(StorageSource("mcfpp:system")).memberIndex(namespaceID)
}
