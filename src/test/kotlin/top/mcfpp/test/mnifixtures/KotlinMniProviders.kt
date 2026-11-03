@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test.mnifixtures

import top.mcfpp.annotations.MNIFunction
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.util.ValueWrapper

private fun note(name: String) {
    KotlinMniObservations.calls.add(name)
}

object KotlinMniObservations {
    val calls = arrayListOf<String>()
    val values = linkedMapOf<String, Int>()
}

@NoExternalWrites
@MNIFunction(identifier = "kotlinTopVoid")
fun kotlinTopVoid() = note("kotlinTopVoid")

@NoExternalWrites
@MNIFunction(identifier = "kotlinTopArgs", readOnlyParams = ["int"], normalParams = ["int"])
fun kotlinTopArgs(readOnly: MCInt, normal: Var<*>) {
    note("kotlinTopArgs")
    KotlinMniObservations.values["kotlinTopArgs"] = readOnly.value
    KotlinMniObservations.values["kotlinTopNormal"] = (normal as MCInt).value
}

@NoExternalWrites
@MNIFunction(identifier = "kotlinTopCaller", caller = "int")
fun kotlinTopCaller(caller: MCInt) {
    note("kotlinTopCaller")
    KotlinMniObservations.values["kotlinTopCaller"] = caller.value
}

@NoExternalWrites
@MNIFunction(identifier = "kotlinTopResult", returnType = "int")
fun kotlinTopResult(result: ValueWrapper<MCInt>) {
    note("kotlinTopResult")
    result.set(MCInt(7))
}

@NoExternalWrites
@MNIFunction(identifier = "kotlinTopAll", readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
fun kotlinTopAll(readOnly: MCInt, normal: MCInt, caller: MCInt, result: ValueWrapper<MCInt>) {
    note("kotlinTopAll")
    result.set(MCInt(readOnly.value * 100 + normal.value * 10 + caller.value))
}

@NoExternalWrites
object KotlinMniObjectProvider {
    @JvmStatic @MNIFunction(identifier = "kotlinObjectVoid")
    fun voidEntry() = note("kotlinObjectVoid")

    @JvmStatic @MNIFunction(identifier = "kotlinObjectArgs", readOnlyParams = ["int"], normalParams = ["int"])
    fun argsEntry(readOnly: MCInt, normal: Var<*>) {
        note("kotlinObjectArgs")
        KotlinMniObservations.values["kotlinObjectArgs"] = readOnly.value
        KotlinMniObservations.values["kotlinObjectNormal"] = (normal as MCInt).value
    }

    @JvmStatic @MNIFunction(identifier = "kotlinObjectCaller", caller = "int")
    fun callerEntry(caller: MCInt) {
        note("kotlinObjectCaller")
        KotlinMniObservations.values["kotlinObjectCaller"] = caller.value
    }

    @JvmStatic @MNIFunction(identifier = "kotlinObjectResult", returnType = "int")
    fun resultEntry(result: ValueWrapper<MCInt>) {
        note("kotlinObjectResult")
        result.set(MCInt(7))
    }

    @JvmStatic @MNIFunction(identifier = "kotlinObjectAll", readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
    fun allEntry(readOnly: MCInt, normal: MCInt, caller: MCInt, result: ValueWrapper<MCInt>) {
        note("kotlinObjectAll")
        result.assign(MCInt(readOnly.value * 100 + normal.value * 10 + caller.value))
    }
}

class KotlinMniCompanionProvider {
    companion object {
        @JvmStatic @NoExternalWrites @MNIFunction(identifier = "kotlinCompanionVoid")
        fun voidEntry() = note("kotlinCompanionVoid")

        @JvmStatic @NoExternalWrites @MNIFunction(identifier = "kotlinCompanionArgs", readOnlyParams = ["int"], normalParams = ["int"])
        fun argsEntry(readOnly: MCInt, normal: Var<*>) {
            note("kotlinCompanionArgs")
            KotlinMniObservations.values["kotlinCompanionArgs"] = readOnly.value
            KotlinMniObservations.values["kotlinCompanionNormal"] = (normal as MCInt).value
        }

        @JvmStatic @NoExternalWrites @MNIFunction(identifier = "kotlinCompanionCaller", caller = "int")
        fun callerEntry(caller: MCInt) {
            note("kotlinCompanionCaller")
            KotlinMniObservations.values["kotlinCompanionCaller"] = caller.value
        }

        @JvmStatic @NoExternalWrites @MNIFunction(identifier = "kotlinCompanionResult", returnType = "int")
        fun resultEntry(result: ValueWrapper<MCInt>) {
            note("kotlinCompanionResult")
            result.set(MCInt(7))
        }

        @JvmStatic @NoExternalWrites @MNIFunction(identifier = "kotlinCompanionAll", readOnlyParams = ["int"], normalParams = ["int"], caller = "int", returnType = "int")
        fun allEntry(readOnly: MCInt, normal: MCInt, caller: MCInt, result: ValueWrapper<MCInt>) {
            note("kotlinCompanionAll")
            result.set(MCInt(readOnly.value * 100 + normal.value * 10 + caller.value))
        }

        @JvmStatic @NoExternalWrites
        @MNIFunction(identifier = "kotlinDefaultArgument", normalParams = ["int"])
        fun defaultArgument(value: MCInt = MCInt(99)) {
            KotlinMniObservations.values["kotlinDefaultArgument"] = value.value
        }

        @JvmStatic @NoExternalWrites
        @MNIFunction(identifier = "kotlinSuspend", normalParams = ["int"])
        suspend fun suspendEntry(value: MCInt) {
            KotlinMniObservations.values["kotlinSuspend"] = value.value
        }
    }
}
