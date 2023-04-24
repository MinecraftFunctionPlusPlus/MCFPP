package top.alumopper.mcfpp.test

import top.alumopper.mcfpp.lang.ClassPointer
import top.alumopper.mcfpp.lang.INativeClass
import top.alumopper.mcfpp.lang.Var

class NativeTest1(vars: Array<Var?>?, cls: ClassPointer?) : INativeClass(vars, cls) {
    init {
        System.out.println("MNI > Create new Instance!")
    }

    companion object {
        fun test(vars: Array<Var>, cls: ClassPointer?) {
            System.out.println("MNI > Hello Minecraft!")
            for (v in vars) {
                System.out.println("MNI > Get argument " + v.identifier)
            }
        }
    }
}