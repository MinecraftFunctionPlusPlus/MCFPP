package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.exception.VariableConverseException

class Selector(identifier: String?) : Var() {
    @get:Override
    override var type = "selector"
    var text: String? = null

    init {
        this.identifier = identifier!!
    }

    @Override
    override fun assign(b: Var?) {
        if (b is MCString) {
            text = b.toString()
        } else {
            throw VariableConverseException()
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Selector {
        TODO()
    }
}