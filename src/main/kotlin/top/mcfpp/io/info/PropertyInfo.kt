package top.mcfpp.io.info

import top.mcfpp.model.property.Property
import top.mcfpp.model.Member

data class PropertyInfo(
    val identifier: String,
    val getter: GetterInfo<*>?,
    val setter: SetterInfo<*>?,
    val accessModifier: Member.AccessModifier
): ModelInfo<Property> {
    override fun get(): Property {
        return Property(identifier, getter?.get(), setter?.get()).also { it.accessModifier = accessModifier }
    }

    companion object {
        fun from(property: Property): PropertyInfo{
            return PropertyInfo(property.identifier, property.accessor?.let { GetterInfo.from(it) },
                property.mutator?.let { SetterInfo.from(it) }, property.accessModifier)
        }
    }
}
