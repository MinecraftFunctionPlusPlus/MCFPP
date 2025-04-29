package top.mcfpp.io.info

import top.mcfpp.model.compound.Enum

data class EnumInfo (
    var namespace: String,
    var identifier: String,
    var values: ArrayList<EnumMemberInfo>
): ModelInfo<Enum> {
    override fun get(): Enum {
        val e = Enum(identifier, namespace)
        values.forEach {
            e.addMember(it.get())
        }
        return e
    }

    companion object {
        fun from(enum: Enum): EnumInfo{
            return EnumInfo(
                enum.namespace,
                enum.identifier,
                ArrayList(enum.members.map { EnumMemberInfo.from(it.value) })
            )
        }
    }
}