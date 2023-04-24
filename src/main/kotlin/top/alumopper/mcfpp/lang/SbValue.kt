package top.alumopper.mcfpp.lang

import top.alumopper.mcfpp.exception.VariableConverseException

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
}