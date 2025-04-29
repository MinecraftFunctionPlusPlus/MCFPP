package top.mcfpp.io.info

import top.mcfpp.model.compound.EnumMember

data class EnumMemberInfo(
    val identifier: String,
    val value: Int
): ModelInfo<EnumMember> {
    override fun get(): EnumMember {
        return EnumMember(identifier, value)
    }

    companion object {
        fun from(member: EnumMember): EnumMemberInfo {
            return EnumMemberInfo(
                member.identifier,
                member.value
            )
        }
    }
}