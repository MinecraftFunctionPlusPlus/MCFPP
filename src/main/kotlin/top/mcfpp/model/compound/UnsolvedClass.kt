package top.mcfpp.model.compound

import top.mcfpp.io.info.ClassInfo
import top.mcfpp.io.info.GenericClassInfo

open class UnsolvedClass(val info: ClassInfo): Class("unsolved_${info.identifier}") {

    open fun resolve(): Class {
        return info.get()
    }

}

class UnsolvedObjectClass(val info: ClassInfo): ObjectClass("unsolved_${info.identifier}"){
     fun resolve(): ObjectClass {
        return info.get() as ObjectClass
    }
}

class UnsolvedGenericClass(val info: GenericClassInfo): GenericClass("unsolved_${info.identifier}", ctx = info.context){
    fun resolve(): GenericClass {
        return info.get()
    }
}

class UnsolvedGenericObjectClass(val info: GenericClassInfo): GenericObjectClass("unsolved_${info.identifier}", ctx = info.context){
    fun resolve(): GenericObjectClass {
        return info.get() as GenericObjectClass
    }
}
