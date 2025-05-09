package top.mcfpp.model.compound

import top.mcfpp.io.info.InterfaceInfo

open class UnsolvedInterface(val info: InterfaceInfo): Interface("unsolved_${info.identifier}") {

    open fun resolve(): Interface {
        return info.get()
    }

}
