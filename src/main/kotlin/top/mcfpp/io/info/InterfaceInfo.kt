package top.mcfpp.io.info

import top.mcfpp.model.compound.Interface

class InterfaceInfo(
    var namespace: String,
    var identifier: String,
    var parents: List<InterfaceInfo>,
    var field: FieldInfo
): ModelInfo<Interface> {

    override fun get(): Interface {
        infoCache[this]?.let { return it }
        val i = Interface(identifier, namespace)
        currInterface = i
        parents.forEach {
            i.extends(it.get())
        }
        i.field = field.get()

        currInterface = null
        infoCache[this] = i
        return i
    }

    companion object {

        var currInterface : Interface? = null

        private val interfaceCache = HashMap<Interface, InterfaceInfo>()

        private val infoCache = HashMap<InterfaceInfo, Interface>()


        fun from(i: Interface): InterfaceInfo{
            interfaceCache[i]?.let { return it }
            val v = InterfaceInfo(
                i.namespace,
                i.identifier,
                i.parent.map { from(it as Interface) },
                FieldInfo.from(i.field)
            )
            interfaceCache[i] = v
            return v
        }
    }
}