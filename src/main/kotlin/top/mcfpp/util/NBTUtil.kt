package top.mcfpp.util

import net.querz.nbt.tag.*
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.model.accessor.SimpleAccessor
import top.mcfpp.core.lang.bool.MCBoolConcrete

object NBTUtil {

    fun toNBT(v : Var<*>): Tag<*>?{
        if(v !is MCFPPValue<*>) return null
        when(v){
            //is ClassPointer -> TODO()
            //is Entity -> TODO()
            //is IntTemplatePointer -> TODO()
            is JavaVar -> return AnyTag(v)
            //is JsonString -> TODO()
            is MCAnyConcrete -> TODO()
            is MCBoolConcrete -> return ByteTag(v.value)
            is MCFloatConcrete -> return FloatTag(v.value)
            is MCFPPTypeVar -> TODO()
            is MCIntConcrete -> return IntTag(v.value)
            is MCStringConcrete -> return v.value
            is NBTBasedDataConcrete -> return v.value
            //is NBTAny<*> -> return v.value
            is PropertyVar -> return toNBT(v.property.getter(v.caller))
            else -> TODO()
        }
    }

    fun toNBT(int: Int): Tag<Int>{
        return IntTag(int)
    }

    fun toNBT(double: Double): Tag<Double>{
        return DoubleTag(double)
    }

    fun toNBT(string: String): Tag<String>{
        return StringTag(string)
    }

    fun toNBT(long: Long): Tag<Long>{
        return LongTag(long)
    }

    fun toNBT(byte: Byte): Tag<Byte>{
        return ByteTag(byte)
    }

    fun toNBT(short: Short): Tag<Short>{
        return ShortTag(short)
    }

    fun toNBT(float: Float): Tag<Float>{
        return FloatTag(float)
    }

    fun toNBT(byteArray: ByteArray): Tag<ByteArray>{
        return ByteArrayTag(byteArray)
    }

    fun toNBT(longArray: LongArray): Tag<LongArray>{
        return LongArrayTag(longArray)
    }

    fun toNBT(int: IntArray): Tag<IntArray>{
        return IntArrayTag(int)
    }

    fun<T : Tag<*>?> ListTag<T>.toArrayList(): List<*>{
        return when(typeClass){
            ByteTag::class.java -> map { (it as ByteTag).asByte()}
            ShortTag::class.java -> map { (it as ShortTag).asShort()}
            IntTag::class.java -> map { (it as IntTag).asInt()}
            LongTag::class.java -> map { (it as LongTag).asLong()}
            FloatTag::class.java -> map { (it as FloatTag).asFloat()}
            DoubleTag::class.java -> map { (it as DoubleTag).asDouble()}
            StringTag::class.java -> map { (it as StringTag).valueToString()}
            ListTag::class.java -> map { (it as ListTag<*>).toArrayList() }
            ByteArrayTag::class.java -> map { (it as ByteArrayTag).value }
            IntArrayTag::class.java -> map { (it as IntArrayTag).value}
            LongArrayTag::class.java -> map { (it as LongArrayTag).value}
            CompoundTag::class.java -> map { (it as CompoundTag).toMap() }
            else -> throw VariableConverseException()
        }
    }

    fun CompoundTag.toMap(): HashMap<String, Any>{
        val map = HashMap<String, Any>()
        for(key in keySet()){
            when(val value = get(key)){
                is ByteTag -> map[key] = value.asByte()
                is ShortTag -> map[key] = value.asShort()
                is IntTag -> map[key] = value.asInt()
                is LongTag -> map[key] = value.asLong()
                is FloatTag -> map[key] = value.asFloat()
                is DoubleTag -> map[key] = value.asDouble()
                is StringTag -> map[key] = value.value
                is ListTag<*> -> map[key] = value.toArrayList()
                is ByteArrayTag -> map[key] = value.value
                is IntArrayTag -> map[key] = value.value
                is LongArrayTag -> map[key] = value.value
                is CompoundTag -> map[key] = value.toMap()
            }
        }
        return map
    }

    fun<T> Tag<T>.toJava(): Any{
        return when(this){
            is ByteTag -> asByte()
            is ShortTag -> asShort()
            is IntTag -> asInt()
            is LongTag -> asLong()
            is FloatTag -> asFloat()
            is DoubleTag -> asDouble()
            is StringTag -> value
            is ListTag<*> -> toArrayList()
            is ByteArrayTag -> value
            is IntArrayTag -> value
            is LongArrayTag -> value
            is CompoundTag -> toMap()
            else -> throw VariableConverseException()
        }
    }
}