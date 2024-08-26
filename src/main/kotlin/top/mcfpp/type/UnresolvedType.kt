package top.mcfpp.type

import top.mcfpp.model.field.IFieldWithType

class UnresolvedType(type: String) : MCFPPType() {

    val originalTypeString : String = type

    override val typeName: String
        get() = "UnresolvedType[${originalTypeString}]"

    override fun toString(): String {
        return typeName
    }

    fun resolve(typeScope: IFieldWithType): MCFPPType {
        return parseFromIdentifier(originalTypeString, typeScope)
    }
}