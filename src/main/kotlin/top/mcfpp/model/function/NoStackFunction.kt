package top.mcfpp.model.function

import top.mcfpp.model.scope.NoStackFunctionScope

class NoStackFunction(identifier: String, parent: Function) : Function(identifier, context = null) {

    init {
        this.parent.add(parent)
        scope = NoStackFunctionScope(parent.scope)
        this.returnType = parent.returnType
        this.returnVar = parent.returnVar
    }

}