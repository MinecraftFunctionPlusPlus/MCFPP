package top.mcfpp.model

import top.mcfpp.lib.SbObject
import top.mcfpp.lang.type.MCFPPEnumType
import top.mcfpp.model.field.GlobalField
import top.mcfpp.util.LogProcessor

class Enum(identifier: String, namespace: String) : CompoundData(identifier, namespace){


    val sbObject : SbObject = SbObject(namespaceID)

    var members: HashMap<String, EnumMember> = HashMap()

    val values get() = members.map { it.value.value }

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

    /**
     * 获取这个类对于的classType
     */
    override val getType: () -> MCFPPEnumType = {
        MCFPPEnumType(this)
    }
}

class EnumMember{
    var identifier: String
    var value: Int

    constructor(identifier: String, value: Int) {
        this.identifier = identifier
        this.value = value
    }
}