package top.mcfpp.lib

class NoStackFunction(identifier: String, parent: Function) : Function(identifier) {

    init {
        this.parent.add(parent)
        field = NoStackFunctionField(parent.field, null)
    }

}