package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException

class MCString : Var {
    var value: String? = null
    override var type = "string"

    constructor(value: String?) {
        this.value = value
    }

    constructor(value: String?, identifier: String?) {
        this.name = identifier!!
    }

    constructor(b: MCString) {
        value = b.value
        type = b.type
    }


    @Override
    override fun toString(): String {
        return value!!
    }

    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): MCString {
        TODO()
    }

    override fun getTempVar(cache: HashMap<Var, String>): Var {
        TODO("Not yet implemented")
    }
}