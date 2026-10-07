package top.mcfpp.io.info

import top.mcfpp.model.compound.EnumMember
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.nbt.tags.Tag

data class EnumMemberInfo(
    val identifier: String,
    val value: Int,
    val data: String
): ModelInfo<EnumMember> {
    override fun get(): EnumMember {
        return EnumMember(identifier, value, Tag.toNBT(data))
    }

    companion object {
        fun from(member: EnumMember): EnumMemberInfo {
            return EnumMemberInfo(
                member.identifier,
                member.value,
                NbtEncoding.snbt(member.data)
            )
        }
    }
}
