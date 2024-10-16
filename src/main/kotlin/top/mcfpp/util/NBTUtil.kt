package top.mcfpp.util

import net.querz.nbt.tag.*
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.bool.MCBoolConcrete

object NBTUtil {

    fun varToNBT(v : Var<*>): Tag<*>?{
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
            is UnionTypeVarConcrete -> return valueToNBT(v.value)
            is EnumVarConcrete -> return v.value.data
            else -> {
                LogProcessor.error("Cannot cast mcfpp var $v to nbt value", VariableConverseException())
                return IntTag(0)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun valueToNBT(any: Any?): Tag<*>{
        return when(any){
            null -> IntTag(0)
            is NBTBasedDataConcrete -> any.value
            is Tag<*> -> any
            is Byte -> ByteTag(any)
            is Short -> ShortTag(any)
            is Int -> IntTag(any)
            is Long -> LongTag(any)
            is Float -> FloatTag(any)
            is Double -> DoubleTag(any)
            is String -> StringTag(any)
            is List<*> -> {
                if(any.isEmpty()) return ListTag(IntTag::class.java)
                val clazz: Class<out Tag<*>> = valueToNBT(any[0]!!)::class.java
                val list = ListTag(clazz) as ListTag<Tag<*>>
                for(value in any){
                    list.add(valueToNBT(value!!))
                }
                list
            }
            is ByteArray -> ByteArrayTag(any)
            is IntArray -> IntArrayTag(any)
            is LongArray -> LongArrayTag(any)
            is HashMap<*, *> -> {
                val map = CompoundTag()
                for(key in any.keys){
                    map.put(key.toString(), valueToNBT(any[key]!!))
                }
                map
            }
            else -> {
                LogProcessor.error("Cannot cast value $any to nbt value", VariableConverseException())
                return IntTag(0)
            }
        }
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