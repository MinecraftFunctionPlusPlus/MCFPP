package top.mcfpp.util

import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.*

object NBTUtil {

    @JvmStatic
    fun varToNBT(v: Var<*>): Tag<*>? = top.mcfpp.analysis.StorageAccess.constantEncoding(v)

    @JvmStatic
    fun ListTag.toArrayList(): ArrayList<*>{
        return ArrayList(map { it.toJava() })
    }

    @JvmStatic
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
                is ListTag -> map[key] = value.toArrayList()
                is ByteArrayTag -> map[key] = value.value
                is IntArrayTag -> map[key] = value.value
                is LongArrayTag -> map[key] = value.value
                is CompoundTag -> map[key] = value.toMap()
            }
        }
        return map
    }

    @JvmStatic
    fun<T> Tag<T>.toJava(): Any{
        return when(this){
            is ByteTag -> asByte()
            is ShortTag -> asShort()
            is IntTag -> asInt()
            is LongTag -> asLong()
            is FloatTag -> asFloat()
            is DoubleTag -> asDouble()
            is StringTag -> value
            is ListTag -> toArrayList()
            is ByteArrayTag -> value
            is IntArrayTag -> value
            is LongArrayTag -> value
            is CompoundTag -> toMap()
            else -> throw VariableConverseException()
        }
    }

    @JvmStatic
    fun String.toNBTByte(): Byte{
        return if(endsWith("b") || endsWith("B")){
            substring(0, length - 1).toByte()
        }else{
            toByte()
        }
    }

    @JvmStatic
    fun String.toNBTShort(): Short{
        return if(endsWith("s") || endsWith("S")){
            substring(0, length - 1).toShort()
        }else{
            toShort()
        }
    }

    @JvmStatic
    fun String.toNBTLong(): Long{
        return if(endsWith("l") || endsWith("L")){
            substring(0, length - 1).toLong()
        }else{
            toLong()
        }
    }

    @JvmStatic
    fun String.toNBTFloat(): Float{
        return if(endsWith("f") || endsWith("F")){
            substring(0, length - 1).toFloat()
        }else{
            toFloat()
        }
    }

    @JvmStatic
    fun String.toNBTDouble(): Double{
        return if(endsWith("d") || endsWith("D")){
            substring(0, length - 1).toDouble()
        }else{
            toDouble()
        }
    }
}
