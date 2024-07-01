package top.mcfpp.model

class Enum(var identifier: String, var namespace: String){

    var members: HashMap<String, EnumMember> = HashMap()

    fun addMember(member: EnumMember){
        members[member.identifier] = member
    }

    fun addMember(identifier: String){
        members[identifier] = EnumMember(identifier, getNextMemberValue())
    }

    fun getNextMemberValue(): Int{
        val values = members.map { it.value.value }
        var i = values.last() + 1
        while (values.contains(i)){
            i++
        }
        return i
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