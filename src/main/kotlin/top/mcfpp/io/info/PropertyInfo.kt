package top.mcfpp.io.info

import top.mcfpp.model.property.Property

data class PropertyInfo(
    val identifier: String,
    val getter: GetterInfo<*>?,
    val setter: SetterInfo<*>?
): ModelInfo<Property> {
    override fun get(): Property {
        return Property(identifier, getter?.get(), setter?.get())
    }

    companion object {
        fun from(property: Property): PropertyInfo{
            return PropertyInfo(property.identifier, property.accessor?.let { GetterInfo.from(it) }, property.mutator?.let { SetterInfo.from(it) })
        }
    }
}