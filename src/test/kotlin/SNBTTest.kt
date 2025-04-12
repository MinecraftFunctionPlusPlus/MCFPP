import org.junit.jupiter.api.Test
import top.mcfpp.nbt.*
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.IntTag


class SNBTTest {

    @Test
    fun testNBTtoSNBT(){
        val tag = CompoundTag().apply {
            putString("tag","string")
            putInt("value_int",1)
            putShort("value_short",1)
            putByte("value_byte",1)
            putFloat("value_float",1.0f)
            putDouble("value_double",1.0)
            putBoolean("value_bool",true)
            putLongArray("array_long", longArrayOf(1,2,3,4))
            putIntArray("array_int", intArrayOf(1,2,3,4))
            putByteArray("array_byte", byteArrayOf(1,2,3,4))
            put("list", ListTag().apply {
                add(IntTag.valueOf(1))
                add(IntTag.valueOf(2))
                add(IntTag.valueOf(3))
                add(IntTag.valueOf(4))
            })
        }
        val str = NbtUtils.nbtToSnbt(tag)
        println(str)
    }

    @Test
    fun testNBTUtil(){
        val tag1 = `compound=` (
            "tag" to "string",
            "value_bool" to true,
            "value_int" to 1,
            "array_byte" to `array=`(byteArrayOf(1,2,3,4)),
            "array_int" to `array=`(1,2,3,4),
            "array_long" to `array=`(1L,2L,3L,4L),
            "list" to `list=`(1,2L,"string")
            )
        println(tag1)
    }

    @Test
    fun testSNBTtoNBT(){
        val tag = NbtUtils.snbtToNbt("""
            {
                array_byte: [B; 1B, 2B, 3B, 4B],
                array_int: [I; 1, 2, 3, 4],
                array_long: [L; 1L, 2L, 3L, 4L],
                list: [
                    1,
                    2,
                    3,
                    4
                ],
                tag: "string",
                value_bool: 1b,
                value_byte: 1b,
                value_double: 1.0d,
                value_float: 1.0f,
                value_int: 1,
                value_short: 1s
            }
        """.trimIndent())
        println(tag)
    }

}