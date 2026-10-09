package top.mcfpp.antlr

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.Var

internal fun negateNumber(value: Var<*>): Var<*> = StorageAccess.unary(value, "-")

internal fun computeFloatCompound(left: MCFloat, right: Var<*>, operation: String): Var<*> =
    StorageAccess.binary(left, right, operation)
