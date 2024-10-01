package top.mcfpp.core.lang

import com.github.javaparser.utils.Log
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

@Suppress("UNCHECKED_CAST")
abstract class ConcreteVar<T: ConcreteVar<T, V>, V: Any>: Var<T>, MCFPPValue<V>{

    final override var value: V
    constructor(identifier: String, value: V): super(identifier) {
        this.value = value
    }

    constructor(value: V): super() {
        this.value = value
    }

    constructor(b: ConcreteVar<T, V>): super(b) {
        this.value = b.value
    }

    override fun doAssignedBy(b: Var<*>): T {
        when(b.type){
            this.type -> {
                this.value = (b as ConcreteVar<*, *>).value as V
                return this as T
            }
            else -> {
                LogProcessor.error("Cannot assign ${b.type} to $type")
                return this as T
            }
        }
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun toDynamic(replace: Boolean): Var<*> {
        LogProcessor.error("Cannot lose track of $type")
        return this
    }

}