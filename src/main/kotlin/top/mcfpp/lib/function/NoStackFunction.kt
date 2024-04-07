package top.mcfpp.lib.function

import top.mcfpp.lib.field.NoStackFunctionField

class NoStackFunction(identifier: String, parent: Function) : Function(identifier) {

    init {
        this.parent.add(parent)
        field = NoStackFunctionField(parent.field, null)
    }

}