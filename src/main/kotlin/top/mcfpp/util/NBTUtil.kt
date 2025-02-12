package top.mcfpp.util

import net.querz.nbt.tag.*
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.exception.VariableConverseException

object NBTUtil {

    fun varToNBT(v : Var<*>): Tag<*>?{
        if(v !is MCFPPValue<*>) return null
        return when(v){
            //is ClassPointer -> TODO()
            //is Entity -> TODO()
            is JavaVar -> if(v.value is Var<*>) varToNBT(v.value as Var<*>) else valueToNBT(v.value)
            //is JsonString -> TODO()
            is MCAnyConcrete -> varToNBT(v.value)
            is ScoreBoolConcrete -> ByteTag(v.value)
            is MCByteConcrete -> ByteTag(v.value)
            is MCShortConcrete -> ShortTag(v.value)
            is MCIntConcrete -> IntTag(v.value)
            is MCLongConcrete -> v.value
            is MCFloatConcrete -> FloatTag(v.value)
            is MCDoubleConcrete -> v.value
            is MCFPPTypeVar -> TODO()
            is MCStringConcrete -> v.value
            is NBTBasedDataConcrete -> v.value
            is UnionTypeVarConcrete -> valueToNBT(v.value)
            is EnumVarConcrete -> v.value.data
            is DataTemplateObjectConcrete -> v.value
            else -> {
                LogProcessor.error("Cannot cast mcfpp var $v to nbt value")
                IntTag(0)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun valueToNBT(any: Any?): Tag<*>{
        return when(any){
            null -> IntTag(0)
            is MCFPPValue<*> -> {
                varToNBT(any as Var<*>)?: StringTag(any.toString())
            }
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
                LogProcessor.error("Cannot cast value $any to nbt value")
                return StringTag(any.toString())
            }
        }
    }

    fun<T : Tag<*>?> ListTag<T>.toArrayList(): ArrayList<*>{
        return when(typeClass){
            ByteTag::class.java -> ArrayList(map { (it as ByteTag).asByte()})
            ShortTag::class.java -> ArrayList(map { (it as ShortTag).asShort()})
            IntTag::class.java -> ArrayList(map { (it as IntTag).asInt()})
            LongTag::class.java -> ArrayList(map { (it as LongTag).asLong()})
            FloatTag::class.java -> ArrayList(map { (it as FloatTag).asFloat()})
            DoubleTag::class.java -> ArrayList(map { (it as DoubleTag).asDouble()})
            StringTag::class.java -> ArrayList(map { (it as StringTag).valueToString()})
            ListTag::class.java -> ArrayList(map { (it as ListTag<*>).toArrayList() })
            ByteArrayTag::class.java -> ArrayList(map { (it as ByteArrayTag).value })
            IntArrayTag::class.java -> ArrayList(map { (it as IntArrayTag).value})
            LongArrayTag::class.java -> ArrayList(map { (it as LongArrayTag).value})
            CompoundTag::class.java -> ArrayList(map { (it as CompoundTag).toMap() })
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
            is BoolTag -> value
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

    fun String.toNBTByte(): Byte{
        return if(endsWith("b") || endsWith("B")){
            substring(0, length - 1).toByte()
        }else{
            toByte()
        }
    }

    fun String.toNBTShort(): Short{
        return if(endsWith("s") || endsWith("S")){
            substring(0, length - 1).toShort()
        }else{
            toShort()
        }
    }

    fun String.toNBTLong(): Long{
        return if(endsWith("l") || endsWith("L")){
            substring(0, length - 1).toLong()
        }else{
            toLong()
        }
    }

    fun String.toNBTFloat(): Float{
        return if(endsWith("f") || endsWith("F")){
            substring(0, length - 1).toFloat()
        }else{
            toFloat()
        }
    }

    fun String.toNBTDouble(): Double{
        return if(endsWith("d") || endsWith("D")){
            substring(0, length - 1).toDouble()
        }else{
            toDouble()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun<T: Tag<*>> ListTag<T>.addUnchecked(index: Int, tag: Tag<*>){
        require(!(typeClass != EndTag::class.java && typeClass != tag.javaClass)) {
            String.format(
                "cannot add %s to ListTag<%s>",
                tag.javaClass.simpleName, typeClass.simpleName
            )
        }
        add(size(), tag as T)
    }


    @Suppress("UNCHECKED_CAST")
    fun<T: Tag<*>> ListTag<T>.indexOfUnchecked(tag: Tag<*>): Int{
        require(!(typeClass != EndTag::class.java && typeClass != tag.javaClass)) {
            return -1
        }
        return indexOf(tag as T)
    }

    @Suppress("UNCHECKED_CAST")
    fun<T: Tag<*>> ListTag<T>.containsUnchecked(tag: Tag<*>): Boolean{
        require(!(typeClass != EndTag::class.java && typeClass != tag.javaClass)) {
            return false
        }
        return contains(tag as T)
    }
}