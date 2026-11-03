@file:JvmName("KotlinTopLevelProvider")

package example.kotlinmni

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.util.ValueWrapper

@NoExternalWrites
@MNIFunction(identifier = "topLevelDouble", normalParams = ["int"], returnType = "int")
fun topLevelDouble(value: MCInt, result: ValueWrapper<MCInt>) {
    result.assign(MCInt(value.value * 2))
}
