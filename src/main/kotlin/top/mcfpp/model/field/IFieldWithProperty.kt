package top.mcfpp.model.field

import top.mcfpp.model.accessor.Property

interface IFieldWithProperty {

    fun putProperty(key: String, property: Property, forced: Boolean = false): Boolean

    fun getProperty(key: String): Property?

    fun containProperty(id: String): Boolean

    fun removeProperty(id : String): Property?

    fun forEachProperty(action: (Property) -> Unit)

    val allProperties: Collection<Property>

}