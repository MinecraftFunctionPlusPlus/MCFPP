package top.mcfpp.util

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.IntArrayTag
import java.util.UUID

class MCUUID {

    val uuid: UUID

    val uuidArray: IntArrayTag

    val uuidArrayStr : String

    constructor() : this(UUID.randomUUID())

    constructor(uuid: UUID){
        this.uuid = uuid
        val array = IntArray(4)
        array[0] = uuid.leastSignificantBits.toInt()
        array[1] = (uuid.leastSignificantBits shr 32).toInt()
        array[2] = uuid.mostSignificantBits.toInt()
        array[3] = (uuid.mostSignificantBits shr 32).toInt()
        uuidArray = IntArrayTag(array)
        uuidArrayStr = SNBTUtil.toSNBT(uuidArray)
    }
}