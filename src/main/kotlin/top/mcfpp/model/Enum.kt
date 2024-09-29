package top.mcfpp.model

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.IntTag
import net.querz.nbt.tag.StringTag
import net.querz.nbt.tag.Tag
import top.mcfpp.lib.SbObject
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor

class Enum(identifier: String, namespace: String) : CompoundData(identifier, namespace){

    val sbObject : SbObject = SbObject(namespaceID)

    var members: HashMap<String, EnumMember> = HashMap()

    private val values get() = members.map { it.value.value }

    /**
     * 获取这个枚举对应的enumType
     */
    override val getType: () -> MCFPPEnumType = {
        MCFPPEnumType(this)
    }

    init {
        GlobalField.scoreboards[namespaceID] = sbObject
    }

    fun addMember(member: EnumMember){
        if(values.contains(member.value)){
            LogProcessor.error("Enum member value conflict: ${member.identifier}(${member.value})")
        }
        members[member.identifier] = member
    }

    fun addMember(identifier: String){
        members[identifier] = EnumMember(identifier, getNextMemberValue())
    }

    fun getNextMemberValue(): Int{
        val values = members.map { it.value.value }
        if(values.isEmpty()) return 0
        var i = values.last() + 1
        while (values.contains(i)){
            i++
        }
        return i
    }

    fun getNBTCompoundTag(): CompoundTag{
        val tag = CompoundTag()
        members.forEach {
            val memberTag = CompoundTag()
            memberTag.put("identifier", StringTag(it.value.identifier))
            memberTag.put("value", IntTag(it.value.value))
            memberTag.put("data", it.value.data)
            tag.put(it.key, memberTag)
        }
        return tag
    }

    fun getMember(value: Int): EnumMember?{
        return members.values.find { it.value == value }
    }
}

data class EnumMember(var identifier: String, var value: Int, var data: Tag<*> = IntTag()){

    fun dataAsString(): String?{
        return if (data is StringTag) (data as StringTag).value else null
    }

    fun dataAsInt(): Int?{
        return if (data is IntTag) (data as IntTag).asInt() else null
    }

}