package top.mcfpp.model.function

import top.mcfpp.core.lang.ClassPointer
import top.mcfpp.core.lang.DataTemplateObject
import top.mcfpp.model.DataTemplate
import java.util.*
class DataConstructor(val data: DataTemplate): Function("_init_" + data.identifier.lowercase(Locale.getDefault()) + "_" + data.constructors.size, data, false, context = null) {
    init {
        val thisObj = DataTemplateObject(data, "this")
        thisObj.identifier = "this"
        field.putVar("this",thisObj)
    }
}

