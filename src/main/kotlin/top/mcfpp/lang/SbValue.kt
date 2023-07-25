package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException

@Deprecated("")
class SbValue : Var() {
    @Override
    @Throws(VariableConverseException::class)
    override fun assign(b: Var?) {
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): SbValue {
        TODO()
    }

    override fun getTempVar(cache: HashMap<Var, String>): Var {
        TODO("Not yet implemented")
    }
}