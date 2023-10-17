package top.mcfpp.lib

import top.mcfpp.command.CommandList

class NoStackFunction(identifier: String, parent: Function) : Function(identifier) {

    init {
        this.parent.add(parent)
        field = NoStackFunctionField(parent.field, null)
    }

}