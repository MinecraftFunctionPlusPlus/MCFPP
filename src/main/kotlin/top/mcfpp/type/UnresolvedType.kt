package top.mcfpp.type

import top.mcfpp.model.field.IFieldWithType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class UnresolvedType(type: String) : MCFPPType() {

    val originalTypeString : String = type

    override val typeName: String
        get() = "UnresolvedType[${originalTypeString}]"

    override fun toString(): String {
        return typeName
    }

    fun resolve(typeScope: IFieldWithType): MCFPPType {
        return parseFromIdentifier(originalTypeString, typeScope)?: run {
            LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(originalTypeString))
            MCFPPBaseType.Any
        }
    }
}