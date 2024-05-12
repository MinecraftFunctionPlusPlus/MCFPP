package top.mcfpp.model.function

import top.mcfpp.model.field.NoStackFunctionField

class NoStackFunction(identifier: String, parent: Function) : Function(identifier) {

    init {
        this.parent.add(parent)
        field = NoStackFunctionField(parent.field, null)
    }

}