package top.mcfpp.lang

import top.mcfpp.model.Template

class IntTemplateType : CompoundDataType {

    override val type: String
        get() = "StructType@${dataType.identifier}"

    constructor(type: Template) : super(type)
}