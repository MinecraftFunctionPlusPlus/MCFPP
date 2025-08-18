package top.mcfpp.model.function

import top.mcfpp.model.scope.NoStackFunctionScope

class NoStackFunction(identifier: String, parent: Function) : Function(identifier, context = null) {

    init {
        this.parent.add(parent)
        field = NoStackFunctionScope(parent.field)
    }

}