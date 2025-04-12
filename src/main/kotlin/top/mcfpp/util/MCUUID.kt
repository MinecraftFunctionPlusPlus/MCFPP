package top.mcfpp.util

import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import java.nio.ByteBuffer
import java.util.*

class MCUUID {

    val uuid: UUID

    val uuidNBT: IntArrayTag

    val uuidSNBT : String

    constructor() : this(UUID.randomUUID())

    constructor(uuid: UUID){
        val bytes = ByteArray(16)
        val byteBuffer = ByteBuffer.wrap(bytes)
        byteBuffer.putLong(uuid.mostSignificantBits)
        byteBuffer.putLong(uuid.leastSignificantBits)
        val array = IntArray(4)
        array[0] = ByteBuffer.wrap(bytes.copyOfRange(0, 4)).int
        array[1] = ByteBuffer.wrap(bytes.copyOfRange(4, 8)).int
        array[2] = ByteBuffer.wrap(bytes.copyOfRange(8, 12)).int
        array[3] = ByteBuffer.wrap(bytes.copyOfRange(12, 16)).int
        uuidNBT = IntArrayTag(array)
        uuidSNBT = Tag.toSNBT(uuidNBT)
        this.uuid = uuid
    }

    constructor(uuid: IntArrayTag){
        fun Int.toByteArray(): ByteArray {
            return ByteBuffer.allocate(4).putInt(this).array()
        }

        val array = uuid.value
        val bytes = ByteArray(16)

        array[0].toByteArray().copyInto(bytes, 0)
        array[1].toByteArray().copyInto(bytes, 4)
        array[2].toByteArray().copyInto(bytes, 8)
        array[3].toByteArray().copyInto(bytes, 12)

        uuidNBT = uuid
        uuidSNBT = Tag.toSNBT(uuidNBT)
        this.uuid = UUID(
            ByteBuffer.wrap(bytes.copyOfRange(0, 8)).long,
            ByteBuffer.wrap(bytes.copyOfRange(8, 16)).long
        )
    }

    companion object{
        fun genFromString(string: String): MCUUID {
            return MCUUID(UUID.nameUUIDFromBytes(string.toByteArray()))
        }
    }
}