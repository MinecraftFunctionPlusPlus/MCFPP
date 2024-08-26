package top.mcfpp.test

import top.mcfpp.`var`.lang.ClassPointer
import top.mcfpp.`var`.lang.INativeClass
import top.mcfpp.`var`.lang.Var

class NativeTest1(vars: Array<Var<*>?>?, cls: ClassPointer?) : INativeClass(vars, cls) {
    init {
        System.out.println("MNI > Create new Instance!")
    }

    companion object {
        fun test(vars: Array<Var<*>>, cls: ClassPointer?) {
            System.out.println("MNI > Hello Minecraft!")
            for (v in vars) {
                System.out.println("MNI > Get argument " + v.name)
            }
        }
    }
}